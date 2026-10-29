/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.core.product.models.repositories.definition.purpose;

import io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.*;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;

/** Exact backend read port. Data constructors establish neither caller authority nor a DB witness. */
public interface InsuranceQuotePurposeReadStore {
    Mono<Read> read(Admission admission);

    record Admission(Scope scope,String issuer,String actor,Instant actorUntil,Instant permitUntil,
            Instant observedAt,long beforeNanos,NativeWindow priorWindow) {
        public Admission(Scope scope,String issuer,String actor,Instant actorUntil,Instant permitUntil,Instant observedAt,long beforeNanos){this(scope,issuer,actor,actorUntil,permitUntil,observedAt,beforeNanos,null);}
        public Admission withPriorWindow(NativeWindow window){Objects.requireNonNull(window);return new Admission(scope,issuer,actor,actorUntil,permitUntil,observedAt,beforeNanos,priorWindow==null?window:priorWindow.intersect(window));}
        public R2dbcOperationBudget operationBudget(Clock clock,LongSupplier nanos){
            return priorWindow==null?R2dbcOperationBudget.observed(observedAt,permitUntil,beforeNanos,Duration.ofSeconds(5),clock,nanos):priorWindow.operationBudget(this,clock,nanos);
        }
        public Admission {
            Objects.requireNonNull(scope);Objects.requireNonNull(issuer);Objects.requireNonNull(actor);
            Objects.requireNonNull(actorUntil);Objects.requireNonNull(permitUntil);Objects.requireNonNull(observedAt);
            if(!permitUntil.isAfter(observedAt) || permitUntil.isAfter(actorUntil)
                    || Duration.between(observedAt,permitUntil).compareTo(Duration.ofSeconds(5))>0)throw invalid();
        }
        public Duration remaining(Instant now,long nanos) {
            long elapsed=nanos-beforeNanos;long budget=Duration.between(observedAt,permitUntil).toNanos();
            if(elapsed<0 || elapsed>=budget || !now.isBefore(permitUntil))throw unavailable();
            var wall=Duration.between(now,permitUntil);var monotonic=Duration.ofNanos(budget-elapsed);var result=wall.compareTo(monotonic)<0?wall:monotonic;
            if(priorWindow!=null){var prior=priorWindow.remaining(now,nanos);if(prior.compareTo(result)<0)result=prior;}return result;
        }
        @Override public String toString(){return "InsuranceQuotePurposeReadAdmission";}
    }
    record Read(Configuration configuration,NativeWindow window) {
        public Read {Objects.requireNonNull(configuration);Objects.requireNonNull(window);}
        @Override public String toString(){return "InsuranceQuotePurposeRead";}
    }

    /** Elapsed time control anchored before real native admission; it is not an authentication proof. */
    final class NativeWindow {
        private record Span(long before,long lifetime) {}
        private final Instant recordedAt,until;
        private final List<Span> spans;
        private NativeWindow(Instant recordedAt,Instant until,List<Span> spans){this.recordedAt=recordedAt;this.until=until;this.spans=List.copyOf(spans);}
        public static NativeWindow from(Instant recordedAt,long dbNowMillis,long untilMillis,long beforeNativeNanos) {
            if(recordedAt==null || recordedAt.getNano()%1000!=0 || dbNowMillis<0 || recordedAt.toEpochMilli()!=dbNowMillis
                    || untilMillis>253402300799999L || untilMillis<=dbNowMillis || untilMillis-dbNowMillis>30000)throw invalid();
            long lifetime=(untilMillis-dbNowMillis-1)*1_000_000L;if(lifetime<=0)throw invalid();
            return new NativeWindow(recordedAt,Instant.ofEpochMilli(untilMillis),List.of(new Span(beforeNativeNanos,lifetime)));
        }
        public Instant recordedAt(){return recordedAt;}
        public Instant until(){return until;}
        public Duration remaining(Instant now,long nanos) {
            if(now==null || !now.isBefore(until))throw unavailable();var result=Duration.between(now,until);
            for(var span:spans){long elapsed=nanos-span.before;if(elapsed<0 || elapsed>=span.lifetime)throw unavailable();var cap=Duration.ofNanos(span.lifetime-elapsed);if(cap.compareTo(result)<0)result=cap;}
            return result;
        }
        public R2dbcOperationBudget operationBudget(Admission original,Clock clock,LongSupplier nanos) {
            Objects.requireNonNull(original);Objects.requireNonNull(clock);Objects.requireNonNull(nanos);
            var all=original.priorWindow()==null || original.priorWindow()==this?this:intersect(original.priorWindow());
            original.remaining(clock.instant(),nanos.getAsLong());all.remaining(clock.instant(),nanos.getAsLong());
            long lifetime=Duration.between(original.observedAt(),original.permitUntil()).toNanos();
            for(var span:all.spans){long offset=span.before-original.beforeNanos();if(offset<0)throw invalid();lifetime=Math.min(lifetime,Math.addExact(offset,span.lifetime));}
            Instant deadline=all.until.isBefore(original.permitUntil())?all.until:original.permitUntil();
            if(original.actorUntil().isBefore(deadline))deadline=original.actorUntil();
            Instant elapsedDeadline=original.observedAt().plusNanos(lifetime);if(elapsedDeadline.isBefore(deadline))deadline=elapsedDeadline;
            return R2dbcOperationBudget.observed(original.observedAt(),deadline,original.beforeNanos(),Duration.ofSeconds(5),clock,nanos);
        }
        public NativeWindow narrowUntil(Instant deadline) {
            Objects.requireNonNull(deadline);if(!deadline.isBefore(until))return this;
            long lifetime=Duration.between(recordedAt,deadline).toNanos()-1_000_000L;if(lifetime<=0)throw unavailable();
            var joined=new ArrayList<>(spans);joined.add(new Span(spans.getFirst().before,lifetime));
            return new NativeWindow(recordedAt,deadline,joined);
        }
        public NativeWindow intersect(NativeWindow other) {
            Objects.requireNonNull(other);var joined=new ArrayList<>(spans);joined.addAll(other.spans);
            return new NativeWindow(recordedAt,until.isBefore(other.until)?until:other.until,joined);
        }
        public NativeWindow narrowExternal(Instant observed,Instant deadline,long beforeExternalNanos) {
            if(observed==null || deadline==null || observed.getNano()%1000!=0 || deadline.getNano()%1000!=0
                    || !deadline.isAfter(observed) || Duration.between(observed,deadline).compareTo(Duration.ofSeconds(30))>0)throw invalid();
            long lifetime=Duration.between(observed,deadline).toNanos()-1_000_000L;if(lifetime<=0)throw invalid();
            var joined=new ArrayList<>(spans);joined.add(new Span(beforeExternalNanos,lifetime));
            return new NativeWindow(recordedAt,until.isBefore(deadline)?until:deadline,joined);
        }
        @Override public String toString(){return "InsuranceQuotePurposeNativeWindow";}
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid purpose read timing data");}
    private static IllegalStateException unavailable(){return new IllegalStateException("INSURANCE_QUOTE_PURPOSE_READ_UNAVAILABLE");}
}

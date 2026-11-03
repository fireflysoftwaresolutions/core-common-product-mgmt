package io.fireflysoftware.core.product.core.services.definition.impl;

import java.time.*;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;

/** Original request timing and actual native admission caps; not caller authentication. */
final class ProductDefinitionLifecycleWindow {
    private final Instant observedAt;
    private final long beforeNanos,actorUntil,permitUntil;
    private final Clock clock;
    private final LongSupplier nanos;
    private R2dbcOperationBudget budget;
    private Instant cap;

    private ProductDefinitionLifecycleWindow(Instant observedAt,long beforeNanos,long actorUntil,long permitUntil,Clock clock,LongSupplier nanos) {
        this.observedAt=Objects.requireNonNull(observedAt);this.beforeNanos=beforeNanos;
        this.actorUntil=actorUntil;this.permitUntil=permitUntil;this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
        if(permitUntil>actorUntil || actorUntil>253402300799999L || permitUntil>253402300799999L)throw invalid();
        var permit=Instant.ofEpochMilli(permitUntil);var maximum=observedAt.plusSeconds(30);cap=permit.isBefore(maximum)?permit:maximum;
        budget=R2dbcOperationBudget.observed(observedAt,cap,beforeNanos,Duration.ofSeconds(30),clock,nanos);
        budget.remaining();
    }
    static ProductDefinitionLifecycleWindow start(Instant observedAt,long beforeNanos,long actorUntil,long permitUntil,Clock clock,LongSupplier nanos) {
        return new ProductDefinitionLifecycleWindow(observedAt,beforeNanos,actorUntil,permitUntil,clock,nanos);
    }
    R2dbcOperationBudget budget(){budget.remaining();return budget;}
    R2dbcOperationBudget observe(Instant recordedAt,long dbNowMillis,long untilMillis,long beforeNativeNanos) {
        budget.remaining();
        if(recordedAt==null || recordedAt.getNano()%1000!=0 || dbNowMillis<0 || recordedAt.toEpochMilli()!=dbNowMillis
                || untilMillis<=dbNowMillis || untilMillis-dbNowMillis>30000 || untilMillis>actorUntil || untilMillis>permitUntil)throw invalid();
        long offset=beforeNativeNanos-beforeNanos;if(offset<0)throw invalid();
        long lifetime=(untilMillis-dbNowMillis-1)*1_000_000L;if(lifetime<=0)throw invalid();
        Instant elapsedDeadline=observedAt.plusNanos(Math.addExact(offset,lifetime));
        Instant nativeUntil=Instant.ofEpochMilli(untilMillis);Instant end=nativeUntil.isBefore(elapsedDeadline)?nativeUntil:elapsedDeadline;
        if(cap.isBefore(end))end=cap;
        var narrowed=R2dbcOperationBudget.observed(observedAt,end,beforeNanos,Duration.ofSeconds(30),clock,nanos);
        cap=end;budget=narrowed;budget.remaining();return budget;
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid product native timing data");}
    @Override public String toString(){return "ProductDefinitionLifecycleWindow";}
}

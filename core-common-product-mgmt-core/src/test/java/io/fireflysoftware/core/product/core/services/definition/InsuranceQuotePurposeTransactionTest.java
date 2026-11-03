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

package io.fireflysoftware.core.product.core.services.definition;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.Scope;
import io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeReadStore.*;
import io.r2dbc.spi.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.r2dbc.core.*;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

/** Actual Spring/driver-publisher lifecycle only; no SQL parser, role, native PG or financial proof. */
class InsuranceQuotePurposeTransactionTest {
    static final Instant BASE=Instant.parse("2026-10-26T12:00:00Z");
    static Clock clock(){return new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return BASE.plusNanos(VirtualTimeScheduler.get().now(TimeUnit.NANOSECONDS));}};}
    static LongSupplier nanos(){return ()->VirtualTimeScheduler.get().now(TimeUnit.NANOSECONDS);}
    static final class Driver implements ConnectionFactory {
        boolean stallAcquire,stallBegin,stallCommit,stallRollback,stallStatement,stallClose;
        int creates,closes,begins,commits,rollbacks,beginCancelled,commitCancelled,rollbackCancelled,statementCancelled,closeCancelled;
        boolean readOnlyIntent;IsolationLevel isolationIntent;final AtomicBoolean closed=new AtomicBoolean();
        final CountDownLatch beginReady=new CountDownLatch(1),commitReady=new CountDownLatch(1),rollbackReady=new CountDownLatch(1);
        final AtomicBoolean contextMarkerRetained=new AtomicBoolean();String expectedContextMarker;
        <T> Mono<T> stalled(CountDownLatch ready,Runnable cancellation){return Mono.create(sink->{
            sink.onCancel(cancellation::run);
            contextMarkerRetained.set(expectedContextMarker==null || Objects.equals(expectedContextMarker,sink.contextView().getOrDefault("owned-fixture-marker",null)));
            ready.countDown();
        });}

        final Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
            case "isAutoCommit"->true;case "getTransactionIsolationLevel"->IsolationLevel.READ_COMMITTED;
            case "beginTransaction"->Mono.defer(()->{begins++;if(a!=null&&a.length==1){var d=(io.r2dbc.spi.TransactionDefinition)a[0];readOnlyIntent=Boolean.TRUE.equals(d.getAttribute(io.r2dbc.spi.TransactionDefinition.READ_ONLY));isolationIntent=d.getAttribute(io.r2dbc.spi.TransactionDefinition.ISOLATION_LEVEL);}return stallBegin?this.<Void>stalled(beginReady,()->beginCancelled++):Mono.empty();});
            case "commitTransaction"->Mono.defer(()->{commits++;return stallCommit?this.<Void>stalled(commitReady,()->commitCancelled++):Mono.empty();});
            case "rollbackTransaction"->Mono.defer(()->{rollbacks++;return stallRollback?this.<Void>stalled(rollbackReady,()->rollbackCancelled++):Mono.empty();});
            case "close"->Mono.defer(()->stallClose?Mono.<Void>never().doOnCancel(()->closeCancelled++):Mono.fromRunnable(()->{if(closed.compareAndSet(false,true))closes++;}));
            case "createStatement"->statement((String)a[0]);case "validate"->Mono.just(true);case "toString"->"OwnedNetworklessReadLease";default->Mono.empty();});
        Statement statement(String sql){return (Statement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Statement.class},(p,m,a)->{
            if(m.getName().equals("execute")){
                if(stallStatement&&sql.equals("SELECT 1"))return Mono.<Result>never().doOnCancel(()->statementCancelled++);
                Result result=(Result)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Result.class},(x,n,z)->n.getName().equals("getRowsUpdated")?Mono.just(0L):Flux.empty());return Mono.just(result);
            }return p;});}
        public org.reactivestreams.Publisher<? extends Connection> create(){return Mono.defer(()->{creates++;return stallAcquire?Mono.never():Mono.just(connection);});}
        public ConnectionFactoryMetadata getMetadata(){return ()->"PostgreSQL";}
    }
    private Object kernel(Driver driver,GenericApplicationContext context){try{return Class.forName("io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeTransaction").getConstructor(ConnectionFactory.class,org.springframework.data.r2dbc.convert.R2dbcConverter.class,org.springframework.context.ApplicationContext.class,Clock.class,LongSupplier.class).newInstance(driver,new DefaultReactiveDataAccessStrategy(PostgresDialect.INSTANCE).getConverter(),context,clock(),nanos());}catch(ClassNotFoundException e){throw new AssertionError("Owned finite RR transaction boundary missing",e);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private Admission admission(long millis){Instant now=clock().instant();return new Admission(new Scope(new UUID(0,1),new UUID(0,2),new UUID(0,3),new UUID(0,4),new UUID(0,5)),"https://issuer.example","actor",now.plusSeconds(60),now.plusMillis(millis),now,nanos().getAsLong());}
    @SuppressWarnings("unchecked") private Mono<String> read(Object k,Admission a,Function<Object,Mono<String>> work){try{return (Mono<String>)k.getClass().getMethod("read",Admission.class,Function.class).invoke(k,a,work);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private void nativeWindow(Object scope){try{var now=clock().instant();scope.getClass().getMethod("observeNative",NativeWindow.class).invoke(scope,NativeWindow.from(now,now.toEpochMilli(),now.toEpochMilli()+1000,nanos().getAsLong()));}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private R2dbcEntityTemplate operations(Object k){try{return (R2dbcEntityTemplate)k.getClass().getMethod("operations").invoke(k);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    @Test void exactStableFactoryIsSharedByTemplateAndManager(){var d=new Driver();try(var c=new GenericApplicationContext()){c.refresh();var k=kernel(d,c);try{var manager=(R2dbcTransactionManager)k.getClass().getMethod("manager").invoke(k);assertSame(operations(k).getDatabaseClient().getConnectionFactory(),manager.getConnectionFactory());assertSame(d,((org.springframework.r2dbc.connection.DelegatingConnectionFactory)manager.getConnectionFactory()).getTargetConnectionFactory());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}assertEquals(0,d.creates);}
    @Test void acquisitionIsBoundedByOriginalAdmission(){var d=new Driver();d.stallAcquire=true;try(var c=new GenericApplicationContext()){c.refresh();var k=kernel(d,c);StepVerifier.withVirtualTime(()->read(k,admission(1000),s->Mono.just("protected"))).thenAwait(Duration.ofSeconds(4)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));}assertEquals(0,d.closes);}
    @Test void beginCancellationClosesExactCreatedLease(){var d=new Driver();d.stallBegin=true;try(var c=new GenericApplicationContext()){c.refresh();var k=kernel(d,c);StepVerifier.withVirtualTime(()->read(k,admission(1000),s->Mono.just("protected"))).thenAwait(Duration.ofSeconds(4)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));}assertEquals(1,d.beginCancelled);assertEquals(1,d.closes);assertEquals(0,d.commits);}
    @Test void readIntendsPhysicalReadonlyRepeatableReadAndAwaitsClose(){var d=new Driver();try(var c=new GenericApplicationContext()){c.refresh();var k=kernel(d,c);StepVerifier.withVirtualTime(()->read(k,admission(1000),s->Mono.just("protected"))).assertNext(x->{assertEquals(1,d.closes);assertTrue(d.readOnlyIntent);assertEquals(IsolationLevel.REPEATABLE_READ,d.isolationIntent);}).verifyComplete();}assertEquals(1,d.commits);}
    @Test void newlyObservedNativeDeadlineTightensStalledWork(){var d=new Driver();try(var c=new GenericApplicationContext()){c.refresh();var k=kernel(d,c);StepVerifier.withVirtualTime(()->read(k,admission(5000),s->{nativeWindow(s);return Mono.never();})).thenAwait(Duration.ofSeconds(3)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));}assertEquals(1,d.closes);assertEquals(0,d.commits);}
    @Test void actualStatementPublisherCannotStallPastAdmission(){var d=new Driver();d.stallStatement=true;try(var c=new GenericApplicationContext()){c.refresh();var k=kernel(d,c);StepVerifier.withVirtualTime(()->read(k,admission(1000),s->operations(k).getDatabaseClient().sql("SELECT 1").fetch().rowsUpdated().map(n->"protected"))).thenAwait(Duration.ofSeconds(4)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));}assertEquals(1,d.statementCancelled);assertEquals(1,d.closes);}
    @Test void commitPublisherIsActuallyBounded(){var d=new Driver();d.stallCommit=true;try(var c=new GenericApplicationContext()){c.refresh();var k=kernel(d,c);StepVerifier.withVirtualTime(()->read(k,admission(1000),s->Mono.just("protected"))).thenAwait(Duration.ofSeconds(5)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));}assertEquals(1,d.commitCancelled);assertEquals(1,d.closes);}
    @Test void rollbackCleanupIsFiniteAndStillAttemptsLeaseClose(){var d=new Driver();d.stallRollback=true;try(var c=new GenericApplicationContext()){c.refresh();var k=kernel(d,c);StepVerifier.withVirtualTime(()->read(k,admission(1000),s->Mono.error(new IllegalStateException("normal callback refusal")))).thenAwait(Duration.ofSeconds(6)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));}assertTrue(d.rollbackCancelled>0);assertEquals(1,d.closes);}
    @Test void callerCancellationDuringBeginClosesLease(){var d=new Driver();d.stallBegin=true;try(var c=new GenericApplicationContext()){c.refresh();var k=kernel(d,c);StepVerifier.withVirtualTime(()->read(k,admission(5000),s->Mono.just("protected"))).thenAwait(Duration.ofMillis(10)).thenCancel().verify(Duration.ofSeconds(1));}assertEquals(1,d.beginCancelled);assertEquals(1,d.closes);}
    @Test void completedCommitWithStalledCloseEmitsNoConfiguration(){var d=new Driver();d.stallClose=true;try(var c=new GenericApplicationContext()){c.refresh();var k=kernel(d,c);StepVerifier.withVirtualTime(()->read(k,admission(1000),s->Mono.just("protected"))).thenAwait(Duration.ofSeconds(5)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));}assertEquals(1,d.commits);assertEquals(1,d.closeCancelled);}
}

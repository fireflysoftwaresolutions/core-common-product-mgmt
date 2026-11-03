package io.fireflysoftware.core.product.core.services.definition;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.concurrent.atomic.*;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.junit.jupiter.api.Test;

/** Supplied timing only; no native admission/authentication proof. */
class TermPcCommandWindowTest {
    static final Instant BASE=Instant.parse("2026-10-26T12:00:00Z");
    final AtomicReference<Instant> now=new AtomicReference<>(BASE);final AtomicLong nano=new AtomicLong();
    final Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now.get();}};
    final LongSupplier ticks=nano::get;
    Object call(Object target,String name,Class<?>[]types,Object...args){try{Class<?> c=target==null?Class.forName("io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionLifecycleWindow"):target.getClass();var m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}catch(InvocationTargetException e){if(e.getCause()instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError("Original/native command budget mapping missing",e);}}
    Object start(){return call(null,"start",new Class<?>[]{Instant.class,long.class,long.class,long.class,Clock.class,LongSupplier.class},BASE,0L,BASE.plusSeconds(60).toEpochMilli(),BASE.plusSeconds(30).toEpochMilli(),clock,ticks);}
    R2dbcOperationBudget budget(Object w){return (R2dbcOperationBudget)call(w,"budget",new Class<?>[]{});}
    R2dbcOperationBudget observe(Object w,Instant at,long until,long before){return (R2dbcOperationBudget)call(w,"observe",new Class<?>[]{Instant.class,long.class,long.class,long.class},at,at.toEpochMilli(),until,before);}
    @Test void originalBeforePdpAnchorConsumesElapsedTime(){var w=start();now.set(BASE.plusSeconds(2));nano.set(4_000_000_000L);assertEquals(Duration.ofSeconds(26),budget(w).remaining());}
    @Test void nativeAbsoluteDeadlineCannotRenewAtObservation(){var w=start();now.set(BASE.plusSeconds(1));nano.set(1_000_000_000L);var b=observe(w,BASE,BASE.plusSeconds(3).toEpochMilli(),0);now.set(BASE.plusSeconds(3));assertThrows(IllegalStateException.class,b::remaining);}
    @Test void nativeMonotonicOffsetAndOneMillisecondChargeSurviveClockDifference(){var w=start();now.set(BASE.plusSeconds(1));nano.set(1_000_000_000L);var b=observe(w,BASE.plusMillis(500),BASE.plusMillis(1500).toEpochMilli(),500_000_000L);assertEquals(Duration.ofMillis(499),b.remaining());}
    @Test void nativeUntilBeyondOriginalPermitIsRejected(){var w=start();assertThrows(IllegalArgumentException.class,()->observe(w,BASE,BASE.plusSeconds(31).toEpochMilli(),0));}
    @Test void subMicrosecondNativeStampIsRejected(){var w=start();assertThrows(IllegalArgumentException.class,()->observe(w,BASE.plusNanos(1),BASE.plusSeconds(1).toEpochMilli(),0));}
    @Test void preOriginalNativeAnchorIsRejected(){var w=start();assertThrows(IllegalArgumentException.class,()->observe(w,BASE,BASE.plusSeconds(1).toEpochMilli(),-1));}
    @Test void laterNativeObservationCannotWidenEarlierCap(){var w=start();observe(w,BASE,BASE.plusSeconds(1).toEpochMilli(),0);nano.set(500_000_000L);now.set(BASE.plusMillis(500));observe(w,BASE.plusMillis(500),BASE.plusSeconds(2).toEpochMilli(),500_000_000L);nano.set(999_000_000L);assertThrows(IllegalStateException.class,()->budget(w).remaining());}
    @Test void elapsedPdpCannotBeReplacedByNewThirtySecondTtl(){now.set(BASE.plusSeconds(30));nano.set(30_000_000_000L);assertThrows(IllegalStateException.class,this::start);}
    @Test void latePdpExpiryCannotExtendTheOriginalThirtySecondMaximum(){now.set(BASE.plusSeconds(1));nano.set(1_000_000_000L);var w=call(null,"start",new Class<?>[]{Instant.class,long.class,long.class,long.class,Clock.class,LongSupplier.class},BASE,0L,BASE.plusSeconds(60).toEpochMilli(),BASE.plusSeconds(31).toEpochMilli(),clock,ticks);assertEquals(Duration.ofSeconds(29),budget(w).remaining());}

}

package io.fireflysoftware.core.product.core.services.definition;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.publisher.*;

/** Pure cancellation-link readiness, not a timeout increase or application/native observation. */
class TermPcReadinessOrderTest {
    Object field(Object value,String name){try{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}catch(ReflectiveOperationException missing){throw new AssertionError("Cancellation-registered readiness missing: "+name,missing);}}
    void set(Object value,String name,Object assigned){try{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);f.set(value,assigned);}catch(ReflectiveOperationException missing){throw new AssertionError("Context-preserving controlled driver readiness missing",missing);}}
    @Test void oldDoOnSubscribeReadinessPrecedesForwardingSubscription(){var order=new ArrayList<String>();var source=Mono.<Void>never().doOnSubscribe(s->order.add("ready"));var subscription=source.subscribeWith(new BaseSubscriber<Void>(){protected void hookOnSubscribe(Subscription s){order.add("downstream");request(1);}});try{assertEquals(List.of("ready","downstream"),order);}finally{subscription.dispose();}}
    @Test void controlledCommitReadinessFollowsSubscriptionAndInstalledCancellationHook(){var d=new InsuranceQuotePurposeTransactionTest.Driver();d.stallCommit=true;var ready=(CountDownLatch)field(d,"commitReady");var downstream=new AtomicBoolean();var subscription=Mono.from(d.connection.commitTransaction()).subscribeWith(new BaseSubscriber<Void>(){protected void hookOnSubscribe(Subscription s){downstream.set(true);assertEquals(1,ready.getCount());request(1);}});try{assertTrue(downstream.get());assertEquals(0,ready.getCount());assertEquals(0,d.commitCancelled);subscription.dispose();assertEquals(1,d.commitCancelled);}finally{subscription.dispose();}}
    @Test void controlledBeginAndRollbackPreserveOriginalReactorContext(){for(boolean begin:new boolean[]{true,false}){var d=new InsuranceQuotePurposeTransactionTest.Driver();d.stallBegin=begin;d.stallRollback=!begin;set(d,"expectedContextMarker","owned-source-marker");var ready=(CountDownLatch)field(d,begin?"beginReady":"rollbackReady");var subscription=Mono.from(begin?d.connection.beginTransaction():d.connection.rollbackTransaction()).contextWrite(c->c.put("owned-fixture-marker","owned-source-marker")).subscribe();try{assertEquals(0,ready.getCount());assertTrue(((AtomicBoolean)field(d,"contextMarkerRetained")).get());subscription.dispose();assertEquals(1,begin?d.beginCancelled:d.rollbackCancelled);}finally{subscription.dispose();}}}
}

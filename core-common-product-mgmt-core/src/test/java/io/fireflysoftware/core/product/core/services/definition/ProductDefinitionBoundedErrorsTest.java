package io.fireflysoftware.core.product.core.services.definition;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.function.Supplier;
import org.fireflyframework.core.transaction.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import org.springframework.context.support.GenericApplicationContext;

/** Supplied technical facts test status mapping, not real rollback/no-effect or financial proof. */
class ProductDefinitionBoundedErrorsTest {
    Object mapper(){try{var c=Class.forName("io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionBoundedErrors").getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}catch(ReflectiveOperationException e){throw new AssertionError("Payload-free owner failure mapping missing",e);}}
    Object call(Object target,String name,Class<?>[]types,Object...args){try{var m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}catch(InvocationTargetException e){throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    void capture(Object m,Throwable error){Mono<?> source=(Mono<?>)call(m,"capture",new Class<?>[]{Mono.class},Mono.error(error));assertThrows(Throwable.class,source::block);}
    RootTransactionException failure(boolean commit,boolean rollback,CleanupFact cleanup,RootTransactionException.Stage stage){return new RootTransactionException(new TransactionFacts(true,commit,false,rollback),cleanup,stage);}
    Supplier<R2dbcOperationBudget> budget(boolean expired){var at=Instant.parse("2026-10-26T12:00:00Z");var value=R2dbcOperationBudget.observed(at,at.plusSeconds(1),0,Duration.ofSeconds(1),Clock.fixed(expired?at.plusSeconds(2):at,ZoneOffset.UTC),()->0);return ()->value;}
    Throwable resolve(Object m,RootTransactionException e,boolean expired){return (Throwable)call(m,"resolve",new Class<?>[]{RootTransactionException.class,Supplier.class},e,budget(expired));}
    @Test void normalConflictPreservesOnlyConstantStatusAfterCompletedRollback(){var m=mapper();capture(m,new ResponseStatusException(HttpStatus.CONFLICT,"private diagnostic"));var result=assertInstanceOf(ResponseStatusException.class,resolve(m,failure(false,true,CleanupFact.COMPLETE,RootTransactionException.Stage.WORK),false));assertEquals(409,result.getStatusCode().value());assertFalse(result.toString().contains("private diagnostic"));assertNull(result.getCause());}
    @Test void rollbackOrCleanupUncertaintyNeverBecomesOrdinaryConflict(){for(var e:new RootTransactionException[]{failure(false,false,CleanupFact.COMPLETE,RootTransactionException.Stage.WORK),failure(false,true,CleanupFact.INCOMPLETE,RootTransactionException.Stage.WORK),failure(true,true,CleanupFact.COMPLETE,RootTransactionException.Stage.WORK),failure(false,true,CleanupFact.COMPLETE,RootTransactionException.Stage.COMMIT)}){var m=mapper();capture(m,new ResponseStatusException(HttpStatus.CONFLICT));assertSame(e,resolve(m,e,false));}}
    @Test void expiredOriginalOrNativeWindowCannotExposeRetainedStatus(){var m=mapper();capture(m,new ResponseStatusException(HttpStatus.CONFLICT));var e=failure(false,true,CleanupFact.COMPLETE,RootTransactionException.Stage.WORK);assertSame(e,resolve(m,e,true));}
    @Test void unclassifiedErrorsRemainUnavailable(){var m=mapper();capture(m,new IllegalArgumentException("private diagnostic"));var e=failure(false,true,CleanupFact.COMPLETE,RootTransactionException.Stage.WORK);assertSame(e,resolve(m,e,false));}
    @SuppressWarnings("unchecked") @Test void actualSpringCompletedRollbackRetainsOnlyNormalConflictStatus(){var helper=new TermPcPersistenceBoundaryTest();var d=new InsuranceQuotePurposeTransactionTest.Driver();var pool=helper.pool(d);try(var c=new GenericApplicationContext()){c.refresh();var p=helper.persistence(pool,c,true);Object m=mapper();StepVerifier.withVirtualTime(()->helper.execute(p,false,scope->(Mono<String>)call(m,"capture",new Class<?>[]{Mono.class},Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,"private diagnostic")))).onErrorMap(RootTransactionException.class,error->{assertEquals(RootTransactionException.Stage.ROLLBACK,error.stage());return resolve(m,error,false);})).expectErrorSatisfies(error->{var safe=assertInstanceOf(ResponseStatusException.class,error);assertEquals(409,safe.getStatusCode().value());assertFalse(error.toString().contains("private diagnostic"));assertTrue(d.rollbacks>0);assertEquals(0,pool.getMetrics().orElseThrow().acquiredSize());}).verify(Duration.ofSeconds(1));}finally{pool.disposeLater().block(Duration.ofSeconds(1));}}
    @SuppressWarnings("unchecked") @Test void actualSpringIncompleteRollbackNeverBecomesOrdinaryConflict(){var helper=new TermPcPersistenceBoundaryTest();var d=new InsuranceQuotePurposeTransactionTest.Driver();d.stallRollback=true;var started=d.rollbackReady;var pool=helper.pool(d);try(var c=new GenericApplicationContext()){c.refresh();var p=helper.persistence(pool,c,true);Object m=mapper();StepVerifier.withVirtualTime(()->helper.execute(p,false,scope->(Mono<String>)call(m,"capture",new Class<?>[]{Mono.class},Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,"private diagnostic")))).onErrorMap(RootTransactionException.class,error->resolve(m,error,false))).then(()->helper.awaitStage(started)).thenAwait(Duration.ofSeconds(8)).expectErrorSatisfies(error->{assertInstanceOf(RootTransactionException.class,error);assertTrue(d.rollbackCancelled>0);assertEquals(0,pool.getMetrics().orElseThrow().acquiredSize());}).verify(Duration.ofSeconds(1));}finally{pool.disposeLater().block(Duration.ofSeconds(1));}}

    @Test void observedRollbackStageStillRequiresCapturedWorkStatusAndCompleteAcknowledgedCleanup(){var m=mapper();capture(m,new ResponseStatusException(HttpStatus.CONFLICT));var e=failure(false,true,CleanupFact.COMPLETE,RootTransactionException.Stage.ROLLBACK);assertEquals(409,assertInstanceOf(ResponseStatusException.class,resolve(m,e,false)).getStatusCode().value());var empty=mapper();assertSame(e,resolve(empty,e,false));}

}

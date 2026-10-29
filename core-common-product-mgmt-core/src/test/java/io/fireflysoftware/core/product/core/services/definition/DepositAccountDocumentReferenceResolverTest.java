package io.fireflysoftware.core.product.core.services.definition;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.product.core.services.definition.DepositAccountDocumentReferenceResolver.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.atomic.*;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Controlled owner ports qualify local binding/timing only, never owner or native acceptance. */
class DepositAccountDocumentReferenceResolverTest {
    @Test void legacyFourArgumentObservationRetainsNativeAndEffectiveEvidence(){
        var value=observation(target());assertEquals(WindowEvidence.NATIVE_AND_EFFECTIVE,value.windowEvidence());assertNotNull(value.nativeUntil());
    }
    @Test void effectiveOnlyEvidenceCannotSmuggleAnIndependentNativeDeadline(){
        var service=service((t,b,u)->Mono.just(observation(t)),(t,b,u)->Mono.just(new Observation(t,NOW,NOW.plusSeconds(4),NOW.plusSeconds(3),WindowEvidence.EFFECTIVE_ONLY)));
        assertThrows(IllegalStateException.class,()->service.verify(target(),budget(),NOW.plusSeconds(5)).block());
    }
    @Test void effectiveOnlyOwnerWindowCannotExceedFiveSeconds(){
        var service=service((t,b,u)->Mono.just(observation(t)),(t,b,u)->Mono.just(Observation.effectiveOnly(t,NOW,NOW.plusSeconds(6))));
        assertThrows(IllegalStateException.class,()->service.verify(target(),budget(),NOW.plusSeconds(5)).block());
    }
    static final Instant NOW=Instant.parse("2026-10-28T12:00:00Z");
    final AtomicReference<Instant> wall=new AtomicReference<>(NOW);final AtomicLong ticks=new AtomicLong();
    final Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return wall.get();}};
    final LongSupplier nanos=ticks::get;
    final Scope scope=new Scope(new UUID(0,1),new UUID(0,2),new UUID(0,3),new UUID(0,4),new UUID(0,5),"a".repeat(64));
    Target target(){return new Target(scope,"DEPOSIT_ACCOUNT_ENROLLMENT",new DocumentRequirement("CONTRACT","CONTRACT",true,
            new ExactReference("core-common-reference-master-data","DOCUMENT_TEMPLATE",new UUID(0,6).toString(),null,"b".repeat(64),"OPAQUE","release.Case"),
            new ExactReference("core-common-config-mgmt","DOCUMENT_ISSUER",new UUID(0,7).toString(),1L,"c".repeat(64)),
            new ExactReference("core-common-reference-master-data","DOCUMENT_LAYOUT",new UUID(0,8).toString(),null,"d".repeat(64),"OPAQUE","release.Case"),"en-US"));}
    R2dbcOperationBudget budget(){return R2dbcOperationBudget.observed(NOW,NOW.plusSeconds(5),0,Duration.ofSeconds(5),clock,nanos);}
    Observation observation(Target t){return new Observation(t,NOW,NOW.plusSeconds(4),NOW.plusSeconds(3));}
    DepositAccountDocumentReferenceResolver service(ProductDocumentIssuerReferenceProvider issuer,DepositAccountTemplateReferenceProvider masters){
        try{return (DepositAccountDocumentReferenceResolver)Class.forName("io.fireflysoftware.core.product.core.services.definition.impl.DepositAccountDocumentReferenceResolverImpl")
            .getConstructor(ProductDocumentIssuerReferenceProvider.class,DepositAccountTemplateReferenceProvider.class,Clock.class,LongSupplier.class).newInstance(issuer,masters,clock,nanos);}
        catch(InvocationTargetException e){throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError("Budget-bearing current document binder missing",e);}
    }
    @Test void bothOwnerCallsRetainTheIdenticalOriginalBudgetAndEarliestInheritedDeadline(){
        var original=budget();var calls=new AtomicInteger();var target=target();
        var service=service((t,b,u)->{assertSame(original,b);assertEquals(NOW.plusSeconds(5),u);calls.incrementAndGet();ticks.set(1_000_000_000L);wall.set(NOW.plusSeconds(1));return Mono.just(observation(t));},
            (t,b,u)->{assertSame(original,b);assertEquals(NOW.plusSeconds(3),u);calls.incrementAndGet();return Mono.just(observation(t));});
        var result=service.verify(target,original,NOW.plusSeconds(5)).block();assertNotNull(result);assertEquals(target,result.target());assertEquals(NOW.plusMillis(2999),result.effectiveUntil());assertEquals(2,calls.get());
    }
    @Test void missingMastersObservationNeverBecomesSuccess(){var service=service((t,b,u)->Mono.just(observation(t)),(t,b,u)->Mono.empty());assertThrows(IllegalStateException.class,()->service.verify(target(),budget(),NOW.plusSeconds(5)).block());}
    @Test void unavailableIssuerNeverCallsMasters(){var calls=new AtomicInteger();var service=service((t,b,u)->Mono.error(new IllegalStateException("owner unavailable")),(t,b,u)->{calls.incrementAndGet();return Mono.just(observation(t));});assertThrows(IllegalStateException.class,()->service.verify(target(),budget(),NOW.plusSeconds(5)).block());assertEquals(0,calls.get());}
    @Test void changedProductPublicationEchoRefuses(){var service=service((t,b,u)->Mono.just(observation(t)),(t,b,u)->Mono.just(observation(new Target(new Scope(scope.tenantId(),scope.rootId(),scope.productId(),scope.revisionId(),new UUID(0,99),scope.definitionDigest()),t.purpose(),t.document()))));assertThrows(IllegalStateException.class,()->service.verify(target(),budget(),NOW.plusSeconds(5)).block());}
    @Test void changedLocaleEchoRefuses(){var service=service((t,b,u)->Mono.just(observation(t)),(t,b,u)->Mono.just(observation(new Target(t.scope(),t.purpose(),new DocumentRequirement("CONTRACT","CONTRACT",true,t.document().template(),t.document().issuer(),t.document().layout(),"es-ES")))));assertThrows(IllegalStateException.class,()->service.verify(target(),budget(),NOW.plusSeconds(5)).block());}
    @Test void originalElapsedBudgetCannotRestartAtOwnerCall(){ticks.set(5_000_000_000L);var calls=new AtomicInteger();var service=service((t,b,u)->{calls.incrementAndGet();return Mono.just(observation(t));},(t,b,u)->Mono.just(observation(t)));assertThrows(IllegalStateException.class,()->service.verify(target(),budget(),NOW.plusSeconds(6)).block());assertEquals(0,calls.get());}
    @Test void elapsedOwnerWindowCannotRestartAfterAwait(){var calls=new AtomicInteger();var service=service((t,b,u)->{ticks.set(3_000_000_000L);return Mono.just(observation(t));},(t,b,u)->{calls.incrementAndGet();return Mono.just(observation(t));});assertThrows(IllegalStateException.class,()->service.verify(target(),budget(),NOW.plusSeconds(5)).block());assertEquals(0,calls.get());}
    @Test void elapsedFirstOwnerWindowIsCheckedAfterTheSecondOwner(){var service=service((t,b,u)->Mono.just(observation(t)),(t,b,u)->{ticks.set(3_000_000_000L);return Mono.just(observation(t));});assertThrows(IllegalStateException.class,()->service.verify(target(),budget(),NOW.plusSeconds(5)).block());}
    @Test void futureObservationNeverCreatesCurrentAuthority(){var service=service((t,b,u)->Mono.just(new Observation(t,NOW.plusMillis(1),NOW.plusSeconds(4),NOW.plusSeconds(3))),(t,b,u)->Mono.just(observation(t)));assertThrows(IllegalStateException.class,()->service.verify(target(),budget(),NOW.plusSeconds(5)).block());}
    @Test void effectiveWindowCannotOutliveNativeAuthority(){var service=service((t,b,u)->Mono.just(new Observation(t,NOW,NOW.plusSeconds(2),NOW.plusSeconds(3))),(t,b,u)->Mono.just(observation(t)));assertThrows(IllegalStateException.class,()->service.verify(target(),budget(),NOW.plusSeconds(5)).block());}
    @Test void originalInheritedAbsoluteDeadlineCannotWiden(){var result=service((t,b,u)->Mono.just(observation(t)),(t,b,u)->Mono.just(observation(t))).verify(target(),budget(),NOW.plusSeconds(2)).block();assertNotNull(result);assertEquals(NOW.plusSeconds(2),result.effectiveUntil());}
    @Test void absentRequiredOwnerCannotConstructAnActivatedBinder(){assertThrows(AssertionError.class,()->service((t,b,u)->Mono.just(observation(t)),null));}
    @Test void stalledOwnerIsBoundedByOriginalBudget(){var service=service((t,b,u)->Mono.never(),(t,b,u)->Mono.just(observation(t)));StepVerifier.withVirtualTime(()->service.verify(target(),budget(),NOW.plusSeconds(5))).thenAwait(Duration.ofSeconds(6)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));}
    @Test void historicalNumericTemplateVersionCannotBecomeCurrentOwnerAuthority(){var t=target();var old=t.document();var numeric=new Target(t.scope(),t.purpose(),new DocumentRequirement(old.code(),old.documentType(),old.mandatory(),new ExactReference("core-common-reference-master-data","DOCUMENT_TEMPLATE",old.template().id(),1L,old.template().digest()),old.issuer(),old.layout(),old.locale()));var calls=new AtomicInteger();var service=service((x,b,u)->{calls.incrementAndGet();return Mono.just(observation(x));},(x,b,u)->Mono.just(observation(x)));assertThrows(IllegalStateException.class,()->service.verify(numeric,budget(),NOW.plusSeconds(5)).block());assertEquals(0,calls.get());}
    @Test void ambientTransactionRefusesBeforeAnyOwnerCall(){
        var calls=new AtomicInteger();var service=service((t,b,u)->{calls.incrementAndGet();return Mono.just(observation(t));},(t,b,u)->Mono.just(observation(t)));
        var source=org.springframework.transaction.reactive.TransactionSynchronizationManager.forCurrentTransaction().flatMap(manager->{
            manager.setActualTransactionActive(true);return service.verify(target(),budget(),NOW.plusSeconds(5));
        }).contextWrite(org.springframework.transaction.reactive.TransactionContextManager.createTransactionContext());
        assertThrows(IllegalStateException.class,source::block);assertEquals(0,calls.get());
    }

    @Test void fractionalOriginalDeadlineProducesAnIntegralConservativeComposedBoundary(){
        wall.set(NOW.plusNanos(123456));var original=R2dbcOperationBudget.observed(NOW,NOW.plusSeconds(2).plusNanos(123456),0,Duration.ofSeconds(5),clock,nanos);
        var service=service((t,b,u)->Mono.just(observation(t)),(t,b,u)->Mono.just(observation(t)));
        var result=service.verify(target(),original,NOW.plusSeconds(5)).block();assertNotNull(result);
        assertEquals(NOW.plusSeconds(2),result.effectiveUntil());assertEquals(0,result.effectiveUntil().getNano()%1_000_000);
    }
    @ParameterizedTest @ValueSource(strings={"tenant","root","product","revision","publication"})
    void nilProductIdentityRefusesBeforeOwnerIo(String field){
        var nil=new UUID(0,0);var t=target();var old=t.scope();var invalid=new Scope(field.equals("tenant")?nil:old.tenantId(),field.equals("root")?nil:old.rootId(),field.equals("product")?nil:old.productId(),field.equals("revision")?nil:old.revisionId(),field.equals("publication")?nil:old.publicationId(),old.definitionDigest());
        var calls=new AtomicInteger();var service=service((x,b,u)->{calls.incrementAndGet();return Mono.just(observation(x));},(x,b,u)->Mono.just(observation(x)));
        assertThrows(IllegalStateException.class,()->service.verify(new Target(invalid,t.purpose(),t.document()),budget(),NOW.plusSeconds(5)).block());assertEquals(0,calls.get());
    }
    @ParameterizedTest @ValueSource(strings={"template","issuer","layout"})
    void nilOwnerReferenceRefusesBeforeOwnerIo(String field){
        var t=target();var d=t.document();var original=field.equals("template")?d.template():field.equals("issuer")?d.issuer():d.layout();
        var invalid=new ExactReference(original.owner(),original.kind(),new UUID(0,0).toString(),original.version(),original.digest(),original.versionFormat(),original.versionText());
        var target=new Target(t.scope(),t.purpose(),new DocumentRequirement(d.code(),d.documentType(),d.mandatory(),field.equals("template")?invalid:d.template(),field.equals("issuer")?invalid:d.issuer(),field.equals("layout")?invalid:d.layout(),d.locale()));
        var calls=new AtomicInteger();var service=service((x,b,u)->{calls.incrementAndGet();return Mono.just(observation(x));},(x,b,u)->Mono.just(observation(x)));
        assertThrows(IllegalStateException.class,()->service.verify(target,budget(),NOW.plusSeconds(5)).block());assertEquals(0,calls.get());
    }
    @ParameterizedTest @ValueSource(strings={"en-us","EN-US","und","zh-hans-cn"})
    void noncanonicalOrUndefinedLocaleRefusesBeforeOwnerIo(String locale){
        var t=target();var d=t.document();var invalid=new Target(t.scope(),t.purpose(),new DocumentRequirement(d.code(),d.documentType(),d.mandatory(),d.template(),d.issuer(),d.layout(),locale));
        var calls=new AtomicInteger();var service=service((x,b,u)->{calls.incrementAndGet();return Mono.just(observation(x));},(x,b,u)->Mono.just(observation(x)));
        assertThrows(IllegalStateException.class,()->service.verify(invalid,budget(),NOW.plusSeconds(5)).block());assertEquals(0,calls.get());
    }
    @ParameterizedTest @ValueSource(strings={"issuer","masters"})
    void impossibleOwnerNativeWindowAboveFiveSecondsRefuses(String owner){
        var service=service((t,b,u)->Mono.just(owner.equals("issuer")?new Observation(t,NOW,NOW.plusMillis(5001),NOW.plusSeconds(3)):observation(t)),
            (t,b,u)->Mono.just(owner.equals("masters")?new Observation(t,NOW,NOW.plusMillis(5001),NOW.plusSeconds(3)):observation(t)));
        assertThrows(IllegalStateException.class,()->service.verify(target(),budget(),NOW.plusSeconds(5)).block());
    }

}

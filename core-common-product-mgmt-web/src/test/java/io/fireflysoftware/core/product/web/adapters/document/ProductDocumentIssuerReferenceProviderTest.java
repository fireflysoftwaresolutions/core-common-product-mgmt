package io.fireflysoftware.core.product.web.adapters.document;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.core.services.definition.DepositAccountDocumentReferenceResolver.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;
import io.fireflysoftware.common.config.sdk.api.DocumentIssuerApi;
import io.fireflysoftware.common.config.sdk.invoker.ApiClient;
import io.fireflysoftware.common.config.sdk.model.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Genuine generated SDK with controlled in-memory exchanges. No HTTP/native/provider acceptance. */
class ProductDocumentIssuerReferenceProviderTest {
    static final Instant NOW=Instant.parse("2026-10-28T12:00:00Z");
    final AtomicReference<Instant> wall=new AtomicReference<>(NOW);final AtomicLong ticks=new AtomicLong();final AtomicInteger calls=new AtomicInteger();
    final Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return wall.get();}};
    final LongSupplier nanos=ticks::get;
    Target target(){return new Target(new Scope(new UUID(0,1),new UUID(0,2),new UUID(0,3),new UUID(0,4),new UUID(0,5),"a".repeat(64)),"DEPOSIT_ACCOUNT_ENROLLMENT",
        new DocumentRequirement("CONTRACT","CONTRACT",true,
            new ExactReference("core-common-reference-master-data","DOCUMENT_TEMPLATE",new UUID(0,6).toString(),null,"b".repeat(64),"OPAQUE","release.Case"),
            new ExactReference("core-common-config-mgmt","DOCUMENT_ISSUER",new UUID(0,7).toString(),1L,"c".repeat(64)),
            new ExactReference("core-common-reference-master-data","DOCUMENT_LAYOUT",new UUID(0,8).toString(),null,"d".repeat(64),"OPAQUE","release.Case"),"en-US"));}
    R2dbcOperationBudget budget(){return R2dbcOperationBudget.observed(NOW,NOW.plusSeconds(5),0,Duration.ofSeconds(5),clock,nanos);}
    DocumentIssuerUse use(){return new DocumentIssuerUse().purpose(DocumentIssuerUse.PurposeEnum.DEPOSIT_ACCOUNT_ENROLLMENT).documentType("CONTRACT").locale("en-US");}
    DocumentIssuerObservation response(){var t=target();var id=new UUID(0,9).toString();
        var source=new DocumentIssuerSourceSnapshot().tenantId(t.scope().tenantId().toString()).tenantActive(true).brandingActive(true).sourceGeneration(1L);
        var definition=new DocumentIssuerDefinition().issuerId(id).revision(1L).uses(List.of(use())).source(source).effectiveFromEpochMillis(NOW.minusSeconds(1).toEpochMilli()).effectiveUntilEpochMillis(NOW.plusSeconds(60).toEpochMilli());
        return new DocumentIssuerObservation().reference(new DocumentIssuerReference().tenantId(t.scope().tenantId().toString()).issuerId(id).publicationId(t.document().issuer().id()).revision(1L).definitionDigest("c".repeat(64)).use(use()))
            .definition(definition).generation(2L).sourceGeneration(1L).observedAtEpochMillis(NOW.toEpochMilli()).nativeUntilEpochMillis(NOW.plusSeconds(4).toEpochMilli()).effectiveUntilEpochMillis(NOW.plusSeconds(3).toEpochMilli());}
    ProductDocumentIssuerReferenceProvider adapter(Function<ClientRequest,Mono<ClientResponse>> exchange){
        var format=ApiClient.createDefaultDateFormat();var mapper=ApiClient.createDefaultMapper(format);
        var web=ApiClient.buildWebClientBuilder(mapper).exchangeFunction(request->{calls.incrementAndGet();return exchange.apply(request);}).build();
        var api=new ApiClient(web,mapper,format);api.setBasePath("https://unit.invalid");api.setBearerToken("unit-test-only");
        try{return (ProductDocumentIssuerReferenceProvider)Class.forName("io.fireflysoftware.core.product.web.adapters.document.impl.ProductDocumentIssuerReferenceProviderImpl")
            .getConstructor(DocumentIssuerApi.class,Clock.class,LongSupplier.class).newInstance(new DocumentIssuerApi(api),clock,nanos);}
        catch(InvocationTargetException e){throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError("Genuine issuer SDK adapter missing",e);}
    }
    ClientResponse json(DocumentIssuerObservation value){try{return ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").body(ApiClient.createDefaultMapper(ApiClient.createDefaultDateFormat()).writeValueAsString(value)).build();}catch(Exception e){throw new AssertionError(e);}}
    @Test void genuineSdkSerializesExactTenantPublicationRevisionDigestAndUse() {
        var wireFailure=new AtomicReference<Throwable>();
        var a=adapter(request->{
            var sink=new org.springframework.mock.http.client.reactive.MockClientHttpRequest(request.method(),request.url());
            var strategies=org.springframework.web.reactive.function.client.ExchangeStrategies.builder().codecs(c->c.defaultCodecs().jackson2JsonEncoder(
                new org.springframework.http.codec.json.Jackson2JsonEncoder(ApiClient.createDefaultMapper(ApiClient.createDefaultDateFormat()),MediaType.APPLICATION_JSON))).build();
            var context=new org.springframework.web.reactive.function.BodyInserter.Context(){
                public java.util.List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters(){return strategies.messageWriters();}
                public java.util.Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest(){return java.util.Optional.empty();}
                public java.util.Map<String,Object> hints(){return java.util.Map.of();}
            };
            return request.body().insert(sink,context).then(Mono.defer(sink::getBodyAsString)).map(body->{
                try{var value=ApiClient.createDefaultMapper(ApiClient.createDefaultDateFormat()).readTree(body);
                    assertEquals("00000000-0000-0000-0000-000000000001",value.path("tenantId").textValue());
                    assertEquals("00000000-0000-0000-0000-000000000007",value.path("publicationId").textValue());
                    assertEquals(1,value.path("revision").longValue());assertEquals("c".repeat(64),value.path("definitionDigest").textValue());
                    assertEquals("DEPOSIT_ACCOUNT_ENROLLMENT",value.path("use").path("purpose").textValue());
                    assertEquals("CONTRACT",value.path("use").path("documentType").textValue());assertEquals("en-US",value.path("use").path("locale").textValue());
                    return json(response());
                }catch(Exception e){throw new AssertionError(e);}
            }).doOnError(wireFailure::set);
        });try{assertNotNull(a.observe(target(),budget(),NOW.plusSeconds(5)).block());}
        catch(RuntimeException failure){throw new AssertionError("Controlled SDK request serialization failed",wireFailure.get()==null?failure:wireFailure.get());}
        assertEquals(1,calls.get());
    }
    @Test void ambientTransactionRefusesBeforeAnySdkExchange() {
        var a=adapter(request->Mono.just(json(response())));
        var source=org.springframework.transaction.reactive.TransactionSynchronizationManager.forCurrentTransaction().flatMap(manager->{
            manager.setActualTransactionActive(true);return a.observe(target(),budget(),NOW.plusSeconds(5));
        }).contextWrite(org.springframework.transaction.reactive.TransactionContextManager.createTransactionContext());
        assertThrows(IllegalStateException.class,source::block);assertEquals(0,calls.get());
    }
    @Test void genuineSdkCurrentRouteReturnsOnlyExactFiniteIssuerReference(){var a=adapter(request->{assertEquals(HttpMethod.POST,request.method());assertEquals("/api/v1/tenants/00000000-0000-0000-0000-000000000001/document-issuers/current-observations",request.url().getPath());assertEquals("Bearer unit-test-only",request.headers().getFirst("Authorization"));return Mono.just(json(response()));});var result=a.observe(target(),budget(),NOW.plusSeconds(5)).block();assertNotNull(result);assertEquals(target(),result.target());assertEquals(NOW,result.observedAt());assertEquals(NOW.plusSeconds(4),result.nativeUntil());assertEquals(NOW.plusMillis(2999),result.effectiveUntil());assertEquals(1,calls.get());}
    @ParameterizedTest @ValueSource(strings={"tenant","publication","revision","digest","purposeType","locale","issuer","sourceTenant","sourceGeneration","inactive","future","nativeWindow","effectiveWindow","definitionWindow","missingDefinition","missingReference","missingStamp"})
    void mismatchedOrUnboundedOwnerBodyRefuses(String variant){var body=response();switch(variant){
        case "tenant"->body.getReference().setTenantId(new UUID(0,99).toString());case "publication"->body.getReference().setPublicationId(new UUID(0,99).toString());
        case "revision"->body.getReference().setRevision(2L);case "digest"->body.getReference().setDefinitionDigest("f".repeat(64));
        case "purposeType"->body.getReference().getUse().setDocumentType("STATEMENT");case "locale"->body.getReference().getUse().setLocale("es-ES");
        case "issuer"->body.getReference().setIssuerId(new UUID(0,99).toString());case "sourceTenant"->body.getDefinition().getSource().setTenantId(new UUID(0,99).toString());
        case "sourceGeneration"->body.setSourceGeneration(2L);case "inactive"->body.getDefinition().getSource().setTenantActive(false);
        case "future"->body.setObservedAtEpochMillis(NOW.plusSeconds(1).toEpochMilli());case "nativeWindow"->body.setNativeUntilEpochMillis(NOW.plusSeconds(31).toEpochMilli());
        case "effectiveWindow"->body.setEffectiveUntilEpochMillis(NOW.plusSeconds(5).toEpochMilli());case "definitionWindow"->body.getDefinition().setEffectiveUntilEpochMillis(NOW.plusSeconds(2).toEpochMilli());
        case "missingDefinition"->body.setDefinition(null);case "missingReference"->body.setReference(null);case "missingStamp"->body.setObservedAtEpochMillis(null);default->throw new AssertionError(variant);}
        var a=adapter(request->Mono.just(json(body)));assertThrows(IllegalStateException.class,()->a.observe(target(),budget(),NOW.plusSeconds(5)).block());assertEquals(1,calls.get());}
    @Test void legacyIssuerOwnerCannotReachTransport(){var old=target();var doc=old.document();var invalid=new Target(old.scope(),old.purpose(),new DocumentRequirement(doc.code(),doc.documentType(),doc.mandatory(),doc.template(),new ExactReference("core-common-reference-master-data","DOCUMENT_ISSUER",doc.issuer().id(),1L,doc.issuer().digest()),doc.layout(),doc.locale()));var a=adapter(request->Mono.just(json(response())));assertThrows(IllegalStateException.class,()->a.observe(invalid,budget(),NOW.plusSeconds(5)).block());assertEquals(0,calls.get());}
    @Test void originalBudgetExhaustionNeverReachesTransport(){ticks.set(5_000_000_000L);var a=adapter(request->Mono.just(json(response())));assertThrows(IllegalStateException.class,()->a.observe(target(),budget(),NOW.plusSeconds(5)).block());assertEquals(0,calls.get());}
    @Test void ownerAwaitCannotRenewElapsedWindow(){var a=adapter(request->{ticks.set(3_000_000_000L);return Mono.just(json(response()));});assertThrows(IllegalStateException.class,()->a.observe(target(),budget(),NOW.plusSeconds(5)).block());}
    @Test void inheritedEarlierDeadlineCapsReturnedAuthority(){var a=adapter(request->Mono.just(json(response())));var result=a.observe(target(),budget(),NOW.plusSeconds(2)).block();assertNotNull(result);assertEquals(NOW.plusSeconds(2),result.effectiveUntil());}
    @Test void ownerUnavailableHasNoHistoricalFallback(){var a=adapter(request->Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()));assertThrows(IllegalStateException.class,()->a.observe(target(),budget(),NOW.plusSeconds(5)).block());assertEquals(1,calls.get());}
    @Test void stalledSdkExchangeUsesTheOriginalDeadline(){var a=adapter(request->Mono.never());StepVerifier.withVirtualTime(()->a.observe(target(),budget(),NOW.plusSeconds(5))).thenAwait(Duration.ofSeconds(6)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));}
    @Test void fractionalOriginalDeadlineIsFlooredWithoutExtendingTheOriginalCap(){
        wall.set(NOW.plusNanos(123456));var until=NOW.plusSeconds(2).plusNanos(123456);
        var original=R2dbcOperationBudget.observed(NOW,until,0,Duration.ofSeconds(5),clock,nanos);
        var a=adapter(request->Mono.just(json(response())));var result=a.observe(target(),original,NOW.plusSeconds(5)).block();assertNotNull(result);
        assertEquals(NOW.plusSeconds(2),result.effectiveUntil());assertTrue(!result.effectiveUntil().isAfter(until));
    }
    @Test void realSdkAdapterAndCoreBinderComposeWithFractionalOriginalClock(){
        wall.set(NOW.plusNanos(123456));var until=NOW.plusSeconds(2).plusNanos(123456);
        var original=R2dbcOperationBudget.observed(NOW,until,0,Duration.ofSeconds(5),clock,nanos);var a=adapter(request->Mono.just(json(response())));
        try{var binder=(DepositAccountDocumentReferenceResolver)Class.forName("io.fireflysoftware.core.product.core.services.definition.impl.DepositAccountDocumentReferenceResolverImpl")
            .getConstructor(ProductDocumentIssuerReferenceProvider.class,DepositAccountTemplateReferenceProvider.class,Clock.class,LongSupplier.class)
            .newInstance(a,(DepositAccountTemplateReferenceProvider)(t,b,u)->Mono.just(new Observation(t,NOW,NOW.plusSeconds(4),NOW.plusSeconds(3))),clock,nanos);
            var result=binder.verify(target(),original,NOW.plusSeconds(5)).block();assertNotNull(result);assertEquals(target(),result.target());
            assertEquals(0,result.effectiveUntil().getNano()%1_000_000);assertTrue(result.effectiveUntil().isBefore(until));
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    @ParameterizedTest @ValueSource(strings={"tenant","publication","locale","undefinedLocale"})
    void nilOrNoncanonicalRequestRefusesBeforeSdkIo(String field){
        var t=target();var d=t.document();var q=t.scope();var scope=new Scope(field.equals("tenant")?new UUID(0,0):q.tenantId(),q.rootId(),q.productId(),q.revisionId(),q.publicationId(),q.definitionDigest());
        var ref=d.issuer();if(field.equals("publication"))ref=new ExactReference(ref.owner(),ref.kind(),new UUID(0,0).toString(),ref.version(),ref.digest());
        var invalid=new Target(scope,t.purpose(),new DocumentRequirement(d.code(),d.documentType(),d.mandatory(),d.template(),ref,d.layout(),field.equals("locale")?"en-us":field.equals("undefinedLocale")?"und":d.locale()));
        var a=adapter(request->Mono.just(json(response())));assertThrows(IllegalStateException.class,()->a.observe(invalid,budget(),NOW.plusSeconds(5)).block());assertEquals(0,calls.get());
    }
    @Test void nilEchoedIssuerCannotBecomeOwnerAuthority(){
        var body=response();body.getReference().setIssuerId(new UUID(0,0).toString());body.getDefinition().setIssuerId(new UUID(0,0).toString());
        var a=adapter(request->Mono.just(json(body)));assertThrows(IllegalStateException.class,()->a.observe(target(),budget(),NOW.plusSeconds(5)).block());
    }
    @Test void nativeWindowAbovePublishedFiveSecondLimitRefuses(){
        var body=response().nativeUntilEpochMillis(NOW.plusMillis(5001).toEpochMilli());var a=adapter(request->Mono.just(json(body)));
        assertThrows(IllegalStateException.class,()->a.observe(target(),budget(),NOW.plusSeconds(5)).block());
    }
    @Test void exactFiveSecondNativeBoundaryRemainsUsable(){
        var body=response().nativeUntilEpochMillis(NOW.plusSeconds(5).toEpochMilli());var a=adapter(request->Mono.just(json(body)));
        assertNotNull(a.observe(target(),budget(),NOW.plusSeconds(5)).block());assertEquals(1,calls.get());
    }

}

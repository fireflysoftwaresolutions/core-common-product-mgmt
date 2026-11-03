package io.fireflysoftware.core.product.web.adapters.document;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.core.services.definition.DepositAccountDocumentReferenceResolver.*;
import io.fireflysoftware.core.product.core.services.definition.DepositAccountDocumentReferenceResolver.Target;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;
import io.fireflysoftware.common.reference.master.data.sdk.api.DocumentReferenceCurrentControllerApi;
import io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient;
import io.fireflysoftware.common.reference.master.data.sdk.model.*;
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

/** Genuine generated models/serialization with controlled exchanges only; no owner or native acceptance. */
class DepositAccountTemplateReferenceProviderTest {
    static final Instant NOW=Instant.parse("2026-10-28T12:00:00Z");
    static final String EMPTY="e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    final AtomicReference<Instant> wall=new AtomicReference<>(NOW);final AtomicLong ticks=new AtomicLong();final AtomicInteger calls=new AtomicInteger();
    final Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return wall.get();}};
    final LongSupplier nanos=ticks::get;
    DocumentLayoutDefinition definition(){try {
        String source="{\"revisionId\":\"00000000-0000-0000-0000-000000000006\",\"templateId\":\"00000000-0000-0000-0000-000000000009\",\"templateCode\":\"CONTRACT\",\"version\":\"release.Case\",\"localeCode\":\"en-US\",\"mediaType\":\"text/html\",\"canonicalizationVersion\":\"MASTERS_JSON_V1\",\"sourceDigest\":\""+"b".repeat(64)+"\",\"contentDigest\":\""+"c".repeat(64)+"\",\"schemaDigest\":\""+"d".repeat(64)+"\",\"styleDigest\":\""+EMPTY+"\"}";
        String notice=source.replace("000000000006","000000000010").replace("000000000009","000000000011").replace("CONTRACT","NOTICE_GENERAL").replace("release.Case","notice.1");
        String json="{\"sourceReference\":"+source+",\"profileMetadata\":{\"reference\":{\"code\":\"SYNTHETIC_A4\",\"version\":\"1\",\"mediaType\":\"text/html\",\"manifestDigest\":\""+"2".repeat(64)+"\"},\"sectionMode\":\"AUTHORED_PAGES\",\"pageSize\":\"A4\",\"orientation\":\"PORTRAIT\",\"allowedClassTokens\":[\"document-page\"],\"maxSections\":16,\"maxContentBytes\":262144},\"semanticDefinition\":{\"sectionMode\":\"AUTHORED_PAGES\",\"pageSize\":\"A4\",\"orientation\":\"PORTRAIT\",\"legalNotices\":[{\"role\":\"GENERAL_DISCLOSURE\",\"placement\":\"FOOTER\",\"masterReference\":"+notice+"}]}}";
        return ApiClient.createDefaultMapper(ApiClient.createDefaultDateFormat()).readValue(json,DocumentLayoutDefinition.class);
    }catch(Exception e){throw new AssertionError(e);}}
    String digest(){return DefinitionCanonical.hash("MASTERS_DOCUMENT_LAYOUT_V1",DefinitionCanonical.encode(DefinitionCanonical.tree(definition())));}
    Target target(){return new Target(new Scope(new UUID(0,1),new UUID(0,2),new UUID(0,3),new UUID(0,4),new UUID(0,5),"a".repeat(64)),"DEPOSIT_ACCOUNT_ENROLLMENT",
        new DocumentRequirement("CONTRACT","CONTRACT",true,new ExactReference("core-common-reference-master-data","DOCUMENT_TEMPLATE",new UUID(0,6).toString(),null,"b".repeat(64),"OPAQUE","release.Case"),
            new ExactReference("core-common-config-mgmt","DOCUMENT_ISSUER",new UUID(0,7).toString(),1L,"c".repeat(64)),
            new ExactReference("core-common-reference-master-data","DOCUMENT_LAYOUT",new UUID(0,8).toString(),null,digest(),"OPAQUE","release.Case"),"en-US"));}
    DocumentReferenceExact reference(String kind,long id,String digest){return new DocumentReferenceExact().owner(DocumentReferenceExact.OwnerEnum.CORE_COMMON_REFERENCE_MASTER_DATA)
        .kind(DocumentReferenceExact.KindEnum.fromValue(kind)).id(new UUID(0,id)).versionFormat(DocumentReferenceExact.VersionFormatEnum.OPAQUE).versionText("release.Case").digest(digest);}
    DocumentReferenceCurrentTarget wireTarget(){var s=target().scope();return new DocumentReferenceCurrentTarget().product(new DocumentReferenceProductScope().tenantId(s.tenantId()).rootId(s.rootId()).productId(s.productId()).revisionId(s.revisionId()).publicationId(s.publicationId()).definitionDigest(s.definitionDigest()))
        .purpose(DocumentReferenceCurrentTarget.PurposeEnum.DEPOSIT_ACCOUNT_ENROLLMENT).documentType("CONTRACT").locale("en-US").template(reference("DOCUMENT_TEMPLATE",6,"b".repeat(64))).layout(reference("DOCUMENT_LAYOUT",8,digest()));}
    DocumentReferenceCurrent response(){return new DocumentReferenceCurrent().target(wireTarget()).definition(definition()).observedAtEpochMillis(NOW.toEpochMilli()).effectiveUntilEpochMillis(NOW.plusSeconds(4).toEpochMilli());}
    R2dbcOperationBudget budget(){return R2dbcOperationBudget.observed(NOW,NOW.plusSeconds(5),0,Duration.ofSeconds(5),clock,nanos);}
    DepositAccountTemplateReferenceProvider adapter(Function<ClientRequest,Mono<ClientResponse>> exchange){return adapter(exchange,true);}
    DepositAccountTemplateReferenceProvider adapter(Function<ClientRequest,Mono<ClientResponse>> exchange,boolean auth){
        var format=ApiClient.createDefaultDateFormat();var mapper=ApiClient.createDefaultMapper(format);var web=ApiClient.buildWebClientBuilder(mapper).exchangeFunction(r->{calls.incrementAndGet();return exchange.apply(r);}).build();
        var api=new ApiClient(web,mapper,format);api.setBasePath("https://unit.invalid");if(auth)api.setBearerToken("unit-test-only");
        try{return (DepositAccountTemplateReferenceProvider)Class.forName("io.fireflysoftware.core.product.web.adapters.document.impl.DepositAccountTemplateReferenceProviderImpl")
            .getConstructor(DocumentReferenceCurrentControllerApi.class,Clock.class,LongSupplier.class).newInstance(new DocumentReferenceCurrentControllerApi(api),clock,nanos);}
        catch(InvocationTargetException e){if(e.getCause()instanceof RuntimeException x)throw x;throw new AssertionError(e.getCause());}
        catch(ReflectiveOperationException e){throw new AssertionError("Genuine Masters SDK adapter missing",e);}
    }
    ClientResponse json(DocumentReferenceCurrent value){try{return ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").body(ApiClient.createDefaultMapper(ApiClient.createDefaultDateFormat()).writeValueAsString(value)).build();}catch(Exception e){throw new AssertionError(e);}}
    @Test void generatedRouteAndEffectiveOnlyProvenanceAreExact(){
        var a=adapter(r->{assertEquals(HttpMethod.POST,r.method());assertEquals("/api/v1/document-reference-current-observations",r.url().getPath());assertEquals("Bearer unit-test-only",r.headers().getFirst("Authorization"));return Mono.just(json(response()));});
        var result=a.observe(target(),budget(),NOW.plusSeconds(5)).block();assertNotNull(result);assertEquals(target(),result.target());assertEquals(NOW,result.observedAt());assertNull(result.nativeUntil());
        try{assertEquals("EFFECTIVE_ONLY",result.getClass().getMethod("windowEvidence").invoke(result).toString());}catch(Exception e){throw new AssertionError("Explicit effective-only provenance missing",e);}
        assertEquals(NOW.plusMillis(3999),result.effectiveUntil());assertEquals(1,calls.get());
    }
    @Test void generatedSdkSerializesAllExactRequestFields(){
        var error=new AtomicReference<Throwable>();var a=adapter(request->{
            var sink=new org.springframework.mock.http.client.reactive.MockClientHttpRequest(request.method(),request.url());
            var strategies=ExchangeStrategies.builder().codecs(c->c.defaultCodecs().jackson2JsonEncoder(new org.springframework.http.codec.json.Jackson2JsonEncoder(ApiClient.createDefaultMapper(ApiClient.createDefaultDateFormat()),MediaType.APPLICATION_JSON))).build();
            var context=new org.springframework.web.reactive.function.BodyInserter.Context(){public List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters(){return strategies.messageWriters();}public Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest(){return Optional.empty();}public Map<String,Object> hints(){return Map.of();}};
            return request.body().insert(sink,context).then(Mono.defer(sink::getBodyAsString)).map(body->{try{assertEquals(ApiClient.createDefaultMapper(ApiClient.createDefaultDateFormat()).readTree(ApiClient.createDefaultMapper(ApiClient.createDefaultDateFormat()).writeValueAsString(wireTarget())),ApiClient.createDefaultMapper(ApiClient.createDefaultDateFormat()).readTree(body));return json(response());}catch(Exception e){throw new AssertionError(e);}}).doOnError(error::set);
        });try{assertNotNull(a.observe(target(),budget(),NOW.plusSeconds(5)).block());}catch(RuntimeException e){throw new AssertionError(error.get()==null?e:error.get());}
    }
    @ParameterizedTest @ValueSource(strings={"tenant","root","product","revision","publication","definitionDigest","purposeType","locale","templateId","layoutId","templateDigest","layoutDigest","version","sourceDigest","sourceRevision","sourceLocale","profile","notices","future","longWindow","emptyWindow","missingTarget","missingDefinition","missingStamp"})
    void mismatchedOrUnboundedResponseHasNoFallback(String field){var body=response();var product=body.getTarget().getProduct();switch(field){
        case "tenant"->product.setTenantId(new UUID(0,99));case "root"->product.setRootId(new UUID(0,99));case "product"->product.setProductId(new UUID(0,99));case "revision"->product.setRevisionId(new UUID(0,99));case "publication"->product.setPublicationId(new UUID(0,99));
        case "definitionDigest"->product.setDefinitionDigest("f".repeat(64));case "purposeType"->body.getTarget().setDocumentType("STATEMENT");case "locale"->body.getTarget().setLocale("es-ES");
        case "templateId"->body.getTarget().getTemplate().setId(new UUID(0,99));case "layoutId"->body.getTarget().getLayout().setId(new UUID(0,99));case "templateDigest"->body.getTarget().getTemplate().setDigest("f".repeat(64));case "layoutDigest"->body.getTarget().getLayout().setDigest("f".repeat(64));case "version"->body.getTarget().getLayout().setVersionText("Release.Case");
        case "sourceDigest"->body.getDefinition().getSourceReference().setSourceDigest("f".repeat(64));case "sourceRevision"->body.getDefinition().getSourceReference().setRevisionId(new UUID(0,99));case "sourceLocale"->body.getDefinition().getSourceReference().setLocaleCode("es-ES");case "profile"->body.getDefinition().getProfileMetadata().setMaxSections(17);case "notices"->body.getDefinition().getSemanticDefinition().getLegalNotices().clear();
        case "future"->body.setObservedAtEpochMillis(NOW.plusMillis(1).toEpochMilli());case "longWindow"->body.setEffectiveUntilEpochMillis(NOW.plusMillis(5001).toEpochMilli());case "emptyWindow"->body.setEffectiveUntilEpochMillis(NOW.toEpochMilli());case "missingTarget"->body.setTarget(null);case "missingDefinition"->body.setDefinition(null);case "missingStamp"->body.setObservedAtEpochMillis(null);
    }var a=adapter(r->Mono.just(json(body)));assertThrows(IllegalStateException.class,()->a.observe(target(),budget(),NOW.plusSeconds(5)).block());assertEquals(1,calls.get());}
    @Test void missingConfiguredBearerRefusesBeforeExchange(){assertThrows(IllegalStateException.class,()->adapter(r->Mono.just(json(response())),false).observe(target(),budget(),NOW.plusSeconds(5)).block());assertEquals(0,calls.get());}
    @Test void exhaustedOriginalBudgetNeverReachesExchange(){ticks.set(5_000_000_000L);var a=adapter(r->Mono.just(json(response())));assertThrows(IllegalStateException.class,()->a.observe(target(),budget(),NOW.plusSeconds(5)).block());assertEquals(0,calls.get());}
    @Test void elapsedOwnerAwaitCannotRenewItsEffectiveWindow(){var a=adapter(r->{ticks.set(4_000_000_000L);return Mono.just(json(response()));});assertThrows(IllegalStateException.class,()->a.observe(target(),budget(),NOW.plusSeconds(5)).block());}
    @Test void stalledSdkExchangeUsesOriginalBudget(){var a=adapter(r->Mono.never());StepVerifier.withVirtualTime(()->a.observe(target(),budget(),NOW.plusSeconds(5))).thenAwait(Duration.ofSeconds(6)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));}
    @Test void inheritedEarlierCapAndFractionalOriginalBudgetAreNeverExtended(){wall.set(NOW.plusNanos(123456));var until=NOW.plusSeconds(2).plusNanos(123456);var original=R2dbcOperationBudget.observed(NOW,until,0,Duration.ofSeconds(5),clock,nanos);var result=adapter(r->Mono.just(json(response()))).observe(target(),original,NOW.plusSeconds(3)).block();assertNotNull(result);assertEquals(NOW.plusSeconds(2),result.effectiveUntil());assertNull(result.nativeUntil());}
    @Test void ambientTransactionRefusesBeforeSdkExchange(){var a=adapter(r->Mono.just(json(response())));var work=org.springframework.transaction.reactive.TransactionSynchronizationManager.forCurrentTransaction().flatMap(m->{m.setActualTransactionActive(true);return a.observe(target(),budget(),NOW.plusSeconds(5));}).contextWrite(org.springframework.transaction.reactive.TransactionContextManager.createTransactionContext());assertThrows(IllegalStateException.class,work::block);assertEquals(0,calls.get());}
    @Test void effectiveOnlyMastersComposesWithIssuerWithoutCreatingNativeTimestamp()throws Exception {
        var original=budget();var issuer=new ProductDocumentIssuerReferenceProviderTest().adapter(r->Mono.just(new ProductDocumentIssuerReferenceProviderTest().json(new ProductDocumentIssuerReferenceProviderTest().response())));
        var masters=adapter(r->Mono.just(json(response())));
        ProductDocumentIssuerReferenceProvider sameIssuer=(t,b,u)->{assertSame(original,b);return issuer.observe(t,b,u);};
        DepositAccountTemplateReferenceProvider sameMasters=(t,b,u)->{assertSame(original,b);assertTrue(u.isBefore(NOW.plusSeconds(3)));return masters.observe(t,b,u);};
        var binder=(DepositAccountDocumentReferenceResolver)Class.forName("io.fireflysoftware.core.product.core.services.definition.impl.DepositAccountDocumentReferenceResolverImpl").getConstructor(ProductDocumentIssuerReferenceProvider.class,DepositAccountTemplateReferenceProvider.class,Clock.class,LongSupplier.class).newInstance(sameIssuer,sameMasters,clock,nanos);
        var result=binder.verify(target(),original,NOW.plusSeconds(5)).block();assertNotNull(result);assertNull(result.nativeUntil());assertTrue(result.effectiveUntil().isBefore(NOW.plusSeconds(3)));assertEquals(target(),result.target());
    }
    @ParameterizedTest @ValueSource(strings={"tenant","root","product","revision","publication","digest","purpose","locale","undefinedLocale","templateOwner","layoutKind","numericVersion","opaqueVersion","versionMismatch","sameId","nilTemplate","nilLayout","referenceDigest"})
    void malformedRequestRefusesBeforeAnySdkExchange(String field){
        var t=target();var s=t.scope();var d=t.document();UUID nil=new UUID(0,0);
        var scope=new Scope(field.equals("tenant")?nil:s.tenantId(),field.equals("root")?nil:s.rootId(),field.equals("product")?nil:s.productId(),field.equals("revision")?nil:s.revisionId(),field.equals("publication")?nil:s.publicationId(),field.equals("digest")?"bad":s.definitionDigest());
        var template=d.template();var layout=d.layout();
        switch(field){
            case "templateOwner"->template=new ExactReference("core-common-config-mgmt",template.kind(),template.id(),null,template.digest(),"OPAQUE",template.versionText());
            case "layoutKind"->layout=new ExactReference(layout.owner(),"DOCUMENT_TEMPLATE",layout.id(),null,layout.digest(),"OPAQUE",layout.versionText());
            case "numericVersion"->template=new ExactReference(template.owner(),template.kind(),template.id(),1L,template.digest());
            case "opaqueVersion"->template=new ExactReference(template.owner(),template.kind(),template.id(),null,template.digest(),"OPAQUE","release/Case");
            case "versionMismatch"->layout=new ExactReference(layout.owner(),layout.kind(),layout.id(),null,layout.digest(),"OPAQUE","Release.Case");
            case "sameId"->layout=new ExactReference(layout.owner(),layout.kind(),template.id(),null,layout.digest(),"OPAQUE",layout.versionText());
            case "nilTemplate"->template=new ExactReference(template.owner(),template.kind(),nil.toString(),null,template.digest(),"OPAQUE",template.versionText());
            case "nilLayout"->layout=new ExactReference(layout.owner(),layout.kind(),nil.toString(),null,layout.digest(),"OPAQUE",layout.versionText());
            case "referenceDigest"->layout=new ExactReference(layout.owner(),layout.kind(),layout.id(),null,"bad","OPAQUE",layout.versionText());
        }
        var invalid=new Target(scope,field.equals("purpose")?"INSURANCE_QUOTE":t.purpose(),new DocumentRequirement(d.code(),d.documentType(),d.mandatory(),template,d.issuer(),layout,field.equals("locale")?"en-us":field.equals("undefinedLocale")?"und":d.locale()));
        var a=adapter(r->Mono.just(json(response())));assertThrows(IllegalStateException.class,()->a.observe(invalid,budget(),NOW.plusSeconds(5)).block());assertEquals(0,calls.get());
    }
    @Test void ownerFailureHasNoHistoricalFallback(){var a=adapter(r->Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()));assertThrows(IllegalStateException.class,()->a.observe(target(),budget(),NOW.plusSeconds(5)).block());assertEquals(1,calls.get());}
    @Test void emptyOwnerBodyHasNoSuccessFallback(){var a=adapter(r->Mono.just(ClientResponse.create(HttpStatus.OK).build()));assertThrows(IllegalStateException.class,()->a.observe(target(),budget(),NOW.plusSeconds(5)).block());}
    @Test void exactFiveSecondEffectiveBoundaryRemainsConservative(){var a=adapter(r->Mono.just(json(response().effectiveUntilEpochMillis(NOW.plusSeconds(5).toEpochMilli()))));var result=a.observe(target(),budget(),NOW.plusSeconds(5)).block();assertNotNull(result);assertNull(result.nativeUntil());assertEquals(NOW.plusMillis(4999),result.effectiveUntil());}
    @Test void genuineOwnerResponseHasNoNativeUntilAccessor(){assertThrows(NoSuchMethodException.class,()->DocumentReferenceCurrent.class.getMethod("getNativeUntilEpochMillis"));}
    @Test void layoutDigestMatchesTheRetainedActualOwnerCanonicalVector(){
        assertEquals("e50caecb829ffe124e5b423dd3eb4e5ce28638dde0f3b89bb82db000cc0b3179",digest());
    }
}

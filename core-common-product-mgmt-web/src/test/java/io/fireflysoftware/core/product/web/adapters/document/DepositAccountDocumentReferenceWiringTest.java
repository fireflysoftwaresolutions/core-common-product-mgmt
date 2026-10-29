package io.fireflysoftware.core.product.web.adapters.document;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.core.services.definition.DepositAccountDocumentReferenceResolver.*;
import io.fireflysoftware.core.product.web.adapters.document.ProductDocumentOwnerTokenProvider.*;
import java.lang.reflect.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.*;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Genuine SDK codecs with controlled exchanges only; no owner HTTP or native authority. */
class DepositAccountDocumentReferenceWiringTest {
    static final Instant NOW=ProductDocumentIssuerReferenceProviderTest.NOW;
    static final URI CONFIG=URI.create("https://config.unit.invalid"),MASTERS=URI.create("https://masters.unit.invalid");
    final AtomicReference<Instant> wall=new AtomicReference<>(NOW);
    final AtomicLong tick=new AtomicLong();final AtomicInteger tokenCalls=new AtomicInteger();
    final List<ClientRequest> requests=new CopyOnWriteArrayList<>();
    final Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return wall.get();}};
    final ProductDocumentIssuerReferenceProviderTest issuers=new ProductDocumentIssuerReferenceProviderTest();
    final DepositAccountTemplateReferenceProviderTest templates=new DepositAccountTemplateReferenceProviderTest();
    Target target(){return templates.target();}
    R2dbcOperationBudget budget(){return R2dbcOperationBudget.observed(NOW,NOW.plusSeconds(30),0,Duration.ofSeconds(30),clock,tick::get);}
    AuthorizedToken token(Owner owner,Target target,Instant until,String suffix){return new AuthorizedToken(owner,target,"https://issuer.unit.invalid","delegated-staff",target.scope().tenantId(),until,owner.name()+"-"+suffix);}
    ProductDocumentOwnerTokenProvider tokens(){return(owner,t,b,u)->{tokenCalls.incrementAndGet();return Mono.just(token(owner,t,NOW.plusSeconds(4),"secret"));};}
    Mono<ClientResponse> exchange(ClientRequest request){requests.add(request);return Mono.just(request.url().getHost().equals(CONFIG.getHost())?issuers.json(issuers.response()):templates.json(templates.response()));}
    DepositAccountDocumentReferenceResolver resolver(ProductDocumentOwnerTokenProvider tokens,ExchangeFunction exchange){return resolver(tokens,exchange,CONFIG,MASTERS);}
    DepositAccountDocumentReferenceResolver resolver(ProductDocumentOwnerTokenProvider tokens,ExchangeFunction exchange,URI config,URI masters){
        try{var c=Class.forName("io.fireflysoftware.core.product.web.adapters.document.impl.SdkDepositAccountDocumentReferenceResolver")
                .getDeclaredConstructor(ProductDocumentOwnerTokenProvider.class,URI.class,URI.class,String.class,String.class,Clock.class,LongSupplier.class,ExchangeFunction.class);
            c.setAccessible(true);return(DepositAccountDocumentReferenceResolver)c.newInstance(tokens,config,masters,"https://issuer.unit.invalid","https://issuer.unit.invalid",clock,(LongSupplier)tick::get,exchange);
        }catch(InvocationTargetException e){if(e.getCause()instanceof RuntimeException x)throw x;throw new AssertionError(e.getCause());}
        catch(ReflectiveOperationException e){throw new AssertionError("Genuine configured owner resolver missing",e);}
    }
    @Test void realGeneratedClientsUseBothOwnerBearersAndSameOriginalBudget(){
        var original=budget();ProductDocumentOwnerTokenProvider credentials=(owner,t,b,u)->{assertSame(original,b);assertEquals(target(),t);assertTrue(!u.isAfter(NOW.plusSeconds(5)));tokenCalls.incrementAndGet();return Mono.just(token(owner,t,NOW.plusSeconds(4),"secret"));};
        var value=resolver(credentials,this::exchange).verify(target(),original,NOW.plusSeconds(20)).block();assertNotNull(value);assertEquals(target(),value.target());assertNull(value.nativeUntil());assertEquals(WindowEvidence.EFFECTIVE_ONLY,value.windowEvidence());assertTrue(value.effectiveUntil().isBefore(NOW.plusSeconds(3)));assertEquals(2,requests.size());assertEquals(2,tokenCalls.get());
        assertEquals(CONFIG.getHost(),requests.get(0).url().getHost());assertEquals("Bearer CONFIGURATION-secret",requests.get(0).headers().getFirst("Authorization"));assertEquals(MASTERS.getHost(),requests.get(1).url().getHost());assertEquals("Bearer MASTERS-secret",requests.get(1).headers().getFirst("Authorization"));
    }
    @Test void tokenExpiryNarrowsBothOwnersAndTheReturnedObservation(){var provider=(ProductDocumentOwnerTokenProvider)(owner,t,b,u)->Mono.just(token(owner,t,NOW.plusSeconds(2),"secret"));var value=resolver(provider,this::exchange).verify(target(),budget(),NOW.plusSeconds(20)).block();assertNotNull(value);assertFalse(value.effectiveUntil().isAfter(NOW.plusSeconds(2)));}
    @ParameterizedTest @ValueSource(strings={"owner","target","tenant","issuer","expired"})
    void wrongOrExpiredDelegationNeverReachesSdk(String variant){
        ProductDocumentOwnerTokenProvider provider=(owner,t,b,u)->{var otherScope=new Scope(t.scope().tenantId(),UUID.randomUUID(),t.scope().productId(),t.scope().revisionId(),t.scope().publicationId(),t.scope().definitionDigest());return Mono.just(new AuthorizedToken(variant.equals("owner")?Owner.MASTERS:owner,variant.equals("target")?new Target(otherScope,t.purpose(),t.document()):t,variant.equals("issuer")?"https://other.unit.invalid":"https://issuer.unit.invalid","staff",variant.equals("tenant")?UUID.randomUUID():t.scope().tenantId(),variant.equals("expired")?NOW:NOW.plusSeconds(4),"secret"));};
        assertThrows(IllegalStateException.class,()->resolver(provider,this::exchange).verify(target(),budget(),NOW.plusSeconds(20)).block());assertTrue(requests.isEmpty());
    }
    @Test void emptyDelegationHasNoFallback(){assertThrows(IllegalStateException.class,()->resolver((a,b,c,d)->Mono.empty(),this::exchange).verify(target(),budget(),NOW.plusSeconds(20)).block());assertTrue(requests.isEmpty());}
    @Test void credentialWaitUsesInitialFiveSeconds(){StepVerifier.withVirtualTime(()->resolver((a,b,c,d)->Mono.never(),this::exchange).verify(target(),budget(),NOW.plusSeconds(20))).thenAwait(Duration.ofSeconds(6)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));assertTrue(requests.isEmpty());}
    @Test void earlierInheritedDeadlineBoundsCredentialWait(){StepVerifier.withVirtualTime(()->resolver((a,b,c,d)->Mono.never(),this::exchange).verify(target(),budget(),NOW.plusSeconds(2))).thenAwait(Duration.ofSeconds(3)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));assertTrue(requests.isEmpty());}
    @Test void expiryAfterIssuerStopsBeforeMasterDelegation(){var provider=(ProductDocumentOwnerTokenProvider)(owner,t,b,u)->{tokenCalls.incrementAndGet();return Mono.just(token(owner,t,NOW.plusSeconds(1),"secret"));};var value=resolver(provider,r->{var result=exchange(r);wall.set(NOW.plusMillis(1500));tick.set(1_500_000_000);return result;});assertThrows(IllegalStateException.class,()->value.verify(target(),budget(),NOW.plusSeconds(20)).block());assertEquals(1,requests.size());assertEquals(1,tokenCalls.get());}
    @Test void concurrentRequestsCannotShareOrForwardCredentials(){ProductDocumentOwnerTokenProvider provider=(owner,t,b,u)->Mono.deferContextual(c->Mono.just(token(owner,t,NOW.plusSeconds(4),c.get("caller"))));var value=resolver(provider,r->exchange(r).delayElement(Duration.ofMillis(1)));Mono.zip(value.verify(target(),budget(),NOW.plusSeconds(20)).contextWrite(c->c.put("caller","A").put("Authorization","Bearer inbound")),value.verify(target(),budget(),NOW.plusSeconds(20)).contextWrite(c->c.put("caller","B"))).block();assertEquals(4,requests.size());assertEquals(Set.of("Bearer CONFIGURATION-A","Bearer CONFIGURATION-B","Bearer MASTERS-A","Bearer MASTERS-B"),new HashSet<>(requests.stream().map(r->r.headers().getFirst("Authorization")).toList()));}
    @Test void redirectCannotLeakBearerOrReachNextOwner(){var value=resolver(tokens(),r->{requests.add(r);return Mono.just(ClientResponse.create(HttpStatus.FOUND).header("Location","https://other.unit.invalid/leak").build());});assertThrows(IllegalStateException.class,()->value.verify(target(),budget(),NOW.plusSeconds(20)).block());assertEquals(1,requests.size());assertEquals(CONFIG.getHost(),requests.getFirst().url().getHost());}
    @Test void errorsAndTokenPresentationNeverExposeBearer(){var t=token(Owner.CONFIGURATION,target(),NOW.plusSeconds(4),"private-sentinel");assertFalse(t.toString().contains("private-sentinel"));var error=assertThrows(IllegalStateException.class,()->resolver((a,b,c,d)->Mono.error(new IllegalStateException("private-sentinel")),this::exchange).verify(target(),budget(),NOW.plusSeconds(20)).block());assertFalse(error.toString().contains("private-sentinel"));assertNull(error.getCause());}
    @Test void headerInjectionRefusesBeforeSdk(){assertThrows(IllegalArgumentException.class,()->token(Owner.CONFIGURATION,target(),NOW.plusSeconds(4),"bad\r\nheader"));assertTrue(requests.isEmpty());}
    @Test void ambientSqlRefusesBeforeDelegation(){var value=resolver(tokens(),this::exchange);var work=org.springframework.transaction.reactive.TransactionSynchronizationManager.forCurrentTransaction().flatMap(m->{m.setActualTransactionActive(true);return value.verify(target(),budget(),NOW.plusSeconds(20));}).contextWrite(org.springframework.transaction.reactive.TransactionContextManager.createTransactionContext());assertThrows(IllegalStateException.class,work::block);assertEquals(0,tokenCalls.get());}
    @ParameterizedTest @ValueSource(strings={"http://config.unit.invalid","https://u:p@config.unit.invalid","https://config.unit.invalid/path","https://config.unit.invalid?q=x","https://config.unit.invalid#fragment"})
    void onlyConfiguredHttpsOriginsAreAccepted(String origin){assertThrows(IllegalArgumentException.class,()->resolver(tokens(),this::exchange,URI.create(origin),MASTERS));assertTrue(requests.isEmpty());}
    Class<?> configuration(){try{return Class.forName("io.fireflysoftware.core.product.web.config.definition.lifecycle.DepositDocumentOwnerConfiguration");}catch(ClassNotFoundException e){throw new AssertionError("Explicit owner configuration missing",e);}}
    @Test void defaultConfigurationAddsNoResolverOrCredentialProvider(){try(var c=new AnnotationConfigApplicationContext()){c.register(configuration());c.refresh();assertTrue(c.getBeansOfType(DepositAccountDocumentReferenceResolver.class).isEmpty());assertTrue(c.getBeansOfType(ProductDocumentOwnerTokenProvider.class).isEmpty());}}
    @Test void explicitTrustedProviderConstructsTheRequiredResolverWithoutRequestingCredentials(){try(var c=new AnnotationConfigApplicationContext()){c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("product.definitions.deposit.enabled","true","product.definitions.deposit.document-references.enabled","true","product.definitions.deposit.document-references.configuration-base",CONFIG.toString(),"product.definitions.deposit.document-references.masters-base",MASTERS.toString(),"product.definitions.deposit.document-references.configuration-issuer","https://issuer.unit.invalid","product.definitions.deposit.document-references.masters-issuer","https://issuer.unit.invalid")));c.registerBean(ProductDocumentOwnerTokenProvider.class,()->tokens());c.register(configuration());c.refresh();assertEquals(1,c.getBeansOfType(DepositAccountDocumentReferenceResolver.class).size());assertEquals(0,tokenCalls.get());assertTrue(requests.isEmpty());}}
    @Test void explicitActivationWithoutTrustedProviderFailsClosed(){try(var c=new AnnotationConfigApplicationContext()){c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("product.definitions.deposit.enabled","true","product.definitions.deposit.document-references.enabled","true","product.definitions.deposit.document-references.configuration-base",CONFIG.toString(),"product.definitions.deposit.document-references.masters-base",MASTERS.toString(),"product.definitions.deposit.document-references.configuration-issuer","https://issuer.unit.invalid","product.definitions.deposit.document-references.masters-issuer","https://issuer.unit.invalid")));c.register(configuration());assertThrows(RuntimeException.class,c::refresh);}}
}

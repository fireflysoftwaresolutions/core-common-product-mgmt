/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.product.web.adapters.identity.impl;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Actual generated SDK and controlled HTTP/OAuth transports; no verified token, Main or native owner. */
class GeneratedConfigInsuranceQuoteTenantBindingResolverTest {
    private static final Instant NOW=Instant.parse("2026-11-01T12:00:00Z");
    private static final String TENANT="10000000-0000-4000-8000-000000000001";
    private static final Clock CLOCK=Clock.fixed(NOW,ZoneOffset.UTC);
    private final AtomicReference<SecurityPrincipal> caller=new AtomicReference<>(SecurityPrincipal.builder().issuer("https://identity.example").subject("original-quote-caller").tenantId("opaque-bank-east").claims(Map.of("tenant_id","opaque-bank-east","exp",NOW.plusSeconds(60),"nbf",NOW.minusSeconds(1))).build());
    private final AtomicInteger requests=new AtomicInteger();
    private ClientRegistration registration(){return ClientRegistration.withRegistrationId("configuration-current").clientId("configured-machine").clientSecret("synthetic-test-secret").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).tokenUri("https://identity.example/token").build();}
    private ReactiveOAuth2AuthorizedClientManager manager(){return request->{assertEquals("configuration-current",request.getClientRegistrationId());assertEquals("product-insurance-quote-current-client",request.getPrincipal().getName());return Mono.just(new OAuth2AuthorizedClient(registration(),request.getPrincipal().getName(),new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"synthetic-controlled-bearer",NOW.minusSeconds(1),NOW.plusSeconds(30))));};}
    private String body(){return "{\"reference\":{\"bindingId\":\"10000000-0000-4000-8000-000000000002\",\"version\":1,\"definitionDigest\":\""+"a".repeat(64)+"\"},\"definition\":{\"bindingId\":\"10000000-0000-4000-8000-000000000002\",\"namespace\":{\"issuer\":\"https://identity.example\",\"platformTenant\":\"opaque-bank-east\"},\"resourceTenant\":{\"value\":\""+TENANT+"\"},\"version\":1,\"eligibility\":{\"tenantGeneration\":0,\"statusId\":\"10000000-0000-4000-8000-000000000003\",\"statusGeneration\":0},\"notAfterEpochMillis\":"+NOW.plusSeconds(60).toEpochMilli()+"},\"controlVersion\":1,\"observedAtEpochMillis\":"+NOW.toEpochMilli()+",\"validUntilEpochMillis\":"+NOW.plusSeconds(4).toEpochMilli()+"}";}
    private GeneratedConfigInsuranceQuoteTenantBindingResolverImpl adapter(ReactiveOAuth2AuthorizedClientManager clients,ExchangeFunction exchange){return new GeneratedConfigInsuranceQuoteTenantBindingResolverImpl(()->Mono.just(caller.get()),clients,URI.create("https://configuration.example"),"configuration-current","https://identity.example","opaque-bank-east","tenant_id",CLOCK,exchange);}
    private Mono<io.fireflysoftware.core.product.core.services.definition.InsuranceQuoteTenantBindingResolver.Observation> resolve(GeneratedConfigInsuranceQuoteTenantBindingResolverImpl adapter){return adapter.resolve(caller.get(),UUID.fromString(TENANT),NOW.plusSeconds(5),()->Duration.ofSeconds(5));}
    private ClientResponse response(String body){return ClientResponse.create(HttpStatus.OK).header(HttpHeaders.CONTENT_TYPE,MediaType.APPLICATION_JSON_VALUE).body(body).build();}
    @Test void actualGeneratedSdkUsesExactZeroArgumentOwnerRouteAndOwnMachineToken(){var a=adapter(manager(),request->{requests.incrementAndGet();assertEquals(HttpMethod.GET,request.method());assertEquals(URI.create("https://configuration.example/api/v1/tenant-resource-bindings/current"),request.url());assertEquals("Bearer synthetic-controlled-bearer",request.headers().getFirst(HttpHeaders.AUTHORIZATION));return Mono.just(response(body()));});var original=caller.get();var observed=resolve(a).block();assertEquals(UUID.fromString(TENANT),observed.resourceTenant());assertEquals("opaque-bank-east",observed.platformTenant());assertEquals(0,observed.tenantGeneration());assertEquals(0,observed.statusGeneration());assertEquals(original,caller.get());assertEquals(1,requests.get());}
    @Test void wrongResourceNamespaceVersionOrWindowRefuses(){for(var body:List.of(body().replace(TENANT,"10000000-0000-4000-8000-000000000099"),body().replace("opaque-bank-east","opaque-bank-west"),body().replace("\"version\":1,\"eligibility\"","\"version\":2,\"eligibility\""),body().replace("\"validUntilEpochMillis\":"+NOW.plusSeconds(4).toEpochMilli(),"\"validUntilEpochMillis\":"+NOW.toEpochMilli()),body().replace("\"observedAtEpochMillis\":"+NOW.toEpochMilli(),"\"observedAtEpochMillis\":"+NOW.minusMillis(1).toEpochMilli()))){var a=adapter(manager(),request->Mono.just(response(body)));assertThrows(IllegalStateException.class,()->resolve(a).block());}}
    @Test void noAuthorizedMachineNeverCallsOwner(){var a=adapter(request->Mono.empty(),request->{requests.incrementAndGet();return Mono.just(response(body()));});assertThrows(IllegalStateException.class,()->resolve(a).block());assertEquals(0,requests.get());}
    @Test void wrongAuthorizedMachineIdentityNeverCallsOwner(){var a=adapter(request->Mono.just(new OAuth2AuthorizedClient(registration(),"other-machine",new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"synthetic-controlled-bearer",NOW.minusSeconds(1),NOW.plusSeconds(30)))),request->{requests.incrementAndGet();return Mono.just(response(body()));});assertThrows(IllegalStateException.class,()->resolve(a).block());assertEquals(0,requests.get());}
    @Test void redirectsCannotForwardMachineCredentialToAnotherOrigin(){var a=adapter(manager(),request->{requests.incrementAndGet();return Mono.just(ClientResponse.create(HttpStatus.FOUND).header(HttpHeaders.LOCATION,"https://other.example/current").build());});assertThrows(IllegalStateException.class,()->resolve(a).block());assertEquals(1,requests.get());}
    @Test void currentCallerChangeDuringOwnerIoWithholdsObservation(){var original=caller.get();var a=adapter(manager(),request->{caller.set(original.toBuilder().subject("another-caller").build());return Mono.just(response(body()));});assertThrows(IllegalStateException.class,()->resolve(a).block());}
    @Test void machineAuthorizationStallHasFiniteNoOwnerOutcome(){var a=adapter(request->Mono.never(),request->{requests.incrementAndGet();return Mono.just(response(body()));});StepVerifier.withVirtualTime(()->resolve(a)).thenAwait(Duration.ofSeconds(6)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));assertEquals(0,requests.get());}
    @Test void missingNamespaceIsNotAnImplicitUuidFallback(){assertThrows(IllegalArgumentException.class,()->new GeneratedConfigInsuranceQuoteTenantBindingResolverImpl(()->Mono.just(caller.get()),manager(),URI.create("https://configuration.example"),"configuration-current","https://identity.example",null,"tenant_id",CLOCK));}
    @Test void changedCallerDuringMachineAuthorizationNeverCallsCurrentOwner(){var original=caller.get();var delegate=manager();var a=adapter(request->{caller.set(original.toBuilder().subject("changed-reader").build());return delegate.authorize(request);},request->{requests.incrementAndGet();return Mono.just(response(body()));});assertThrows(IllegalStateException.class,()->resolve(a).block());assertEquals(0,requests.get());}

}

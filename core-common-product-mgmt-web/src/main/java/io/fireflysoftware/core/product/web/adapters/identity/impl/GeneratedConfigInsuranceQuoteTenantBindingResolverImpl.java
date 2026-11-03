/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.product.web.adapters.identity.impl;

import io.fireflysoftware.common.config.sdk.api.TenantResourceBindingCurrentApi;
import io.fireflysoftware.common.config.sdk.invoker.ApiClient;
import io.fireflysoftware.common.config.sdk.model.TenantResourceBindingCurrent;
import io.fireflysoftware.core.product.core.services.definition.InsuranceQuoteTenantBindingResolver;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.http.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/** Genuine Configuration CURRENT through an explicitly configured machine registration. */
public final class GeneratedConfigInsuranceQuoteTenantBindingResolverImpl implements InsuranceQuoteTenantBindingResolver {
    private record Lookup(SecurityPrincipal principal,UUID resource,Instant originalUntil,Supplier<Duration> remaining) {}
    private final SecurityContextPort principals;
    private final TenantResourceBindingCurrentApi current;
    private final String issuer,platformTenant,tenantClaim;
    private final Clock clock;
    private final Object lookupKey=new Object();

    public GeneratedConfigInsuranceQuoteTenantBindingResolverImpl(SecurityContextPort principals,ReactiveOAuth2AuthorizedClientManager manager,
            URI endpoint,String registrationId,String issuer,String platformTenant,String tenantClaim,Clock clock) {
        this(principals,manager,endpoint,registrationId,issuer,platformTenant,tenantClaim,clock,null);
    }
    GeneratedConfigInsuranceQuoteTenantBindingResolverImpl(SecurityContextPort principals,ReactiveOAuth2AuthorizedClientManager manager,
            URI endpoint,String registrationId,String issuer,String platformTenant,String tenantClaim,Clock clock,ExchangeFunction exchange) {
        this.principals=Objects.requireNonNull(principals);Objects.requireNonNull(manager);this.clock=Objects.requireNonNull(clock);
        this.issuer=text(issuer,512);this.platformTenant=text(platformTenant,1024);this.tenantClaim=text(tenantClaim,64);
        if(registrationId==null||!registrationId.matches("[A-Za-z0-9._-]{1,100}")||endpoint==null||!"https".equals(endpoint.getScheme())
                ||endpoint.getHost()==null||endpoint.getRawUserInfo()!=null||endpoint.getRawQuery()!=null||endpoint.getRawFragment()!=null
                ||!Set.of("","/").contains(endpoint.getRawPath())||endpoint.getPort()==0||endpoint.getPort()>65535)throw invalid();
        String base=endpoint.toASCIIString().replaceFirst("/$","");URI exact=URI.create(base+"/api/v1/tenant-resource-bindings/current");
        var machine=UsernamePasswordAuthenticationToken.authenticated("product-insurance-quote-current-client",null,List.of());
        var format=ApiClient.createDefaultDateFormat();var mapper=ApiClient.createDefaultMapper(format);var builder=ApiClient.buildWebClientBuilder(mapper);
        if(exchange==null)builder.clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false).responseTimeout(Duration.ofSeconds(5))));else builder.exchangeFunction(exchange);
        WebClient authorized=builder.filter((request,next)->Mono.deferContextual(context->{
            Lookup lookup=context.getOrDefault(lookupKey,null);if(lookup==null)return Mono.error(denied());requireLive(lookup);
            if(request.method()!=HttpMethod.GET||!exact.equals(request.url())||request.headers().containsHeader(HttpHeaders.AUTHORIZATION))return Mono.error(denied());
            return Mono.defer(()->manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(registrationId).principal(machine).build()))
                .switchIfEmpty(Mono.error(denied())).timeout(lookup.remaining.get()).flatMap(authorization->{
                    requireLive(lookup);var registration=authorization.getClientRegistration();var token=authorization.getAccessToken();
                    if(!machine.getName().equals(authorization.getPrincipalName())||!registrationId.equals(registration.getRegistrationId())||!AuthorizationGrantType.CLIENT_CREDENTIALS.equals(registration.getAuthorizationGrantType())
                            ||token==null||!OAuth2AccessToken.TokenType.BEARER.equals(token.getTokenType())||token.getTokenValue().isBlank()
                            ||token.getExpiresAt()==null||!clock.instant().isBefore(token.getExpiresAt()))return Mono.error(denied());
                    return requireCaller(lookup).then(Mono.defer(()->next.exchange(ClientRequest.from(request).headers(headers->headers.setBearerAuth(token.getTokenValue())).build())))
                        .timeout(lookup.remaining.get()).flatMap(response->response.statusCode().is3xxRedirection()?response.releaseBody().then(Mono.error(denied())):Mono.just(response));
                });
        })).build();
        current=new TenantResourceBindingCurrentApi(new ApiClient(authorized,mapper,format).setBasePath(base));
    }
    @Override public Mono<Observation> resolve(SecurityPrincipal original,UUID resourceTenant,Instant originalUntil,Supplier<Duration> remaining) {
        return Mono.defer(()->{
            var lookup=new Lookup(Objects.requireNonNull(original),Objects.requireNonNull(resourceTenant),Objects.requireNonNull(originalUntil),Objects.requireNonNull(remaining));requireLive(lookup);Instant started=clock.instant();
            return requireCaller(lookup).then(Mono.defer(current::observeCurrentTenantResourceBinding)).switchIfEmpty(Mono.error(denied()))
                .map(value->snapshot(value,lookup,started)).flatMap(value->requireCaller(lookup).thenReturn(value))
                .timeout(remaining.get()).contextWrite(context->context.put(lookupKey,lookup));
        }).onErrorMap(error->denied());
    }
    private Mono<Void> requireCaller(Lookup lookup) {
        return Mono.defer(principals::currentPrincipal).switchIfEmpty(Mono.error(denied())).flatMap(principal->{
            requireLive(lookup);var expiry=principal.claim("exp",Instant.class);var nbf=principal.claim("nbf",Instant.class);
            if(!lookup.principal.equals(principal)||!issuer.equals(principal.issuer())||!platformTenant.equals(principal.tenantId())
                    ||!platformTenant.equals(principal.claim(tenantClaim,String.class))||expiry==null||nbf==null
                    ||!clock.instant().isBefore(expiry)||clock.instant().isBefore(nbf)||!nbf.isBefore(expiry))throw denied();return Mono.<Void>empty();
        }).timeout(lookup.remaining.get());
    }
    private Observation snapshot(TenantResourceBindingCurrent value,Lookup lookup,Instant started) {
        requireLive(lookup);var reference=value.getReference();var definition=value.getDefinition();if(reference==null||definition==null)throw denied();
        var namespace=definition.getNamespace();var resource=definition.getResourceTenant();var eligibility=definition.getEligibility();if(namespace==null||resource==null||eligibility==null)throw denied();
        var binding=reference.getBindingId();var version=reference.getVersion();var control=value.getControlVersion();var tenantGeneration=eligibility.getTenantGeneration();var statusGeneration=eligibility.getStatusGeneration();
        var observed=value.getObservedAtEpochMillis();var valid=value.getValidUntilEpochMillis();var business=definition.getNotAfterEpochMillis();var now=clock.instant();
        if(binding==null||!binding.equals(definition.getBindingId())||version==null||!version.equals(definition.getVersion())
                ||!lookup.resource.equals(resource.getValue())||!issuer.equals(namespace.getIssuer())||!platformTenant.equals(namespace.getPlatformTenant())
                ||control==null||tenantGeneration==null||statusGeneration==null||observed==null||valid==null||business==null
                ||observed<started.toEpochMilli()||observed>now.toEpochMilli()||valid<=now.toEpochMilli()||business<=now.toEpochMilli()||business>253402300799999L)throw denied();
        var result=new Observation(namespace.getIssuer(),namespace.getPlatformTenant(),resource.getValue(),binding,version,reference.getDefinitionDigest(),control,tenantGeneration,eligibility.getStatusId(),statusGeneration,Instant.ofEpochMilli(observed),Instant.ofEpochMilli(valid),Instant.ofEpochMilli(business));
        requireLive(lookup);return result;
    }
    private void requireLive(Lookup lookup){lookup.remaining.get();if(!clock.instant().isBefore(lookup.originalUntil))throw denied();lookup.remaining.get();}
    private static String text(String value,int max){if(value==null||value.isBlank()||value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>max||value.codePoints().anyMatch(c->Character.isISOControl(c)||c>=0xD800&&c<=0xDFFF))throw invalid();return value;}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Explicit trusted quote namespace, HTTPS origin and machine registration required");}
    private static IllegalStateException denied(){return new IllegalStateException("PRODUCT_INSURANCE_CURRENT_TENANT_BINDING_UNAVAILABLE");}
    @Override public String toString(){return "GeneratedConfigInsuranceQuoteTenantBindingResolverImpl";}
}

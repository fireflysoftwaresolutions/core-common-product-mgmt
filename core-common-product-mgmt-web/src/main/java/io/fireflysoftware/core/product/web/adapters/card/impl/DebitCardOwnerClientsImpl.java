/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.product.web.adapters.card.impl;

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

/** Own configured machine identities, exact owner origins and the original debit caller/window. */
public final class DebitCardOwnerClientsImpl {
    record Lookup(SecurityPrincipal caller,Supplier<Duration> remaining){@Override public String toString(){return "DebitCardOwnerLookup";}}
    private final SecurityContextPort security;
    private final ReactiveOAuth2AuthorizedClientManager manager;
    private final Clock clock;
    private final Object key=new Object();
    public DebitCardOwnerClientsImpl(SecurityContextPort security,ReactiveOAuth2AuthorizedClientManager manager,Clock clock){this.security=Objects.requireNonNull(security);this.manager=Objects.requireNonNull(manager);this.clock=Objects.requireNonNull(clock);}
    <T> Mono<T> within(SecurityPrincipal caller,Supplier<Duration> remaining,Supplier<Mono<T>> work){return Mono.defer(work).contextWrite(context->context.put(key,new Lookup(caller,remaining)));}
    WebClient transport(WebClient.Builder builder,URI endpoint,String registrationId,ExchangeFunction exchange){
        URI origin=origin(endpoint);if(registrationId==null||!registrationId.matches("[A-Za-z0-9._-]{1,100}"))throw invalid();
        var machine=UsernamePasswordAuthenticationToken.authenticated("product-debit-owner-"+registrationId,null,List.of());
        if(exchange==null)builder.clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false).responseTimeout(Duration.ofSeconds(5))));else builder.exchangeFunction(exchange);
        return builder.filter((request,next)->Mono.deferContextual(context->{
            Lookup lookup=context.getOrDefault(key,null);if(lookup==null)return Mono.error(unavailable());lookup.remaining.get();URI target=request.url();
            if(!"https".equals(target.getScheme())||target.getRawUserInfo()!=null||!origin.getHost().equalsIgnoreCase(target.getHost())||port(origin)!=port(target)
                    ||target.getRawFragment()!=null||!Set.of(HttpMethod.GET,HttpMethod.POST).contains(request.method())||request.headers().containsHeader(HttpHeaders.AUTHORIZATION))return Mono.error(unavailable());
            return requireCaller(lookup).then(Mono.defer(()->manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(registrationId).principal(machine).build())))
                .switchIfEmpty(Mono.error(unavailable())).timeout(lookup.remaining.get()).flatMap(client->{
                    lookup.remaining.get();var registration=client.getClientRegistration();var token=client.getAccessToken();
                    if(!registrationId.equals(registration.getRegistrationId())||!machine.getName().equals(client.getPrincipalName())
                            ||!AuthorizationGrantType.CLIENT_CREDENTIALS.equals(registration.getAuthorizationGrantType())||token==null
                            ||!OAuth2AccessToken.TokenType.BEARER.equals(token.getTokenType())||token.getTokenValue().isBlank()
                            ||token.getExpiresAt()==null||!clock.instant().isBefore(token.getExpiresAt()))return Mono.error(unavailable());
                    return requireCaller(lookup).then(Mono.defer(()->next.exchange(ClientRequest.from(request).headers(headers->headers.setBearerAuth(token.getTokenValue())).build())))
                        .timeout(lookup.remaining.get()).flatMap(response->response.statusCode().is3xxRedirection()?response.releaseBody().then(Mono.error(unavailable())):Mono.just(response));
                });
        })).build();
    }
    private Mono<Void> requireCaller(Lookup lookup){return Mono.defer(security::currentPrincipal).switchIfEmpty(Mono.error(unavailable())).flatMap(current->{lookup.remaining.get();if(!lookup.caller.equals(current))throw unavailable();return Mono.<Void>empty();}).timeout(lookup.remaining.get());}
    static URI origin(URI value){if(value==null||!"https".equals(value.getScheme())||value.getHost()==null||value.getRawUserInfo()!=null||value.getRawQuery()!=null||value.getRawFragment()!=null||!Set.of("","/").contains(value.getRawPath())||value.getPort()==0||value.getPort()>65535)throw invalid();return URI.create(value.toASCIIString().replaceFirst("/$",""));}
    private static int port(URI value){return value.getPort()<0?443:value.getPort();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Explicit debit owner origin and machine registration required");}
    private static IllegalStateException unavailable(){return new IllegalStateException("DEBIT_CARD_REFERENCE_OWNER_UNAVAILABLE");}
}

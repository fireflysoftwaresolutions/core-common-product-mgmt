/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.core.services.definition.DefinitionRules;
import io.fireflysoftware.core.product.core.services.definition.DebitCardTenantBindingResolver;

import io.fireflysoftware.core.product.interfaces.dtos.definition.DebitCardPurposeContracts.Scope;
import java.time.*;
import java.util.*;
import java.util.function.LongSupplier;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import reactor.core.publisher.Mono;

/** Current purpose-specific shared SPI binding. Inbound signature validation remains the owner's job. */
public final class DebitCardPurposeAuthorizationImpl {
    private static final String ACTION="product.definition.debit-card.read";
    private static final String REQUIRED_SCOPE="product:definitions:debit-cards:read";
    private final SecurityContextPort security;
    private final PolicyDecisionPort policies;
    private final String issuer,audience,tenantClaim,clientClaim,clientId;
    private final Clock clock;
    private final LongSupplier nanos;
    private final DebitCardTenantBindingResolver bindings;

    public DebitCardPurposeAuthorizationImpl(SecurityContextPort security,PolicyDecisionPort policies,
            String issuer,String audience,String tenantClaim,String clientClaim,String clientId,Clock clock,LongSupplier nanos) {
        this(security,policies,issuer,audience,tenantClaim,clientClaim,clientId,clock,nanos,null);
    }
    public DebitCardPurposeAuthorizationImpl(SecurityContextPort security,PolicyDecisionPort policies,
            String issuer,String audience,String tenantClaim,String clientClaim,String clientId,Clock clock,LongSupplier nanos,
            DebitCardTenantBindingResolver bindings) {
        this.bindings=bindings;
        this.security=Objects.requireNonNull(security);this.policies=Objects.requireNonNull(policies);
        text(issuer,512);text(audience,255);text(clientId,255);
        for(String key:new String[]{tenantClaim,clientClaim})if(key==null || !key.matches("[A-Za-z][A-Za-z0-9_:.]{0,63}")
                || Set.of("iss","sub","exp","nbf","aud").contains(key))throw unavailable();
        if(tenantClaim.equals(clientClaim))throw unavailable();
        this.issuer=issuer;this.audience=audience;this.tenantClaim=tenantClaim;this.clientClaim=clientClaim;this.clientId=clientId;
        this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
    }
    public final class Permit {
        private final DebitCardPurposeAuthorizationImpl owner=DebitCardPurposeAuthorizationImpl.this;
        private final SecurityPrincipal principal;
        private final Scope scope;
        private final Instant actorUntil,until,observedAt;
        private final long beforeNanos,budgetNanos;
        private final DebitCardTenantBindingResolver.Observation binding;
        private Permit(SecurityPrincipal principal,Scope scope,Instant actorUntil,Instant observedAt,long beforeNanos) {
            this(principal,scope,actorUntil,observedAt,beforeNanos,null);
        }
        private Permit(SecurityPrincipal principal,Scope scope,Instant actorUntil,Instant observedAt,long beforeNanos,DebitCardTenantBindingResolver.Observation binding) {
            this.principal=principal;this.scope=scope;this.actorUntil=actorUntil;this.observedAt=observedAt;this.beforeNanos=beforeNanos;this.binding=binding;
            var cap=actorUntil.isBefore(observedAt.plusSeconds(5))?actorUntil:observedAt.plusSeconds(5);
            until=binding==null||cap.isBefore(binding.validUntil())?cap:binding.validUntil();
            budgetNanos=Duration.between(observedAt,until).toNanos();remaining();
        }
        public Scope scope(){return scope;}
        public String issuer(){return principal.issuer();}
        public String subject(){return principal.subject();}
        public Instant actorUntil(){return actorUntil;}
        public Instant until(){return until;}
        public Instant observedAt(){return observedAt;}
        public long beforeNanos(){return beforeNanos;}
        public Duration remaining() {
            Instant now=clock.instant();long elapsed=nanos.getAsLong()-beforeNanos;
            if(!now.isBefore(until) || elapsed<0 || elapsed>=budgetNanos)throw unavailable();
            var absolute=Duration.between(now,until);var monotonic=Duration.ofNanos(budgetNanos-elapsed);
            return absolute.compareTo(monotonic)<0?absolute:monotonic;
        }
        @Override public String toString(){return "DebitCardPurposePermit";}
    }
    public Mono<Permit> authorize(Scope scope) {
        return Mono.defer(()->{
            Objects.requireNonNull(scope);Instant observed=clock.instant();long start=nanos.getAsLong();
            return security.currentPrincipal().switchIfEmpty(Mono.error(unavailable())).flatMap(principal->{
                Instant expiry=validate(principal,scope,observed);
                var initial=new Permit(principal,scope,expiry,observed,start);
                Mono<Permit> selected=bindings==null?Mono.just(initial):observeBinding(initial).map(binding->new Permit(principal,scope,expiry,observed,start,binding));
                return selected.flatMap(permit->requirePrincipal(permit).then(decide(principal,scope,"READ")).then(Mono.defer(security::currentPrincipal).switchIfEmpty(Mono.error(unavailable())))
                    .map(current->{if(!principal.equals(current)||!expiry.equals(validate(current,scope,clock.instant())))throw unavailable();permit.remaining();return permit;}).timeout(permit.remaining()));
            });
        }).timeout(Duration.ofSeconds(2)).onErrorMap(error->unavailable());
    }
    public Mono<Void> reauthorize(Permit permit) {
        return Mono.defer(()->{
            if(permit==null || permit.owner!=this)throw unavailable();permit.remaining();
            return security.currentPrincipal().switchIfEmpty(Mono.error(unavailable())).flatMap(principal->{
                if(!permit.principal.equals(principal))throw unavailable();validate(principal,permit.scope,clock.instant());
                Mono<DebitCardTenantBindingResolver.Observation> currentBinding=bindings==null?Mono.empty():observeBinding(permit).doOnNext(current->{if(!permit.binding.sameAuthority(current))throw unavailable();});
                return currentBinding.map(Optional::of).defaultIfEmpty(Optional.empty()).flatMap(observation->requirePrincipal(permit).then(decide(principal,permit.scope,"RESPONSE")).then(Mono.defer(security::currentPrincipal).switchIfEmpty(Mono.error(unavailable())))
                    .flatMap(current->{if(!permit.principal.equals(current))throw unavailable();validate(current,permit.scope,clock.instant());permit.remaining();observation.ifPresent(value->{if(!clock.instant().isBefore(value.validUntil()))throw unavailable();});return Mono.<Void>empty();}));
            }).timeout(permit.remaining());
        }).onErrorMap(error->unavailable());
    }
    private Instant validate(SecurityPrincipal principal,Scope scope,Instant now) {
        text(principal.issuer(),512);text(principal.subject(),255);
        var tenant=principal.claim(tenantClaim,String.class);
        text(tenant,1024);text(principal.tenantId(),1024);
        if(!issuer.equals(principal.issuer()) || !principal.tenantId().equals(tenant)
                || bindings==null&&!scope.tenantId().toString().equals(tenant) || !principal.hasScope(REQUIRED_SCOPE))throw unavailable();
        var exp=principal.claim("exp",Instant.class);var nbf=principal.claim("nbf",Instant.class);var aud=principal.claim("aud",List.class);
        if(exp==null || nbf==null || !now.isBefore(exp) || now.isBefore(nbf) || !nbf.isBefore(exp)
                || aud==null || aud.size()!=1 || !audience.equals(aud.getFirst()) || !clientId.equals(principal.claim(clientClaim,String.class)))throw unavailable();
        return exp;
    }
    private Mono<Void> requirePrincipal(Permit permit) {
        return Mono.defer(security::currentPrincipal).switchIfEmpty(Mono.error(unavailable())).flatMap(current->{
            if(!permit.principal.equals(current))throw unavailable();validate(current,permit.scope,clock.instant());permit.remaining();return Mono.<Void>empty();
        });
    }
    private Mono<DebitCardTenantBindingResolver.Observation> observeBinding(Permit permit) {
        return Mono.defer(()->{permit.remaining();return bindings.resolve(permit.principal,permit.scope.tenantId(),permit.until(),permit::remaining);})
            .switchIfEmpty(Mono.error(unavailable())).map(binding->{
                permit.remaining();var now=clock.instant();
                if(!permit.principal.issuer().equals(binding.issuer())||!permit.principal.tenantId().equals(binding.platformTenant())
                        ||!permit.scope.tenantId().equals(binding.resourceTenant())||binding.observedAt().isBefore(permit.observedAt())
                        ||binding.observedAt().isAfter(now)||!now.isBefore(binding.validUntil())||!now.isBefore(binding.businessUntil()))throw unavailable();
                return binding;
            }).timeout(permit.remaining());
    }
    private Mono<Void> decide(SecurityPrincipal principal,Scope scope,String phase) {
        String resource="tenant/"+scope.tenantId()+"/product/"+scope.productId()+"/definition/"+scope.rootId()+"/revision/"+scope.revisionId()+"/publication/"+scope.publicationId()+"/purpose/CARD_ENROLLMENT";
        Map<String,Object> context=Map.of("tenantId",scope.tenantId().toString(),"productId",scope.productId().toString(),"rootId",scope.rootId().toString(),"revisionId",scope.revisionId().toString(),"publicationId",scope.publicationId().toString(),"profileCode","DEBIT_CARD_V1","businessArea","BANKING","purpose","CARD_ENROLLMENT","phase",phase);
        return Mono.defer(()->policies.authorize(principal,ACTION,resource,context)).filter(d->d.effect()==Decision.Effect.PERMIT && d.obligations().isEmpty()).switchIfEmpty(Mono.error(unavailable())).then();
    }
    private static void text(String value,int max){DefinitionRules.text(value,max,"/purposeIdentity");}
    private static IllegalStateException unavailable(){return new IllegalStateException("DEBIT_CARD_PURPOSE_AUTHORITY_UNAVAILABLE");}
    @Override public String toString(){return "DebitCardPurposeAuthorizationImpl";}
}

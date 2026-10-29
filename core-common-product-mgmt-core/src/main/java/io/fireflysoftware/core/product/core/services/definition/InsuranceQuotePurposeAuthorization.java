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

package io.fireflysoftware.core.product.core.services.definition;

import io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.Scope;
import java.time.*;
import java.util.*;
import java.util.function.LongSupplier;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import reactor.core.publisher.Mono;

/** Current purpose-specific shared SPI binding. Inbound signature validation remains the owner's job. */
public final class InsuranceQuotePurposeAuthorization {
    private static final String ACTION="product.definition.insurance-quote.read";
    private static final String REQUIRED_SCOPE="product:definitions:insurance-quotes:read";
    private final SecurityContextPort security;
    private final PolicyDecisionPort policies;
    private final String issuer,audience,tenantClaim,clientClaim,clientId;
    private final Clock clock;
    private final LongSupplier nanos;

    public InsuranceQuotePurposeAuthorization(SecurityContextPort security,PolicyDecisionPort policies,
            String issuer,String audience,String tenantClaim,String clientClaim,String clientId,Clock clock,LongSupplier nanos) {
        this.security=Objects.requireNonNull(security);this.policies=Objects.requireNonNull(policies);
        text(issuer,512);text(audience,255);text(clientId,255);
        for(String key:new String[]{tenantClaim,clientClaim})if(key==null || !key.matches("[A-Za-z][A-Za-z0-9_:.]{0,63}")
                || Set.of("iss","sub","exp","nbf","aud").contains(key))throw unavailable();
        if(tenantClaim.equals(clientClaim))throw unavailable();
        this.issuer=issuer;this.audience=audience;this.tenantClaim=tenantClaim;this.clientClaim=clientClaim;this.clientId=clientId;
        this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
    }
    public final class Permit {
        private final InsuranceQuotePurposeAuthorization owner=InsuranceQuotePurposeAuthorization.this;
        private final SecurityPrincipal principal;
        private final Scope scope;
        private final Instant actorUntil,until,observedAt;
        private final long beforeNanos,budgetNanos;
        private Permit(SecurityPrincipal principal,Scope scope,Instant actorUntil,Instant observedAt,long beforeNanos) {
            this.principal=principal;this.scope=scope;this.actorUntil=actorUntil;this.observedAt=observedAt;this.beforeNanos=beforeNanos;
            until=actorUntil.isBefore(observedAt.plusSeconds(5))?actorUntil:observedAt.plusSeconds(5);
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
        @Override public String toString(){return "InsuranceQuotePurposePermit";}
    }
    public Mono<Permit> authorize(Scope scope) {
        return Mono.defer(()->{
            Objects.requireNonNull(scope);Instant observed=clock.instant();long start=nanos.getAsLong();
            return security.currentPrincipal().switchIfEmpty(Mono.error(unavailable())).flatMap(principal->{
                Instant expiry=validate(principal,scope,observed);
                return decide(principal,scope,"READ").then(Mono.defer(security::currentPrincipal).switchIfEmpty(Mono.error(unavailable())))
                    .map(current->{if(!principal.equals(current) || !expiry.equals(validate(current,scope,clock.instant())))throw unavailable();return new Permit(principal,scope,expiry,observed,start);});
            });
        }).timeout(Duration.ofSeconds(2)).onErrorMap(error->unavailable());
    }
    public Mono<Void> reauthorize(Permit permit) {
        return Mono.defer(()->{
            if(permit==null || permit.owner!=this)throw unavailable();permit.remaining();
            return security.currentPrincipal().switchIfEmpty(Mono.error(unavailable())).flatMap(principal->{
                if(!permit.principal.equals(principal))throw unavailable();validate(principal,permit.scope,clock.instant());
                return decide(principal,permit.scope,"RESPONSE").then(Mono.defer(security::currentPrincipal).switchIfEmpty(Mono.error(unavailable())))
                    .flatMap(current->{if(!permit.principal.equals(current))throw unavailable();validate(current,permit.scope,clock.instant());permit.remaining();return Mono.<Void>empty();});
            }).timeout(permit.remaining());
        }).onErrorMap(error->unavailable());
    }
    private Instant validate(SecurityPrincipal principal,Scope scope,Instant now) {
        text(principal.issuer(),512);text(principal.subject(),255);
        var tenant=principal.claim(tenantClaim,String.class);
        if(!issuer.equals(principal.issuer()) || !scope.tenantId().toString().equals(principal.tenantId())
                || !scope.tenantId().toString().equals(tenant) || !principal.hasScope(REQUIRED_SCOPE))throw unavailable();
        var exp=principal.claim("exp",Instant.class);var nbf=principal.claim("nbf",Instant.class);var aud=principal.claim("aud",List.class);
        if(exp==null || nbf==null || !now.isBefore(exp) || now.isBefore(nbf) || !nbf.isBefore(exp)
                || aud==null || aud.size()!=1 || !audience.equals(aud.getFirst()) || !clientId.equals(principal.claim(clientClaim,String.class)))throw unavailable();
        return exp;
    }
    private Mono<Void> decide(SecurityPrincipal principal,Scope scope,String phase) {
        String resource="tenant/"+scope.tenantId()+"/product/"+scope.productId()+"/definition/"+scope.rootId()+"/revision/"+scope.revisionId()+"/publication/"+scope.publicationId()+"/purpose/TERM_PC_INSURANCE_QUOTE";
        Map<String,Object> context=Map.of("tenantId",scope.tenantId().toString(),"productId",scope.productId().toString(),"rootId",scope.rootId().toString(),"revisionId",scope.revisionId().toString(),"publicationId",scope.publicationId().toString(),"profileCode","TERM_PC_INSURANCE_V1","businessArea","INSURANCE","purpose","TERM_PC_INSURANCE_QUOTE","phase",phase);
        return Mono.defer(()->policies.authorize(principal,ACTION,resource,context)).filter(d->d.effect()==Decision.Effect.PERMIT && d.obligations().isEmpty()).switchIfEmpty(Mono.error(unavailable())).then();
    }
    private static void text(String value,int max){DefinitionRules.text(value,max,"/purposeIdentity");}
    private static IllegalStateException unavailable(){return new IllegalStateException("INSURANCE_QUOTE_PURPOSE_AUTHORITY_UNAVAILABLE");}
    @Override public String toString(){return "InsuranceQuotePurposeAuthorization";}
}

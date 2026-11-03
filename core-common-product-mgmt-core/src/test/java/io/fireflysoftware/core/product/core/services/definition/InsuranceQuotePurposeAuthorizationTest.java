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

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.Scope;
import java.lang.reflect.InvocationTargetException;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.LongSupplier;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Controlled shared API facts; no signature, HTTP, live PDP or owner-record acceptance. */
class InsuranceQuotePurposeAuthorizationTest {
    private static final Instant NOW=Instant.parse("2026-10-26T12:00:00Z");
    private final AtomicReference<Instant> now=new AtomicReference<>(NOW);
    private final AtomicLong nanos=new AtomicLong();
    private final Scope scope=new Scope(new UUID(0,1),new UUID(0,2),new UUID(0,3),new UUID(0,4),new UUID(0,5));
    private static final String REQUIRED="product:definitions:insurance-quotes:read";
    private final Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now.get();}};
    private SecurityPrincipal principal(String subject,Map<String,Object> changed) {
        var claims=new HashMap<String,Object>(Map.of("tenant_id",scope.tenantId().toString(),"exp",NOW.plusSeconds(60),"nbf",NOW.minusSeconds(1),"aud",List.of("product-insurance-purpose"),"azp","underwriting-client"));claims.putAll(changed);
        return new SecurityPrincipal(subject,"https://issuer.example",scope.tenantId().toString(),Set.of(),Set.of(REQUIRED),claims,NOW,null,Set.of(),Map.of("privateFixture","do-not-emit"));
    }
    private Object authority(SecurityContextPort context,PolicyDecisionPort pdp) {
        try {return Class.forName("io.fireflysoftware.core.product.core.services.definition.InsuranceQuotePurposeAuthorization").getConstructor(SecurityContextPort.class,PolicyDecisionPort.class,String.class,String.class,String.class,String.class,String.class,Clock.class,LongSupplier.class).newInstance(context,pdp,"https://issuer.example","product-insurance-purpose","tenant_id","azp","underwriting-client",clock,(LongSupplier)nanos::get);}
        catch(ClassNotFoundException missing){throw new AssertionError("Current insurance-purpose authority adapter missing",missing);}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static Object invoke(Object g,String method,Class<?> type,Object value) {
        try{return g.getClass().getMethod(method,type).invoke(g,value);}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof RuntimeException e)throw e;throw new AssertionError(failure.getCause());}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    @SuppressWarnings("unchecked") private Mono<Object> authorize(Object g){return (Mono<Object>)invoke(g,"authorize",Scope.class,scope);}
    @SuppressWarnings("unchecked") private Mono<Void> post(Object g,Object p){return (Mono<Void>)invoke(g,"reauthorize",p.getClass(),p);}
    private Object property(Object p,String name){try{return p.getClass().getMethod(name).invoke(p);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    @Test void actualSpiRequiresExactCurrentPurposeAndFullGraphWithoutRawClaims() {
        var calls=new AtomicInteger();var g=authority(()->Mono.just(principal("actor",Map.of())),(p,a,r,c)->{
            calls.incrementAndGet();assertEquals("product.definition.insurance-quote.read",a);assertTrue(r.contains(scope.publicationId().toString()));assertEquals(scope.tenantId().toString(),c.get("tenantId"));assertEquals(scope.revisionId().toString(),c.get("revisionId"));assertEquals("TERM_PC_INSURANCE_V1",c.get("profileCode"));assertFalse(c.containsKey("claims"));assertFalse(c.containsKey("token"));return Mono.just(Decision.permit());});
        var permit=authorize(g).block();assertEquals(NOW.plusSeconds(5),property(permit,"until"));assertEquals(scope,property(permit,"scope"));assertEquals("InsuranceQuotePurposePermit",permit.toString());assertEquals(1,calls.get());
    }
    @Test void absentPrincipalOrDecisionFailsClosed(){assertThrows(IllegalStateException.class,()->authorize(authority(Mono::empty,(p,a,r,c)->Mono.just(Decision.permit()))).block());assertThrows(IllegalStateException.class,()->authorize(authority(()->Mono.just(principal("actor",Map.of())),(p,a,r,c)->Mono.empty())).block());}
    @Test void wrongTenantAudienceClientAndTypedTimeRefuseBeforePdp() {
        var calls=new AtomicInteger();for(var changed:List.of(Map.<String,Object>of("tenant_id",new UUID(0,9).toString()),Map.<String,Object>of("aud",List.of("other-owner")),Map.<String,Object>of("azp","other-client"),Map.<String,Object>of("exp",NOW.getEpochSecond()),Map.<String,Object>of("nbf",NOW.plusSeconds(1)))){
            var g=authority(()->Mono.just(principal("actor",changed)),(p,a,r,c)->{calls.incrementAndGet();return Mono.just(Decision.permit());});assertThrows(IllegalStateException.class,()->authorize(g).block());}assertEquals(0,calls.get());
    }
    @Test void missingScopeRefusesBeforePdp(){var p=principal("actor",Map.of());var wrong=new SecurityPrincipal(p.subject(),p.issuer(),p.tenantId(),p.authorities(),Set.of(),p.claims(),p.authTime(),p.acr(),p.amr(),p.attributes());var calls=new AtomicInteger();assertThrows(IllegalStateException.class,()->authorize(authority(()->Mono.just(wrong),(x,a,r,c)->{calls.incrementAndGet();return Mono.just(Decision.permit());})).block());assertEquals(0,calls.get());}
    @Test void denyIndeterminateAndUnsupportedObligationNeverProducePermit(){for(var d:List.of(Decision.deny("normal source denial"),Decision.indeterminate("normal source unavailability"),Decision.permit(List.of(Obligation.of("unhandled",Map.of())))))assertThrows(IllegalStateException.class,()->authorize(authority(()->Mono.just(principal("actor",Map.of())),(p,a,r,c)->Mono.just(d))).block());}
    @Test void principalReacquiredAfterPdpMustBeTheSame(){var p=new AtomicReference<>(principal("actor",Map.of()));var g=authority(()->Mono.just(p.get()),(x,a,r,c)->{p.set(principal("changed",Map.of()));return Mono.just(Decision.permit());});assertThrows(IllegalStateException.class,()->authorize(g).block());}
    @Test void currentPdpRunsAgainAndCannotRenewOriginalCap(){var calls=new AtomicInteger();var g=authority(()->Mono.just(principal("actor",Map.of())),(p,a,r,c)->{calls.incrementAndGet();return Mono.just(Decision.permit());});var permit=authorize(g).block();nanos.set(4_000_000_000L);post(g,permit).block();assertEquals(2,calls.get());assertEquals(NOW.plusSeconds(5),property(permit,"until"));nanos.set(5_000_000_000L);assertThrows(IllegalStateException.class,()->post(g,permit).block());}
    @Test void currentDenialAfterReadWithholdsResponse(){var current=new AtomicReference<>(Decision.permit());var g=authority(()->Mono.just(principal("actor",Map.of())),(p,a,r,c)->Mono.just(current.get()));var permit=authorize(g).block();current.set(Decision.deny("ordinary current revocation"));assertThrows(IllegalStateException.class,()->post(g,permit).block());}
    @Test void originalAbsoluteDeadlineAlsoRemainsRequired(){var g=authority(()->Mono.just(principal("actor",Map.of())),(p,a,r,c)->Mono.just(Decision.permit()));var permit=authorize(g).block();now.set(NOW.plusSeconds(5));assertThrows(IllegalStateException.class,()->post(g,permit).block());}
    @Test void monotonicClockCannotMoveBackwards(){var g=authority(()->Mono.just(principal("actor",Map.of())),(p,a,r,c)->Mono.just(Decision.permit()));var permit=authorize(g).block();nanos.set(-1);assertThrows(IllegalStateException.class,()->post(g,permit).block());}
    @Test void anotherAdapterCannotUseOriginalPermit(){var g=authority(()->Mono.just(principal("actor",Map.of())),(p,a,r,c)->Mono.just(Decision.permit()));var other=authority(()->Mono.just(principal("actor",Map.of())),(p,a,r,c)->Mono.just(Decision.permit()));var permit=authorize(g).block();assertThrows(IllegalStateException.class,()->post(other,permit).block());}
    @Test void publisherStallHasFiniteNoGrantOutcome(){var g=authority(()->Mono.just(principal("actor",Map.of())),(p,a,r,c)->Mono.never());StepVerifier.withVirtualTime(()->authorize(g)).thenAwait(Duration.ofSeconds(3)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));}
}

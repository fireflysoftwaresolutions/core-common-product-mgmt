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
import io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.TermPcInsuranceTerms;
import io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeReadStore;
import io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeReadStore.*;
import io.fireflysoftware.core.product.core.services.definition.InsuranceQuotePurposeReferenceResolver.VerifiedReferences;
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

/** Controlled persistence/reference API facts, never native, signature or real owner-record evidence. */
class InsuranceQuotePurposeServiceTest {
    private static final Instant NOW=Instant.parse("2026-10-26T12:00:00Z");
    private final AtomicReference<Instant> now=new AtomicReference<>(NOW);
    private final AtomicLong nanos=new AtomicLong();
    private final Scope scope=new Scope(new UUID(0,1),new UUID(0,2),new UUID(0,3),new UUID(0,4),new UUID(0,5));
    private final Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now.get();}};
    private Configuration configuration(){var fixture=ProfileFixtures.load("insurance");((com.fasterxml.jackson.databind.node.ObjectNode)fixture.get("common")).put("effectiveFrom","2026-10-01T00:00:00.000Z");var raw=fixture.toString();return new Configuration(scope,DefinitionProfiles.canonical(raw).digest(),DefinitionProfiles.decode(raw));}
    private SecurityPrincipal principal(String subject){return new SecurityPrincipal(subject,"https://issuer.example",scope.tenantId().toString(),Set.of(),Set.of("product:definitions:insurance-quotes:read"),Map.of("tenant_id",scope.tenantId().toString(),"exp",NOW.plusSeconds(60),"nbf",NOW.minusSeconds(1),"aud",List.of("product-insurance-purpose"),"azp","underwriting-client"),NOW,null,Set.of(),Map.of());}
    private InsuranceQuotePurposeAuthorization auth(SecurityContextPort c,PolicyDecisionPort p){return new InsuranceQuotePurposeAuthorization(c,p,"https://issuer.example","product-insurance-purpose","tenant_id","azp","underwriting-client",clock,nanos::get);}
    private InsuranceQuotePurposeAuthorization auth(){return auth(()->Mono.just(principal("actor")),(p,a,r,c)->Mono.just(Decision.permit()));}
    private Read read(){return new Read(configuration(),NativeWindow.from(NOW.plusNanos(123000),NOW.toEpochMilli(),NOW.toEpochMilli()+5000,nanos.get()));}
    private Set<ExactReference> references(Configuration c){var set=new HashSet<ExactReference>();var t=(TermPcInsuranceTerms)c.definition().terms();t.taxComponents().forEach(v->set.add(v.policy()));t.coverages().forEach(v->set.add(v.wording()));set.addAll(t.exclusions());for(var d:c.definition().common().requirements().documents()){if(d.template()!=null)set.add(d.template());if(d.issuer()!=null)set.add(d.issuer());if(d.layout()!=null)set.add(d.layout());}return Set.copyOf(set);}
    private VerifiedReferences verified(Configuration c){return new VerifiedReferences(scope,InsuranceQuotePurposeCodec.digest(c),c.definition().common().market().currencyPolicy(),references(c),NOW,NOW.plusSeconds(4));}
    private InsuranceQuotePurposeService service(InsuranceQuotePurposeAuthorization a,InsuranceQuotePurposeReadStore store,InsuranceQuotePurposeReferenceResolver refs){
        try {return (InsuranceQuotePurposeService)Class.forName("io.fireflysoftware.core.product.core.services.definition.impl.InsuranceQuotePurposeServiceImpl").getConstructor(InsuranceQuotePurposeAuthorization.class,InsuranceQuotePurposeReadStore.class,InsuranceQuotePurposeReferenceResolver.class,Clock.class,LongSupplier.class).newInstance(a,store,refs,clock,(LongSupplier)nanos::get);}
        catch(ClassNotFoundException missing){throw new AssertionError("Functional insurance-purpose service missing",missing);}
        catch(InvocationTargetException e){throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    @Test void actualAuthorityTwoCompletedReadsAndMandatoryReferencesProduceOnlyConfiguration(){var reads=new AtomicInteger();var resolves=new AtomicInteger();var original=new AtomicReference<Admission>();var s=service(auth(),a->{reads.incrementAndGet();if(original.get()==null)original.set(a);else {var first=original.get();assertEquals(first.scope(),a.scope());assertEquals(first.issuer(),a.issuer());assertEquals(first.actor(),a.actor());assertEquals(first.actorUntil(),a.actorUntil());assertEquals(first.permitUntil(),a.permitUntil());assertEquals(first.observedAt(),a.observedAt());assertEquals(first.beforeNanos(),a.beforeNanos());assertNotNull(a.priorWindow());assertEquals(NOW.plusSeconds(4),a.priorWindow().until());}return Mono.just(read());},c->{resolves.incrementAndGet();return Mono.just(verified(c));});var o=s.observe(scope).block();assertNotNull(o);assertEquals(scope,o.configuration().scope());assertEquals(NOW.plusNanos(123000),o.observedAt());assertEquals(NOW.plusSeconds(4),o.effectiveUntil());assertEquals(2,reads.get());assertEquals(1,resolves.get());}
    @Test void emptyReferenceResolutionDoesNotBecomeSuccess(){var reads=new AtomicInteger();var s=service(auth(),a->{reads.incrementAndGet();return Mono.just(read());},c->Mono.empty());assertThrows(IllegalStateException.class,()->s.observe(scope).block());assertEquals(1,reads.get());}
    @Test void omittedConfiguredReferenceRefuses(){var s=service(auth(),a->Mono.just(read()),c->Mono.just(new VerifiedReferences(scope,InsuranceQuotePurposeCodec.digest(c),c.definition().common().market().currencyPolicy(),Set.of(),NOW,NOW.plusSeconds(4))));assertThrows(IllegalStateException.class,()->s.observe(scope).block());}
    @Test void wrongResolvedCurrencyRefuses(){var s=service(auth(),a->Mono.just(read()),c->{var old=c.definition().common().market().currencyPolicy();var wrong=new CurrencyPolicyReference(old.policyId(),old.currencyId(),"EUR",old.version(),old.digest(),old.encodingVersion());return Mono.just(new VerifiedReferences(scope,InsuranceQuotePurposeCodec.digest(c),wrong,references(c),NOW,NOW.plusSeconds(4)));});assertThrows(IllegalStateException.class,()->s.observe(scope).block());}
    @Test void wrongResolvedConfigurationDigestRefuses(){var s=service(auth(),a->Mono.just(read()),c->Mono.just(new VerifiedReferences(scope,"0".repeat(64),c.definition().common().market().currencyPolicy(),references(c),NOW,NOW.plusSeconds(4))));assertThrows(IllegalStateException.class,()->s.observe(scope).block());}
    @Test void currentPublicationReadAfterRemoteIoCannotDisappear(){var reads=new AtomicInteger();var s=service(auth(),a->reads.incrementAndGet()==1?Mono.just(read()):Mono.empty(),c->Mono.just(verified(c)));assertThrows(IllegalStateException.class,()->s.observe(scope).block());assertEquals(2,reads.get());}
    @Test void changedSecondPublicationCannotReplaceRequestedConfiguration(){var reads=new AtomicInteger();var s=service(auth(),a->{if(reads.incrementAndGet()==1)return Mono.just(read());var c=configuration();var other=new Scope(scope.tenantId(),scope.rootId(),scope.productId(),scope.revisionId(),new UUID(0,99));return Mono.just(new Read(new Configuration(other,c.publicDigest(),c.definition()),read().window()));},c->Mono.just(verified(c)));assertThrows(IllegalStateException.class,()->s.observe(scope).block());}
    @Test void currentPdpDenialAfterReadsWithholdsResult(){var deny=new AtomicBoolean();var reads=new AtomicInteger();var authority=auth(()->Mono.just(principal("actor")),(p,a,r,c)->Mono.just(deny.get()?Decision.deny("normal current denial"):Decision.permit()));var s=service(authority,a->{reads.incrementAndGet();return Mono.just(read());},c->{deny.set(true);return Mono.just(verified(c));});assertThrows(IllegalStateException.class,()->s.observe(scope).block());assertEquals(2,reads.get());}
    @Test void principalChangeAcrossRemoteIoRefuses(){var actor=new AtomicReference<>(principal("actor"));var s=service(auth(()->Mono.just(actor.get()),(p,a,r,c)->Mono.just(Decision.permit())),a->Mono.just(read()),c->{actor.set(principal("another"));return Mono.just(verified(c));});assertThrows(IllegalStateException.class,()->s.observe(scope).block());}
    @Test void originalNativeCapSurvivesReferenceIoAndLaterRead(){var s=service(auth(),a->Mono.just(read()),c->{nanos.set(4_999_000_000L);return Mono.just(verified(c));});assertThrows(IllegalStateException.class,()->s.observe(scope).block());}
    @Test void finalPdpCannotConsumeNativeBudgetAndStillEmit(){var calls=new AtomicInteger();var authority=auth(()->Mono.just(principal("actor")),(p,a,r,c)->{if(calls.incrementAndGet()==2)nanos.set(5_000_000_000L);return Mono.just(Decision.permit());});var s=service(authority,a->Mono.just(read()),c->Mono.just(verified(c)));assertThrows(IllegalStateException.class,()->s.observe(scope).block());}
    @Test void stalledReferenceIoIsFiniteAndNoFallback(){var s=service(auth(),a->Mono.just(read()),c->Mono.never());StepVerifier.withVirtualTime(()->s.observe(scope)).thenAwait(Duration.ofSeconds(6)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));}
    @Test void stalledInitialBackendReadIsFinite(){var s=service(auth(),a->Mono.never(),c->Mono.just(verified(c)));StepVerifier.withVirtualTime(()->s.observe(scope)).thenAwait(Duration.ofSeconds(6)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));}

    private Configuration endingSoon(){var fixture=ProfileFixtures.load("insurance");var c=(com.fasterxml.jackson.databind.node.ObjectNode)fixture.get("common");c.put("effectiveFrom","2026-10-01T00:00:00.000Z");c.put("effectiveTo","2026-10-26T12:00:02.000Z");String raw=fixture.toString();return new Configuration(scope,DefinitionProfiles.canonical(raw).digest(),DefinitionProfiles.decode(raw));}
    @Test void definitionExpiryNarrowsTheAdvertisedWindow(){var c=endingSoon();var s=service(auth(),a->Mono.just(new Read(c,read().window())),x->Mono.just(verified(x)));var o=s.observe(scope).block();assertEquals(NOW.plusSeconds(2),o.effectiveUntil());}
    @Test void definitionExpiryAlsoBoundsRemotePublisher(){var c=endingSoon();var s=service(auth(),a->Mono.just(new Read(c,read().window())),x->Mono.never());StepVerifier.withVirtualTime(()->s.observe(scope)).thenAwait(Duration.ofSeconds(3)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(1));}
    @Test void futureDefinitionNeverBecomesCurrentPurpose(){var raw=ProfileFixtures.load("insurance").toString();var c=new Configuration(scope,DefinitionProfiles.canonical(raw).digest(),DefinitionProfiles.decode(raw));var calls=new AtomicInteger();var s=service(auth(),a->Mono.just(new Read(c,read().window())),x->{calls.incrementAndGet();return Mono.just(verified(x));});assertThrows(IllegalStateException.class,()->s.observe(scope).block());assertEquals(0,calls.get());}
}

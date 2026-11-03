/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.product.core.services.definition;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.product.core.services.definition.impl.DebitCardPurposeAuthorizationImpl;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DebitCardPurposeContracts.Scope;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.security.api.domain.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/** Controlled current-owner observations; this does not establish SDK or live Main acceptance. */
class DebitCardCurrentTenantBindingAuthorizationTest {
    private static final Instant NOW=Instant.parse("2026-11-01T12:00:00Z");
    private final AtomicReference<Instant> now=new AtomicReference<>(NOW);
    private final AtomicLong ticks=new AtomicLong();
    private final Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now.get();}};
    private final Scope scope=new Scope(new UUID(0,1),new UUID(0,2),new UUID(0,3),new UUID(0,4),new UUID(0,5));
    private final AtomicReference<SecurityPrincipal> caller=new AtomicReference<>(principal("issuer-namespace:bank-east","cards-client",Set.of("product:definitions:debit-cards:read")));
    private SecurityPrincipal principal(String tenant,String client,Set<String> scopes){return new SecurityPrincipal("card-reader","https://issuer.example",tenant,Set.of(),scopes,Map.of("tenant_id",tenant,"azp",client,"aud",List.of("product-debit-purpose"),"exp",NOW.plusSeconds(60),"nbf",NOW.minusSeconds(1)),NOW,null,Set.of(),Map.of());}
    private DebitCardTenantBindingResolver.Observation binding(UUID tenant,long control,Instant until){return new DebitCardTenantBindingResolver.Observation("https://issuer.example",caller.get().tenantId(),tenant,new UUID(0,6),1,"a".repeat(64),control,0,new UUID(0,7),0,NOW,until,NOW.plusSeconds(60));}
    private DebitCardPurposeAuthorizationImpl authority(DebitCardTenantBindingResolver resolver,AtomicInteger policies){return new DebitCardPurposeAuthorizationImpl(()->Mono.just(caller.get()),(p,a,r,c)->{assertSame(caller.get(),p);assertEquals(scope.tenantId().toString(),c.get("tenantId"));assertEquals("issuer-namespace:bank-east",p.tenantId());policies.incrementAndGet();return Mono.just(Decision.permit());},"https://issuer.example","product-debit-purpose","tenant_id","azp","cards-client",clock,ticks::get,resolver);}
    @Test void opaqueNamespaceUsesCurrentResourceWithoutRewritingPrincipal(){var reads=new AtomicInteger();var policies=new AtomicInteger();var original=caller.get();var a=authority((p,tenant,until,left)->{assertSame(original,p);assertEquals(scope.tenantId(),tenant);assertEquals(reads.get()==0?NOW.plusSeconds(5):NOW.plusSeconds(4),until);left.get();reads.incrementAndGet();return Mono.just(binding(tenant,1,NOW.plusSeconds(4)));},policies);var permit=a.authorize(scope).block();assertEquals(NOW.plusSeconds(4),permit.until());ticks.set(1_000_000_000L);a.reauthorize(permit).block();assertEquals(original,caller.get());assertEquals(2,reads.get());assertEquals(2,policies.get());assertEquals(NOW.plusSeconds(4),permit.until());}
    @Test void absentCurrentBindingRefusesBeforeProductPolicy(){var policies=new AtomicInteger();var a=authority((p,t,u,r)->Mono.empty(),policies);assertThrows(IllegalStateException.class,()->a.authorize(scope).block());assertEquals(0,policies.get());}
    @Test void currentOwnerFailureCannotFallBackToUuidTenant(){caller.set(principal(scope.tenantId().toString(),"cards-client",Set.of("product:definitions:debit-cards:read")));var policies=new AtomicInteger();var a=authority((p,t,u,r)->Mono.error(new IllegalStateException("Owner unavailable")),policies);assertThrows(IllegalStateException.class,()->a.authorize(scope).block());assertEquals(0,policies.get());}
    @Test void wrongResourceTenantRefusesBeforeProductPolicy(){var policies=new AtomicInteger();var a=authority((p,t,u,r)->Mono.just(binding(new UUID(0,99),1,NOW.plusSeconds(4))),policies);assertThrows(IllegalStateException.class,()->a.authorize(scope).block());assertEquals(0,policies.get());}
    @Test void revokedOrReplacedCurrentBindingWithholdsResponse(){var control=new AtomicLong(1);var a=authority((p,t,u,r)->Mono.just(binding(t,control.get(),NOW.plusSeconds(4))),new AtomicInteger());var permit=a.authorize(scope).block();control.incrementAndGet();assertThrows(IllegalStateException.class,()->a.reauthorize(permit).block());}
    @Test void exhaustedCurrentWindowCannotRenewOriginalPermit(){var a=authority((p,t,u,r)->Mono.just(binding(t,1,NOW.plusMillis(100))),new AtomicInteger());var permit=a.authorize(scope).block();ticks.set(100_000_000L);assertThrows(IllegalStateException.class,()->a.reauthorize(permit).block());}
    @Test void bindingDoesNotGrantMissingProductScopeOrClient(){for(var actor:List.of(principal("issuer-namespace:bank-east","other-client",Set.of("product:definitions:debit-cards:read")),principal("issuer-namespace:bank-east","cards-client",Set.of()))){caller.set(actor);var reads=new AtomicInteger();var a=authority((p,t,u,r)->{reads.incrementAndGet();return Mono.just(binding(t,1,NOW.plusSeconds(4)));},new AtomicInteger());assertThrows(IllegalStateException.class,()->a.authorize(scope).block());assertEquals(0,reads.get());}}
    @Test void legacyConstructorDoesNotAcceptOpaqueNamespace(){var a=new DebitCardPurposeAuthorizationImpl(()->Mono.just(caller.get()),(p,a1,r,c)->Mono.just(Decision.permit()),"https://issuer.example","product-debit-purpose","tenant_id","azp","cards-client",clock,ticks::get);assertThrows(IllegalStateException.class,()->a.authorize(scope).block());}
    @Test void changedCallerAfterOwnerReadCannotAcquirePermit(){var original=caller.get();var a=authority((p,t,u,r)->{caller.set(original.toBuilder().subject("another-reader").build());return Mono.just(binding(t,1,NOW.plusSeconds(4)));},new AtomicInteger());assertThrows(IllegalStateException.class,()->a.authorize(scope).block());}
}

package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionCommandAuthorization;
import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionPublicationReadAuthorization;
import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionPublicationReadAuthorization.*;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.reactive.TransactionContextManager;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class ProductDefinitionPublicationReadAuthorizationTest {
    final UUID tenant = UUID.randomUUID(), root = UUID.randomUUID();
    final Instant now = Instant.parse("2026-10-25T00:00:00Z");
    final String issuer = "https://issuer.example.invalid";

    SecurityPrincipal maker(Set<String> scopes) {
        return SecurityPrincipal.builder()
                .issuer(issuer)
                .subject("independent-maker")
                .tenantId(tenant.toString())
                .scopes(scopes)
                .claims(Map.of("exp", now.plusSeconds(300)))
                .build();
    }

    ProductDefinitionPublicationReadAuthorization auth(
            SecurityPrincipal actor, PolicyDecisionPort policy) {
        return (ProductDefinitionPublicationReadAuthorization)
                (Object)
                        new ProductDefinitionCommandAuthorizationImpl(
                                () -> Mono.just(actor),
                                policy,
                                Clock.fixed(now, ZoneOffset.UTC),
                                Set.of(issuer),
                                ProductDefinitionCommandAuthorization.POLICY_PROFILE);
    }

    static void status(Mono<?> value, int expected) {
        assertThatThrownBy(value::block)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(expected));
    }

    @Test
    void independentMakerNeedsNoPrivateCostOrCheckerGrant() {
        var actor = maker(Set.of("product:definitions:publish", "product:definitions:withdraw"));
        var calls = new AtomicInteger();
        for (var target : Target.values()) {
            var auth =
                    auth(
                            actor,
                            (p, action, resource, context) -> {
                                assertThat(p).isSameAs(actor);
                                assertThat(action).isEqualTo(target.action());
                                assertThat(resource)
                                        .isEqualTo(
                                                "tenant/"
                                                        + tenant
                                                        + "/product-definition/"
                                                        + root
                                                        + "/publication/"
                                                        + target.resourceSuffix());
                                assertThat(context)
                                        .containsEntry(
                                                "authorityProfile",
                                                ProductDefinitionCommandAuthorization
                                                        .POLICY_PROFILE)
                                        .doesNotContainKeys(
                                                "actorKind",
                                                "principal_kind",
                                                "human",
                                                "operationId");
                                calls.incrementAndGet();
                                return Mono.just(Decision.permit());
                            });
            var admitted = auth.authorize(new Scope(tenant, root, target)).block();
            assertThat(admitted.principal()).isSameAs(actor);
            assertThat(admitted.validUntilEpochMillis())
                    .isEqualTo(now.plusSeconds(30).toEpochMilli());
            auth.reauthorize(admitted).block();
        }
        assertThat(calls).hasValue(4);
    }

    @Test
    void unrelatedOrOppositeCommandScopeFailsBeforePdp() {
        var calls = new AtomicInteger();
        for (var target : Target.values()) {
            var opposite =
                    target == Target.READINESS
                            ? "product:definitions:withdraw"
                            : "product:definitions:publish";
            var a =
                    auth(
                            maker(Set.of(opposite)),
                            (p, action, resource, context) -> {
                                calls.incrementAndGet();
                                return Mono.just(Decision.permit());
                            });
            status(a.authorize(new Scope(tenant, root, target)), 403);
        }
        assertThat(calls).hasValue(0);
    }

    @Test
    void currentPolicyMustStillPermitAfterCompletedRead() {
        var calls = new AtomicInteger();
        var a =
                auth(
                        maker(Set.of("product:definitions:publish")),
                        (p, action, resource, context) ->
                                Mono.just(
                                        calls.incrementAndGet() == 1
                                                ? Decision.permit()
                                                : Decision.deny("current rule")));
        var admitted = a.authorize(new Scope(tenant, root, Target.READINESS)).block();
        status(a.reauthorize(admitted), 403);
        assertThat(calls).hasValue(2);
    }

    @Test
    void changingTheCurrentPrincipalCannotReuseAnAdmission() {
        var actor = maker(Set.of("product:definitions:publish"));
        var current = new AtomicReference<>(actor);
        var impl =
                new ProductDefinitionCommandAuthorizationImpl(
                        () -> Mono.just(current.get()),
                        (p, action, resource, context) -> Mono.just(Decision.permit()),
                        Clock.fixed(now, ZoneOffset.UTC),
                        Set.of(issuer),
                        ProductDefinitionCommandAuthorization.POLICY_PROFILE);
        var a = (ProductDefinitionPublicationReadAuthorization) (Object) impl;
        var admitted = a.authorize(new Scope(tenant, root, Target.READINESS)).block();
        current.set(actor.toBuilder().subject("another-maker").build());
        status(a.reauthorize(admitted), 401);
    }

    @Test
    void ambientSqlIsRejectedBeforePrincipalOrPolicyIo() {
        var calls = new AtomicInteger();
        var impl =
                new ProductDefinitionCommandAuthorizationImpl(
                        () -> {
                            calls.incrementAndGet();
                            return Mono.just(maker(Set.of("product:definitions:publish")));
                        },
                        (p, action, resource, context) -> {
                            calls.incrementAndGet();
                            return Mono.just(Decision.permit());
                        },
                        Clock.fixed(now, ZoneOffset.UTC),
                        Set.of(issuer),
                        ProductDefinitionCommandAuthorization.POLICY_PROFILE);
        var a = (ProductDefinitionPublicationReadAuthorization) (Object) impl;
        var value =
                TransactionSynchronizationManager.forCurrentTransaction()
                        .flatMap(
                                manager -> {
                                    manager.setActualTransactionActive(true);
                                    return a.authorize(new Scope(tenant, root, Target.READINESS));
                                });
        status(value.contextWrite(TransactionContextManager.createTransactionContext()), 503);
        assertThat(calls).hasValue(0);
    }

    @Test
    void nullOrZeroReadScopeIsBadRequest() {
        var a =
                auth(
                        maker(Set.of("product:definitions:publish")),
                        (p, action, resource, context) -> Mono.just(Decision.permit()));
        status(a.authorize(null), 400);
        status(a.authorize(new Scope(tenant, new UUID(0, 0), Target.READINESS)), 400);
    }
}

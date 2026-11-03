package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionReadAuthorization;
import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionReadAuthorization.*;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.Obligation;
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

class ProductDefinitionReadAuthorizationTest {
    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000011");
    static final UUID ROOT = UUID.fromString("00000000-0000-0000-0000-000000000022");
    static final String ISSUER = "https://trusted-product.example.test";
    static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    Scope root() {
        return new Scope(TENANT, ROOT, Operation.ROOT, null);
    }

    SecurityPrincipal principal() {
        return SecurityPrincipal.builder()
                .subject("unchanged-current-actor")
                .issuer(ISSUER)
                .tenantId(TENANT.toString())
                .scopes(Set.of("product:definitions:read"))
                .claims(Map.of("exp", NOW.plusSeconds(300)))
                .attributes(Map.of("actual-policy-actor-classification", "external"))
                .build();
    }

    ProductDefinitionReadAuthorization auth(SecurityPrincipal p, PolicyDecisionPort policy) {
        return new ProductDefinitionReadAuthorizationImpl(
                () -> Mono.justOrEmpty(p), policy, CLOCK, Set.of(ISSUER));
    }

    static void status(Mono<?> action, int code) {
        assertThatThrownBy(action::block)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(code));
    }

    @Test
    void currentPdpReceivesTheExactSharedPrincipalAndScopeBeforeAndAfterRead() {
        var actor = principal();
        var calls = new AtomicInteger();
        var auth =
                auth(
                        actor,
                        (p, action, resource, context) -> {
                            assertThat(p).isSameAs(actor);
                            assertThat(action).isEqualTo("product.definition.root.read");
                            assertThat(resource)
                                    .isEqualTo("tenant/" + TENANT + "/product-definition/" + ROOT);
                            assertThat(context)
                                    .containsEntry("tenantId", TENANT.toString())
                                    .containsEntry("rootId", ROOT.toString())
                                    .doesNotContainKeys("actorKind", "principal_kind");
                            calls.incrementAndGet();
                            return Mono.just(Decision.permit());
                        });
        var admitted = auth.authorize(root()).block();
        assertThat(admitted.principal()).isSameAs(actor);
        assertThat(admitted.validUntilEpochMillis()).isEqualTo(NOW.plusSeconds(30).toEpochMilli());
        auth.reauthorize(admitted).block();
        assertThat(calls).hasValue(2);
    }

    @Test
    void revisionAndPublicationResourcesRetainExactChildIdentity() {
        var child = UUID.randomUUID();
        var seen = new ArrayList<String>();
        var auth =
                auth(
                        principal(),
                        (p, a, r, c) -> {
                            seen.add(a + " " + r);
                            assertThat(c).containsEntry("resourceId", child.toString());
                            return Mono.just(Decision.permit());
                        });
        auth.authorize(new Scope(TENANT, ROOT, Operation.REVISION, child)).block();
        auth.authorize(new Scope(TENANT, ROOT, Operation.PUBLICATION, child)).block();
        assertThat(seen)
                .containsExactly(
                        "product.definition.revision.read tenant/"
                                + TENANT
                                + "/product-definition/"
                                + ROOT
                                + "/revision/"
                                + child,
                        "product.definition.publication.read tenant/"
                                + TENANT
                                + "/product-definition/"
                                + ROOT
                                + "/publication/"
                                + child);
    }

    @Test
    void missingUntrustedExpiredAndUnscopedPrincipalsCannotRead() {
        PolicyDecisionPort noCall =
                (p, a, r, c) -> Mono.error(new AssertionError("PDP must not see invalid actor"));
        status(auth(null, noCall).authorize(root()), 401);
        status(
                auth(principal().toBuilder().issuer("https://other.example.test").build(), noCall)
                        .authorize(root()),
                401);
        status(
                auth(principal().toBuilder().claims(Map.of("exp", NOW)).build(), noCall)
                        .authorize(root()),
                401);
        status(
                auth(principal().toBuilder().scopes(Set.of()).build(), noCall).authorize(root()),
                403);
        status(
                auth(principal().toBuilder().tenantId(UUID.randomUUID().toString()).build(), noCall)
                        .authorize(root()),
                403);
    }

    @Test
    void deniedIndeterminateAndUnhandledObligationsFailClosed() {
        for (var decision :
                List.of(
                        Decision.deny("not-authorized"),
                        Decision.indeterminate("unknown"),
                        Decision.permit(List.of(Obligation.of("unsupported", Map.of())))))
            status(auth(principal(), (p, a, r, c) -> Mono.just(decision)).authorize(root()), 403);
    }

    @Test
    void emptyAndFailingPolicyAreUnavailableWithoutLeakingTheProviderError() {
        status(auth(principal(), (p, a, r, c) -> Mono.empty()).authorize(root()), 503);
        var action =
                auth(
                                principal(),
                                (p, a, r, c) ->
                                        Mono.error(
                                                new IllegalStateException(
                                                        "private-provider-details")))
                        .authorize(root());
        status(action, 503);
    }

    @Test
    void finalCurrentDenialSuppressesAnEarlierPermit() {
        var calls = new AtomicInteger();
        var auth =
                auth(
                        principal(),
                        (p, a, r, c) ->
                                Mono.just(
                                        calls.incrementAndGet() == 1
                                                ? Decision.permit()
                                                : Decision.deny("revoked")));
        var admitted = auth.authorize(root()).block();
        status(auth.reauthorize(admitted), 403);
        assertThat(calls).hasValue(2);
    }

    @Test
    void remotePolicyCannotRunInsideAnAmbientSqlTransaction() {
        var calls = new AtomicInteger();
        var auth =
                auth(
                        principal(),
                        (p, a, r, c) -> {
                            calls.incrementAndGet();
                            return Mono.just(Decision.permit());
                        });
        Mono<Admission> inside =
                TransactionSynchronizationManager.forCurrentTransaction()
                        .flatMap(
                                manager -> {
                                    manager.setActualTransactionActive(true);
                                    return auth.authorize(root());
                                });
        status(inside.contextWrite(TransactionContextManager.createTransactionContext()), 503);
        assertThat(calls).hasValue(0);
    }
}

package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.core.services.definition.*;

import org.fireflyframework.security.api.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class ProductDefinitionCurrentSelectionAuthorizationTest {
    final UUID tenant = UUID.randomUUID(), root = UUID.randomUUID();
    final Instant now = Instant.parse("2026-10-24T12:00:00Z");

    @Test
    void currentSelectorHasCurrentRootScopeWithoutInventingASubmissionIdentifier() {
        var calls = new AtomicInteger();
        var actor =
                SecurityPrincipal.builder()
                        .subject("independent-checker")
                        .issuer("https://staff.example.test")
                        .tenantId(tenant.toString())
                        .scopes(
                                Set.of(
                                        "product:definitions:review",
                                        "product:definitions:cost:read"))
                        .claims(Map.of("exp", now.plusSeconds(300)))
                        .build();
        ProductDefinitionReviewAuthorization auth =
                new ProductDefinitionCommandAuthorizationImpl(
                        () -> Mono.just(actor),
                        (p, a, r, c) -> {
                            calls.incrementAndGet();
                            assertThat(a).isEqualTo("product.definition.selection.read");
                            assertThat(r)
                                    .isEqualTo(
                                            "tenant/"
                                                    + tenant
                                                    + "/product-definition/"
                                                    + root
                                                    + "/submission/current");
                            assertThat(c)
                                    .containsEntry("selectionTarget", "CURRENT")
                                    .doesNotContainKey("submissionId");
                            return Mono.just(Decision.permit());
                        },
                        Clock.fixed(now, ZoneOffset.UTC),
                        Set.of(actor.issuer()),
                        ProductDefinitionCommandAuthorization.POLICY_PROFILE);
        var admitted =
                auth.authorize(ProductDefinitionReviewAuthorization.Scope.current(tenant, root))
                        .block();
        assertThat(admitted).isNotNull();
        auth.reauthorize(admitted).block();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void exactSelectorStillRequiresARealNonzeroSubmissionId() {
        ProductDefinitionReviewAuthorization auth =
                new ProductDefinitionCommandAuthorizationImpl(
                        () -> Mono.empty(),
                        (p, a, r, c) -> Mono.just(Decision.permit()),
                        Clock.fixed(now, ZoneOffset.UTC),
                        Set.of("https://staff.example.test"),
                        ProductDefinitionCommandAuthorization.POLICY_PROFILE);
        assertThatThrownBy(
                        () ->
                                auth.authorize(
                                                new ProductDefinitionReviewAuthorization.Scope(
                                                        tenant, root, null))
                                        .block())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(400));
    }
}

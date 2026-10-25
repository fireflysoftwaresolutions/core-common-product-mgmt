package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.core.services.definition.*;

import org.fireflyframework.security.api.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class ProductDefinitionReviewAuthorizationTest {
    final Instant now = Instant.parse("2026-10-24T12:00:00Z");
    final UUID tenant = UUID.randomUUID(), root = UUID.randomUUID(), submission = UUID.randomUUID();
    final AtomicInteger requests = new AtomicInteger();
    boolean permit = true;
    Set<String> scopes = Set.of("product:definitions:review", "product:definitions:cost:read");

    ProductDefinitionReviewAuthorization authority() {
        var value =
                new ProductDefinitionCommandAuthorizationImpl(
                        () ->
                                Mono.just(
                                        SecurityPrincipal.builder()
                                                .issuer("https://staff.example.test")
                                                .subject("different-checker")
                                                .tenantId(tenant.toString())
                                                .scopes(scopes)
                                                .claims(Map.of("exp", now.plusSeconds(300)))
                                                .build()),
                        (principal, action, resource, context) -> {
                            requests.incrementAndGet();
                            assertThat(action).isEqualTo("product.definition.selection.read");
                            assertThat(resource).endsWith("/submission/" + submission);
                            assertThat(context.get("authorityProfile"))
                                    .isEqualTo(
                                            ProductDefinitionCommandAuthorization.POLICY_PROFILE);
                            return Mono.just(
                                    permit ? Decision.permit() : Decision.deny("current rule"));
                        },
                        Clock.fixed(now, ZoneOffset.UTC),
                        Set.of("https://staff.example.test"),
                        ProductDefinitionCommandAuthorization.POLICY_PROFILE);
        assertThat(value).isInstanceOf(ProductDefinitionReviewAuthorization.class);
        return (ProductDefinitionReviewAuthorization) (Object) value;
    }

    ProductDefinitionReviewAuthorization.Scope scope() {
        return new ProductDefinitionReviewAuthorization.Scope(tenant, root, submission);
    }

    @Test
    void exactSelectionReadActionAndCurrentPolicyAreCheckedBeforeAndAfterRead() {
        var auth = authority();
        var admission = auth.authorize(scope()).block();
        assertThat(admission).isNotNull();
        auth.reauthorize(admission).block();
        assertThat(requests.get()).isEqualTo(2);
        permit = false;
        StepVerifier.create(auth.authorize(scope()))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(403))
                .verify();
    }

    @Test
    void reviewScopeAloneCannotReadPrivateCost() {
        scopes = Set.of("product:definitions:review");
        StepVerifier.create(authority().authorize(scope()))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(403))
                .verify();
        assertThat(requests.get()).isZero();
    }

    @Test
    void costReadScopeAloneCannotObtainCheckerSelection() {
        scopes = Set.of("product:definitions:cost:read");
        StepVerifier.create(authority().authorize(scope()))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(403))
                .verify();
        assertThat(requests.get()).isZero();
    }
}

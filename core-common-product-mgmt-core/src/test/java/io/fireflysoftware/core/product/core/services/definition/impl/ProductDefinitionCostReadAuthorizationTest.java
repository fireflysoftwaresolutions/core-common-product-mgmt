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

class ProductDefinitionCostReadAuthorizationTest {
    final Instant now = Instant.parse("2026-10-24T12:00:00Z");
    final UUID tenant = UUID.randomUUID(), root = UUID.randomUUID();
    final AtomicInteger requests = new AtomicInteger();
    boolean permit = true;
    Set<String> scopes = Set.of("product:definitions:cost:read", "product:definitions:cost:write");

    ProductDefinitionCostReadAuthorization authority() {
        var value =
                new ProductDefinitionCommandAuthorizationImpl(
                        () ->
                                Mono.just(
                                        SecurityPrincipal.builder()
                                                .issuer("https://staff.example.test")
                                                .subject("cost-editor")
                                                .tenantId(tenant.toString())
                                                .scopes(scopes)
                                                .claims(Map.of("exp", now.plusSeconds(300)))
                                                .build()),
                        (principal, action, resource, context) -> {
                            requests.incrementAndGet();
                            assertThat(action).isEqualTo("product.definition.cost.read");
                            assertThat(resource)
                                    .isEqualTo(
                                            "tenant/"
                                                    + tenant
                                                    + "/product-definition/"
                                                    + root
                                                    + "/cost/current");
                            assertThat(context.get("authorityProfile"))
                                    .isEqualTo(
                                            ProductDefinitionCommandAuthorization.POLICY_PROFILE);
                            return Mono.just(
                                    permit ? Decision.permit() : Decision.deny("current rule"));
                        },
                        Clock.fixed(now, ZoneOffset.UTC),
                        Set.of("https://staff.example.test"),
                        ProductDefinitionCommandAuthorization.POLICY_PROFILE);
        assertThat(value).isInstanceOf(ProductDefinitionCostReadAuthorization.class);
        return (ProductDefinitionCostReadAuthorization) (Object) value;
    }

    ProductDefinitionCostReadAuthorization.Scope scope() {
        return new ProductDefinitionCostReadAuthorization.Scope(tenant, root);
    }

    @Test
    void ordinaryCostEditorCanReopenCurrentCostsWithoutCheckerReviewPermission() {
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
    void writePermissionAloneCannotRevealPrivateCosts() {
        scopes = Set.of("product:definitions:cost:write");
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
    void reviewerPermissionAloneCannotUseCostEditorRead() {
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
}

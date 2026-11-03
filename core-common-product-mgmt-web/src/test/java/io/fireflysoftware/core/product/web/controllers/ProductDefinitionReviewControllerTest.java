package io.fireflysoftware.core.product.web.controllers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionReviewReadService;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleContracts.Selection;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;

import java.util.UUID;

class ProductDefinitionReviewControllerTest {
    final UUID tenant = UUID.randomUUID(), root = UUID.randomUUID(), submission = UUID.randomUUID();
    final ProductDefinitionReviewReadService service =
            mock(ProductDefinitionReviewReadService.class);

    Class<?> controller() {
        try {
            return Class.forName(
                    "io.fireflysoftware.core.product.web.controllers.ProductDefinitionReviewController");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Missing private checker read route", missing);
        }
    }

    WebTestClient client() throws Exception {
        return WebTestClient.bindToController(
                        controller()
                                .getConstructor(ProductDefinitionReviewReadService.class)
                                .newInstance(service))
                .build();
    }

    String path() {
        return "/api/v1/tenants/"
                + tenant
                + "/product-definitions/"
                + root
                + "/staff-review/selections/"
                + submission;
    }

    @Test
    void privateSelectionRouteReturnsTypedBindingWithNoStoreAndAuthorizationVary()
            throws Exception {
        var selection =
                new Selection(
                        tenant,
                        root,
                        submission,
                        UUID.randomUUID(),
                        "a".repeat(64),
                        3,
                        UUID.randomUUID(),
                        "b".repeat(64),
                        2);
        when(service.readSelection(tenant, root, submission))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionReviewSelectionView(
                                        selection,
                                        "c".repeat(64),
                                        null,
                                        null,
                                        "COMPATIBLE",
                                        "TRUSTED_SERVICE",
                                        java.time.Instant.parse("2026-10-24T12:00:00Z"))));
        client().get()
                .uri(path())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueMatches("Cache-Control", ".*no-store.*")
                .expectHeader()
                .valueEquals("Vary", "Authorization")
                .expectBody()
                .jsonPath("$.selection.costRevisionId")
                .isEqualTo(selection.costRevisionId().toString())
                .jsonPath("$.selectionFingerprint")
                .isEqualTo("c".repeat(64));
        verify(service).readSelection(tenant, root, submission);
    }

    @Test
    void privateReviewRouteIsDisabledUnlessExplicitlyEnabled() {
        var enabled = controller().getAnnotation(ConditionalOnProperty.class);
        assertThat(enabled).isNotNull();
        assertThat(enabled.prefix()).isEqualTo("product.definitions.commands");
        assertThat(enabled.matchIfMissing()).isFalse();
    }

    @Test
    void currentSelectionRouteDoesNotRequireMakerForwardedSubmissionId() throws Exception {
        var selected =
                new Selection(
                        tenant,
                        root,
                        submission,
                        UUID.randomUUID(),
                        "a".repeat(64),
                        3,
                        UUID.randomUUID(),
                        "b".repeat(64),
                        2);
        when(service.readCurrentSelection(tenant, root))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionReviewSelectionView(
                                        selected,
                                        "c".repeat(64),
                                        null,
                                        null,
                                        "COMPATIBLE",
                                        "TRUSTED_SERVICE",
                                        java.time.Instant.parse("2026-10-24T12:00:00Z"),
                                        UUID.randomUUID())));
        client().get()
                .uri(
                        "/api/v1/tenants/"
                                + tenant
                                + "/product-definitions/"
                                + root
                                + "/staff-review/selection/current")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueMatches("Cache-Control", ".*no-store.*")
                .expectBody()
                .jsonPath("$.selection.submissionId")
                .isEqualTo(submission.toString())
                .jsonPath("$.activeReviewId")
                .exists();
        verify(service).readCurrentSelection(tenant, root);
    }
}

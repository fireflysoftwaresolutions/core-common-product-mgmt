package io.fireflysoftware.core.product.web.controllers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionCostReadService;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCostView;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;

import java.util.UUID;

class ProductDefinitionCostControllerTest {
    final UUID tenant = UUID.randomUUID(),
            root = UUID.randomUUID(),
            revision = UUID.randomUUID(),
            cost = UUID.randomUUID();
    final ProductDefinitionCostReadService service = mock(ProductDefinitionCostReadService.class);

    Class<?> controller() {
        try {
            return Class.forName(
                    "io.fireflysoftware.core.product.web.controllers.ProductDefinitionCostController");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Missing protected ordinary cost read route", missing);
        }
    }

    WebTestClient client() throws Exception {
        return WebTestClient.bindToController(
                        controller()
                                .getConstructor(ProductDefinitionCostReadService.class)
                                .newInstance(service))
                .build();
    }

    String path() {
        return "/api/v1/tenants/" + tenant + "/product-definitions/" + root + "/staff-cost/current";
    }

    @Test
    void costEditorGetsExactCurrentRevisionReferencesWithoutResponseCaching() throws Exception {
        when(service.readCurrent(tenant, root))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionCostView(
                                        tenant,
                                        root,
                                        revision,
                                        "a".repeat(64),
                                        3,
                                        "DRAFT",
                                        null,
                                        cost,
                                        "b".repeat(64),
                                        2,
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
                .jsonPath("$.publicRevisionId")
                .isEqualTo(revision.toString())
                .jsonPath("$.costRevisionId")
                .isEqualTo(cost.toString())
                .jsonPath("$.costVersion")
                .isEqualTo(2);
        verify(service).readCurrent(tenant, root);
    }

    @Test
    void privateCostRouteIsDisabledByDefault() {
        var enabled = controller().getAnnotation(ConditionalOnProperty.class);
        assertThat(enabled).isNotNull();
        assertThat(enabled.prefix()).isEqualTo("product.definitions.commands");
        assertThat(enabled.matchIfMissing()).isFalse();
    }
}

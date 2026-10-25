package io.fireflysoftware.core.product.web.controllers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionLifecycleService;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.*;

import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.PostMapping;

import reactor.core.publisher.Mono;

import java.util.*;

class ProductDefinitionLifecycleControllerTest {
    final UUID tenant = UUID.randomUUID(), root = UUID.randomUUID();
    final ProductDefinitionLifecycleService service = mock(ProductDefinitionLifecycleService.class);
    static final Map<ProductDefinitionCommand, String> PATHS =
            Map.of(
                    ProductDefinitionCommand.CREATE_DRAFT,
                    "draft",
                    ProductDefinitionCommand.EDIT_PUBLIC,
                    "public-revision",
                    ProductDefinitionCommand.EDIT_COST,
                    "private-cost",
                    ProductDefinitionCommand.SUBMIT_PUBLIC,
                    "submission",
                    ProductDefinitionCommand.APPROVE_SELECTION,
                    "selection/approve",
                    ProductDefinitionCommand.REJECT_SELECTION,
                    "selection/reject",
                    ProductDefinitionCommand.PUBLISH,
                    "publication",
                    ProductDefinitionCommand.WITHDRAW_PUBLICATION,
                    "publication/withdraw");

    Class<?> controller() {
        try {
            return Class.forName(
                    "io.fireflysoftware.core.product.web.controllers.ProductDefinitionLifecycleController");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Missing real lifecycle command controller", missing);
        }
    }

    WebTestClient client() throws Exception {
        return WebTestClient.bindToController(
                        controller()
                                .getConstructor(ProductDefinitionLifecycleService.class)
                                .newInstance(service))
                .build();
    }

    String path(String suffix) {
        return "/api/v1/tenants/" + tenant + "/product-definitions/" + root + "/commands/" + suffix;
    }

    Receipt receipt(ProductDefinitionCommand command) {
        var scope = new ProductDefinitionLifecycleContracts.Scope(tenant, root, UUID.randomUUID());
        boolean hidden =
                command == ProductDefinitionCommand.EDIT_COST
                        || command == ProductDefinitionCommand.APPROVE_SELECTION
                        || command == ProductDefinitionCommand.REJECT_SELECTION;
        if (hidden)
            return new PrivateReceipt(
                    scope,
                    command,
                    UUID.randomUUID(),
                    "2026-10-24T12:00:00Z",
                    UUID.randomUUID(),
                    1,
                    UUID.randomUUID(),
                    "APPROVED");
        return new PublicReceipt(
                scope,
                command,
                UUID.randomUUID(),
                "2026-10-24T12:00:00Z",
                "DRAFT",
                1,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                "NOT_ADMITTED");
    }

    @Test
    void allEightPostRoutesForwardExactRawBodyAndReturnUncachedTypedVisibility() throws Exception {
        var client = client();
        String raw = "{\"operationId\":\"original\",\"operationId\":\"duplicate\"}";
        for (var entry : PATHS.entrySet()) {
            var receipt = receipt(entry.getKey());
            when(service.execute(tenant, root, entry.getKey(), raw)).thenReturn(Mono.just(receipt));
            var response =
                    client.post()
                            .uri(path(entry.getValue()))
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .bodyValue(raw)
                            .exchange()
                            .expectStatus()
                            .isOk()
                            .expectHeader()
                            .valueMatches("Cache-Control", ".*no-store.*")
                            .expectHeader()
                            .valueEquals("Vary", "Authorization")
                            .expectBody();
            if (receipt instanceof PublicReceipt)
                response.jsonPath("$.operationalAdmission")
                        .isEqualTo("NOT_ADMITTED")
                        .jsonPath("$.costRevisionId")
                        .doesNotExist();
            else
                response.jsonPath("$.costRevisionId")
                        .exists()
                        .jsonPath("$.operationalAdmission")
                        .doesNotExist();
            verify(service).execute(tenant, root, entry.getKey(), raw);
        }
    }

    @Test
    void publicRouteCannotSerializePrivateReceipt() throws Exception {
        when(service.execute(tenant, root, ProductDefinitionCommand.CREATE_DRAFT, "{}"))
                .thenReturn(Mono.just(receipt(ProductDefinitionCommand.EDIT_COST)));
        client().post()
                .uri(path("draft"))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.costRevisionId")
                .doesNotExist();
    }

    @Test
    void eachRealCommandOptsOutOfSharedHttpIdempotencyCachingAndIsDisabledByDefault() {
        var type = controller();
        var enabled = type.getAnnotation(ConditionalOnProperty.class);
        assertThat(enabled).isNotNull();
        assertThat(enabled.prefix()).isEqualTo("product.definitions.commands");
        assertThat(enabled.name()).containsExactly("enabled");
        assertThat(enabled.havingValue()).isEqualTo("true");
        assertThat(enabled.matchIfMissing()).isFalse();
        var posts =
                Arrays.stream(type.getDeclaredMethods())
                        .filter(method -> method.isAnnotationPresent(PostMapping.class))
                        .toList();
        assertThat(posts).hasSize(8);
        assertThat(posts).allMatch(method -> method.isAnnotationPresent(DisableIdempotency.class));
    }

    @Test
    void malformedPathAndWrongVerbNeverInvokeBusinessService() throws Exception {
        var client = client();
        client.post()
                .uri("/api/v1/tenants/bad/product-definitions/" + root + "/commands/draft")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isBadRequest();
        client.get().uri(path("draft")).exchange().expectStatus().isEqualTo(405);
        verifyNoInteractions(service);
    }
}

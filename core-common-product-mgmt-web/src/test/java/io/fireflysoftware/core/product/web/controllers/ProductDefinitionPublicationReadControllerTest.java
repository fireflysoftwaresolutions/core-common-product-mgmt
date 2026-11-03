package io.fireflysoftware.core.product.web.controllers;

import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionPublicationReadService;
import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationDiscoveryView;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationDiscoveryView.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationReadinessView;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationReadinessView.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

class ProductDefinitionPublicationReadControllerTest {
    final UUID tenant = UUID.randomUUID(), root = UUID.randomUUID();
    final ProductDefinitionPublicationReadService service =
            mock(ProductDefinitionPublicationReadService.class);
    final ReadObservation observation =
            new ReadObservation(
                    Instant.parse("2026-10-25T00:00:00Z"), Instant.parse("2026-10-25T00:00:20Z"));
    final String base =
            "/api/v1/tenants/" + tenant + "/product-definitions/" + root + "/staff-publication";

    WebTestClient client() throws Exception {
        Object controller =
                Class.forName(
                                "io.fireflysoftware.core.product.web.controllers.ProductDefinitionPublicationReadController")
                        .getConstructor(ProductDefinitionPublicationReadService.class)
                        .newInstance(service);
        return WebTestClient.bindToController(controller).build();
    }

    @Test
    void notReadyIsCurrentMetadataWithoutSelectionCoordinates() throws Exception {
        when(service.readiness(tenant, root))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionPublicationReadinessView(
                                        tenant,
                                        root,
                                        PublicationReadinessStatus.NOT_READY,
                                        null,
                                        observation,
                                        OperationalAdmission.NOT_ADMITTED)));
        client().get()
                .uri(base + "/readiness")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store, private")
                .expectHeader()
                .valueEquals(HttpHeaders.VARY, "Authorization")
                .expectHeader()
                .doesNotExist(HttpHeaders.SET_COOKIE)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("NOT_READY")
                .jsonPath("$.selection")
                .doesNotExist()
                .jsonPath("$.reason")
                .doesNotExist()
                .jsonPath("$.operationalAdmission")
                .isEqualTo("NOT_ADMITTED");
    }

    @Test
    void readyReturnsOnlyExactPublishCoordinates() throws Exception {
        UUID review = UUID.randomUUID();
        when(service.readiness(tenant, root))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionPublicationReadinessView(
                                        tenant,
                                        root,
                                        PublicationReadinessStatus.READY,
                                        new PublicationReadySelection(review, 4, 2),
                                        observation,
                                        OperationalAdmission.NOT_ADMITTED)));
        client().get()
                .uri(base + "/readiness")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.selection.reviewId")
                .isEqualTo(review.toString())
                .jsonPath("$.selection.publicVersion")
                .isEqualTo(4)
                .jsonPath("$.selection.costVersion")
                .isEqualTo(2)
                .jsonPath("$.selection.costRevisionId")
                .doesNotExist()
                .jsonPath("$.selection.costDigest")
                .doesNotExist()
                .jsonPath("$.selection.selectionFingerprint")
                .doesNotExist()
                .jsonPath("$.actor")
                .doesNotExist();
    }

    @Test
    void noneHasNoPublicationCoordinates() throws Exception {
        when(service.discover(tenant, root))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionPublicationDiscoveryView(
                                        tenant,
                                        root,
                                        PublicationDiscoveryState.NONE,
                                        null,
                                        observation,
                                        OperationalAdmission.NOT_ADMITTED)));
        client().get()
                .uri(base + "/current")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store, private")
                .expectHeader()
                .valueEquals(HttpHeaders.VARY, "Authorization")
                .expectHeader()
                .doesNotExist(HttpHeaders.SET_COOKIE)
                .expectBody()
                .jsonPath("$.state")
                .isEqualTo("NONE")
                .jsonPath("$.publication")
                .doesNotExist();
    }

    @Test
    void activeAndRetainedWithdrawalAreExplicitWithoutPermissionFlags() throws Exception {
        for (var state :
                new PublicationDiscoveryState[] {
                    PublicationDiscoveryState.ACTIVE, PublicationDiscoveryState.RETAINED_WITHDRAWN
                }) {
            int version = state == PublicationDiscoveryState.ACTIVE ? 0 : 1;
            when(service.discover(tenant, root))
                    .thenReturn(
                            Mono.just(
                                    new ProductDefinitionPublicationDiscoveryView(
                                            tenant,
                                            root,
                                            state,
                                            new DiscoveredPublication(UUID.randomUUID(), version),
                                            observation,
                                            OperationalAdmission.NOT_ADMITTED)));
            client().get()
                    .uri(base + "/current")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.state")
                    .isEqualTo(state.name())
                    .jsonPath("$.publication.stateVersion")
                    .isEqualTo(version)
                    .jsonPath("$.withdrawable")
                    .doesNotExist()
                    .jsonPath("$.cost")
                    .doesNotExist();
        }
    }

    @Test
    void absentBusinessResultIsUnavailableInsteadOfAnInventedReadyOrNone() throws Exception {
        when(service.readiness(tenant, root)).thenReturn(Mono.empty());
        when(service.discover(tenant, root)).thenReturn(Mono.empty());
        client().get().uri(base + "/readiness").exchange().expectStatus().isEqualTo(503);
        client().get().uri(base + "/current").exchange().expectStatus().isEqualTo(503);
    }
}

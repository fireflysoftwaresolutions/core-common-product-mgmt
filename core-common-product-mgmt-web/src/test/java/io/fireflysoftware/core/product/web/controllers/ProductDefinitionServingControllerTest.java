package io.fireflysoftware.core.product.web.controllers;

import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionServingService;
import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.util.UUID;

class ProductDefinitionServingControllerTest {
    final UUID tenant = UUID.randomUUID(),
            root = UUID.randomUUID(),
            revision = UUID.randomUUID(),
            publication = UUID.randomUUID();
    final ProductDefinitionServingService service = mock(ProductDefinitionServingService.class);
    final WebTestClient client =
            WebTestClient.bindToController(new ProductDefinitionServingController(service)).build();

    String rootPath() {
        return "/api/v1/tenants/" + tenant + "/product-definitions/" + root;
    }

    @Test
    void rootRoutePreservesScopeAndExplicitNonAdmissionWithoutResponseCaching() {
        when(service.readRoot(tenant, root))
                .thenReturn(
                        Mono.just(
                                new RootView(null, null, null, OperationalAdmission.NOT_ADMITTED)));
        client.get()
                .uri(rootPath())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueMatches("Cache-Control", ".*no-store.*")
                .expectBody()
                .jsonPath("$.operationalAdmission")
                .isEqualTo("NOT_ADMITTED");
    }

    @Test
    void exactRevisionAndPublicationRoutesRemainTypedAndUncached() {
        when(service.readRevision(tenant, root, revision))
                .thenReturn(
                        Mono.just(
                                new RevisionView(
                                        null, null, null, OperationalAdmission.NOT_ADMITTED)));
        when(service.readPublication(tenant, root, publication))
                .thenReturn(
                        Mono.just(
                                new PublicationView(
                                        null,
                                        null,
                                        null,
                                        null,
                                        OperationalAdmission.NOT_ADMITTED)));
        client.get()
                .uri(rootPath() + "/revisions/" + revision)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueMatches("Cache-Control", ".*no-store.*")
                .expectBody()
                .jsonPath("$.operationalAdmission")
                .isEqualTo("NOT_ADMITTED");
        client.get()
                .uri(rootPath() + "/publications/" + publication)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueMatches("Cache-Control", ".*no-store.*")
                .expectBody()
                .jsonPath("$.operationalAdmission")
                .isEqualTo("NOT_ADMITTED");
    }

    @Test
    void boundedServiceErrorsRemainTheirExactStatus() {
        for (var status :
                new HttpStatus[] {
                    HttpStatus.UNAUTHORIZED,
                    HttpStatus.FORBIDDEN,
                    HttpStatus.NOT_FOUND,
                    HttpStatus.SERVICE_UNAVAILABLE
                }) {
            when(service.readRoot(tenant, root))
                    .thenReturn(
                            Mono.error(
                                    new ResponseStatusException(
                                            status, "Current product read unavailable")));
            client.get().uri(rootPath()).exchange().expectStatus().isEqualTo(status);
        }
    }

    @Test
    void malformedIdentityAndMutationNeverInvokeServingService() {
        client.get()
                .uri("/api/v1/tenants/not-a-uuid/product-definitions/" + root)
                .exchange()
                .expectStatus()
                .isBadRequest();
        client.post().uri(rootPath()).exchange().expectStatus().isEqualTo(405);
        verifyNoInteractions(service);
    }
}

package io.fireflysoftware.core.product.web.controllers;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionServingService;
import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/product-definitions/{rootId}")
@Tag(
        name = "Account tariff definitions",
        description =
                "Current-authorized retained public metadata; operational admission remains"
                        + " NOT_ADMITTED")
@io.swagger.v3.oas.annotations.security.SecurityScheme(
        name = "productDefinitionBearer",
        type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
@SecurityRequirement(name = "productDefinitionBearer")
public final class ProductDefinitionServingController {
    private final ProductDefinitionServingService service;

    public ProductDefinitionServingController(ProductDefinitionServingService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Read the current public account tariff head",
            description =
                    "Returns the retained public-head revision under current finite read"
                        + " permission. This read does not admit or activate an account product.")
    public Mono<ResponseEntity<RootView>> root(
            @PathVariable UUID tenantId, @PathVariable UUID rootId) {
        return service.readRoot(tenantId, rootId).map(ProductDefinitionServingController::uncached);
    }

    @GetMapping("/revisions/{revisionId}")
    @Operation(summary = "Read an exact retained account tariff revision")
    public Mono<ResponseEntity<RevisionView>> revision(
            @PathVariable UUID tenantId, @PathVariable UUID rootId, @PathVariable UUID revisionId) {
        return service.readRevision(tenantId, rootId, revisionId)
                .map(ProductDefinitionServingController::uncached);
    }

    @GetMapping("/publications/{publicationId}")
    @Operation(
            summary = "Read an exact retained publication and observed current control",
            description =
                    "The control state is observed in a read snapshot under finite permission."
                            + " Publication metadata remains NOT_ADMITTED and does not represent"
                            + " instantaneous revocation or consumer financial activation.")
    public Mono<ResponseEntity<PublicationView>> publication(
            @PathVariable UUID tenantId,
            @PathVariable UUID rootId,
            @PathVariable UUID publicationId) {
        return service.readPublication(tenantId, rootId, publicationId)
                .map(ProductDefinitionServingController::uncached);
    }

    private static <T> ResponseEntity<T> uncached(T view) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .varyBy("Authorization")
                .body(view);
    }
}

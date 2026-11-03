package io.fireflysoftware.core.product.web.controllers;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionPublicationReadService;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationDiscoveryView;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationReadinessView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/product-definitions/{rootId}/staff-publication")
@ConditionalOnProperty(
        prefix = "product.definitions",
        name = {"commands.enabled", "publication-read.enabled"},
        havingValue = "true")
@Tag(
        name = "Product publication workflow",
        description = "Current-authorized advisory publication metadata")
@SecurityRequirement(name = "productDefinitionBearer")
public final class ProductDefinitionPublicationReadController {
    private final ProductDefinitionPublicationReadService service;

    public ProductDefinitionPublicationReadController(
            ProductDefinitionPublicationReadService service) {
        this.service = Objects.requireNonNull(service);
    }

    @GetMapping("/readiness")
    @DisableIdempotency
    @Operation(
            operationId = "publicationReadiness",
            summary = "Discover exact current approved publication coordinates",
            description =
                    "Requires current publish scope and publication.readiness policy. READY returns"
                        + " only the exact review/public/cost versions for PUBLISH; NOT_READY has"
                        + " no selection coordinates. This advisory snapshot is not a command"
                        + " permit. PUBLISH reauthorizes and checks the selection again; metadata"
                        + " remains NOT_ADMITTED.")
    public Mono<ResponseEntity<ProductDefinitionPublicationReadinessView>> readiness(
            @PathVariable UUID tenantId, @PathVariable UUID rootId) {
        return response(service.readiness(tenantId, rootId));
    }

    @GetMapping("/current")
    @DisableIdempotency
    @Operation(
            operationId = "publicationDiscovery",
            summary = "Discover the active or latest retained publication",
            description =
                    "Requires current withdraw scope and publication.discovery policy. ACTIVE,"
                        + " RETAINED_WITHDRAWN and NONE are observed metadata states, not"
                        + " withdrawability. Latest ordering uses native public-state history, not"
                        + " timestamps. Incomplete or ambiguous retained ordering is unavailable;"
                        + " known-ID public publication reads remain separate. WITHDRAW"
                        + " independently reauthorizes and checks the returned control version.")
    public Mono<ResponseEntity<ProductDefinitionPublicationDiscoveryView>> current(
            @PathVariable UUID tenantId, @PathVariable UUID rootId) {
        return response(service.discover(tenantId, rootId));
    }

    private static <T> Mono<ResponseEntity<T>> response(Mono<T> value) {
        return value.switchIfEmpty(
                        Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.SERVICE_UNAVAILABLE,
                                        "Product publication metadata unavailable")))
                .map(
                        view ->
                                ResponseEntity.ok()
                                        .cacheControl(CacheControl.noStore().cachePrivate())
                                        .varyBy("Authorization")
                                        .body(view));
    }
}

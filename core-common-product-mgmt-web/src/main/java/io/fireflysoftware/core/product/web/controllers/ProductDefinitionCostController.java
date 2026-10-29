package io.fireflysoftware.core.product.web.controllers;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionCostReadService;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCostView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/product-definitions/{rootId}/staff-cost")
@ConditionalOnProperty(
        prefix = "product.definitions.commands",
        name = "enabled",
        havingValue = "true")
@Tag(
        name = "Private product costs",
        description = "Current-authorized private cost editing context")
@SecurityRequirement(name = "productDefinitionBearer")
public final class ProductDefinitionCostController {
    private final ProductDefinitionCostReadService service;

    public ProductDefinitionCostController(ProductDefinitionCostReadService service) {
        this.service = Objects.requireNonNull(service);
    }

    @GetMapping("/current")
    @Operation(
            summary = "Reopen exact current costs and their public definition context",
            description =
                    "Requires current cost:read scope and staff cost.read policy, without checker"
                        + " review permission. Retain the returned cost revision/version and public"
                        + " revision/digest when editing; a changed selection conflicts. This"
                        + " private read does not admit or activate a product.")
    public Mono<ResponseEntity<ProductDefinitionCostView>> current(
            @PathVariable UUID tenantId, @PathVariable UUID rootId) {
        return service.readCurrent(tenantId, rootId)
                .switchIfEmpty(
                        Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.SERVICE_UNAVAILABLE,
                                        "Product current cost unavailable")))
                .map(
                        value ->
                                ResponseEntity.ok()
                                        .cacheControl(CacheControl.noStore().cachePrivate())
                                        .varyBy("Authorization")
                                        .body(value));
    }
}

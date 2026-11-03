package io.fireflysoftware.core.product.web.controllers;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionReviewReadService;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionReviewSelectionView;
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
@RequestMapping("/api/v1/tenants/{tenantId}/product-definitions/{rootId}/staff-review")
@ConditionalOnProperty(
        prefix = "product.definitions.commands",
        name = "enabled",
        havingValue = "true")
@Tag(
        name = "Private product review",
        description = "Current-authorized checker selection and private cost inspection")
@SecurityRequirement(name = "productDefinitionBearer")
public final class ProductDefinitionReviewController {
    private final ProductDefinitionReviewReadService service;

    public ProductDefinitionReviewController(ProductDefinitionReviewReadService service) {
        this.service = Objects.requireNonNull(service);
    }

    @GetMapping("/selection/current")
    @Operation(
            summary = "Discover the actual current submitted selection",
            description =
                    "Resolves the real immutable submission for the current IN_REVIEW head/version;"
                        + " no maker-held submission ID is required. Current review and"
                        + " private-cost read permission is checked before and after the read. The"
                        + " observed active review pointer supports resuming an authorized publish"
                        + " command; publication still validates the retained decision and exact"
                        + " selection.")
    public Mono<ResponseEntity<ProductDefinitionReviewSelectionView>> current(
            @PathVariable UUID tenantId, @PathVariable UUID rootId) {
        return service.readCurrentSelection(tenantId, rootId)
                .switchIfEmpty(
                        Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.SERVICE_UNAVAILABLE,
                                        "Product review selection unavailable")))
                .map(
                        value ->
                                ResponseEntity.ok()
                                        .cacheControl(CacheControl.noStore().cachePrivate())
                                        .varyBy("Authorization")
                                        .body(value));
    }

    @GetMapping("/selections/{submissionId}")
    @Operation(
            summary = "Inspect the exact current submitted public and private selection",
            description =
                    "Requires current review and private-cost read scope plus staff selection.read"
                        + " policy. Retain the returned IDs, digests, versions and"
                        + " selectionFingerprint when approving or rejecting. A later head change"
                        + " causes a conflict; validation is a trusted service boundary.")
    public Mono<ResponseEntity<ProductDefinitionReviewSelectionView>> selection(
            @PathVariable UUID tenantId,
            @PathVariable UUID rootId,
            @PathVariable UUID submissionId) {
        return service.readSelection(tenantId, rootId, submissionId)
                .switchIfEmpty(
                        Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.SERVICE_UNAVAILABLE,
                                        "Product review selection unavailable")))
                .map(
                        value ->
                                ResponseEntity.ok()
                                        .cacheControl(CacheControl.noStore().cachePrivate())
                                        .varyBy("Authorization")
                                        .body(value));
    }
}

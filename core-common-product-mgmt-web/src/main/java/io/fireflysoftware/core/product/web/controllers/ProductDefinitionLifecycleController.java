package io.fireflysoftware.core.product.web.controllers;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionLifecycleService;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCommand;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/product-definitions/{rootId}/commands")
@ConditionalOnProperty(
        prefix = "product.definitions.commands",
        name = "enabled",
        havingValue = "true")
@Tag(
        name = "Governed product lifecycle",
        description =
                "Current-authorized staff commands; publication metadata remains NOT_ADMITTED")
@SecurityRequirement(name = "productDefinitionBearer")
public final class ProductDefinitionLifecycleController {
    private final ProductDefinitionLifecycleService service;

    public ProductDefinitionLifecycleController(ProductDefinitionLifecycleService service) {
        this.service = Objects.requireNonNull(service);
    }

    @PostMapping(value = "/draft", consumes = "application/json")
    @DisableIdempotency
    @Operation(
            summary = "Create a complete validated account tariff draft",
            description =
                    "The operation ID binds the full original intent. Every retry rechecks current"
                        + " staff permission; durable outcome and current receipt visibility are"
                        + " separate.")
    public Mono<ResponseEntity<PublicReceipt>> create(
            @PathVariable UUID tenantId,
            @PathVariable UUID rootId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    CreateDraftBody.class)))
                    @RequestBody
                    String body) {
        return execute(
                tenantId, rootId, ProductDefinitionCommand.CREATE_DRAFT, body, PublicReceipt.class);
    }

    @PostMapping(value = "/public-revision", consumes = "application/json")
    @DisableIdempotency
    @Operation(
            summary = "Append public terms and return the root to draft",
            description =
                    "The operation ID binds the full original intent. Every retry rechecks current"
                        + " staff permission; durable outcome and current receipt visibility are"
                        + " separate.")
    public Mono<ResponseEntity<PublicReceipt>> editPublic(
            @PathVariable UUID tenantId,
            @PathVariable UUID rootId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(implementation = EditPublicBody.class)))
                    @RequestBody
                    String body) {
        return execute(
                tenantId, rootId, ProductDefinitionCommand.EDIT_PUBLIC, body, PublicReceipt.class);
    }

    @PostMapping(value = "/private-cost", consumes = "application/json")
    @DisableIdempotency
    @Operation(
            summary = "Append private cost terms without changing public state",
            description =
                    "The operation ID binds the full original intent. Every retry rechecks current"
                        + " staff permission; durable outcome and current receipt visibility are"
                        + " separate.")
    public Mono<ResponseEntity<PrivateReceipt>> editCost(
            @PathVariable UUID tenantId,
            @PathVariable UUID rootId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(schema = @Schema(implementation = EditCostBody.class)))
                    @RequestBody
                    String body) {
        return execute(
                tenantId, rootId, ProductDefinitionCommand.EDIT_COST, body, PrivateReceipt.class);
    }

    @PostMapping(value = "/submission", consumes = "application/json")
    @DisableIdempotency
    @Operation(
            summary = "Retain an immutable public submission for review",
            description =
                    "The operation ID binds the full original intent. Every retry rechecks current"
                        + " staff permission; durable outcome and current receipt visibility are"
                        + " separate.")
    public Mono<ResponseEntity<PublicReceipt>> submit(
            @PathVariable UUID tenantId,
            @PathVariable UUID rootId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    SubmitPublicBody.class)))
                    @RequestBody
                    String body) {
        return execute(
                tenantId,
                rootId,
                ProductDefinitionCommand.SUBMIT_PUBLIC,
                body,
                PublicReceipt.class);
    }

    @PostMapping(value = "/selection/approve", consumes = "application/json")
    @DisableIdempotency
    @Operation(
            summary = "Approve an exact public and private selection as an independent checker",
            description =
                    "The operation ID binds the full original intent. Every retry rechecks current"
                        + " staff permission; durable outcome and current receipt visibility are"
                        + " separate.")
    public Mono<ResponseEntity<PrivateReceipt>> approve(
            @PathVariable UUID tenantId,
            @PathVariable UUID rootId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    ReviewSelectionBody.class)))
                    @RequestBody
                    String body) {
        return execute(
                tenantId,
                rootId,
                ProductDefinitionCommand.APPROVE_SELECTION,
                body,
                PrivateReceipt.class);
    }

    @PostMapping(value = "/selection/reject", consumes = "application/json")
    @DisableIdempotency
    @Operation(
            summary = "Retain a private checker rejection of an exact selection",
            description =
                    "The operation ID binds the full original intent. Every retry rechecks current"
                        + " staff permission; durable outcome and current receipt visibility are"
                        + " separate.")
    public Mono<ResponseEntity<PrivateReceipt>> reject(
            @PathVariable UUID tenantId,
            @PathVariable UUID rootId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    ReviewSelectionBody.class)))
                    @RequestBody
                    String body) {
        return execute(
                tenantId,
                rootId,
                ProductDefinitionCommand.REJECT_SELECTION,
                body,
                PrivateReceipt.class);
    }

    @PostMapping(value = "/publication", consumes = "application/json")
    @DisableIdempotency
    @Operation(
            summary = "Publish approved retained metadata without operational admission",
            description =
                    "The operation ID binds the full original intent. Every retry rechecks current"
                        + " staff permission; durable outcome and current receipt visibility are"
                        + " separate.")
    public Mono<ResponseEntity<PublicReceipt>> publish(
            @PathVariable UUID tenantId,
            @PathVariable UUID rootId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(schema = @Schema(implementation = PublishBody.class)))
                    @RequestBody
                    String body) {
        return execute(
                tenantId, rootId, ProductDefinitionCommand.PUBLISH, body, PublicReceipt.class);
    }

    @PostMapping(value = "/publication/withdraw", consumes = "application/json")
    @DisableIdempotency
    @Operation(
            summary = "Withdraw current publication control without changing retained terms",
            description =
                    "The operation ID binds the full original intent. Every retry rechecks current"
                        + " staff permission; durable outcome and current receipt visibility are"
                        + " separate.")
    public Mono<ResponseEntity<PublicReceipt>> withdraw(
            @PathVariable UUID tenantId,
            @PathVariable UUID rootId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(schema = @Schema(implementation = WithdrawBody.class)))
                    @RequestBody
                    String body) {
        return execute(
                tenantId,
                rootId,
                ProductDefinitionCommand.WITHDRAW_PUBLICATION,
                body,
                PublicReceipt.class);
    }

    private <T extends Receipt> Mono<ResponseEntity<T>> execute(
            UUID tenantId,
            UUID rootId,
            ProductDefinitionCommand command,
            String body,
            Class<T> type) {
        return service.execute(tenantId, rootId, command, body)
                .switchIfEmpty(Mono.error(unavailable()))
                .map(
                        receipt -> {
                            if (!type.isInstance(receipt) || receipt.command() != command)
                                throw unavailable();
                            return ResponseEntity.ok()
                                    .cacheControl(CacheControl.noStore().cachePrivate())
                                    .varyBy("Authorization")
                                    .body(type.cast(receipt));
                        });
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Product action receipt unavailable");
    }
}

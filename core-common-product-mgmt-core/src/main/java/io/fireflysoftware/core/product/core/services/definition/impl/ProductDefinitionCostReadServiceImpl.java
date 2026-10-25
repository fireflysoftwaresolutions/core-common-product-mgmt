package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCostView;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandPersistence;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.util.*;

public final class ProductDefinitionCostReadServiceImpl
        implements ProductDefinitionCostReadService {
    private final ProductDefinitionCostReadAuthorization authorization;
    private final ProductDefinitionCommandPersistence persistence;

    public ProductDefinitionCostReadServiceImpl(
            ProductDefinitionCostReadAuthorization authorization,
            ProductDefinitionCommandPersistence persistence) {
        this.authorization = Objects.requireNonNull(authorization);
        this.persistence = Objects.requireNonNull(persistence);
    }

    public Mono<ProductDefinitionCostView> readCurrent(UUID tenantId, UUID rootId) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(
                        () ->
                                authorization
                                        .authorize(
                                                new ProductDefinitionCostReadAuthorization.Scope(
                                                        tenantId, rootId))
                                        .flatMap(
                                                admission -> {
                                                    if (!admission
                                                            .scope()
                                                            .equals(
                                                                    new ProductDefinitionCostReadAuthorization
                                                                            .Scope(
                                                                            tenantId, rootId)))
                                                        throw unavailable();
                                                    var actor = admission.principal();
                                                    return persistence
                                                            .readTransactions()
                                                            .execute(
                                                                    status ->
                                                                            persistence
                                                                                    .reviewReadNative()
                                                                                    .openCost(
                                                                                            tenantId,
                                                                                            rootId,
                                                                                            actor
                                                                                                    .issuer(),
                                                                                            actor
                                                                                                    .subject(),
                                                                                            admission
                                                                                                    .actorExpiresAtEpochMillis(),
                                                                                            admission
                                                                                                    .validUntilEpochMillis(),
                                                                                            ProductDefinitionCommandAuthorization
                                                                                                    .POLICY_PROFILE)
                                                                                    .switchIfEmpty(
                                                                                            Mono
                                                                                                    .error(
                                                                                                            unavailable()))
                                                                                    .flatMap(
                                                                                            context -> {
                                                                                                if (context
                                                                                                                        .contextId()
                                                                                                                == null
                                                                                                        || context
                                                                                                                        .recordedAt()
                                                                                                                == null)
                                                                                                    throw unavailable();
                                                                                                return live(context
                                                                                                                .contextId())
                                                                                                        .then(
                                                                                                                Mono
                                                                                                                        .defer(
                                                                                                                                () ->
                                                                                                                                        snapshot(
                                                                                                                                                tenantId,
                                                                                                                                                rootId,
                                                                                                                                                context
                                                                                                                                                        .recordedAt())))
                                                                                                        .flatMap(
                                                                                                                view ->
                                                                                                                        live(context
                                                                                                                                        .contextId())
                                                                                                                                .thenReturn(
                                                                                                                                        view));
                                                                                            }))
                                                            .single()
                                                            .flatMap(
                                                                    view ->
                                                                            authorization
                                                                                    .reauthorize(
                                                                                            admission)
                                                                                    .thenReturn(
                                                                                            view));
                                                }))
                .onErrorMap(ProductDefinitionCommandErrors::safe);
    }

    private Mono<ProductDefinitionCostView> snapshot(
            UUID tenant, UUID root, java.time.Instant time) {
        return ProductDefinitionPrivateReadSnapshots.head(persistence, tenant, root)
                .flatMap(
                        head ->
                                ProductDefinitionPrivateReadSnapshots.cost(persistence, head)
                                        .map(
                                                cost ->
                                                        new ProductDefinitionCostView(
                                                                tenant,
                                                                root,
                                                                head.revision().revisionId(),
                                                                head.revision().publicDigest(),
                                                                head.root().publicStateVersion(),
                                                                head.root().phase(),
                                                                head.definition(),
                                                                cost.revision().costRevisionId(),
                                                                cost.revision().costDigest(),
                                                                cost.control().costStateVersion(),
                                                                cost.terms(),
                                                                cost.compatibility(),
                                                                "TRUSTED_SERVICE",
                                                                time)));
    }

    private Mono<Void> live(UUID id) {
        return persistence
                .reviewReadNative()
                .isCostCurrent(id)
                .filter(Boolean.TRUE::equals)
                .switchIfEmpty(Mono.error(unavailable()))
                .then();
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Product current cost unavailable");
    }
}

package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCostView;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandPersistence;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.util.*;
import java.time.*;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.fireflyframework.core.transaction.RootTransactionException;

public final class ProductDefinitionCostReadServiceImpl
        implements ProductDefinitionCostReadService {
    private final ProductDefinitionCostReadAuthorization authorization;
    private final ProductDefinitionCommandPersistence persistence;
    private final GovernedDefinitionProfile backend;
    private final Clock clock;
    private final LongSupplier nanos;
    private final boolean boundedMode;

    public ProductDefinitionCostReadServiceImpl(ProductDefinitionCostReadAuthorization authorization,ProductDefinitionCommandPersistence persistence) {
        this(authorization,persistence,GovernedDefinitionProfile.ACCOUNT_TARIFF,Clock.systemUTC(),System::nanoTime,false);
    }
    public ProductDefinitionCostReadServiceImpl(ProductDefinitionCostReadAuthorization authorization,ProductDefinitionCommandPersistence persistence,
            GovernedDefinitionProfile backend,Clock clock,LongSupplier nanos) {
        this(authorization,persistence,backend,clock,nanos,true);
    }
    private ProductDefinitionCostReadServiceImpl(ProductDefinitionCostReadAuthorization authorization,ProductDefinitionCommandPersistence persistence,
            GovernedDefinitionProfile backend,Clock clock,LongSupplier nanos,boolean boundedMode) {
        this.authorization=Objects.requireNonNull(authorization);this.persistence=Objects.requireNonNull(persistence);
        this.backend=Objects.requireNonNull(backend);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);this.boundedMode=boundedMode;
        if(boundedMode && (authorization.definitionProfile()!=backend || !persistence.boundedTransactions()))throw new IllegalArgumentException("Matching bounded product backend required");
    }
    public Mono<ProductDefinitionCostView> readCurrent(UUID tenantId,UUID rootId) {
        return boundedMode?readBounded(tenantId,rootId):readLegacy(tenantId,rootId);
    }
    private Mono<ProductDefinitionCostView> readBounded(UUID tenantId,UUID rootId) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(()->Mono.defer(()->{
            Instant observed=clock.instant();long before=nanos.getAsLong();
            var request=R2dbcOperationBudget.observed(observed,observed.plusSeconds(30),before,Duration.ofSeconds(30),clock,nanos);
            var requested=new ProductDefinitionCostReadAuthorization.Scope(tenantId,rootId);
            return authorization.authorize(requested).switchIfEmpty(Mono.error(unavailable())).flatMap(admission->{
                if(!requested.equals(admission.scope()))throw unavailable();var actor=admission.principal();
                var window=ProductDefinitionLifecycleWindow.start(observed,before,admission.actorExpiresAtEpochMillis(),admission.validUntilEpochMillis(),clock,nanos);
                var failures=new ProductDefinitionBoundedErrors();
                return persistence.executeBounded(window.budget(),true,scope->{
                    long nativeBefore=nanos.getAsLong();
                    return failures.capture(persistence.reviewReadNative().openCost(tenantId,rootId,actor.issuer(),actor.subject(),admission.actorExpiresAtEpochMillis(),admission.validUntilEpochMillis(),backend.policyProfile())
                        .switchIfEmpty(Mono.error(unavailable())).flatMap(context->persistence.reviewReadNative().admission(context.contextId())
                            .switchIfEmpty(Mono.error(unavailable())).flatMap(stamp->{
                                if(context.contextId()==null || stamp.recordedAt()==null || !stamp.recordedAt().equals(context.recordedAt()) || stamp.dbNowMillis()==null || stamp.admissionUntilMillis()==null)throw unavailable();
                                scope.narrow(window.observe(stamp.recordedAt(),stamp.dbNowMillis(),stamp.admissionUntilMillis(),nativeBefore));
                                return live(context.contextId()).then(Mono.defer(()->snapshot(tenantId,rootId,context.recordedAt())))
                                    .flatMap(view->live(context.contextId()).thenReturn(view));
                            })));
                }).onErrorMap(RootTransactionException.class,error->failures.resolve(error,window::budget)).flatMap(view->{window.budget().remaining();return authorization.reauthorize(admission).timeout(window.budget().remaining())
                    .then(Mono.defer(()->{window.budget().remaining();return Mono.just(view);}));});
            }).timeout(request.remaining());
        })).onErrorMap(ProductDefinitionCommandErrors::safe);
    }

    private Mono<ProductDefinitionCostView> readLegacy(UUID tenantId, UUID rootId) {
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
        return ProductDefinitionPrivateReadSnapshots.head(persistence, tenant, root, backend)
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

package io.fireflysoftware.core.product.core.services.definition.impl;

import static io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionPublicationSnapshots.*;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionPublicationReadAuthorization.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.ReadObservation;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationDiscoveryView;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationReadinessView;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionDraftRoot;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionPublicationReadRows.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionPublicationReadNativeRepository.ReadContext;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionPublicationReadPersistence;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

public final class ProductDefinitionPublicationReadServiceImpl
        implements ProductDefinitionPublicationReadService {
    private final ProductDefinitionPublicationReadAuthorization authorization;
    private final ProductDefinitionPublicationReadPersistence persistence;

    private final Clock clock;
    private final LongSupplier nanoTime;

    public ProductDefinitionPublicationReadServiceImpl(
            ProductDefinitionPublicationReadAuthorization authorization,
            ProductDefinitionPublicationReadPersistence persistence) {
        this(authorization, persistence, Clock.systemUTC());
    }

    public ProductDefinitionPublicationReadServiceImpl(
            ProductDefinitionPublicationReadAuthorization authorization,
            ProductDefinitionPublicationReadPersistence persistence,
            Clock clock) {
        this(authorization, persistence, clock, System::nanoTime);
    }

    ProductDefinitionPublicationReadServiceImpl(
            ProductDefinitionPublicationReadAuthorization authorization,
            ProductDefinitionPublicationReadPersistence persistence,
            Clock clock,
            LongSupplier nanoTime) {
        this.authorization = Objects.requireNonNull(authorization);
        this.persistence = Objects.requireNonNull(persistence);
        this.clock = Objects.requireNonNull(clock);
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }

    public Mono<ProductDefinitionPublicationReadinessView> readiness(UUID tenantId, UUID rootId) {
        var scope = new Scope(tenantId, rootId, Target.READINESS);
        return read(scope, observation -> readySnapshot(scope, observation));
    }

    public Mono<ProductDefinitionPublicationDiscoveryView> discover(UUID tenantId, UUID rootId) {
        var scope = new Scope(tenantId, rootId, Target.DISCOVERY);
        return read(scope, observation -> discoverySnapshot(scope, observation));
    }

    private record AdmittedView<T>(
            T view,
            Admission admission,
            Instant nativeUntil,
            long startNanos,
            long lifetimeNanos) {}

    private <T> Mono<T> read(Scope scope, Function<ReadObservation, Mono<T>> snapshot) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(
                        () ->
                                authorization
                                        .authorize(scope)
                                        .flatMap(
                                                admission -> {
                                                    require(
                                                            admission != null
                                                                    && scope.equals(
                                                                            admission.scope())
                                                                    && admission.principal()
                                                                            != null);
                                                    return Mono.defer(
                                                            () -> {
                                                                long started = nanoTime.getAsLong();
                                                                return persistence
                                                                        .readTransactions()
                                                                        .execute(
                                                                                status ->
                                                                                        open(admission)
                                                                                                .flatMap(
                                                                                                        context -> {
                                                                                                            var
                                                                                                                    observation =
                                                                                                                            observation(
                                                                                                                                    context,
                                                                                                                                    admission);
                                                                                                            var
                                                                                                                    effective =
                                                                                                                            new Admission(
                                                                                                                                    admission
                                                                                                                                            .principal(),
                                                                                                                                    admission
                                                                                                                                            .scope(),
                                                                                                                                    admission
                                                                                                                                            .actorExpiresAtEpochMillis(),
                                                                                                                                    Math
                                                                                                                                            .min(
                                                                                                                                                    admission
                                                                                                                                                            .validUntilEpochMillis(),
                                                                                                                                                    context.admissionValidUntil()
                                                                                                                                                            .toEpochMilli()));
                                                                                                            long
                                                                                                                    lifetime =
                                                                                                                            Duration
                                                                                                                                    .between(
                                                                                                                                            context
                                                                                                                                                    .recordedAt(),
                                                                                                                                            context
                                                                                                                                                    .admissionValidUntil())
                                                                                                                                    .toNanos();
                                                                                                            return live(
                                                                                                                            context
                                                                                                                                    .contextId(),
                                                                                                                            scope
                                                                                                                                    .target())
                                                                                                                    .then(
                                                                                                                            Mono
                                                                                                                                    .defer(
                                                                                                                                            () ->
                                                                                                                                                    snapshot
                                                                                                                                                            .apply(
                                                                                                                                                                    observation)))
                                                                                                                    .flatMap(
                                                                                                                            view ->
                                                                                                                                    live(
                                                                                                                                                    context
                                                                                                                                                            .contextId(),
                                                                                                                                                    scope
                                                                                                                                                            .target())
                                                                                                                                            .thenReturn(
                                                                                                                                                    new AdmittedView<>(
                                                                                                                                                            view,
                                                                                                                                                            effective,
                                                                                                                                                            context
                                                                                                                                                                    .admissionValidUntil(),
                                                                                                                                                            started,
                                                                                                                                                            lifetime)));
                                                                                                        }))
                                                                        .single()
                                                                        .flatMap(
                                                                                result ->
                                                                                        Mono.defer(
                                                                                                () -> {
                                                                                                    current(
                                                                                                            result);
                                                                                                    return authorization
                                                                                                            .reauthorize(
                                                                                                                    result
                                                                                                                            .admission())
                                                                                                            .then(
                                                                                                                    Mono
                                                                                                                            .defer(
                                                                                                                                    () -> {
                                                                                                                                        current(
                                                                                                                                                result);
                                                                                                                                        return Mono
                                                                                                                                                .just(
                                                                                                                                                        result
                                                                                                                                                                .view());
                                                                                                                                    }));
                                                                                                }));
                                                            });
                                                }))
                .onErrorMap(ProductDefinitionCommandErrors::safe);
    }

    private void current(AdmittedView<?> result) {
        long elapsed = nanoTime.getAsLong() - result.startNanos();
        Instant now = clock.instant();
        // The elapsed cap begins before native admission, so clock skew cannot renew its lifetime.
        if (!now.isBefore(result.nativeUntil())
                || !now.isBefore(
                        Instant.ofEpochMilli(result.admission().actorExpiresAtEpochMillis()))
                || elapsed < 0
                || elapsed >= result.lifetimeNanos()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current permission required");
        }
    }

    private Mono<ReadContext> open(Admission admission) {
        var scope = admission.scope();
        var actor = admission.principal();
        var nativeReads = persistence.publicationReadNative();
        return required(
                scope.target() == Target.READINESS
                        ? nativeReads.openReadiness(
                                scope.tenantId(),
                                scope.rootId(),
                                actor.issuer(),
                                actor.subject(),
                                admission.actorExpiresAtEpochMillis(),
                                admission.validUntilEpochMillis(),
                                ProductDefinitionCommandAuthorization.POLICY_PROFILE)
                        : nativeReads.openDiscovery(
                                scope.tenantId(),
                                scope.rootId(),
                                actor.issuer(),
                                actor.subject(),
                                admission.actorExpiresAtEpochMillis(),
                                admission.validUntilEpochMillis(),
                                ProductDefinitionCommandAuthorization.POLICY_PROFILE));
    }

    private static ReadObservation observation(ReadContext context, Admission admission) {
        require(
                context != null
                        && id(context.contextId())
                        && context.recordedAt() != null
                        && context.admissionValidUntil() != null
                        && context.admissionValidUntil().isAfter(context.recordedAt())
                        && !context.admissionValidUntil()
                                .isAfter(context.recordedAt().plusSeconds(30))
                        && !context.admissionValidUntil()
                                .isAfter(
                                        Instant.ofEpochMilli(admission.actorExpiresAtEpochMillis()))
                        && !context.admissionValidUntil()
                                .isAfter(Instant.ofEpochMilli(admission.validUntilEpochMillis())));
        return new ReadObservation(context.recordedAt(), context.admissionValidUntil());
    }

    private Mono<Void> live(UUID context, Target target) {
        var reads = persistence.publicationReadNative();
        return required(
                        target == Target.READINESS
                                ? reads.isReadinessCurrent(context)
                                : reads.isDiscoveryCurrent(context))
                .flatMap(
                        value ->
                                Boolean.TRUE.equals(value)
                                        ? Mono.empty()
                                        : Mono.error(unavailable()));
    }

    private Mono<ProductDefinitionDraftRoot> root(Scope scope) {
        return persistence
                .drafts()
                .findByTenantIdAndRootId(scope.tenantId(), scope.rootId())
                .switchIfEmpty(
                        Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Product definition not found")))
                .doOnNext(header -> header(scope.tenantId(), scope.rootId(), header));
    }

    private record ApprovalRows(
            ReviewMetadata review,
            ReviewCostIdentity identity,
            DecisionMetadata decision,
            SubmissionMetadata submission) {}

    private Mono<ApprovalRows> approval(Scope scope, UUID reviewId) {
        require(id(reviewId));
        var tenant = scope.tenantId();
        var root = scope.rootId();
        var rows = persistence.publicationReads();
        return required(
                        rows.reviewMetadata()
                                .findByTenantIdAndRootIdAndReviewId(tenant, root, reviewId))
                .flatMap(
                        review ->
                                required(
                                                rows.reviewCostIdentity()
                                                        .findByTenantIdAndRootIdAndReviewId(
                                                                tenant, root, reviewId))
                                        .flatMap(
                                                identity ->
                                                        required(
                                                                        rows.decisionMetadata()
                                                                                .findByTenantIdAndRootIdAndReviewId(
                                                                                        tenant,
                                                                                        root,
                                                                                        reviewId))
                                                                .flatMap(
                                                                        decision -> {
                                                                            require(
                                                                                    id(
                                                                                            decision
                                                                                                    .submissionId()));
                                                                            return required(
                                                                                            rows.submissionMetadata()
                                                                                                    .findByTenantIdAndRootIdAndSubmissionId(
                                                                                                            tenant,
                                                                                                            root,
                                                                                                            decision
                                                                                                                    .submissionId()))
                                                                                    .map(
                                                                                            submission ->
                                                                                                    new ApprovalRows(
                                                                                                            review,
                                                                                                            identity,
                                                                                                            decision,
                                                                                                            submission));
                                                                        })));
    }

    private Mono<ProductDefinitionPublicationReadinessView> readySnapshot(
            Scope scope, ReadObservation observation) {
        var tenant = scope.tenantId();
        var root = scope.rootId();
        var rows = persistence.publicationReads();
        return root(scope)
                .flatMap(
                        header -> {
                            if (!"IN_REVIEW".equals(header.phase())
                                    || header.activeReviewId() == null)
                                return Mono.just(
                                        ProductDefinitionPublicationSnapshots.readiness(
                                                tenant,
                                                root,
                                                header,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                observation));
                            return required(
                                            rows.revisionMetadata()
                                                    .findByTenantIdAndRootIdAndRevisionId(
                                                            tenant, root, header.publicHead()))
                                    .flatMap(
                                            revision ->
                                                    approval(scope, header.activeReviewId())
                                                            .flatMap(
                                                                    approved ->
                                                                            required(
                                                                                            persistence
                                                                                                    .costs()
                                                                                                    .findByTenantIdAndRootId(
                                                                                                            tenant,
                                                                                                            root))
                                                                                    .map(
                                                                                            control ->
                                                                                                    ProductDefinitionPublicationSnapshots
                                                                                                            .readiness(
                                                                                                                    tenant,
                                                                                                                    root,
                                                                                                                    header,
                                                                                                                    revision,
                                                                                                                    approved
                                                                                                                            .review(),
                                                                                                                    approved
                                                                                                                            .identity(),
                                                                                                                    approved
                                                                                                                            .decision(),
                                                                                                                    approved
                                                                                                                            .submission(),
                                                                                                                    control,
                                                                                                                    observation))));
                        });
    }

    private Mono<ProductDefinitionPublicationDiscoveryView> discoverySnapshot(
            Scope scope, ReadObservation observation) {
        return root(scope)
                .flatMap(
                        header ->
                                persistence
                                        .publicationReads()
                                        .publishHistory()
                                        .findTop2ByTenantIdAndRootIdAndEventTypeOrderByPublicStateVersionDescHistoryIdDesc(
                                                scope.tenantId(), scope.rootId(), "PUBLISH")
                                        .collectList()
                                        .flatMap(
                                                history ->
                                                        discoverFromHistory(
                                                                scope,
                                                                header,
                                                                history,
                                                                observation)));
    }

    private Mono<ProductDefinitionPublicationDiscoveryView> discoverFromHistory(
            Scope scope,
            ProductDefinitionDraftRoot header,
            List<PublishHistory> history,
            ReadObservation observation) {
        var tenant = scope.tenantId();
        var root = scope.rootId();
        var rows = persistence.publicationReads();
        var latest = latestPublish(tenant, root, header, history);
        return persistence
                .lifecyclePublicationControlRepository()
                .findTop2ByTenantIdAndRootIdAndState(tenant, root, "ACTIVE")
                .collectList()
                .flatMap(
                        active -> {
                            require(active.size() <= 1);
                            if (latest.isEmpty())
                                return required(
                                                rows.publicationMetadata()
                                                        .existsByTenantIdAndRootId(tenant, root))
                                        .map(
                                                exists ->
                                                        discovery(
                                                                tenant,
                                                                root,
                                                                header,
                                                                latest,
                                                                active,
                                                                exists,
                                                                null,
                                                                observation));
                            var selected = latest.get();
                            return required(
                                            rows.publicPublishAction()
                                                    .findByTenantIdAndRootIdAndActionId(
                                                            tenant, root, selected.actionId()))
                                    .flatMap(
                                            action -> {
                                                var receipt =
                                                        publishReceipt(
                                                                tenant, root, header, selected,
                                                                action);
                                                return required(
                                                                rows.publicationMetadata()
                                                                        .findByTenantIdAndRootIdAndPublicationId(
                                                                                tenant,
                                                                                root,
                                                                                receipt
                                                                                        .publicationId()))
                                                        .flatMap(
                                                                publication ->
                                                                        publicationEvidence(
                                                                                scope,
                                                                                selected,
                                                                                action,
                                                                                publication))
                                                        .map(
                                                                evidence ->
                                                                        discovery(
                                                                                tenant,
                                                                                root,
                                                                                header,
                                                                                latest,
                                                                                active,
                                                                                true,
                                                                                evidence,
                                                                                observation));
                                            });
                        });
    }

    private Mono<PublicationEvidence> publicationEvidence(
            Scope scope,
            PublishHistory history,
            PublicPublishAction action,
            PublicationMetadata publication) {
        require(id(publication.publicationId()) && id(publication.reviewId()));
        var tenant = scope.tenantId();
        var root = scope.rootId();
        return required(
                        persistence
                                .lifecyclePublicationControlRepository()
                                .findByTenantIdAndRootIdAndPublicationId(
                                        tenant, root, publication.publicationId()))
                .flatMap(
                        control ->
                                required(
                                                persistence
                                                        .lifecycleVersionIdentityRepository()
                                                        .findByTenantIdAndRootIdAndPublicationId(
                                                                tenant,
                                                                root,
                                                                publication.publicationId()))
                                        .flatMap(
                                                identity ->
                                                        approval(scope, publication.reviewId())
                                                                .map(
                                                                        approved ->
                                                                                new PublicationEvidence(
                                                                                        history,
                                                                                        action,
                                                                                        publication,
                                                                                        identity,
                                                                                        control,
                                                                                        approved
                                                                                                .review(),
                                                                                        approved
                                                                                                .identity(),
                                                                                        approved
                                                                                                .decision(),
                                                                                        approved
                                                                                                .submission()))));
    }

    private static <T> Mono<T> required(Mono<T> value) {
        return value.switchIfEmpty(Mono.error(unavailable()));
    }
}

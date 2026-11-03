package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleContracts.Selection;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionLifecycleRows.Submission;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandPersistence;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.*;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.fireflyframework.core.transaction.RootTransactionException;
import java.util.*;

public final class ProductDefinitionReviewReadServiceImpl
        implements ProductDefinitionReviewReadService {
    private final ProductDefinitionReviewAuthorization authorization;
    private final ProductDefinitionCommandPersistence persistence;
    private final GovernedDefinitionProfile backend;
    private final Clock clock;
    private final LongSupplier nanos;
    private final boolean boundedMode;

    public ProductDefinitionReviewReadServiceImpl(ProductDefinitionReviewAuthorization authorization,ProductDefinitionCommandPersistence persistence) {
        this(authorization,persistence,GovernedDefinitionProfile.ACCOUNT_TARIFF,Clock.systemUTC(),System::nanoTime,false);
    }
    public ProductDefinitionReviewReadServiceImpl(ProductDefinitionReviewAuthorization authorization,ProductDefinitionCommandPersistence persistence,
            GovernedDefinitionProfile backend,Clock clock,LongSupplier nanos) {
        this(authorization,persistence,backend,clock,nanos,true);
    }
    private ProductDefinitionReviewReadServiceImpl(ProductDefinitionReviewAuthorization authorization,ProductDefinitionCommandPersistence persistence,
            GovernedDefinitionProfile backend,Clock clock,LongSupplier nanos,boolean boundedMode) {
        this.authorization=Objects.requireNonNull(authorization);this.persistence=Objects.requireNonNull(persistence);
        this.backend=Objects.requireNonNull(backend);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);this.boundedMode=boundedMode;
        if(boundedMode && (authorization.definitionProfile()!=backend || !persistence.boundedTransactions()))throw new IllegalArgumentException("Matching bounded product backend required");
    }

    public Mono<ProductDefinitionReviewSelectionView> readSelection(
            UUID tenant, UUID root, UUID submission) {
        return read(new ProductDefinitionReviewAuthorization.Scope(tenant, root, submission));
    }

    public Mono<ProductDefinitionReviewSelectionView> readCurrentSelection(UUID tenant, UUID root) {
        return read(ProductDefinitionReviewAuthorization.Scope.current(tenant, root));
    }

    private Mono<ProductDefinitionReviewSelectionView> read(ProductDefinitionReviewAuthorization.Scope scope) {
        return boundedMode?readBounded(scope):readLegacy(scope);
    }
    private Mono<ProductDefinitionReviewSelectionView> readBounded(ProductDefinitionReviewAuthorization.Scope requested) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(()->Mono.defer(()->{
            Instant observed=clock.instant();long before=nanos.getAsLong();
            var request=R2dbcOperationBudget.observed(observed,observed.plusSeconds(30),before,Duration.ofSeconds(30),clock,nanos);
            return authorization.authorize(requested).switchIfEmpty(Mono.error(unavailable())).flatMap(admission->{
                require(requested.equals(admission.scope()));var actor=admission.principal();
                var window=ProductDefinitionLifecycleWindow.start(observed,before,admission.actorExpiresAtEpochMillis(),admission.validUntilEpochMillis(),clock,nanos);
                var failures=new ProductDefinitionBoundedErrors();
                return persistence.executeBounded(window.budget(),true,scope->{
                    long nativeBefore=nanos.getAsLong();var nativeReads=persistence.reviewReadNative();
                    var opened=requested.target()==ProductDefinitionReviewAuthorization.Target.CURRENT
                        ?nativeReads.openCurrent(requested.tenantId(),requested.rootId(),actor.issuer(),actor.subject(),admission.actorExpiresAtEpochMillis(),admission.validUntilEpochMillis(),backend.policyProfile())
                        :nativeReads.open(requested.tenantId(),requested.rootId(),requested.submissionId(),actor.issuer(),actor.subject(),admission.actorExpiresAtEpochMillis(),admission.validUntilEpochMillis(),backend.policyProfile());
                    return failures.capture(opened.switchIfEmpty(Mono.error(unavailable())).flatMap(context->nativeReads.admission(context.contextId())
                        .switchIfEmpty(Mono.error(unavailable())).flatMap(stamp->{
                            require(context.contextId()!=null && stamp.recordedAt()!=null && stamp.recordedAt().equals(context.recordedAt()) && stamp.dbNowMillis()!=null && stamp.admissionUntilMillis()!=null);
                            scope.narrow(window.observe(stamp.recordedAt(),stamp.dbNowMillis(),stamp.admissionUntilMillis(),nativeBefore));
                            return live(context.contextId(),requested.target()).then(Mono.defer(()->snapshot(requested,context.recordedAt())))
                                .flatMap(view->live(context.contextId(),requested.target()).thenReturn(view));
                        })));
                }).onErrorMap(RootTransactionException.class,error->failures.resolve(error,window::budget)).flatMap(view->{window.budget().remaining();return authorization.reauthorize(admission).timeout(window.budget().remaining())
                    .then(Mono.defer(()->{window.budget().remaining();return Mono.just(view);}));});
            }).timeout(request.remaining());
        })).onErrorMap(ProductDefinitionCommandErrors::safe);
    }
    private Mono<ProductDefinitionReviewSelectionView> readLegacy(
            ProductDefinitionReviewAuthorization.Scope scope) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(
                        () ->
                                authorization
                                        .authorize(scope)
                                        .flatMap(
                                                admission -> {
                                                    require(scope.equals(admission.scope()));
                                                    var actor = admission.principal();
                                                    var nativeReads =
                                                            persistence.reviewReadNative();
                                                    return persistence
                                                            .readTransactions()
                                                            .execute(
                                                                    status -> {
                                                                        var opened =
                                                                                scope.target()
                                                                                                == ProductDefinitionReviewAuthorization
                                                                                                        .Target
                                                                                                        .CURRENT
                                                                                        ? nativeReads
                                                                                                .openCurrent(
                                                                                                        scope
                                                                                                                .tenantId(),
                                                                                                        scope
                                                                                                                .rootId(),
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
                                                                                        : nativeReads
                                                                                                .open(
                                                                                                        scope
                                                                                                                .tenantId(),
                                                                                                        scope
                                                                                                                .rootId(),
                                                                                                        scope
                                                                                                                .submissionId(),
                                                                                                        actor
                                                                                                                .issuer(),
                                                                                                        actor
                                                                                                                .subject(),
                                                                                                        admission
                                                                                                                .actorExpiresAtEpochMillis(),
                                                                                                        admission
                                                                                                                .validUntilEpochMillis(),
                                                                                                        ProductDefinitionCommandAuthorization
                                                                                                                .POLICY_PROFILE);
                                                                        return opened.switchIfEmpty(
                                                                                        Mono.error(
                                                                                                unavailable()))
                                                                                .flatMap(
                                                                                        context -> {
                                                                                            require(
                                                                                                    context
                                                                                                                            .contextId()
                                                                                                                    != null
                                                                                                            && context
                                                                                                                            .recordedAt()
                                                                                                                    != null);
                                                                                            return live(
                                                                                                            context
                                                                                                                    .contextId(),
                                                                                                            scope
                                                                                                                    .target())
                                                                                                    .then(
                                                                                                            Mono
                                                                                                                    .defer(
                                                                                                                            () ->
                                                                                                                                    snapshot(
                                                                                                                                            scope,
                                                                                                                                            context
                                                                                                                                                    .recordedAt())))
                                                                                                    .flatMap(
                                                                                                            view ->
                                                                                                                    live(
                                                                                                                                    context
                                                                                                                                            .contextId(),
                                                                                                                                    scope
                                                                                                                                            .target())
                                                                                                                            .thenReturn(
                                                                                                                                    view));
                                                                                        });
                                                                    })
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

    private Mono<ProductDefinitionReviewSelectionView> snapshot(
            ProductDefinitionReviewAuthorization.Scope scope, Instant time) {
        var tenant = scope.tenantId();
        var root = scope.rootId();
        return ProductDefinitionPrivateReadSnapshots.head(persistence, tenant, root, backend)
                .flatMap(
                        head -> {
                            if (!"IN_REVIEW".equals(head.root().phase())) throw conflict();
                            Mono<Submission> selected =
                                    scope.target()
                                                    == ProductDefinitionReviewAuthorization.Target
                                                            .CURRENT
                                            ? persistence
                                                    .lifecycleSubmissionRepository()
                                                    .findTop2ByTenantIdAndRootIdAndRevisionIdAndPublicVersion(
                                                            tenant,
                                                            root,
                                                            head.revision().revisionId(),
                                                            head.root().publicStateVersion())
                                                    .singleOrEmpty()
                                            : persistence
                                                    .lifecycleSubmissionRepository()
                                                    .findByTenantIdAndRootIdAndSubmissionId(
                                                            tenant, root, scope.submissionId());
                            return selected.switchIfEmpty(Mono.error(notFound()))
                                    .flatMap(
                                            submission -> {
                                                require(
                                                        submission.tenantId().equals(tenant)
                                                                && submission.rootId().equals(root)
                                                                && submission.submissionId() != null
                                                                && !submission
                                                                        .submissionId()
                                                                        .equals(new UUID(0, 0)));
                                                if (scope.target()
                                                        == ProductDefinitionReviewAuthorization
                                                                .Target.EXACT)
                                                    require(
                                                            submission
                                                                    .submissionId()
                                                                    .equals(scope.submissionId()));
                                                if (!submission
                                                                .revisionId()
                                                                .equals(
                                                                        head.revision()
                                                                                .revisionId())
                                                        || !submission
                                                                .publicVersion()
                                                                .equals(
                                                                        head.root()
                                                                                .publicStateVersion())
                                                        || !submission
                                                                .publicDigest()
                                                                .equals(
                                                                        head.revision()
                                                                                .publicDigest()))
                                                    throw conflict();
                                                return ProductDefinitionPrivateReadSnapshots.cost(
                                                                persistence, head)
                                                        .map(
                                                                cost -> {
                                                                    var selection =
                                                                            new Selection(
                                                                                    tenant,
                                                                                    root,
                                                                                    submission
                                                                                            .submissionId(),
                                                                                    head.revision()
                                                                                            .revisionId(),
                                                                                    head.revision()
                                                                                            .publicDigest(),
                                                                                    head.root()
                                                                                            .publicStateVersion(),
                                                                                    cost.revision()
                                                                                            .costRevisionId(),
                                                                                    cost.revision()
                                                                                            .costDigest(),
                                                                                    cost.control()
                                                                                            .costStateVersion());
                                                                    String fingerprint =
                                                                            DefinitionCanonical
                                                                                    .hash(
                                                                                            "product-definition-selection-v1",
                                                                                            DefinitionCanonical
                                                                                                    .encode(
                                                                                                            DefinitionCanonical
                                                                                                                    .tree(
                                                                                                                            selection)));
                                                                    return new ProductDefinitionReviewSelectionView(
                                                                            selection,
                                                                            fingerprint,
                                                                            head.definition(),
                                                                            cost.terms(),
                                                                            cost.compatibility(),
                                                                            "TRUSTED_SERVICE",
                                                                            time,
                                                                            head.root()
                                                                                    .activeReviewId());
                                                                });
                                            });
                        });
    }

    private Mono<Void> live(UUID id, ProductDefinitionReviewAuthorization.Target target) {
        var value =
                target == ProductDefinitionReviewAuthorization.Target.CURRENT
                        ? persistence.reviewReadNative().isCurrentSelectionLive(id)
                        : persistence.reviewReadNative().isCurrent(id);
        return value.filter(Boolean.TRUE::equals).switchIfEmpty(Mono.error(unavailable())).then();
    }

    private static void require(boolean value) {
        if (!value) throw unavailable();
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Product review selection unavailable");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Product review selection not found");
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Product review selection changed");
    }
}

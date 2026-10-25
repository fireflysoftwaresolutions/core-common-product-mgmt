package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionCommandAuthorization.Admission;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.*;
import io.fireflysoftware.core.product.models.entities.definition.*;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.*;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionLifecycleRows.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandNativeRepository.NativeAdmission;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;

public final class ProductDefinitionLifecycleServiceImpl
        implements ProductDefinitionLifecycleService {
    private final ProductDefinitionCommandAuthorization authorization;
    private final ProductDefinitionCommandPersistence persistence;

    private record Frame(
            Parsed parsed,
            Admission admission,
            NativeAdmission nativeAdmission,
            ProductDefinitionCommandResources resources) {}

    private record Effect(Receipt receipt, UUID costEventReference, String selectionFingerprint) {}

    public ProductDefinitionLifecycleServiceImpl(
            ProductDefinitionCommandAuthorization authorization,
            ProductDefinitionCommandPersistence persistence) {
        this.authorization = Objects.requireNonNull(authorization);
        this.persistence = Objects.requireNonNull(persistence);
    }

    public Mono<Receipt> execute(
            UUID tenantId, UUID rootId, ProductDefinitionCommand command, String body) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(
                        () ->
                                Mono.fromSupplier(() -> parse(tenantId, rootId, command, body))
                                        .flatMap(
                                                parsed -> {
                                                    var instruction = parsed.instruction();
                                                    var scope = instruction.scope();
                                                    var requested =
                                                            new ProductDefinitionCommandAuthorization
                                                                    .Scope(
                                                                    scope.tenantId(),
                                                                    scope.rootId(),
                                                                    scope.operationId(),
                                                                    command,
                                                                    instruction.targetId());
                                                    return authorization
                                                            .authorize(requested)
                                                            .flatMap(
                                                                    admission -> {
                                                                        require(
                                                                                requested.equals(
                                                                                        admission
                                                                                                .scope()));
                                                                        return transaction(
                                                                                        parsed,
                                                                                        admission)
                                                                                .flatMap(
                                                                                        receipt ->
                                                                                                authorization
                                                                                                        .reauthorize(
                                                                                                                admission)
                                                                                                        .onErrorMap(
                                                                                                                error ->
                                                                                                                        new ResponseStatusException(
                                                                                                                                HttpStatus
                                                                                                                                        .SERVICE_UNAVAILABLE,
                                                                                                                                "Product"
                                                                                                                                    + " action"
                                                                                                                                    + " receipt"
                                                                                                                                    + " unavailable;"
                                                                                                                                    + " retry"
                                                                                                                                    + " the same"
                                                                                                                                    + " operation"
                                                                                                                                    + " under"
                                                                                                                                    + " current"
                                                                                                                                    + " permission"))
                                                                                                        .thenReturn(
                                                                                                                receipt));
                                                                    });
                                                }))
                .onErrorMap(ProductDefinitionLifecycleServiceImpl::safe);
    }

    private static Parsed parse(
            UUID tenant, UUID root, ProductDefinitionCommand command, String body) {
        try {
            return ProductDefinitionInstructionCodec.parse(tenant, root, command, body);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Exact product command required");
        }
    }

    private Mono<Receipt> transaction(Parsed parsed, Admission admission) {
        var resources = ProductDefinitionCommandResources.fresh(parsed.instruction().command());
        var scope = parsed.instruction().scope();
        return persistence
                .transactions()
                .execute(
                        status ->
                                persistence
                                        .productDefinitionCommandNativeRepository()
                                        .begin(
                                                scope.tenantId(),
                                                scope.rootId(),
                                                scope.operationId(),
                                                parsed.instruction().command().name(),
                                                admission.principal().issuer(),
                                                admission.principal().subject(),
                                                admission.actorExpiresAtEpochMillis(),
                                                admission.validUntilEpochMillis(),
                                                ProductDefinitionCommandAuthorization
                                                        .POLICY_PROFILE,
                                                parsed.canonicalIntent(),
                                                parsed.intentDigest(),
                                                ProductDefinitionCommandPlan.encode(
                                                        parsed, resources))
                                        .switchIfEmpty(Mono.error(unavailable()))
                                        .flatMap(
                                                nativeAdmission -> {
                                                    validateNative(nativeAdmission, resources);
                                                    var frame =
                                                            new Frame(
                                                                    parsed,
                                                                    admission,
                                                                    nativeAdmission,
                                                                    resources);
                                                    return truth(
                                                                    persistence
                                                                            .productDefinitionCommandNativeRepository()
                                                                            .isCurrent(
                                                                                    nativeAdmission
                                                                                            .commandId()))
                                                            .then(
                                                                    Mono.defer(
                                                                            () ->
                                                                                    Boolean.TRUE
                                                                                                    .equals(
                                                                                                            nativeAdmission
                                                                                                                    .replay())
                                                                                            ? replay(
                                                                                                    frame)
                                                                                            : fresh(
                                                                                                    frame)))
                                                            .flatMap(
                                                                    receipt ->
                                                                            truth(
                                                                                            persistence
                                                                                                    .productDefinitionCommandNativeRepository()
                                                                                                    .finish(
                                                                                                            nativeAdmission
                                                                                                                    .commandId()))
                                                                                    .thenReturn(
                                                                                            receipt));
                                                }))
                .single();
    }

    private static void validateNative(
            NativeAdmission value, ProductDefinitionCommandResources resources) {
        require(
                value != null
                        && value.commandId() != null
                        && value.replay() != null
                        && value.commandRecordedAt() != null
                        && value.actionId() != null);
        if (Boolean.TRUE.equals(value.replay())) require(value.retainedActionRecordedAt() != null);
        else
            require(
                    value.retainedActionRecordedAt() == null
                            && resources.actionId().equals(value.actionId()));
    }

    private Mono<Receipt> replay(Frame frame) {
        var scope = frame.parsed().instruction().scope();
        var id = frame.nativeAdmission().actionId();
        return persistence
                .lifecycleActionRepository()
                .findByTenantIdAndRootIdAndActionId(scope.tenantId(), scope.rootId(), id)
                .switchIfEmpty(Mono.error(unavailable()))
                .flatMap(
                        action ->
                                persistence
                                        .lifecycleActionPrivateRepository()
                                        .findByTenantIdAndRootIdAndActionId(
                                                scope.tenantId(), scope.rootId(), id)
                                        .switchIfEmpty(Mono.error(unavailable()))
                                        .map(
                                                original -> {
                                                    require(
                                                            action.tenantId()
                                                                            .equals(
                                                                                    scope
                                                                                            .tenantId())
                                                                    && action.rootId()
                                                                            .equals(scope.rootId())
                                                                    && action.actionId()
                                                                            .equals(id));
                                                    require(
                                                            original.tenantId()
                                                                            .equals(
                                                                                    scope
                                                                                            .tenantId())
                                                                    && original.rootId()
                                                                            .equals(scope.rootId())
                                                                    && original.actionId()
                                                                            .equals(id)
                                                                    && original.requestFingerprint()
                                                                            .equals(
                                                                                    frame.parsed()
                                                                                            .intentDigest())
                                                                    && original.canonicalInstruction()
                                                                            .equals(
                                                                                    frame.parsed()
                                                                                            .canonicalIntent()));
                                                    require(
                                                            action.command()
                                                                            .equals(
                                                                                    frame.parsed()
                                                                                            .instruction()
                                                                                            .command()
                                                                                            .name())
                                                                    && action.createdAt()
                                                                            .equals(
                                                                                    frame.nativeAdmission()
                                                                                            .retainedActionRecordedAt()));
                                                    var receipt =
                                                            ProductDefinitionCommandReceiptCodec
                                                                    .decode(
                                                                            action
                                                                                    .canonicalReceipt(),
                                                                            frame.parsed()
                                                                                    .instruction()
                                                                                    .command());
                                                    require(
                                                            receipt.scope().equals(scope)
                                                                    && receipt.actionId().equals(id)
                                                                    && receipt.recordedAt()
                                                                            .equals(
                                                                                    action.createdAt()
                                                                                            .toString()));
                                                    require(
                                                            action.visibility()
                                                                    .equals(
                                                                            ProductDefinitionCommandReceiptCodec
                                                                                            .privateCommand(
                                                                                                    receipt
                                                                                                            .command())
                                                                                    ? "PRIVATE"
                                                                                    : "PUBLIC"));
                                                    return receipt;
                                                }));
    }

    private Mono<Receipt> fresh(Frame frame) {
        return switch (frame.parsed().instruction().command()) {
            case CREATE_DRAFT -> create(frame).flatMap(effect -> evidence(frame, effect));
            case EDIT_PUBLIC -> editPublic(frame).flatMap(effect -> evidence(frame, effect));
            case EDIT_COST -> editCost(frame).flatMap(effect -> evidence(frame, effect));
            case SUBMIT_PUBLIC -> submit(frame).flatMap(effect -> evidence(frame, effect));
            case APPROVE_SELECTION, REJECT_SELECTION ->
                    review(frame).flatMap(effect -> evidence(frame, effect));
            case PUBLISH -> publish(frame).flatMap(effect -> evidence(frame, effect));
            case WITHDRAW_PUBLICATION -> withdraw(frame).flatMap(effect -> evidence(frame, effect));
        };
    }

    private Mono<Effect> create(Frame frame) {
        var request = (CreateDraftBody) frame.parsed().instruction().body();
        var scope = frame.parsed().instruction().scope();
        var ids = frame.resources();
        var prepared =
                ProductDefinitionLifecycleRules.prepare(
                        new ProductDefinitionLifecycleContracts.CreateDraft(
                                scope,
                                request.productId(),
                                request.categoryId(),
                                request.definition(),
                                request.cost()));
        var common = request.definition().common();
        DefinitionRules.text(common.name(), 255, "/definition/common/name");
        var time = frame.nativeAdmission().commandRecordedAt();
        var technical = LocalDateTime.ofInstant(time, ZoneOffset.UTC);
        var actor = frame.admission().principal();
        var product =
                new ProductBirth(
                        request.productId(),
                        scope.tenantId(),
                        request.categoryId(),
                        io.fireflysoftware.core.product.interfaces.enums.ProductTypeEnum.FINANCIAL,
                        common.name(),
                        common.code(),
                        common.description(),
                        io.fireflysoftware.core.product.interfaces.enums.ProductStatusEnum.DRAFT,
                        LocalDateTime.ofInstant(
                                Instant.parse(common.effectiveFrom()), ZoneOffset.UTC),
                        common.effectiveTo() == null
                                ? null
                                : LocalDateTime.ofInstant(
                                        Instant.parse(common.effectiveTo()), ZoneOffset.UTC),
                        technical,
                        technical,
                        io.r2dbc.postgresql.codec.Json.of("[]"));
        var root =
                new ProductDefinitionDraftRoot(
                        scope.rootId(),
                        scope.tenantId(),
                        request.productId(),
                        common.code(),
                        "BANKING",
                        "ACCOUNT_TARIFF_V1",
                        1,
                        "DRAFT",
                        0,
                        ids.revisionId(),
                        null,
                        time);
        var revision =
                new ProductDefinitionDraftRevision(
                        ids.revisionId(),
                        scope.tenantId(),
                        scope.rootId(),
                        prepared.canonicalDefinition(),
                        prepared.publicDigest(),
                        DefinitionCanonical.ENCODING,
                        null,
                        null,
                        actor.issuer(),
                        actor.subject(),
                        time);
        var cost =
                new ProductDefinitionCostRevision(
                        ids.costRevisionId(),
                        scope.tenantId(),
                        scope.rootId(),
                        prepared.canonicalCost(),
                        prepared.costDigest(),
                        DefinitionCanonical.ENCODING,
                        actor.issuer(),
                        actor.subject(),
                        time);
        var control =
                new ProductDefinitionCostControl(
                        scope.rootId(), scope.tenantId(), ids.costRevisionId(), 0);
        var receipt =
                new PublicReceipt(
                        scope,
                        ProductDefinitionCommand.CREATE_DRAFT,
                        ids.actionId(),
                        time.toString(),
                        "DRAFT",
                        0,
                        ids.revisionId(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "NOT_ADMITTED");
        var selected =
                selection(
                        "tenantId",
                        scope.tenantId(),
                        "rootId",
                        scope.rootId(),
                        "revisionId",
                        ids.revisionId(),
                        "publicDigest",
                        prepared.publicDigest(),
                        "publicVersion",
                        0,
                        "costRevisionId",
                        ids.costRevisionId(),
                        "costDigest",
                        prepared.costDigest(),
                        "costVersion",
                        0);
        return insert(product)
                .then(insert(root))
                .then(insert(revision))
                .then(insert(cost))
                .then(insert(control))
                .thenReturn(new Effect(receipt, ids.costRevisionId(), selected));
    }

    private Mono<ProductDefinitionDraftRoot> root(Frame frame) {
        var scope = frame.parsed().instruction().scope();
        return persistence
                .drafts()
                .findByTenantIdAndRootId(scope.tenantId(), scope.rootId())
                .switchIfEmpty(
                        Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Product definition not found")))
                .map(
                        row -> {
                            require(
                                    row.tenantId().equals(scope.tenantId())
                                            && row.rootId().equals(scope.rootId())
                                            && row.productId() != null
                                            && "BANKING".equals(row.businessArea())
                                            && "ACCOUNT_TARIFF_V1".equals(row.profileCode())
                                            && Integer.valueOf(1).equals(row.profileVersion())
                                            && row.publicHead() != null
                                            && row.publicStateVersion() != null
                                            && row.publicStateVersion() >= 0
                                            && Set.of("DRAFT", "IN_REVIEW", "PUBLISHED")
                                                    .contains(row.phase()));
                            return row;
                        });
    }

    private Mono<ProductDefinitionDraftRevision> revision(
            ProductDefinitionDraftRoot root, UUID id) {
        return persistence
                .lifecycleRevisionRepository()
                .findByTenantIdAndRootIdAndRevisionId(root.tenantId(), root.rootId(), id)
                .switchIfEmpty(Mono.error(unavailable()))
                .map(
                        row -> {
                            require(
                                    row.tenantId().equals(root.tenantId())
                                            && row.rootId().equals(root.rootId())
                                            && row.revisionId().equals(id)
                                            && DefinitionCanonical.ENCODING.equals(
                                                    row.encodingVersion()));
                            var canonical = DefinitionProfiles.canonical(row.canonicalDefinition());
                            require(
                                    canonical.json().equals(row.canonicalDefinition())
                                            && canonical.digest().equals(row.publicDigest())
                                            && root.productCode()
                                                    .equals(
                                                            DefinitionProfiles.decode(
                                                                            canonical.json())
                                                                    .common()
                                                                    .code())
                                            && "BANKING"
                                                    .equals(
                                                            DefinitionProfiles.decode(
                                                                            canonical.json())
                                                                    .common()
                                                                    .businessArea())
                                            && "ACCOUNT_TARIFF_V1"
                                                    .equals(
                                                            DefinitionProfiles.decode(
                                                                            canonical.json())
                                                                    .common()
                                                                    .profileCode()));
                            return row;
                        });
    }

    private static void expectedPublic(
            ProductDefinitionDraftRoot root, Integer version, UUID head) {
        if (!root.publicStateVersion().equals(version) || !root.publicHead().equals(head))
            throw conflict();
    }

    private static int increment(int value) {
        try {
            return ProductDefinitionLifecycleRules.nextVersion(value);
        } catch (IllegalArgumentException exhausted) {
            throw conflict();
        }
    }

    private Mono<Effect> editPublic(Frame frame) {
        var request = (EditPublicBody) frame.parsed().instruction().body();
        var scope = frame.parsed().instruction().scope();
        var ids = frame.resources();
        return root(frame)
                .flatMap(
                        root -> {
                            expectedPublic(
                                    root,
                                    request.expectedPublicVersion(),
                                    request.expectedPublicHead());
                            var definition =
                                    DefinitionProfiles.canonical(
                                            DefinitionCanonical.tree(request.definition())
                                                    .toString());
                            if (!root.productCode().equals(request.definition().common().code()))
                                throw conflict();
                            int version = increment(root.publicStateVersion());
                            String phase =
                                    ProductDefinitionLifecycleRules.phaseAfter(
                                                    ProductDefinitionLifecycleContracts.Phase
                                                            .valueOf(root.phase()),
                                                    ProductDefinitionLifecycleContracts
                                                            .PublicCommand.EDIT_PUBLIC)
                                            .name();
                            Mono<Void> source = Mono.empty();
                            if (request.sourcePublicationId() != null)
                                source =
                                        persistence
                                                .lifecyclePublicationRepository()
                                                .findByTenantIdAndRootIdAndPublicationId(
                                                        scope.tenantId(),
                                                        scope.rootId(),
                                                        request.sourcePublicationId())
                                                .switchIfEmpty(Mono.error(conflict()))
                                                .flatMap(
                                                        value -> {
                                                            if (!value.tenantId()
                                                                            .equals(
                                                                                    scope
                                                                                            .tenantId())
                                                                    || !value.rootId()
                                                                            .equals(scope.rootId())
                                                                    || !value.publicationId()
                                                                            .equals(
                                                                                    request
                                                                                            .sourcePublicationId())
                                                                    || !value.publicDigest()
                                                                            .equals(
                                                                                    request
                                                                                            .sourcePublicationDigest()))
                                                                throw conflict();
                                                            return Mono.empty();
                                                        });
                            var actor = frame.admission().principal();
                            var time = frame.nativeAdmission().commandRecordedAt();
                            var next =
                                    new ProductDefinitionDraftRevision(
                                            ids.revisionId(),
                                            scope.tenantId(),
                                            scope.rootId(),
                                            definition.json(),
                                            definition.digest(),
                                            DefinitionCanonical.ENCODING,
                                            request.sourcePublicationId(),
                                            request.sourcePublicationDigest(),
                                            actor.issuer(),
                                            actor.subject(),
                                            time);
                            var receipt =
                                    new PublicReceipt(
                                            scope,
                                            ProductDefinitionCommand.EDIT_PUBLIC,
                                            ids.actionId(),
                                            time.toString(),
                                            phase,
                                            version,
                                            ids.revisionId(),
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            "NOT_ADMITTED");
                            return source.then(insert(next))
                                    .then(
                                            persistence
                                                    .drafts()
                                                    .replacePublicHead(
                                                            new ProductDefinitionDraftWrites
                                                                    .PublicHeadChange(
                                                                    scope.tenantId(),
                                                                    scope.rootId(),
                                                                    root.publicHead(),
                                                                    root.publicStateVersion(),
                                                                    root.phase(),
                                                                    ids.revisionId(),
                                                                    phase)))
                                    .thenReturn(
                                            new Effect(
                                                    receipt,
                                                    null,
                                                    selection(
                                                            "tenantId",
                                                            scope.tenantId(),
                                                            "rootId",
                                                            scope.rootId(),
                                                            "revisionId",
                                                            ids.revisionId(),
                                                            "publicDigest",
                                                            definition.digest(),
                                                            "publicVersion",
                                                            version)));
                        });
    }

    private Mono<ProductDefinitionCostControl> costControl(ProductDefinitionDraftRoot root) {
        return persistence
                .costs()
                .findByTenantIdAndRootId(root.tenantId(), root.rootId())
                .switchIfEmpty(Mono.error(unavailable()))
                .map(
                        value -> {
                            require(
                                    value.tenantId().equals(root.tenantId())
                                            && value.rootId().equals(root.rootId())
                                            && value.costHead() != null
                                            && value.costStateVersion() != null
                                            && value.costStateVersion() >= 0);
                            return value;
                        });
    }

    private Mono<Effect> editCost(Frame frame) {
        var request = (EditCostBody) frame.parsed().instruction().body();
        var scope = frame.parsed().instruction().scope();
        var ids = frame.resources();
        return root(frame)
                .flatMap(
                        root -> {
                            if (!root.publicHead().equals(request.publicRevisionId()))
                                throw conflict();
                            return revision(root, root.publicHead())
                                    .flatMap(
                                            publicRevision -> {
                                                var requested =
                                                        DefinitionProfiles.canonical(
                                                                DefinitionCanonical.tree(
                                                                                request
                                                                                        .publicDefinition())
                                                                        .toString());
                                                if (!request.publicDigest()
                                                                .equals(
                                                                        publicRevision
                                                                                .publicDigest())
                                                        || !requested
                                                                .json()
                                                                .equals(
                                                                        publicRevision
                                                                                .canonicalDefinition()))
                                                    throw conflict();
                                                return costControl(root)
                                                        .flatMap(
                                                                control -> {
                                                                    if (!control.costHead()
                                                                                    .equals(
                                                                                            request
                                                                                                    .expectedCostHead())
                                                                            || !control.costStateVersion()
                                                                                    .equals(
                                                                                            request
                                                                                                    .expectedCostVersion()))
                                                                        throw conflict();
                                                                    int version =
                                                                            increment(
                                                                                    control
                                                                                            .costStateVersion());
                                                                    var cost =
                                                                            CostRules.canonical(
                                                                                    DefinitionCanonical
                                                                                            .tree(
                                                                                                    request
                                                                                                            .cost())
                                                                                            .toString(),
                                                                                    publicRevision
                                                                                            .canonicalDefinition());
                                                                    var actor =
                                                                            frame.admission()
                                                                                    .principal();
                                                                    var time =
                                                                            frame.nativeAdmission()
                                                                                    .commandRecordedAt();
                                                                    var row =
                                                                            new ProductDefinitionCostRevision(
                                                                                    ids
                                                                                            .costRevisionId(),
                                                                                    scope
                                                                                            .tenantId(),
                                                                                    scope.rootId(),
                                                                                    cost.json(),
                                                                                    cost.digest(),
                                                                                    DefinitionCanonical
                                                                                            .ENCODING,
                                                                                    actor.issuer(),
                                                                                    actor.subject(),
                                                                                    time);
                                                                    var receipt =
                                                                            new PrivateReceipt(
                                                                                    scope,
                                                                                    ProductDefinitionCommand
                                                                                            .EDIT_COST,
                                                                                    ids.actionId(),
                                                                                    time.toString(),
                                                                                    ids
                                                                                            .costRevisionId(),
                                                                                    version,
                                                                                    null,
                                                                                    "EDITED");
                                                                    return insert(row)
                                                                            .then(
                                                                                    persistence
                                                                                            .drafts()
                                                                                            .replaceCostHead(
                                                                                                    new ProductDefinitionDraftWrites
                                                                                                            .CostHeadChange(
                                                                                                            scope
                                                                                                                    .tenantId(),
                                                                                                            scope
                                                                                                                    .rootId(),
                                                                                                            control
                                                                                                                    .costHead(),
                                                                                                            control
                                                                                                                    .costStateVersion(),
                                                                                                            ids
                                                                                                                    .costRevisionId())))
                                                                            .then(
                                                                                    persistence
                                                                                            .drafts()
                                                                                            .replaceActiveReview(
                                                                                                    new ProductDefinitionDraftWrites
                                                                                                            .ActiveReviewChange(
                                                                                                            scope
                                                                                                                    .tenantId(),
                                                                                                            scope
                                                                                                                    .rootId(),
                                                                                                            root
                                                                                                                    .publicHead(),
                                                                                                            root
                                                                                                                    .publicStateVersion(),
                                                                                                            root
                                                                                                                    .activeReviewId(),
                                                                                                            null)))
                                                                            .thenReturn(
                                                                                    new Effect(
                                                                                            receipt,
                                                                                            ids
                                                                                                    .costRevisionId(),
                                                                                            selection(
                                                                                                    "tenantId",
                                                                                                    scope
                                                                                                            .tenantId(),
                                                                                                    "rootId",
                                                                                                    scope
                                                                                                            .rootId(),
                                                                                                    "revisionId",
                                                                                                    publicRevision
                                                                                                            .revisionId(),
                                                                                                    "publicDigest",
                                                                                                    publicRevision
                                                                                                            .publicDigest(),
                                                                                                    "publicVersion",
                                                                                                    root
                                                                                                            .publicStateVersion(),
                                                                                                    "costRevisionId",
                                                                                                    ids
                                                                                                            .costRevisionId(),
                                                                                                    "costDigest",
                                                                                                    cost
                                                                                                            .digest(),
                                                                                                    "costVersion",
                                                                                                    version)));
                                                                });
                                            });
                        });
    }

    private Mono<Effect> submit(Frame frame) {
        var request = (SubmitPublicBody) frame.parsed().instruction().body();
        var scope = frame.parsed().instruction().scope();
        var ids = frame.resources();
        return root(frame)
                .flatMap(
                        root -> {
                            expectedPublic(
                                    root,
                                    request.expectedPublicVersion(),
                                    request.expectedPublicHead());
                            if (!"DRAFT".equals(root.phase())) throw conflict();
                            int version = increment(root.publicStateVersion());
                            return revision(root, root.publicHead())
                                    .flatMap(
                                            revision -> {
                                                var actor = frame.admission().principal();
                                                var time =
                                                        frame.nativeAdmission().commandRecordedAt();
                                                var row =
                                                        new Submission(
                                                                ids.submissionId(),
                                                                scope.tenantId(),
                                                                scope.rootId(),
                                                                revision.revisionId(),
                                                                revision.publicDigest(),
                                                                version,
                                                                actor.issuer(),
                                                                actor.subject(),
                                                                time);
                                                var receipt =
                                                        new PublicReceipt(
                                                                scope,
                                                                ProductDefinitionCommand
                                                                        .SUBMIT_PUBLIC,
                                                                ids.actionId(),
                                                                time.toString(),
                                                                "IN_REVIEW",
                                                                version,
                                                                revision.revisionId(),
                                                                null,
                                                                null,
                                                                null,
                                                                null,
                                                                null,
                                                                "NOT_ADMITTED");
                                                return insert(row)
                                                        .then(
                                                                persistence
                                                                        .drafts()
                                                                        .replacePublicHead(
                                                                                new ProductDefinitionDraftWrites
                                                                                        .PublicHeadChange(
                                                                                        scope
                                                                                                .tenantId(),
                                                                                        scope
                                                                                                .rootId(),
                                                                                        root
                                                                                                .publicHead(),
                                                                                        root
                                                                                                .publicStateVersion(),
                                                                                        root
                                                                                                .phase(),
                                                                                        root
                                                                                                .publicHead(),
                                                                                        "IN_REVIEW")))
                                                        .thenReturn(
                                                                new Effect(
                                                                        receipt,
                                                                        null,
                                                                        selection(
                                                                                "tenantId",
                                                                                scope.tenantId(),
                                                                                "rootId",
                                                                                scope.rootId(),
                                                                                "submissionId",
                                                                                ids.submissionId(),
                                                                                "revisionId",
                                                                                revision
                                                                                        .revisionId(),
                                                                                "publicDigest",
                                                                                revision
                                                                                        .publicDigest(),
                                                                                "publicVersion",
                                                                                version)));
                                            });
                        });
    }

    private Mono<ProductDefinitionCostRevision> costRevision(
            ProductDefinitionDraftRoot root, UUID id) {
        return persistence
                .lifecycleCostRevisionRepository()
                .findByTenantIdAndRootIdAndCostRevisionId(root.tenantId(), root.rootId(), id)
                .switchIfEmpty(Mono.error(unavailable()))
                .map(
                        row -> {
                            require(
                                    row.tenantId().equals(root.tenantId())
                                            && row.rootId().equals(root.rootId())
                                            && row.costRevisionId().equals(id)
                                            && DefinitionCanonical.ENCODING.equals(
                                                    row.encodingVersion()));
                            var typed =
                                    DefinitionCanonical.read(
                                            DefinitionCanonical.parse(row.canonicalCost()),
                                            CostTerms.class);
                            require(
                                    DefinitionCanonical.encode(DefinitionCanonical.tree(typed))
                                                    .equals(row.canonicalCost())
                                            && DefinitionCanonical.hash(
                                                            "product-definition-private-cost-v1",
                                                            row.canonicalCost())
                                                    .equals(row.costDigest()));
                            return row;
                        });
    }

    private Mono<Set<ProductDefinitionLifecycleContracts.Actor>> contributors(
            ProductDefinitionDraftRoot root) {
        var publicActors =
                persistence
                        .lifecycleRevisionRepository()
                        .findDistinctTop513ByTenantIdAndRootId(root.tenantId(), root.rootId())
                        .map(
                                value ->
                                        new ProductDefinitionLifecycleContracts.Actor(
                                                value.getActorIssuer(), value.getActorId()));
        var privateActors =
                persistence
                        .lifecycleCostRevisionRepository()
                        .findDistinctTop513ByTenantIdAndRootId(root.tenantId(), root.rootId())
                        .map(
                                value ->
                                        new ProductDefinitionLifecycleContracts.Actor(
                                                value.getActorIssuer(), value.getActorId()));
        var submitters =
                persistence
                        .lifecycleSubmissionRepository()
                        .findDistinctTop513ByTenantIdAndRootId(root.tenantId(), root.rootId())
                        .map(
                                value ->
                                        new ProductDefinitionLifecycleContracts.Actor(
                                                value.getActorIssuer(), value.getActorId()));
        return Flux.concat(publicActors, privateActors, submitters)
                .collect(
                        () -> new HashSet<ProductDefinitionLifecycleContracts.Actor>(),
                        (actors, actor) -> {
                            actors.add(actor);
                            if (actors.size() > 512) throw conflict();
                        })
                .map(Set::copyOf);
    }

    private Mono<Effect> review(Frame frame) {
        var request = (ReviewSelectionBody) frame.parsed().instruction().body();
        var scope = frame.parsed().instruction().scope();
        var ids = frame.resources();
        boolean approve =
                frame.parsed().instruction().command()
                        == ProductDefinitionCommand.APPROVE_SELECTION;
        return root(frame)
                .flatMap(
                        root -> {
                            if (!"IN_REVIEW".equals(root.phase())) throw conflict();
                            expectedPublic(root, request.publicVersion(), request.revisionId());
                            return revision(root, root.publicHead())
                                    .flatMap(
                                            publicRevision ->
                                                    costControl(root)
                                                            .flatMap(
                                                                    control ->
                                                                            costRevision(
                                                                                            root,
                                                                                            control
                                                                                                    .costHead())
                                                                                    .flatMap(
                                                                                            cost ->
                                                                                                    persistence
                                                                                                            .lifecycleSubmissionRepository()
                                                                                                            .findByTenantIdAndRootIdAndSubmissionId(
                                                                                                                    scope
                                                                                                                            .tenantId(),
                                                                                                                    scope
                                                                                                                            .rootId(),
                                                                                                                    request
                                                                                                                            .submissionId())
                                                                                                            .switchIfEmpty(
                                                                                                                    Mono
                                                                                                                            .error(
                                                                                                                                    conflict()))
                                                                                                            .flatMap(
                                                                                                                    submission -> {
                                                                                                                        require(
                                                                                                                                submission
                                                                                                                                                .tenantId()
                                                                                                                                                .equals(
                                                                                                                                                        scope
                                                                                                                                                                .tenantId())
                                                                                                                                        && submission
                                                                                                                                                .rootId()
                                                                                                                                                .equals(
                                                                                                                                                        scope
                                                                                                                                                                .rootId())
                                                                                                                                        && submission
                                                                                                                                                .submissionId()
                                                                                                                                                .equals(
                                                                                                                                                        request
                                                                                                                                                                .submissionId()));
                                                                                                                        var
                                                                                                                                current =
                                                                                                                                        new ProductDefinitionLifecycleContracts
                                                                                                                                                .Selection(
                                                                                                                                                scope
                                                                                                                                                        .tenantId(),
                                                                                                                                                scope
                                                                                                                                                        .rootId(),
                                                                                                                                                submission
                                                                                                                                                        .submissionId(),
                                                                                                                                                publicRevision
                                                                                                                                                        .revisionId(),
                                                                                                                                                publicRevision
                                                                                                                                                        .publicDigest(),
                                                                                                                                                root
                                                                                                                                                        .publicStateVersion(),
                                                                                                                                                cost
                                                                                                                                                        .costRevisionId(),
                                                                                                                                                cost
                                                                                                                                                        .costDigest(),
                                                                                                                                                control
                                                                                                                                                        .costStateVersion());
                                                                                                                        var
                                                                                                                                selected =
                                                                                                                                        new ProductDefinitionLifecycleContracts
                                                                                                                                                .Selection(
                                                                                                                                                scope
                                                                                                                                                        .tenantId(),
                                                                                                                                                scope
                                                                                                                                                        .rootId(),
                                                                                                                                                request
                                                                                                                                                        .submissionId(),
                                                                                                                                                request
                                                                                                                                                        .revisionId(),
                                                                                                                                                request
                                                                                                                                                        .publicDigest(),
                                                                                                                                                request
                                                                                                                                                        .publicVersion(),
                                                                                                                                                request
                                                                                                                                                        .costRevisionId(),
                                                                                                                                                request
                                                                                                                                                        .costDigest(),
                                                                                                                                                request
                                                                                                                                                        .costVersion());
                                                                                                                        try {
                                                                                                                            ProductDefinitionLifecycleRules
                                                                                                                                    .requireSelection(
                                                                                                                                            selected,
                                                                                                                                            current);
                                                                                                                        } catch (
                                                                                                                                IllegalArgumentException
                                                                                                                                        stale) {
                                                                                                                            throw conflict();
                                                                                                                        }
                                                                                                                        if (!submission
                                                                                                                                        .revisionId()
                                                                                                                                        .equals(
                                                                                                                                                publicRevision
                                                                                                                                                        .revisionId())
                                                                                                                                || !submission
                                                                                                                                        .publicDigest()
                                                                                                                                        .equals(
                                                                                                                                                publicRevision
                                                                                                                                                        .publicDigest())
                                                                                                                                || !submission
                                                                                                                                        .publicVersion()
                                                                                                                                        .equals(
                                                                                                                                                root
                                                                                                                                                        .publicStateVersion()))
                                                                                                                            throw conflict();
                                                                                                                        if (approve) {
                                                                                                                            var
                                                                                                                                    canonical =
                                                                                                                                            CostRules
                                                                                                                                                    .canonical(
                                                                                                                                                            cost
                                                                                                                                                                    .canonicalCost(),
                                                                                                                                                            publicRevision
                                                                                                                                                                    .canonicalDefinition());
                                                                                                                            require(
                                                                                                                                    canonical
                                                                                                                                                    .json()
                                                                                                                                                    .equals(
                                                                                                                                                            cost
                                                                                                                                                                    .canonicalCost())
                                                                                                                                            && canonical
                                                                                                                                                    .digest()
                                                                                                                                                    .equals(
                                                                                                                                                            cost
                                                                                                                                                                    .costDigest()));
                                                                                                                        }
                                                                                                                        return contributors(
                                                                                                                                        root)
                                                                                                                                .flatMap(
                                                                                                                                        contributors -> {
                                                                                                                                            var
                                                                                                                                                    actor =
                                                                                                                                                            frame.admission()
                                                                                                                                                                    .principal();
                                                                                                                                            try {
                                                                                                                                                ProductDefinitionLifecycleRules
                                                                                                                                                        .requireIndependent(
                                                                                                                                                                new ProductDefinitionLifecycleContracts
                                                                                                                                                                        .Actor(
                                                                                                                                                                        actor
                                                                                                                                                                                .issuer(),
                                                                                                                                                                        actor
                                                                                                                                                                                .subject()),
                                                                                                                                                                contributors);
                                                                                                                                            } catch (
                                                                                                                                                    IllegalArgumentException
                                                                                                                                                            dependent) {
                                                                                                                                                throw conflict();
                                                                                                                                            }
                                                                                                                                            var
                                                                                                                                                    time =
                                                                                                                                                            frame.nativeAdmission()
                                                                                                                                                                    .commandRecordedAt();
                                                                                                                                            String
                                                                                                                                                    decision =
                                                                                                                                                            approve
                                                                                                                                                                    ? "APPROVED"
                                                                                                                                                                    : "REJECTED";
                                                                                                                                            String
                                                                                                                                                    fingerprint =
                                                                                                                                                            DefinitionCanonical
                                                                                                                                                                    .hash(
                                                                                                                                                                            "product-definition-selection-v1",
                                                                                                                                                                            DefinitionCanonical
                                                                                                                                                                                    .encode(
                                                                                                                                                                                            DefinitionCanonical
                                                                                                                                                                                                    .tree(
                                                                                                                                                                                                            current)));
                                                                                                                                            if (!fingerprint
                                                                                                                                                    .equals(
                                                                                                                                                            request
                                                                                                                                                                    .selectionFingerprint()))
                                                                                                                                                throw conflict();
                                                                                                                                            var
                                                                                                                                                    review =
                                                                                                                                                            new Review(
                                                                                                                                                                    ids
                                                                                                                                                                            .reviewId(),
                                                                                                                                                                    scope
                                                                                                                                                                            .tenantId(),
                                                                                                                                                                    scope
                                                                                                                                                                            .rootId(),
                                                                                                                                                                    publicRevision
                                                                                                                                                                            .revisionId(),
                                                                                                                                                                    publicRevision
                                                                                                                                                                            .publicDigest(),
                                                                                                                                                                    actor
                                                                                                                                                                            .issuer(),
                                                                                                                                                                    actor
                                                                                                                                                                            .subject(),
                                                                                                                                                                    time);
                                                                                                                                            var
                                                                                                                                                    privateReview =
                                                                                                                                                            new ReviewPrivate(
                                                                                                                                                                    ids
                                                                                                                                                                            .reviewId(),
                                                                                                                                                                    scope
                                                                                                                                                                            .tenantId(),
                                                                                                                                                                    scope
                                                                                                                                                                            .rootId(),
                                                                                                                                                                    cost
                                                                                                                                                                            .costRevisionId(),
                                                                                                                                                                    fingerprint);
                                                                                                                                            var
                                                                                                                                                    outcome =
                                                                                                                                                            new Decision(
                                                                                                                                                                    ids
                                                                                                                                                                            .reviewId(),
                                                                                                                                                                    scope
                                                                                                                                                                            .tenantId(),
                                                                                                                                                                    scope
                                                                                                                                                                            .rootId(),
                                                                                                                                                                    submission
                                                                                                                                                                            .submissionId(),
                                                                                                                                                                    root
                                                                                                                                                                            .publicStateVersion(),
                                                                                                                                                                    control
                                                                                                                                                                            .costStateVersion(),
                                                                                                                                                                    decision,
                                                                                                                                                                    request
                                                                                                                                                                            .reason(),
                                                                                                                                                                    actor
                                                                                                                                                                            .issuer(),
                                                                                                                                                                    actor
                                                                                                                                                                            .subject(),
                                                                                                                                                                    time);
                                                                                                                                            var
                                                                                                                                                    receipt =
                                                                                                                                                            new PrivateReceipt(
                                                                                                                                                                    scope,
                                                                                                                                                                    frame.parsed()
                                                                                                                                                                            .instruction()
                                                                                                                                                                            .command(),
                                                                                                                                                                    ids
                                                                                                                                                                            .actionId(),
                                                                                                                                                                    time
                                                                                                                                                                            .toString(),
                                                                                                                                                                    cost
                                                                                                                                                                            .costRevisionId(),
                                                                                                                                                                    control
                                                                                                                                                                            .costStateVersion(),
                                                                                                                                                                    ids
                                                                                                                                                                            .reviewId(),
                                                                                                                                                                    decision);
                                                                                                                                            return insert(
                                                                                                                                                            review)
                                                                                                                                                    .then(
                                                                                                                                                            insert(
                                                                                                                                                                    privateReview))
                                                                                                                                                    .then(
                                                                                                                                                            insert(
                                                                                                                                                                    outcome))
                                                                                                                                                    .then(
                                                                                                                                                            persistence
                                                                                                                                                                    .drafts()
                                                                                                                                                                    .replaceActiveReview(
                                                                                                                                                                            new ProductDefinitionDraftWrites
                                                                                                                                                                                    .ActiveReviewChange(
                                                                                                                                                                                    scope
                                                                                                                                                                                            .tenantId(),
                                                                                                                                                                                    scope
                                                                                                                                                                                            .rootId(),
                                                                                                                                                                                    root
                                                                                                                                                                                            .publicHead(),
                                                                                                                                                                                    root
                                                                                                                                                                                            .publicStateVersion(),
                                                                                                                                                                                    root
                                                                                                                                                                                            .activeReviewId(),
                                                                                                                                                                                    approve
                                                                                                                                                                                            ? ids
                                                                                                                                                                                                    .reviewId()
                                                                                                                                                                                            : null)))
                                                                                                                                                    .thenReturn(
                                                                                                                                                            new Effect(
                                                                                                                                                                    receipt,
                                                                                                                                                                    cost
                                                                                                                                                                            .costRevisionId(),
                                                                                                                                                                    fingerprint));
                                                                                                                                        });
                                                                                                                    }))));
                        });
    }

    private record Approved(
            Review review, ReviewPrivate privateReview, Decision decision, Submission submission) {}

    private Mono<Approved> approved(
            ProductDefinitionDraftRoot root,
            ProductDefinitionDraftRevision revision,
            ProductDefinitionCostControl control,
            ProductDefinitionCostRevision cost,
            UUID id) {
        return persistence
                .lifecycleReviewRepository()
                .findByTenantIdAndRootIdAndReviewId(root.tenantId(), root.rootId(), id)
                .switchIfEmpty(Mono.error(conflict()))
                .flatMap(
                        review ->
                                persistence
                                        .lifecycleReviewPrivateRepository()
                                        .findByTenantIdAndRootIdAndReviewId(
                                                root.tenantId(), root.rootId(), id)
                                        .switchIfEmpty(Mono.error(conflict()))
                                        .flatMap(
                                                privateReview ->
                                                        persistence
                                                                .lifecycleDecisionRepository()
                                                                .findByTenantIdAndRootIdAndReviewId(
                                                                        root.tenantId(),
                                                                        root.rootId(),
                                                                        id)
                                                                .switchIfEmpty(
                                                                        Mono.error(conflict()))
                                                                .flatMap(
                                                                        decision ->
                                                                                persistence
                                                                                        .lifecycleSubmissionRepository()
                                                                                        .findByTenantIdAndRootIdAndSubmissionId(
                                                                                                root
                                                                                                        .tenantId(),
                                                                                                root
                                                                                                        .rootId(),
                                                                                                decision
                                                                                                        .submissionId())
                                                                                        .switchIfEmpty(
                                                                                                Mono
                                                                                                        .error(
                                                                                                                conflict()))
                                                                                        .flatMap(
                                                                                                submission -> {
                                                                                                    require(
                                                                                                            review.tenantId()
                                                                                                                            .equals(
                                                                                                                                    root
                                                                                                                                            .tenantId())
                                                                                                                    && review.rootId()
                                                                                                                            .equals(
                                                                                                                                    root
                                                                                                                                            .rootId())
                                                                                                                    && review.reviewId()
                                                                                                                            .equals(
                                                                                                                                    id)
                                                                                                                    && privateReview
                                                                                                                            .tenantId()
                                                                                                                            .equals(
                                                                                                                                    root
                                                                                                                                            .tenantId())
                                                                                                                    && privateReview
                                                                                                                            .rootId()
                                                                                                                            .equals(
                                                                                                                                    root
                                                                                                                                            .rootId())
                                                                                                                    && privateReview
                                                                                                                            .reviewId()
                                                                                                                            .equals(
                                                                                                                                    id)
                                                                                                                    && decision.tenantId()
                                                                                                                            .equals(
                                                                                                                                    root
                                                                                                                                            .tenantId())
                                                                                                                    && decision.rootId()
                                                                                                                            .equals(
                                                                                                                                    root
                                                                                                                                            .rootId())
                                                                                                                    && decision.reviewId()
                                                                                                                            .equals(
                                                                                                                                    id)
                                                                                                                    && submission
                                                                                                                            .tenantId()
                                                                                                                            .equals(
                                                                                                                                    root
                                                                                                                                            .tenantId())
                                                                                                                    && submission
                                                                                                                            .rootId()
                                                                                                                            .equals(
                                                                                                                                    root
                                                                                                                                            .rootId())
                                                                                                                    && submission
                                                                                                                            .submissionId()
                                                                                                                            .equals(
                                                                                                                                    decision
                                                                                                                                            .submissionId()));
                                                                                                    if (!"APPROVED"
                                                                                                                    .equals(
                                                                                                                            decision
                                                                                                                                    .state())
                                                                                                            || !review.revisionId()
                                                                                                                    .equals(
                                                                                                                            revision
                                                                                                                                    .revisionId())
                                                                                                            || !review.publicDigest()
                                                                                                                    .equals(
                                                                                                                            revision
                                                                                                                                    .publicDigest())
                                                                                                            || !privateReview
                                                                                                                    .costRevisionId()
                                                                                                                    .equals(
                                                                                                                            cost
                                                                                                                                    .costRevisionId())
                                                                                                            || !decision.publicVersion()
                                                                                                                    .equals(
                                                                                                                            root
                                                                                                                                    .publicStateVersion())
                                                                                                            || !decision.costVersion()
                                                                                                                    .equals(
                                                                                                                            control
                                                                                                                                    .costStateVersion())
                                                                                                            || !submission
                                                                                                                    .revisionId()
                                                                                                                    .equals(
                                                                                                                            revision
                                                                                                                                    .revisionId())
                                                                                                            || !submission
                                                                                                                    .publicDigest()
                                                                                                                    .equals(
                                                                                                                            revision
                                                                                                                                    .publicDigest())
                                                                                                            || !submission
                                                                                                                    .publicVersion()
                                                                                                                    .equals(
                                                                                                                            root
                                                                                                                                    .publicStateVersion()))
                                                                                                        throw conflict();
                                                                                                    require(
                                                                                                            decision.actorIssuer()
                                                                                                                            .equals(
                                                                                                                                    review
                                                                                                                                            .actorIssuer())
                                                                                                                    && decision.actorId()
                                                                                                                            .equals(
                                                                                                                                    review
                                                                                                                                            .actorId())
                                                                                                                    && decision.createdAt()
                                                                                                                            .equals(
                                                                                                                                    review
                                                                                                                                            .createdAt()));
                                                                                                    var
                                                                                                            selected =
                                                                                                                    new ProductDefinitionLifecycleContracts
                                                                                                                            .Selection(
                                                                                                                            root
                                                                                                                                    .tenantId(),
                                                                                                                            root
                                                                                                                                    .rootId(),
                                                                                                                            submission
                                                                                                                                    .submissionId(),
                                                                                                                            revision
                                                                                                                                    .revisionId(),
                                                                                                                            revision
                                                                                                                                    .publicDigest(),
                                                                                                                            root
                                                                                                                                    .publicStateVersion(),
                                                                                                                            cost
                                                                                                                                    .costRevisionId(),
                                                                                                                            cost
                                                                                                                                    .costDigest(),
                                                                                                                            control
                                                                                                                                    .costStateVersion());
                                                                                                    String
                                                                                                            fingerprint =
                                                                                                                    DefinitionCanonical
                                                                                                                            .hash(
                                                                                                                                    "product-definition-selection-v1",
                                                                                                                                    DefinitionCanonical
                                                                                                                                            .encode(
                                                                                                                                                    DefinitionCanonical
                                                                                                                                                            .tree(
                                                                                                                                                                    selected)));
                                                                                                    require(
                                                                                                            fingerprint
                                                                                                                    .equals(
                                                                                                                            privateReview
                                                                                                                                    .selectionFingerprint()));
                                                                                                    var
                                                                                                            canonical =
                                                                                                                    CostRules
                                                                                                                            .canonical(
                                                                                                                                    cost
                                                                                                                                            .canonicalCost(),
                                                                                                                                    revision
                                                                                                                                            .canonicalDefinition());
                                                                                                    require(
                                                                                                            canonical
                                                                                                                            .json()
                                                                                                                            .equals(
                                                                                                                                    cost
                                                                                                                                            .canonicalCost())
                                                                                                                    && canonical
                                                                                                                            .digest()
                                                                                                                            .equals(
                                                                                                                                    cost
                                                                                                                                            .costDigest()));
                                                                                                    return contributors(
                                                                                                                    root)
                                                                                                            .map(
                                                                                                                    contributors -> {
                                                                                                                        try {
                                                                                                                            ProductDefinitionLifecycleRules
                                                                                                                                    .requireIndependent(
                                                                                                                                            new ProductDefinitionLifecycleContracts
                                                                                                                                                    .Actor(
                                                                                                                                                    review
                                                                                                                                                            .actorIssuer(),
                                                                                                                                                    review
                                                                                                                                                            .actorId()),
                                                                                                                                            contributors);
                                                                                                                        } catch (
                                                                                                                                IllegalArgumentException
                                                                                                                                        dependent) {
                                                                                                                            throw conflict();
                                                                                                                        }
                                                                                                                        return new Approved(
                                                                                                                                review,
                                                                                                                                privateReview,
                                                                                                                                decision,
                                                                                                                                submission);
                                                                                                                    });
                                                                                                }))));
    }

    private Mono<Effect> publish(Frame frame) {
        var request = (PublishBody) frame.parsed().instruction().body();
        var scope = frame.parsed().instruction().scope();
        var ids = frame.resources();
        return root(frame)
                .flatMap(
                        root -> {
                            if (!"IN_REVIEW".equals(root.phase())
                                    || !Objects.equals(root.activeReviewId(), request.reviewId())
                                    || !root.publicStateVersion()
                                            .equals(request.expectedPublicVersion()))
                                throw conflict();
                            int version = increment(root.publicStateVersion());
                            return revision(root, root.publicHead())
                                    .flatMap(
                                            revision ->
                                                    costControl(root)
                                                            .flatMap(
                                                                    control -> {
                                                                        if (!control.costStateVersion()
                                                                                .equals(
                                                                                        request
                                                                                                .expectedCostVersion()))
                                                                            throw conflict();
                                                                        return costRevision(
                                                                                        root,
                                                                                        control
                                                                                                .costHead())
                                                                                .flatMap(
                                                                                        cost ->
                                                                                                approved(
                                                                                                                root,
                                                                                                                revision,
                                                                                                                control,
                                                                                                                cost,
                                                                                                                request
                                                                                                                        .reviewId())
                                                                                                        .flatMap(
                                                                                                                approved ->
                                                                                                                        persistence
                                                                                                                                .lifecyclePublicationControlRepository()
                                                                                                                                .countByTenantIdAndRootIdAndState(
                                                                                                                                        scope
                                                                                                                                                .tenantId(),
                                                                                                                                        scope
                                                                                                                                                .rootId(),
                                                                                                                                        "ACTIVE")
                                                                                                                                .switchIfEmpty(
                                                                                                                                        Mono
                                                                                                                                                .error(
                                                                                                                                                        unavailable()))
                                                                                                                                .flatMap(
                                                                                                                                        count -> {
                                                                                                                                            require(
                                                                                                                                                    count
                                                                                                                                                            >= 0);
                                                                                                                                            if (count
                                                                                                                                                    != 0)
                                                                                                                                                throw conflict();
                                                                                                                                            return persistence
                                                                                                                                                    .lifecycleVersionIdentityRepository()
                                                                                                                                                    .existsByTenantIdAndRootIdAndProductIdAndVersionNumber(
                                                                                                                                                            scope
                                                                                                                                                                    .tenantId(),
                                                                                                                                                            scope
                                                                                                                                                                    .rootId(),
                                                                                                                                                            root
                                                                                                                                                                    .productId(),
                                                                                                                                                            request
                                                                                                                                                                    .versionNumber())
                                                                                                                                                    .switchIfEmpty(
                                                                                                                                                            Mono
                                                                                                                                                                    .error(
                                                                                                                                                                            unavailable()))
                                                                                                                                                    .flatMap(
                                                                                                                                                            used -> {
                                                                                                                                                                if (used)
                                                                                                                                                                    throw conflict();
                                                                                                                                                                var
                                                                                                                                                                        time =
                                                                                                                                                                                frame.nativeAdmission()
                                                                                                                                                                                        .commandRecordedAt();
                                                                                                                                                                var
                                                                                                                                                                        technical =
                                                                                                                                                                                LocalDateTime
                                                                                                                                                                                        .ofInstant(
                                                                                                                                                                                                time,
                                                                                                                                                                                                ZoneOffset
                                                                                                                                                                                                        .UTC);
                                                                                                                                                                var
                                                                                                                                                                        definition =
                                                                                                                                                                                DefinitionProfiles
                                                                                                                                                                                        .decode(
                                                                                                                                                                                                revision
                                                                                                                                                                                                        .canonicalDefinition());
                                                                                                                                                                var
                                                                                                                                                                        legacy =
                                                                                                                                                                                new Version(
                                                                                                                                                                                        ids
                                                                                                                                                                                                .productVersionId(),
                                                                                                                                                                                        root
                                                                                                                                                                                                .productId(),
                                                                                                                                                                                        request
                                                                                                                                                                                                .versionNumber(),
                                                                                                                                                                                        "Governed"
                                                                                                                                                                                            + " product"
                                                                                                                                                                                            + " definition"
                                                                                                                                                                                            + " publication",
                                                                                                                                                                                        LocalDateTime
                                                                                                                                                                                                .ofInstant(
                                                                                                                                                                                                        Instant
                                                                                                                                                                                                                .parse(
                                                                                                                                                                                                                        definition
                                                                                                                                                                                                                                .common()
                                                                                                                                                                                                                                .effectiveFrom()),
                                                                                                                                                                                                        ZoneOffset
                                                                                                                                                                                                                .UTC),
                                                                                                                                                                                        technical,
                                                                                                                                                                                        technical);
                                                                                                                                                                var
                                                                                                                                                                        publication =
                                                                                                                                                                                new ProductDefinitionPublication(
                                                                                                                                                                                        ids
                                                                                                                                                                                                .publicationId(),
                                                                                                                                                                                        scope
                                                                                                                                                                                                .tenantId(),
                                                                                                                                                                                        scope
                                                                                                                                                                                                .rootId(),
                                                                                                                                                                                        approved.review()
                                                                                                                                                                                                .reviewId(),
                                                                                                                                                                                        revision
                                                                                                                                                                                                .revisionId(),
                                                                                                                                                                                        ids
                                                                                                                                                                                                .productVersionId(),
                                                                                                                                                                                        revision
                                                                                                                                                                                                .canonicalDefinition(),
                                                                                                                                                                                        revision
                                                                                                                                                                                                .publicDigest(),
                                                                                                                                                                                        DefinitionCanonical
                                                                                                                                                                                                .ENCODING,
                                                                                                                                                                                        "NOT_ADMITTED",
                                                                                                                                                                                        time);
                                                                                                                                                                var
                                                                                                                                                                        identity =
                                                                                                                                                                                new VersionIdentity(
                                                                                                                                                                                        ids
                                                                                                                                                                                                .productVersionId(),
                                                                                                                                                                                        scope
                                                                                                                                                                                                .tenantId(),
                                                                                                                                                                                        scope
                                                                                                                                                                                                .rootId(),
                                                                                                                                                                                        root
                                                                                                                                                                                                .productId(),
                                                                                                                                                                                        ids
                                                                                                                                                                                                .publicationId(),
                                                                                                                                                                                        request
                                                                                                                                                                                                .versionNumber(),
                                                                                                                                                                                        time);
                                                                                                                                                                var
                                                                                                                                                                        publicationControl =
                                                                                                                                                                                new ProductDefinitionPublicationControl(
                                                                                                                                                                                        ids
                                                                                                                                                                                                .publicationId(),
                                                                                                                                                                                        scope
                                                                                                                                                                                                .tenantId(),
                                                                                                                                                                                        scope
                                                                                                                                                                                                .rootId(),
                                                                                                                                                                                        "ACTIVE",
                                                                                                                                                                                        0);
                                                                                                                                                                var
                                                                                                                                                                        receipt =
                                                                                                                                                                                new PublicReceipt(
                                                                                                                                                                                        scope,
                                                                                                                                                                                        ProductDefinitionCommand
                                                                                                                                                                                                .PUBLISH,
                                                                                                                                                                                        ids
                                                                                                                                                                                                .actionId(),
                                                                                                                                                                                        time
                                                                                                                                                                                                .toString(),
                                                                                                                                                                                        "PUBLISHED",
                                                                                                                                                                                        version,
                                                                                                                                                                                        revision
                                                                                                                                                                                                .revisionId(),
                                                                                                                                                                                        ids
                                                                                                                                                                                                .publicationId(),
                                                                                                                                                                                        ids
                                                                                                                                                                                                .productVersionId(),
                                                                                                                                                                                        request
                                                                                                                                                                                                .versionNumber(),
                                                                                                                                                                                        "ACTIVE",
                                                                                                                                                                                        0,
                                                                                                                                                                                        "NOT_ADMITTED");
                                                                                                                                                                return insert(
                                                                                                                                                                                legacy)
                                                                                                                                                                        .then(
                                                                                                                                                                                insert(
                                                                                                                                                                                        publication))
                                                                                                                                                                        .then(
                                                                                                                                                                                insert(
                                                                                                                                                                                        identity))
                                                                                                                                                                        .then(
                                                                                                                                                                                insert(
                                                                                                                                                                                        publicationControl))
                                                                                                                                                                        .then(
                                                                                                                                                                                persistence
                                                                                                                                                                                        .drafts()
                                                                                                                                                                                        .replacePublicHead(
                                                                                                                                                                                                new ProductDefinitionDraftWrites
                                                                                                                                                                                                        .PublicHeadChange(
                                                                                                                                                                                                        scope
                                                                                                                                                                                                                .tenantId(),
                                                                                                                                                                                                        scope
                                                                                                                                                                                                                .rootId(),
                                                                                                                                                                                                        root
                                                                                                                                                                                                                .publicHead(),
                                                                                                                                                                                                        root
                                                                                                                                                                                                                .publicStateVersion(),
                                                                                                                                                                                                        root
                                                                                                                                                                                                                .phase(),
                                                                                                                                                                                                        root
                                                                                                                                                                                                                .publicHead(),
                                                                                                                                                                                                        "PUBLISHED")))
                                                                                                                                                                        .thenReturn(
                                                                                                                                                                                new Effect(
                                                                                                                                                                                        receipt,
                                                                                                                                                                                        null,
                                                                                                                                                                                        approved.privateReview()
                                                                                                                                                                                                .selectionFingerprint()));
                                                                                                                                                            });
                                                                                                                                        })));
                                                                    }));
                        });
    }

    private Mono<Effect> withdraw(Frame frame) {
        var request = (WithdrawBody) frame.parsed().instruction().body();
        var scope = frame.parsed().instruction().scope();
        var ids = frame.resources();
        return root(frame)
                .flatMap(
                        root ->
                                persistence
                                        .lifecyclePublicationRepository()
                                        .findByTenantIdAndRootIdAndPublicationId(
                                                scope.tenantId(),
                                                scope.rootId(),
                                                request.publicationId())
                                        .switchIfEmpty(
                                                Mono.error(
                                                        new ResponseStatusException(
                                                                HttpStatus.NOT_FOUND,
                                                                "Product publication not found")))
                                        .flatMap(
                                                publication -> {
                                                    require(
                                                            publication
                                                                            .tenantId()
                                                                            .equals(
                                                                                    scope
                                                                                            .tenantId())
                                                                    && publication
                                                                            .rootId()
                                                                            .equals(scope.rootId())
                                                                    && publication
                                                                            .publicationId()
                                                                            .equals(
                                                                                    request
                                                                                            .publicationId())
                                                                    && "NOT_ADMITTED"
                                                                            .equals(
                                                                                    publication
                                                                                            .operationalAdmission())
                                                                    && DefinitionCanonical.ENCODING
                                                                            .equals(
                                                                                    publication
                                                                                            .encodingVersion()));
                                                    return revision(root, publication.revisionId())
                                                            .flatMap(
                                                                    revision -> {
                                                                        require(
                                                                                revision.canonicalDefinition()
                                                                                                .equals(
                                                                                                        publication
                                                                                                                .canonicalDefinition())
                                                                                        && revision.publicDigest()
                                                                                                .equals(
                                                                                                        publication
                                                                                                                .publicDigest()));
                                                                        return persistence
                                                                                .lifecycleVersionIdentityRepository()
                                                                                .findByTenantIdAndRootIdAndPublicationId(
                                                                                        scope
                                                                                                .tenantId(),
                                                                                        scope
                                                                                                .rootId(),
                                                                                        request
                                                                                                .publicationId())
                                                                                .switchIfEmpty(
                                                                                        Mono.error(
                                                                                                unavailable()))
                                                                                .flatMap(
                                                                                        identity -> {
                                                                                            require(
                                                                                                    identity.tenantId()
                                                                                                                    .equals(
                                                                                                                            scope
                                                                                                                                    .tenantId())
                                                                                                            && identity.rootId()
                                                                                                                    .equals(
                                                                                                                            scope
                                                                                                                                    .rootId())
                                                                                                            && identity.productId()
                                                                                                                    .equals(
                                                                                                                            root
                                                                                                                                    .productId())
                                                                                                            && identity.productVersionId()
                                                                                                                    .equals(
                                                                                                                            publication
                                                                                                                                    .productVersionId())
                                                                                                            && identity.publicationId()
                                                                                                                    .equals(
                                                                                                                            publication
                                                                                                                                    .publicationId())
                                                                                                            && identity
                                                                                                                            .versionNumber()
                                                                                                                    != null
                                                                                                            && identity
                                                                                                                            .versionNumber()
                                                                                                                    > 0
                                                                                                            && identity.recordedAt()
                                                                                                                    .equals(
                                                                                                                            publication
                                                                                                                                    .createdAt()));
                                                                                            return persistence
                                                                                                    .lifecyclePublicationControlRepository()
                                                                                                    .findByTenantIdAndRootIdAndPublicationId(
                                                                                                            scope
                                                                                                                    .tenantId(),
                                                                                                            scope
                                                                                                                    .rootId(),
                                                                                                            request
                                                                                                                    .publicationId())
                                                                                                    .switchIfEmpty(
                                                                                                            Mono
                                                                                                                    .error(
                                                                                                                            unavailable()))
                                                                                                    .flatMap(
                                                                                                            control -> {
                                                                                                                require(
                                                                                                                        control.tenantId()
                                                                                                                                        .equals(
                                                                                                                                                scope
                                                                                                                                                        .tenantId())
                                                                                                                                && control.rootId()
                                                                                                                                        .equals(
                                                                                                                                                scope
                                                                                                                                                        .rootId())
                                                                                                                                && control.publicationId()
                                                                                                                                        .equals(
                                                                                                                                                publication
                                                                                                                                                        .publicationId()));
                                                                                                                if (!"ACTIVE"
                                                                                                                                .equals(
                                                                                                                                        control
                                                                                                                                                .state())
                                                                                                                        || !Objects
                                                                                                                                .equals(
                                                                                                                                        request
                                                                                                                                                .expectedStateVersion(),
                                                                                                                                        control
                                                                                                                                                .stateVersion()))
                                                                                                                    throw conflict();
                                                                                                                int
                                                                                                                        version =
                                                                                                                                increment(
                                                                                                                                        control
                                                                                                                                                .stateVersion());
                                                                                                                var
                                                                                                                        receipt =
                                                                                                                                new PublicReceipt(
                                                                                                                                        scope,
                                                                                                                                        ProductDefinitionCommand
                                                                                                                                                .WITHDRAW_PUBLICATION,
                                                                                                                                        ids
                                                                                                                                                .actionId(),
                                                                                                                                        frame.nativeAdmission()
                                                                                                                                                .commandRecordedAt()
                                                                                                                                                .toString(),
                                                                                                                                        root
                                                                                                                                                .phase(),
                                                                                                                                        root
                                                                                                                                                .publicStateVersion(),
                                                                                                                                        publication
                                                                                                                                                .revisionId(),
                                                                                                                                        publication
                                                                                                                                                .publicationId(),
                                                                                                                                        publication
                                                                                                                                                .productVersionId(),
                                                                                                                                        identity
                                                                                                                                                .versionNumber(),
                                                                                                                                        "WITHDRAWN",
                                                                                                                                        version,
                                                                                                                                        "NOT_ADMITTED");
                                                                                                                return persistence
                                                                                                                        .drafts()
                                                                                                                        .withdrawPublication(
                                                                                                                                new ProductDefinitionDraftWrites
                                                                                                                                        .PublicationStateChange(
                                                                                                                                        scope
                                                                                                                                                .tenantId(),
                                                                                                                                        scope
                                                                                                                                                .rootId(),
                                                                                                                                        publication
                                                                                                                                                .publicationId(),
                                                                                                                                        control
                                                                                                                                                .stateVersion()))
                                                                                                                        .thenReturn(
                                                                                                                                new Effect(
                                                                                                                                        receipt,
                                                                                                                                        null,
                                                                                                                                        selection(
                                                                                                                                                "tenantId",
                                                                                                                                                scope
                                                                                                                                                        .tenantId(),
                                                                                                                                                "rootId",
                                                                                                                                                scope
                                                                                                                                                        .rootId(),
                                                                                                                                                "publicationId",
                                                                                                                                                publication
                                                                                                                                                        .publicationId(),
                                                                                                                                                "productVersionId",
                                                                                                                                                publication
                                                                                                                                                        .productVersionId(),
                                                                                                                                                "versionNumber",
                                                                                                                                                identity
                                                                                                                                                        .versionNumber(),
                                                                                                                                                "publicationStateVersion",
                                                                                                                                                version)));
                                                                                                            });
                                                                                        });
                                                                    });
                                                }));
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Product definition selection conflict");
    }

    private Mono<Receipt> evidence(Frame frame, Effect effect) {
        var scope = frame.parsed().instruction().scope();
        var ids = frame.resources();
        var actor = frame.admission().principal();
        var time = frame.nativeAdmission().commandRecordedAt();
        String command = frame.parsed().instruction().command().name();
        String visibility =
                ProductDefinitionCommandReceiptCodec.privateCommand(effect.receipt().command())
                        ? "PRIVATE"
                        : "PUBLIC";
        var action =
                new Action(
                        ids.actionId(),
                        scope.tenantId(),
                        scope.rootId(),
                        command,
                        visibility,
                        ProductDefinitionCommandReceiptCodec.encode(effect.receipt()),
                        actor.issuer(),
                        actor.subject(),
                        time);
        var original =
                new ActionPrivate(
                        ids.actionId(),
                        scope.tenantId(),
                        scope.rootId(),
                        frame.parsed().intentDigest(),
                        effect.selectionFingerprint(),
                        frame.parsed().canonicalIntent());
        Mono<Void> writes =
                insert(action)
                        .then(insert(original))
                        .then(
                                insert(
                                        new Audit(
                                                ids.auditId(),
                                                scope.tenantId(),
                                                scope.rootId(),
                                                ids.actionId(),
                                                command,
                                                actor.issuer(),
                                                actor.subject(),
                                                time)))
                        .then();
        if (ids.historyId() != null) {
            var receipt = (PublicReceipt) effect.receipt();
            writes =
                    writes.then(
                                    insert(
                                            new History(
                                                    ids.historyId(),
                                                    scope.tenantId(),
                                                    scope.rootId(),
                                                    ids.actionId(),
                                                    receipt.publicVersion(),
                                                    command)))
                            .then();
        }
        if (ids.publicEventId() != null)
            writes =
                    writes.then(
                                    insert(
                                            new Outbox(
                                                    ids.publicEventId(),
                                                    scope.tenantId(),
                                                    scope.rootId(),
                                                    ids.actionId(),
                                                    command,
                                                    time)))
                            .then();
        if (ids.costEventId() != null) {
            require(effect.costEventReference() != null);
            writes =
                    writes.then(
                                    insert(
                                            new CostOutbox(
                                                    ids.costEventId(),
                                                    scope.tenantId(),
                                                    scope.rootId(),
                                                    ids.actionId(),
                                                    effect.costEventReference(),
                                                    time)))
                            .then();
        }
        return writes.thenReturn(effect.receipt());
    }

    private <T> Mono<T> insert(T row) {
        return persistence.operations().insert(row).switchIfEmpty(Mono.error(unavailable()));
    }

    private static String selection(Object... entries) {
        var values = new LinkedHashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2) values.put((String) entries[i], entries[i + 1]);
        return DefinitionCanonical.hash(
                "product-definition-selection-v1",
                DefinitionCanonical.encode(DefinitionCanonical.tree(values)));
    }

    private static Mono<Void> truth(Mono<Boolean> value) {
        return value.filter(Boolean.TRUE::equals).switchIfEmpty(Mono.error(unavailable())).then();
    }

    private static void require(boolean value) {
        if (!value) throw unavailable();
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Product lifecycle evidence unavailable");
    }

    private static Throwable safe(Throwable error) {
        return ProductDefinitionCommandErrors.safe(error);
    }
}

package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.*;
import io.fireflysoftware.core.product.models.entities.definition.*;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.*;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionLifecycleRows.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandNativeRepository.NativeAdmission;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.*;
import org.springframework.transaction.reactive.*;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.*;
import java.util.*;

class ProductDefinitionLifecycleServiceTest {
    final UUID tenant = UUID.randomUUID(), root = UUID.randomUUID(), operation = UUID.randomUUID();
    final Instant stamp = Instant.parse("2026-10-24T12:00:00Z");
    final List<Object> inserted = new ArrayList<>();
    final List<String> order = new ArrayList<>();
    final ProductDefinitionCommandAuthorization auth =
            mock(ProductDefinitionCommandAuthorization.class);
    final ProductDefinitionCommandPersistence persistence =
            mock(ProductDefinitionCommandPersistence.class);
    final R2dbcEntityTemplate operations = mock(R2dbcEntityTemplate.class);
    final ProductDefinitionCommandNativeRepository nativeCommands =
            mock(ProductDefinitionCommandNativeRepository.class);
    final LifecycleActionRepository actions = mock(LifecycleActionRepository.class);
    final LifecycleActionPrivateRepository originals = mock(LifecycleActionPrivateRepository.class);
    final ProductDefinitionDraftRepository drafts = mock(ProductDefinitionDraftRepository.class);
    final ProductDefinitionCostControlRepository costs =
            mock(ProductDefinitionCostControlRepository.class);
    final LifecycleRevisionRepository revisions = mock(LifecycleRevisionRepository.class);
    final LifecycleCostRevisionRepository costRevisions =
            mock(LifecycleCostRevisionRepository.class);
    final LifecycleSubmissionRepository submissions = mock(LifecycleSubmissionRepository.class);
    final LifecycleReviewRepository reviews = mock(LifecycleReviewRepository.class);
    final LifecycleReviewPrivateRepository privateReviews =
            mock(LifecycleReviewPrivateRepository.class);
    final LifecycleDecisionRepository decisions = mock(LifecycleDecisionRepository.class);
    final LifecyclePublicationRepository publications = mock(LifecyclePublicationRepository.class);
    final LifecyclePublicationControlRepository publicationControls =
            mock(LifecyclePublicationControlRepository.class);
    final LifecycleVersionIdentityRepository identities =
            mock(LifecycleVersionIdentityRepository.class);
    com.fasterxml.jackson.databind.JsonNode admittedPlan;
    String admittedOriginal;
    boolean replay;
    Action retainedAction;

    ProductDefinitionLifecycleServiceTest() {
        when(persistence.lifecycleReviewRepository()).thenReturn(reviews);
        when(persistence.lifecycleReviewPrivateRepository()).thenReturn(privateReviews);
        when(persistence.lifecycleDecisionRepository()).thenReturn(decisions);
        when(persistence.lifecyclePublicationRepository()).thenReturn(publications);
        when(persistence.lifecyclePublicationControlRepository()).thenReturn(publicationControls);
        when(persistence.lifecycleVersionIdentityRepository()).thenReturn(identities);
        when(drafts.withdrawPublication(any())).thenReturn(Mono.empty());
        when(persistence.lifecycleSubmissionRepository()).thenReturn(submissions);
        when(persistence.drafts()).thenReturn(drafts);
        when(persistence.costs()).thenReturn(costs);
        when(persistence.lifecycleRevisionRepository()).thenReturn(revisions);
        when(persistence.lifecycleCostRevisionRepository()).thenReturn(costRevisions);
        when(drafts.replacePublicHead(any())).thenReturn(Mono.empty());
        when(drafts.replaceCostHead(any())).thenReturn(Mono.empty());
        when(drafts.replaceActiveReview(any())).thenReturn(Mono.empty());
        when(persistence.operations()).thenReturn(operations);
        when(persistence.productDefinitionCommandNativeRepository()).thenReturn(nativeCommands);
        when(persistence.lifecycleActionRepository()).thenReturn(actions);
        when(persistence.lifecycleActionPrivateRepository()).thenReturn(originals);
        var manager = new RecordingManager(order);
        when(persistence.transactions()).thenReturn(TransactionalOperator.create(manager));
        when(operations.insert((Object) any()))
                .thenAnswer(
                        call ->
                                Mono.fromSupplier(
                                        () -> {
                                            var row = call.getArgument(0);
                                            inserted.add(row);
                                            order.add("insert");
                                            return row;
                                        }));
        var actor =
                SecurityPrincipal.builder()
                        .subject("staff-maker")
                        .issuer("https://issuer.example.invalid")
                        .tenantId(tenant.toString())
                        .claims(Map.of("exp", stamp.plusSeconds(300)))
                        .build();
        when(auth.authorize(any()))
                .thenAnswer(
                        call ->
                                outside("authorize")
                                        .thenReturn(
                                                new ProductDefinitionCommandAuthorization.Admission(
                                                        actor,
                                                        call.getArgument(0),
                                                        stamp.plusSeconds(300).toEpochMilli(),
                                                        stamp.plusSeconds(30).toEpochMilli())));
        when(auth.reauthorize(any())).thenAnswer(call -> outside("reauthorize"));
        when(nativeCommands.begin(
                        any(), any(), any(), any(), any(), any(), anyLong(), anyLong(), any(),
                        any(), any(), any()))
                .thenAnswer(
                        call ->
                                TransactionSynchronizationManager.forCurrentTransaction()
                                        .flatMap(
                                                tx -> {
                                                    assertThat(tx.isActualTransactionActive())
                                                            .isTrue();
                                                    order.add("native-begin");
                                                    var plan =
                                                            DefinitionCanonical.parse(
                                                                    call.getArgument(11));
                                                    admittedPlan = plan;
                                                    admittedOriginal = call.getArgument(9);
                                                    return Mono.just(
                                                            new NativeAdmission(
                                                                    UUID.randomUUID(),
                                                                    replay,
                                                                    replay
                                                                            ? stamp.plusSeconds(100)
                                                                            : stamp,
                                                                    replay
                                                                            ? retainedAction
                                                                                    .actionId()
                                                                            : UUID.fromString(
                                                                                    plan.get(
                                                                                                    "resources")
                                                                                            .get(
                                                                                                    "actionId")
                                                                                            .asText()),
                                                                    replay
                                                                            ? retainedAction
                                                                                    .createdAt()
                                                                            : null));
                                                }));
        when(nativeCommands.isCurrent(any())).thenReturn(Mono.just(true));
        when(nativeCommands.finish(any()))
                .thenAnswer(
                        call ->
                                Mono.fromSupplier(
                                        () -> {
                                            order.add("native-finish");
                                            return true;
                                        }));
    }

    Mono<Void> outside(String event) {
        return TransactionSynchronizationManager.forCurrentTransaction()
                .map(TransactionSynchronizationManager::isActualTransactionActive)
                .onErrorResume(NoTransactionException.class, e -> Mono.just(false))
                .doOnNext(
                        active -> {
                            assertThat(active).isFalse();
                            order.add(event);
                        })
                .then();
    }

    String createBody() throws Exception {
        String raw =
                new String(
                        Objects.requireNonNull(
                                        getClass()
                                                .getResourceAsStream("/definition/v1/account.json"))
                                .readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
        return DefinitionCanonical.encode(
                DefinitionCanonical.tree(
                        Map.of(
                                "operationId",
                                operation,
                                "productId",
                                UUID.randomUUID(),
                                "categoryId",
                                UUID.randomUUID(),
                                "definition",
                                DefinitionCanonical.parse(raw),
                                "cost",
                                Map.of(
                                        "schemaVersion",
                                        1,
                                        "businessArea",
                                        "BANKING",
                                        "profileCode",
                                        "ACCOUNT_TARIFF_V1",
                                        "mode",
                                        "NONE",
                                        "lines",
                                        List.of()))));
    }

    ProductDefinitionLifecycleService service() {
        return new ProductDefinitionLifecycleServiceImpl(auth, persistence);
    }

    @Test
    void createDraftWritesRealMappedAggregateEvidenceAndUsesOnlyAfterLockNativeTime()
            throws Exception {
        var receipt =
                service()
                        .execute(tenant, root, ProductDefinitionCommand.CREATE_DRAFT, createBody())
                        .block();
        assertThat(receipt).isNotNull().isInstanceOf(PublicReceipt.class);
        var publicReceipt = (PublicReceipt) receipt;
        assertThat(publicReceipt.recordedAt()).isEqualTo(stamp.toString());
        assertThat(publicReceipt.phase()).isEqualTo("DRAFT");
        assertThat(publicReceipt.publicVersion()).isZero();
        assertThat(publicReceipt.operationalAdmission()).isEqualTo("NOT_ADMITTED");
        assertThat(inserted)
                .anyMatch(ProductBirth.class::isInstance)
                .anyMatch(ProductDefinitionDraftRoot.class::isInstance)
                .anyMatch(ProductDefinitionDraftRevision.class::isInstance)
                .anyMatch(ProductDefinitionCostRevision.class::isInstance)
                .anyMatch(ProductDefinitionCostControl.class::isInstance)
                .anyMatch(Action.class::isInstance)
                .anyMatch(ActionPrivate.class::isInstance)
                .anyMatch(History.class::isInstance)
                .anyMatch(Audit.class::isInstance)
                .anyMatch(Outbox.class::isInstance)
                .anyMatch(CostOutbox.class::isInstance);
        for (var value : inserted)
            for (var component : value.getClass().getRecordComponents())
                if (component.getType() == Instant.class)
                    assertThat(component.getAccessor().invoke(value)).isEqualTo(stamp);
        assertThat(DefinitionCanonical.tree(receipt).toString())
                .doesNotContain(
                        "costRevisionId", "costVersion", "canonicalCost", "selectionFingerprint");
        assertThat(order.indexOf("authorize")).isLessThan(order.indexOf("begin"));
        assertThat(order.indexOf("commit")).isLessThan(order.indexOf("reauthorize"));
    }

    @Test
    void malformedIntentNeverReachesAuthorityOrNativeAdmission() {
        StepVerifier.create(
                        service()
                                .execute(
                                        tenant,
                                        root,
                                        ProductDefinitionCommand.CREATE_DRAFT,
                                        "{\"actorId\":\"caller\"}"))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(400))
                .verify();
        verifyNoInteractions(auth, nativeCommands);
        assertThat(inserted).isEmpty();
    }

    @Test
    void authorizedReplayReturnsOriginalReceiptTimeWithoutRepeatingWrites() throws Exception {
        var body = createBody();
        var service = service();
        var first =
                service.execute(tenant, root, ProductDefinitionCommand.CREATE_DRAFT, body).block();
        assertThat(first).isNotNull();
        retainedAction =
                inserted.stream()
                        .filter(Action.class::isInstance)
                        .map(Action.class::cast)
                        .findFirst()
                        .orElseThrow();
        var original =
                inserted.stream()
                        .filter(ActionPrivate.class::isInstance)
                        .map(ActionPrivate.class::cast)
                        .findFirst()
                        .orElseThrow();
        when(actions.findByTenantIdAndRootIdAndActionId(tenant, root, retainedAction.actionId()))
                .thenReturn(Mono.just(retainedAction));
        when(originals.findByTenantIdAndRootIdAndActionId(tenant, root, retainedAction.actionId()))
                .thenReturn(Mono.just(original));
        int attemptedWrites = inserted.size();
        replay = true;
        var second =
                service.execute(tenant, root, ProductDefinitionCommand.CREATE_DRAFT, body).block();
        assertThat(second).isEqualTo(first);
        assertThat(inserted).hasSize(attemptedWrites);
        verify(auth, times(2)).authorize(any());
        verify(auth, times(2)).reauthorize(any());
    }

    @Test
    void lostPostCommitPermissionReportsReceiptUnavailableWithoutClaimingRollback()
            throws Exception {
        when(auth.reauthorize(any()))
                .thenAnswer(
                        call ->
                                outside("reauthorize")
                                        .then(
                                                Mono.error(
                                                        new ResponseStatusException(
                                                                HttpStatus.FORBIDDEN, "revoked"))));
        StepVerifier.create(
                        service()
                                .execute(
                                        tenant,
                                        root,
                                        ProductDefinitionCommand.CREATE_DRAFT,
                                        createBody()))
                .expectErrorSatisfies(
                        error -> {
                            assertThat(error).isInstanceOf(ResponseStatusException.class);
                            assertThat(((ResponseStatusException) error).getStatusCode().value())
                                    .isEqualTo(503);
                        })
                .verify();
        assertThat(order).contains("commit", "reauthorize").doesNotContain("rollback");
    }

    @Test
    void ambientTransactionCannotBeSilentlySuspendedBeforeCommandAuthority() throws Exception {
        var action =
                service()
                        .execute(tenant, root, ProductDefinitionCommand.CREATE_DRAFT, createBody());
        StepVerifier.create(
                        TransactionalOperator.create(new RecordingManager(order))
                                .transactional(action))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(503))
                .verify();
        verifyNoInteractions(auth, nativeCommands);
        assertThat(inserted).isEmpty();
    }

    record Head(
            ProductDefinitionDraftRoot root,
            ProductDefinitionDraftRevision revision,
            com.fasterxml.jackson.databind.JsonNode definition) {}

    Head head(String phase) throws Exception {
        var definition = DefinitionCanonical.parse(createBody()).get("definition");
        var canonical = DefinitionProfiles.canonical(definition.toString());
        var typed = DefinitionProfiles.decode(canonical.json());
        var id = UUID.randomUUID();
        var row =
                new ProductDefinitionDraftRoot(
                        root,
                        tenant,
                        UUID.randomUUID(),
                        typed.common().code(),
                        "BANKING",
                        "ACCOUNT_TARIFF_V1",
                        1,
                        phase,
                        1,
                        id,
                        null,
                        stamp.minusSeconds(500));
        var revision =
                new ProductDefinitionDraftRevision(
                        id,
                        tenant,
                        root,
                        canonical.json(),
                        canonical.digest(),
                        DefinitionCanonical.ENCODING,
                        null,
                        null,
                        "https://issuer.example.invalid",
                        "original-maker",
                        stamp.minusSeconds(500));
        when(drafts.findByTenantIdAndRootId(tenant, root)).thenReturn(Mono.just(row));
        when(revisions.findByTenantIdAndRootIdAndRevisionId(tenant, root, id))
                .thenReturn(Mono.just(revision));
        return new Head(row, revision, definition);
    }

    @Test
    void editingPublicTermsAppendsRevisionAndNeverReadsOrChangesPrivateCost() throws Exception {
        var current = head("DRAFT");
        var definition = current.definition().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) definition.get("common"))
                .put("name", "New governed terms");
        String body =
                DefinitionCanonical.encode(
                        DefinitionCanonical.tree(
                                Map.of(
                                        "operationId",
                                        operation,
                                        "expectedPublicVersion",
                                        1,
                                        "expectedPublicHead",
                                        current.revision().revisionId(),
                                        "definition",
                                        definition)));
        var receipt =
                service().execute(tenant, root, ProductDefinitionCommand.EDIT_PUBLIC, body).block();
        assertThat(receipt).isNotNull();
        assertThat(((PublicReceipt) receipt).publicVersion()).isEqualTo(2);
        assertThat(
                        inserted.stream()
                                .filter(ProductDefinitionDraftRevision.class::isInstance)
                                .map(ProductDefinitionDraftRevision.class::cast)
                                .findFirst()
                                .orElseThrow()
                                .canonicalDefinition())
                .contains("New governed terms");
        verify(drafts)
                .replacePublicHead(
                        argThat(
                                change ->
                                        change.expectedVersion() == 1
                                                && change.tenantId().equals(tenant)
                                                && change.newPhase().equals("DRAFT")));
        verifyNoInteractions(costs, costRevisions);
        assertThat(inserted)
                .noneMatch(
                        value ->
                                value instanceof CostOutbox
                                        || value instanceof ProductDefinitionCostRevision);
    }

    @Test
    void submittingPublicTermsRetainsImmutableSubmissionWithoutPrivateCompletenessChecks()
            throws Exception {
        var current = head("DRAFT");
        String body =
                DefinitionCanonical.encode(
                        DefinitionCanonical.tree(
                                Map.of(
                                        "operationId",
                                        operation,
                                        "expectedPublicVersion",
                                        1,
                                        "expectedPublicHead",
                                        current.revision().revisionId())));
        var receipt =
                service()
                        .execute(tenant, root, ProductDefinitionCommand.SUBMIT_PUBLIC, body)
                        .block();
        assertThat(receipt).isNotNull();
        assertThat(((PublicReceipt) receipt).phase()).isEqualTo("IN_REVIEW");
        assertThat(((PublicReceipt) receipt).publicVersion()).isEqualTo(2);
        var submission =
                inserted.stream()
                        .filter(Submission.class::isInstance)
                        .map(Submission.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertThat(submission.revisionId()).isEqualTo(current.revision().revisionId());
        assertThat(submission.publicVersion()).isEqualTo(2);
        verifyNoInteractions(costs, costRevisions);
    }

    String costBody(Head head, UUID requestedRevision) {
        return DefinitionCanonical.encode(
                DefinitionCanonical.tree(
                        Map.of(
                                "operationId",
                                operation,
                                "expectedCostVersion",
                                2,
                                "expectedCostHead",
                                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                                "publicRevisionId",
                                requestedRevision,
                                "publicDigest",
                                head.revision().publicDigest(),
                                "publicDefinition",
                                head.definition(),
                                "cost",
                                Map.of(
                                        "schemaVersion",
                                        1,
                                        "businessArea",
                                        "BANKING",
                                        "profileCode",
                                        "ACCOUNT_TARIFF_V1",
                                        "mode",
                                        "NONE",
                                        "lines",
                                        List.of()))));
    }

    @Test
    void editingPrivateCostDoesNotMovePublicHeadPhaseVersionOrPublicOutbox() throws Exception {
        var current = head("IN_REVIEW");
        when(costs.findByTenantIdAndRootId(tenant, root))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionCostControl(
                                        root,
                                        tenant,
                                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                                        2)));
        var receipt =
                service()
                        .execute(
                                tenant,
                                root,
                                ProductDefinitionCommand.EDIT_COST,
                                costBody(current, current.revision().revisionId()))
                        .block();
        assertThat(receipt).isNotNull().isInstanceOf(PrivateReceipt.class);
        assertThat(((PrivateReceipt) receipt).costVersion()).isEqualTo(3);
        verify(drafts)
                .replaceCostHead(
                        argThat(
                                change ->
                                        change.expectedVersion() == 2
                                                && change.tenantId().equals(tenant)));
        verify(drafts, never()).replacePublicHead(any());
        verify(drafts)
                .replaceActiveReview(
                        argThat(
                                change ->
                                        change.publicVersion() == 1
                                                && change.newReviewId() == null));
        assertThat(inserted)
                .noneMatch(value -> value instanceof History || value instanceof Outbox)
                .anyMatch(CostOutbox.class::isInstance);
    }

    @Test
    void stalePublicSelectionCannotBeUsedToWritePrivateCost() throws Exception {
        var current = head("IN_REVIEW");
        StepVerifier.create(
                        service()
                                .execute(
                                        tenant,
                                        root,
                                        ProductDefinitionCommand.EDIT_COST,
                                        costBody(current, UUID.randomUUID())))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(409))
                .verify();
        assertThat(inserted).isEmpty();
        assertThat(order).contains("rollback");
    }

    record Selected(Head head, ProductDefinitionCostRevision cost, Submission submission) {}

    Selected selected() throws Exception {
        var current = head("IN_REVIEW");
        var id = UUID.randomUUID();
        var canonical =
                CostRules.canonical(
                        "{\"schemaVersion\":1,\"businessArea\":\"BANKING\",\"profileCode\":\"ACCOUNT_TARIFF_V1\",\"mode\":\"NONE\",\"lines\":[]}",
                        current.revision().canonicalDefinition());
        var row =
                new ProductDefinitionCostRevision(
                        id,
                        tenant,
                        root,
                        canonical.json(),
                        canonical.digest(),
                        DefinitionCanonical.ENCODING,
                        "https://issuer.example.invalid",
                        "cost-maker",
                        stamp.minusSeconds(100));
        when(costs.findByTenantIdAndRootId(tenant, root))
                .thenReturn(Mono.just(new ProductDefinitionCostControl(root, tenant, id, 2)));
        when(costRevisions.findByTenantIdAndRootIdAndCostRevisionId(tenant, root, id))
                .thenReturn(Mono.just(row));
        var submitted =
                new Submission(
                        UUID.randomUUID(),
                        tenant,
                        root,
                        current.revision().revisionId(),
                        current.revision().publicDigest(),
                        1,
                        "https://issuer.example.invalid",
                        "submitter",
                        stamp.minusSeconds(50));
        when(submissions.findByTenantIdAndRootIdAndSubmissionId(
                        tenant, root, submitted.submissionId()))
                .thenReturn(Mono.just(submitted));
        when(revisions.findDistinctTop513ByTenantIdAndRootId(tenant, root))
                .thenReturn(
                        Flux.just(
                                new LifecycleRevisionRepository.Contributor() {
                                    public String getActorIssuer() {
                                        return "https://issuer.example.invalid";
                                    }

                                    public String getActorId() {
                                        return "original-maker";
                                    }
                                }));
        when(costRevisions.findDistinctTop513ByTenantIdAndRootId(tenant, root))
                .thenReturn(
                        Flux.just(
                                new LifecycleCostRevisionRepository.Contributor() {
                                    public String getActorIssuer() {
                                        return "https://issuer.example.invalid";
                                    }

                                    public String getActorId() {
                                        return "cost-maker";
                                    }
                                }));
        when(submissions.findDistinctTop513ByTenantIdAndRootId(tenant, root))
                .thenReturn(
                        Flux.just(
                                new LifecycleSubmissionRepository.Contributor() {
                                    public String getActorIssuer() {
                                        return "https://issuer.example.invalid";
                                    }

                                    public String getActorId() {
                                        return "submitter";
                                    }
                                }));
        return new Selected(current, row, submitted);
    }

    String reviewBody(Selected selected, int costVersion, String reason) {
        var body = new LinkedHashMap<String, Object>();
        body.put("operationId", operation);
        body.put("submissionId", selected.submission().submissionId());
        body.put("revisionId", selected.head().revision().revisionId());
        body.put("publicDigest", selected.head().revision().publicDigest());
        body.put("publicVersion", 1);
        body.put("costRevisionId", selected.cost().costRevisionId());
        body.put("costDigest", selected.cost().costDigest());
        body.put("costVersion", costVersion);
        var selectedBinding =
                new ProductDefinitionLifecycleContracts.Selection(
                        tenant,
                        root,
                        selected.submission().submissionId(),
                        selected.head().revision().revisionId(),
                        selected.head().revision().publicDigest(),
                        1,
                        selected.cost().costRevisionId(),
                        selected.cost().costDigest(),
                        2);
        body.put(
                "selectionFingerprint",
                DefinitionCanonical.hash(
                        "product-definition-selection-v1",
                        DefinitionCanonical.encode(DefinitionCanonical.tree(selectedBinding))));
        if (reason != null) body.put("reason", reason);
        return DefinitionCanonical.encode(DefinitionCanonical.tree(body));
    }

    @Test
    void
            independentCheckerApprovesExactPublicPrivateSelectionWithoutPublicDisclosureOrCounterChange()
                    throws Exception {
        var selected = selected();
        var receipt =
                service()
                        .execute(
                                tenant,
                                root,
                                ProductDefinitionCommand.APPROVE_SELECTION,
                                reviewBody(selected, 2, null))
                        .block();
        assertThat(receipt).isInstanceOf(PrivateReceipt.class);
        var result = (PrivateReceipt) receipt;
        assertThat(result.decision()).isEqualTo("APPROVED");
        assertThat(result.costVersion()).isEqualTo(2);
        var decision =
                inserted.stream()
                        .filter(Decision.class::isInstance)
                        .map(Decision.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertThat(decision.submissionId()).isEqualTo(selected.submission().submissionId());
        assertThat(decision.publicVersion()).isEqualTo(1);
        assertThat(decision.costVersion()).isEqualTo(2);
        assertThat(inserted)
                .anyMatch(Review.class::isInstance)
                .anyMatch(ReviewPrivate.class::isInstance)
                .anyMatch(CostOutbox.class::isInstance)
                .noneMatch(value -> value instanceof History || value instanceof Outbox);
        verify(drafts)
                .replaceActiveReview(
                        argThat(change -> result.reviewId().equals(change.newReviewId())));
        verify(drafts, never()).replacePublicHead(any());
        verify(drafts, never()).replaceCostHead(any());
    }

    @Test
    void rejectionRetainsPrivateReasonAndClearsApprovalWithoutChangingPublicPhase()
            throws Exception {
        var selected = selected();
        var receipt =
                service()
                        .execute(
                                tenant,
                                root,
                                ProductDefinitionCommand.REJECT_SELECTION,
                                reviewBody(selected, 2, "Private cost justification missing"))
                        .block();
        assertThat(((PrivateReceipt) receipt).decision()).isEqualTo("REJECTED");
        assertThat(
                        inserted.stream()
                                .filter(Decision.class::isInstance)
                                .map(Decision.class::cast)
                                .findFirst()
                                .orElseThrow()
                                .reason())
                .isEqualTo("Private cost justification missing");
        verify(drafts).replaceActiveReview(argThat(change -> change.newReviewId() == null));
        verify(drafts, never()).replacePublicHead(any());
        assertThat(inserted)
                .noneMatch(value -> value instanceof History || value instanceof Outbox);
    }

    @Test
    void everyRetainedSubmissionContributorIsExcludedFromCheckerPool() throws Exception {
        var selected = selected();
        when(submissions.findDistinctTop513ByTenantIdAndRootId(tenant, root))
                .thenReturn(
                        Flux.just(
                                new LifecycleSubmissionRepository.Contributor() {
                                    public String getActorIssuer() {
                                        return "https://issuer.example.invalid";
                                    }

                                    public String getActorId() {
                                        return "staff-maker";
                                    }
                                }));
        StepVerifier.create(
                        service()
                                .execute(
                                        tenant,
                                        root,
                                        ProductDefinitionCommand.APPROVE_SELECTION,
                                        reviewBody(selected, 2, null)))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(409))
                .verify();
        assertThat(inserted).isEmpty();
        assertThat(order).contains("rollback");
    }

    @Test
    void staleCostVersionCannotReceiveApproval() throws Exception {
        var selected = selected();
        StepVerifier.create(
                        service()
                                .execute(
                                        tenant,
                                        root,
                                        ProductDefinitionCommand.APPROVE_SELECTION,
                                        reviewBody(selected, 1, null)))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(409))
                .verify();
        assertThat(inserted).isEmpty();
        verify(drafts, never()).replaceActiveReview(any());
    }

    @Test
    void approvalRejectsFingerprintDifferentFromExactProtectedSelection() throws Exception {
        var selected = selected();
        var body =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        DefinitionCanonical.parse(reviewBody(selected, 2, null));
        body.put("selectionFingerprint", "f".repeat(64));
        StepVerifier.create(
                        service()
                                .execute(
                                        tenant,
                                        root,
                                        ProductDefinitionCommand.APPROVE_SELECTION,
                                        body.toString()))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(409))
                .verify();
        assertThat(inserted).isEmpty();
    }

    @Test
    void independentCheckerLimitFailsClosedWithoutCollectingUnboundedContributorHistory()
            throws Exception {
        var selected = selected();
        when(revisions.findDistinctTop513ByTenantIdAndRootId(tenant, root))
                .thenReturn(
                        Flux.range(0, 513)
                                .map(
                                        index ->
                                                new LifecycleRevisionRepository.Contributor() {
                                                    public String getActorIssuer() {
                                                        return "https://issuer.example.invalid";
                                                    }

                                                    public String getActorId() {
                                                        return "retained-maker-" + index;
                                                    }
                                                }));
        StepVerifier.create(
                        service()
                                .execute(
                                        tenant,
                                        root,
                                        ProductDefinitionCommand.APPROVE_SELECTION,
                                        reviewBody(selected, 2, null)))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(409))
                .verify();
        assertThat(inserted).isEmpty();
    }

    UUID approved(Selected selected) {
        var id = UUID.randomUUID();
        var previous = selected.head().root();
        var active =
                new ProductDefinitionDraftRoot(
                        root,
                        tenant,
                        previous.productId(),
                        previous.productCode(),
                        "BANKING",
                        "ACCOUNT_TARIFF_V1",
                        1,
                        "IN_REVIEW",
                        1,
                        previous.publicHead(),
                        id,
                        previous.createdAt());
        when(drafts.findByTenantIdAndRootId(tenant, root)).thenReturn(Mono.just(active));
        var review =
                new Review(
                        id,
                        tenant,
                        root,
                        previous.publicHead(),
                        selected.head().revision().publicDigest(),
                        "https://issuer.example.invalid",
                        "independent-checker",
                        stamp.minusSeconds(20));
        var selection =
                new ProductDefinitionLifecycleContracts.Selection(
                        tenant,
                        root,
                        selected.submission().submissionId(),
                        previous.publicHead(),
                        selected.head().revision().publicDigest(),
                        1,
                        selected.cost().costRevisionId(),
                        selected.cost().costDigest(),
                        2);
        var fingerprint =
                DefinitionCanonical.hash(
                        "product-definition-selection-v1",
                        DefinitionCanonical.encode(DefinitionCanonical.tree(selection)));
        when(reviews.findByTenantIdAndRootIdAndReviewId(tenant, root, id))
                .thenReturn(Mono.just(review));
        when(privateReviews.findByTenantIdAndRootIdAndReviewId(tenant, root, id))
                .thenReturn(
                        Mono.just(
                                new ReviewPrivate(
                                        id,
                                        tenant,
                                        root,
                                        selected.cost().costRevisionId(),
                                        fingerprint)));
        when(decisions.findByTenantIdAndRootIdAndReviewId(tenant, root, id))
                .thenReturn(
                        Mono.just(
                                new Decision(
                                        id,
                                        tenant,
                                        root,
                                        selected.submission().submissionId(),
                                        1,
                                        2,
                                        "APPROVED",
                                        null,
                                        review.actorIssuer(),
                                        review.actorId(),
                                        review.createdAt())));
        when(publicationControls.countByTenantIdAndRootIdAndState(tenant, root, "ACTIVE"))
                .thenReturn(Mono.just(0L));
        when(identities.existsByTenantIdAndRootIdAndProductIdAndVersionNumber(
                        eq(tenant), eq(root), eq(previous.productId()), anyLong()))
                .thenReturn(Mono.just(false));
        return id;
    }

    String publishBody(UUID review) {
        return DefinitionCanonical.encode(
                DefinitionCanonical.tree(
                        Map.of(
                                "operationId",
                                operation,
                                "reviewId",
                                review,
                                "expectedPublicVersion",
                                1,
                                "expectedCostVersion",
                                2,
                                "versionNumber",
                                17)));
    }

    @Test
    void publishAppendsVersionPublicationIdentityAndControlAsNotAdmittedUsingExactSelectedSnapshot()
            throws Exception {
        var selected = selected();
        var review = approved(selected);
        var receipt =
                service()
                        .execute(
                                tenant, root, ProductDefinitionCommand.PUBLISH, publishBody(review))
                        .block();
        assertThat(receipt).isInstanceOf(PublicReceipt.class);
        var result = (PublicReceipt) receipt;
        assertThat(result.phase()).isEqualTo("PUBLISHED");
        assertThat(result.publicVersion()).isEqualTo(2);
        assertThat(result.versionNumber()).isEqualTo(17L);
        assertThat(result.operationalAdmission()).isEqualTo("NOT_ADMITTED");
        assertThat(result.publicationState()).isEqualTo("ACTIVE");
        var version =
                inserted.stream()
                        .filter(Version.class::isInstance)
                        .map(Version.class::cast)
                        .findFirst()
                        .orElseThrow();
        var publication =
                inserted.stream()
                        .filter(ProductDefinitionPublication.class::isInstance)
                        .map(ProductDefinitionPublication.class::cast)
                        .findFirst()
                        .orElseThrow();
        var identity =
                inserted.stream()
                        .filter(VersionIdentity.class::isInstance)
                        .map(VersionIdentity.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertThat(version.productId()).isEqualTo(selected.head().root().productId());
        assertThat(version.versionNumber()).isEqualTo(17L);
        assertThat(publication.canonicalDefinition())
                .isEqualTo(selected.head().revision().canonicalDefinition());
        assertThat(publication.productVersionId()).isEqualTo(version.productVersionId());
        assertThat(identity.productId()).isEqualTo(version.productId());
        assertThat(identity.publicationId()).isEqualTo(publication.publicationId());
        assertThat(identity.versionNumber()).isEqualTo(17L);
        assertThat(inserted)
                .anyMatch(ProductDefinitionPublicationControl.class::isInstance)
                .noneMatch(value -> value instanceof CostOutbox || value instanceof ProductBirth);
        verify(drafts)
                .replacePublicHead(
                        argThat(
                                change ->
                                        change.newPhase().equals("PUBLISHED")
                                                && change.newHead()
                                                        .equals(
                                                                selected.head()
                                                                        .root()
                                                                        .publicHead())));
    }

    @Test
    void activePublicationPreventsImplicitSupersession() throws Exception {
        var selected = selected();
        var review = approved(selected);
        when(publicationControls.countByTenantIdAndRootIdAndState(tenant, root, "ACTIVE"))
                .thenReturn(Mono.just(1L));
        StepVerifier.create(
                        service()
                                .execute(
                                        tenant,
                                        root,
                                        ProductDefinitionCommand.PUBLISH,
                                        publishBody(review)))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(409))
                .verify();
        assertThat(inserted).isEmpty();
    }

    @Test
    void versionNumberCannotBeReusedAcrossWithdrawnGovernedHistory() throws Exception {
        var selected = selected();
        var review = approved(selected);
        when(identities.existsByTenantIdAndRootIdAndProductIdAndVersionNumber(
                        tenant, root, selected.head().root().productId(), 17L))
                .thenReturn(Mono.just(true));
        StepVerifier.create(
                        service()
                                .execute(
                                        tenant,
                                        root,
                                        ProductDefinitionCommand.PUBLISH,
                                        publishBody(review)))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(409))
                .verify();
        assertThat(inserted).isEmpty();
    }

    @Test
    void existingReviewWithoutExplicitImmutableDecisionCannotPublish() throws Exception {
        var selected = selected();
        var review = approved(selected);
        when(decisions.findByTenantIdAndRootIdAndReviewId(tenant, root, review))
                .thenReturn(Mono.empty());
        StepVerifier.create(
                        service()
                                .execute(
                                        tenant,
                                        root,
                                        ProductDefinitionCommand.PUBLISH,
                                        publishBody(review)))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(409))
                .verify();
        assertThat(inserted).isEmpty();
    }

    ProductDefinitionPublication published(Head current, int stateVersion, String state) {
        var publication =
                new ProductDefinitionPublication(
                        UUID.randomUUID(),
                        tenant,
                        root,
                        UUID.randomUUID(),
                        current.revision().revisionId(),
                        UUID.randomUUID(),
                        current.revision().canonicalDefinition(),
                        current.revision().publicDigest(),
                        DefinitionCanonical.ENCODING,
                        "NOT_ADMITTED",
                        stamp.minusSeconds(10));
        when(publications.findByTenantIdAndRootIdAndPublicationId(
                        tenant, root, publication.publicationId()))
                .thenReturn(Mono.just(publication));
        when(publicationControls.findByTenantIdAndRootIdAndPublicationId(
                        tenant, root, publication.publicationId()))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionPublicationControl(
                                        publication.publicationId(),
                                        tenant,
                                        root,
                                        state,
                                        stateVersion)));
        when(identities.findByTenantIdAndRootIdAndPublicationId(
                        tenant, root, publication.publicationId()))
                .thenReturn(
                        Mono.just(
                                new VersionIdentity(
                                        publication.productVersionId(),
                                        tenant,
                                        root,
                                        current.root().productId(),
                                        publication.publicationId(),
                                        17L,
                                        publication.createdAt())));
        return publication;
    }

    String withdrawBody(UUID publication, int version) {
        return DefinitionCanonical.encode(
                DefinitionCanonical.tree(
                        Map.of(
                                "operationId",
                                operation,
                                "publicationId",
                                publication,
                                "expectedStateVersion",
                                version)));
    }

    @Test
    void withdrawalChangesOnlyCurrentPublicationControlAndPublicEvidenceWithoutPrivateReads()
            throws Exception {
        var current = head("DRAFT");
        var publication = published(current, 0, "ACTIVE");
        var receipt =
                service()
                        .execute(
                                tenant,
                                root,
                                ProductDefinitionCommand.WITHDRAW_PUBLICATION,
                                withdrawBody(publication.publicationId(), 0))
                        .block();
        var result = (PublicReceipt) receipt;
        assertThat(result.publicationState()).isEqualTo("WITHDRAWN");
        assertThat(result.publicationStateVersion()).isEqualTo(1);
        assertThat(result.phase()).isEqualTo("DRAFT");
        assertThat(result.publicVersion()).isEqualTo(1);
        assertThat(result.versionNumber()).isEqualTo(17L);
        verify(drafts)
                .withdrawPublication(
                        argThat(
                                change ->
                                        change.publicationId().equals(publication.publicationId())
                                                && change.expectedVersion() == 0));
        verify(drafts, never()).replacePublicHead(any());
        verifyNoInteractions(costs, costRevisions, reviews, privateReviews, decisions);
        assertThat(inserted)
                .noneMatch(
                        value ->
                                value instanceof ProductDefinitionPublication
                                        || value instanceof Version
                                        || value instanceof CostOutbox)
                .anyMatch(Outbox.class::isInstance);
    }

    @Test
    void alreadyWithdrawnPublicationCannotBeWithdrawnAgainUnderNewOperation() throws Exception {
        var current = head("PUBLISHED");
        var publication = published(current, 1, "WITHDRAWN");
        StepVerifier.create(
                        service()
                                .execute(
                                        tenant,
                                        root,
                                        ProductDefinitionCommand.WITHDRAW_PUBLICATION,
                                        withdrawBody(publication.publicationId(), 1)))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(409))
                .verify();
        assertThat(inserted).isEmpty();
    }

    @Test
    void nativeAdmissionReceivesServerPreparedBytesAndSeparateOriginalIntentBinding()
            throws Exception {
        var receipt =
                service()
                        .execute(tenant, root, ProductDefinitionCommand.CREATE_DRAFT, createBody())
                        .block();
        assertThat(receipt).isNotNull();
        assertThat(admittedPlan.path("encoding").asText())
                .isEqualTo("product-definition-native-plan-v1");
        assertThat(admittedPlan.path("validationBoundary").asText()).isEqualTo("TRUSTED_SERVICE");
        assertThat(admittedPlan.path("originalIntentDigest").asText())
                .isEqualTo(
                        DefinitionCanonical.intentDigest(
                                DefinitionCanonical.parse(admittedOriginal)));
        var revision =
                inserted.stream()
                        .filter(ProductDefinitionDraftRevision.class::isInstance)
                        .map(ProductDefinitionDraftRevision.class::cast)
                        .findFirst()
                        .orElseThrow();
        var cost =
                inserted.stream()
                        .filter(ProductDefinitionCostRevision.class::isInstance)
                        .map(ProductDefinitionCostRevision.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertThat(admittedPlan.at("/publicSnapshot/canonical").asText())
                .isEqualTo(revision.canonicalDefinition());
        assertThat(admittedPlan.at("/privateSnapshot/canonical").asText())
                .isEqualTo(cost.canonicalCost());
    }

    @Test
    void changedPrivateCostHeadAndVersionCannotBeSilentlyReplacedAfterEditorRead()
            throws Exception {
        var current = head("DRAFT");
        when(costs.findByTenantIdAndRootId(tenant, root))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionCostControl(
                                        root, tenant, UUID.randomUUID(), 3)));
        StepVerifier.create(
                        service()
                                .execute(
                                        tenant,
                                        root,
                                        ProductDefinitionCommand.EDIT_COST,
                                        costBody(current, current.revision().revisionId())))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(409))
                .verify();
        assertThat(inserted).isEmpty();
        verify(drafts, never()).replaceCostHead(any());
    }

    static final class RecordingManager extends AbstractReactiveTransactionManager {
        private final List<String> events;

        RecordingManager(List<String> events) {
            this.events = events;
        }

        protected Object doGetTransaction(TransactionSynchronizationManager manager) {
            return new Object();
        }

        protected Mono<Void> doBegin(
                TransactionSynchronizationManager manager,
                Object transaction,
                TransactionDefinition definition) {
            return Mono.fromRunnable(() -> events.add("begin"));
        }

        protected Mono<Void> doCommit(
                TransactionSynchronizationManager manager, GenericReactiveTransaction transaction) {
            return Mono.fromRunnable(() -> events.add("commit"));
        }

        protected Mono<Void> doRollback(
                TransactionSynchronizationManager manager, GenericReactiveTransaction transaction) {
            return Mono.fromRunnable(() -> events.add("rollback"));
        }
    }
}

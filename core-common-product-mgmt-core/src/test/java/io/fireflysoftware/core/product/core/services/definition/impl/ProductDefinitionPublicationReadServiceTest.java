package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationDiscoveryView.PublicationDiscoveryState;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationReadinessView.PublicationReadinessStatus;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.*;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionPublicationReadRows.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionPublicationReadRepositories.*;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.reactive.TransactionContextManager;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

class ProductDefinitionPublicationReadServiceTest {
    final ProductDefinitionReviewReadServiceTest f;
    final ProductDefinitionPublicationReadPersistence persistence =
            mock(ProductDefinitionPublicationReadPersistence.class);
    final RootMetadataRepository drafts = mock(RootMetadataRepository.class);
    final CostControlMetadataRepository controls = mock(CostControlMetadataRepository.class);
    final ProductDefinitionPublicationReadAuthorization auth =
            mock(ProductDefinitionPublicationReadAuthorization.class);
    final ProductDefinitionPublicationReadNativeRepository nativeReads =
            mock(ProductDefinitionPublicationReadNativeRepository.class);
    final ProductDefinitionPublicationReadRepositories reads =
            mock(ProductDefinitionPublicationReadRepositories.class);
    final RevisionMetadataRepository revisions = mock(RevisionMetadataRepository.class);
    final ReviewMetadataRepository reviews = mock(ReviewMetadataRepository.class);
    final ReviewCostIdentityRepository identities = mock(ReviewCostIdentityRepository.class);
    final DecisionMetadataRepository decisions = mock(DecisionMetadataRepository.class);
    final SubmissionMetadataRepository submissions = mock(SubmissionMetadataRepository.class);
    final PublishHistoryRepository history = mock(PublishHistoryRepository.class);
    final PublicationMetadataRepository publications = mock(PublicationMetadataRepository.class);
    final PublicPublishActionRepository actions = mock(PublicPublishActionRepository.class);
    final VersionIdentityMetadataRepository versions =
            mock(VersionIdentityMetadataRepository.class);
    final PublicationControlMetadataRepository publicationControls =
            mock(PublicationControlMetadataRepository.class);
    final UUID review = UUID.randomUUID();
    final UUID contextId = UUID.randomUUID();

    ProductDefinitionPublicationReadServiceTest() throws Exception {
        f = new ProductDefinitionReviewReadServiceTest();
        var readTransactions = f.persistence.readTransactions();
        when(persistence.readTransactions()).thenReturn(readTransactions);
        when(persistence.drafts()).thenReturn(drafts);
        when(persistence.costs()).thenReturn(controls);
        when(persistence.publicationReadNative()).thenReturn(nativeReads);
        when(persistence.publicationReads()).thenReturn(reads);
        when(persistence.lifecyclePublicationControlRepository()).thenReturn(publicationControls);
        when(reads.revisionMetadata()).thenReturn(revisions);
        when(reads.reviewMetadata()).thenReturn(reviews);
        when(reads.reviewCostIdentity()).thenReturn(identities);
        when(reads.decisionMetadata()).thenReturn(decisions);
        when(reads.submissionMetadata()).thenReturn(submissions);
        when(reads.publishHistory()).thenReturn(history);
        when(reads.publicationMetadata()).thenReturn(publications);
        when(reads.publicPublishAction()).thenReturn(actions);
        when(persistence.lifecycleVersionIdentityRepository()).thenReturn(versions);
        var actor =
                SecurityPrincipal.builder()
                        .issuer("https://issuer.example.invalid")
                        .subject("resumed-maker")
                        .tenantId(f.tenant.toString())
                        .scopes(
                                java.util.Set.of(
                                        "product:definitions:publish",
                                        "product:definitions:withdraw"))
                        .build();
        when(auth.authorize(any()))
                .thenAnswer(
                        call ->
                                f.outside("authorize")
                                        .thenReturn(
                                                new ProductDefinitionPublicationReadAuthorization
                                                        .Admission(
                                                        actor,
                                                        call.getArgument(0),
                                                        f.stamp.plusSeconds(300).toEpochMilli(),
                                                        f.stamp.plusSeconds(30).toEpochMilli())));
        when(auth.reauthorize(any())).thenAnswer(call -> f.outside("reauthorize"));
        var context =
                new ProductDefinitionPublicationReadNativeRepository.ReadContext(
                        contextId, f.stamp, f.stamp.plusSeconds(20));
        when(nativeReads.openReadiness(
                        eq(f.tenant),
                        eq(f.root),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyLong(),
                        anyString()))
                .thenReturn(inside("native-open", context));
        when(nativeReads.openDiscovery(
                        eq(f.tenant),
                        eq(f.root),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyLong(),
                        anyString()))
                .thenReturn(inside("native-open", context));
        when(nativeReads.isReadinessCurrent(any())).thenReturn(inside("native-live", true));
        when(nativeReads.isDiscoveryCurrent(any())).thenReturn(inside("native-live", true));
        when(drafts.findByTenantIdAndRootId(f.tenant, f.root))
                .thenReturn(inside("root", header("IN_REVIEW", review)));
        when(revisions.findByTenantIdAndRootIdAndRevisionId(f.tenant, f.root, f.revision))
                .thenReturn(
                        inside(
                                "revision",
                                new RevisionMetadata(
                                        f.revision, f.tenant, f.root, f.publicDigest)));
        when(reviews.findByTenantIdAndRootIdAndReviewId(f.tenant, f.root, review))
                .thenReturn(
                        inside(
                                "review",
                                new ReviewMetadata(
                                        review, f.tenant, f.root, f.revision, f.publicDigest)));
        when(identities.findByTenantIdAndRootIdAndReviewId(f.tenant, f.root, review))
                .thenReturn(
                        inside(
                                "cost-identity",
                                new ReviewCostIdentity(review, f.tenant, f.root, f.cost)));
        when(decisions.findByTenantIdAndRootIdAndReviewId(f.tenant, f.root, review))
                .thenReturn(
                        inside(
                                "decision",
                                new DecisionMetadata(
                                        review, f.tenant, f.root, f.submission, 2, 1, "APPROVED")));
        when(submissions.findByTenantIdAndRootIdAndSubmissionId(f.tenant, f.root, f.submission))
                .thenReturn(
                        inside(
                                "submission",
                                new SubmissionMetadata(
                                        f.submission,
                                        f.tenant,
                                        f.root,
                                        f.revision,
                                        f.publicDigest,
                                        2)));
        when(controls.findByTenantIdAndRootId(f.tenant, f.root))
                .thenReturn(
                        inside(
                                "control",
                                new ProductDefinitionCostControl(f.root, f.tenant, f.cost, 1)));
        when(history
                        .findTop2ByTenantIdAndRootIdAndEventTypeOrderByPublicStateVersionDescHistoryIdDesc(
                                f.tenant, f.root, "PUBLISH"))
                .thenReturn(
                        inside("history", List.<PublishHistory>of())
                                .flatMapMany(Flux::fromIterable));
        when(publicationControls.findTop2ByTenantIdAndRootIdAndState(f.tenant, f.root, "ACTIVE"))
                .thenReturn(
                        inside(
                                        "active",
                                        List
                                                .<io.fireflysoftware.core.product.models.entities
                                                                .definition
                                                                .ProductDefinitionPublicationControl>
                                                        of())
                                .flatMapMany(Flux::fromIterable));
        when(publications.existsByTenantIdAndRootId(f.tenant, f.root))
                .thenReturn(inside("publication-exists", false));
        clearInvocations(
                auth,
                persistence,
                drafts,
                controls,
                f.costs,
                nativeReads,
                reads,
                revisions,
                reviews,
                identities,
                decisions,
                submissions,
                history,
                publications,
                publicationControls);
    }

    ProductDefinitionDraftRoot header(String phase, UUID active) {
        return new ProductDefinitionDraftRoot(
                f.root,
                f.tenant,
                UUID.randomUUID(),
                "ACCOUNT",
                "BANKING",
                "ACCOUNT_TARIFF_V1",
                1,
                phase,
                2,
                f.revision,
                active,
                f.stamp.minusSeconds(100));
    }

    <T> Mono<T> inside(String name, T value) {
        return TransactionSynchronizationManager.forCurrentTransaction()
                .flatMap(
                        manager -> {
                            assertThat(manager.isActualTransactionActive()).isTrue();
                            assertThat(manager.isCurrentTransactionReadOnly()).isTrue();
                            f.order.add(name);
                            return Mono.just(value);
                        });
    }

    ProductDefinitionPublicationReadService service() {
        try {
            return (ProductDefinitionPublicationReadService)
                    Class.forName(
                                    "io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionPublicationReadServiceImpl")
                            .getConstructor(
                                    ProductDefinitionPublicationReadAuthorization.class,
                                    ProductDefinitionPublicationReadPersistence.class,
                                    java.time.Clock.class)
                            .newInstance(
                                    auth,
                                    persistence,
                                    java.time.Clock.fixed(f.stamp, java.time.ZoneOffset.UTC));
        } catch (ReflectiveOperationException missing) {
            throw new AssertionError("Missing actual publication read service", missing);
        }
    }

    @Test
    void readinessKeepsNativeAndMappedReadsInsideOneReadonlyTransactionAndPdpOutside() {
        var result = service().readiness(f.tenant, f.root).block();
        assertThat(result.status()).isEqualTo(PublicationReadinessStatus.READY);
        assertThat(f.order)
                .startsWith("authorize", "begin", "native-open", "native-live")
                .endsWith("native-live", "commit", "reauthorize");
        verify(nativeReads, times(2)).isReadinessCurrent(contextId);
        verifyNoInteractions(f.costs);
    }

    @Test
    void nativeEffectiveDeadlineIsReturnedWithoutRecomputingItFromApplicationPermit() {
        var result = service().readiness(f.tenant, f.root).block();
        assertThat(result.observation().databaseRecordedAt()).isEqualTo(f.stamp);
        assertThat(result.observation().admissionValidUntil()).isEqualTo(f.stamp.plusSeconds(20));
    }

    @Test
    void noCurrentApprovalDoesNotReadPrivateApprovalMetadata() {
        when(drafts.findByTenantIdAndRootId(f.tenant, f.root))
                .thenReturn(inside("root", header("DRAFT", null)));
        assertThat(service().readiness(f.tenant, f.root).block().status())
                .isEqualTo(PublicationReadinessStatus.NOT_READY);
        verifyNoInteractions(reviews, identities, decisions, submissions, controls, f.costs);
    }

    @Test
    void expiredNativeContextSuppressesTheResponse() {
        when(nativeReads.isReadinessCurrent(any()))
                .thenReturn(inside("native-live", true), inside("native-live", false));
        ProductDefinitionPublicationSnapshotsTest.assertUnavailable(
                () -> service().readiness(f.tenant, f.root).block());
        verify(auth, never()).reauthorize(any());
    }

    @Test
    void revokedPostReadAuthoritySuppressesAnOtherwiseReadyResponse() {
        when(auth.reauthorize(any()))
                .thenAnswer(
                        call ->
                                f.outside("reauthorize")
                                        .then(
                                                Mono.error(
                                                        new ResponseStatusException(
                                                                org.springframework.http.HttpStatus
                                                                        .FORBIDDEN,
                                                                "Current permission required"))));
        assertThatThrownBy(() -> service().readiness(f.tenant, f.root).block())
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(403));
        assertThat(f.order).endsWith("commit", "reauthorize");
    }

    @Test
    void absentNativeAdmissionFailsBeforeMappedRows() {
        when(nativeReads.openReadiness(
                        any(), any(), anyString(), anyString(), anyLong(), anyLong(), anyString()))
                .thenReturn(Mono.empty());
        ProductDefinitionPublicationSnapshotsTest.assertUnavailable(
                () -> service().readiness(f.tenant, f.root).block());
        verifyNoInteractions(drafts, reviews, identities, decisions, submissions);
    }

    @Test
    void ambientCallerTransactionIsRejectedBeforeAuthorityOrPersistence() {
        var service = service();
        var value =
                TransactionSynchronizationManager.forCurrentTransaction()
                        .flatMap(
                                manager -> {
                                    manager.setActualTransactionActive(true);
                                    return service.readiness(f.tenant, f.root);
                                })
                        .contextWrite(TransactionContextManager.createTransactionContext());
        ProductDefinitionPublicationSnapshotsTest.assertUnavailable(value::block);
        verifyNoInteractions(auth, persistence);
    }

    @Test
    void discoveryNoneUsesCurrentDiscoveryAuthorityAndOnlyBoundedPublicMetadata() {
        assertThat(service().discover(f.tenant, f.root).block().state())
                .isEqualTo(PublicationDiscoveryState.NONE);
        verify(nativeReads)
                .openDiscovery(
                        eq(f.tenant),
                        eq(f.root),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyLong(),
                        anyString());
        verify(nativeReads, times(2)).isDiscoveryCurrent(contextId);
        verifyNoInteractions(reviews, identities, decisions, submissions, controls, f.costs);
        assertThat(f.order).endsWith("native-live", "commit", "reauthorize");
    }

    @Test
    void legacyPublicationWithoutNativeOrderingEvidenceNeverBecomesNone() {
        when(publications.existsByTenantIdAndRootId(f.tenant, f.root))
                .thenReturn(inside("publication-exists", true));
        ProductDefinitionPublicationSnapshotsTest.assertUnavailable(
                () -> service().discover(f.tenant, f.root).block());
        assertThat(f.order).endsWith("rollback");
        verify(auth, never()).reauthorize(any());
    }

    @Test
    void nativeDeadlineOutsideAdmittedBoundsIsUnavailable() {
        when(nativeReads.openReadiness(
                        any(), any(), anyString(), anyString(), anyLong(), anyLong(), anyString()))
                .thenReturn(
                        inside(
                                "native-open",
                                new ProductDefinitionPublicationReadNativeRepository.ReadContext(
                                        contextId, f.stamp, f.stamp.plusSeconds(31))));
        ProductDefinitionPublicationSnapshotsTest.assertUnavailable(
                () -> service().readiness(f.tenant, f.root).block());
        verifyNoInteractions(drafts, reviews, identities, decisions, submissions);
    }

    UUID published(String state) {
        UUID publication = UUID.randomUUID(),
                productVersion = UUID.randomUUID(),
                product = UUID.randomUUID(),
                actionId = UUID.randomUUID();
        var recordedAt = f.stamp.minusSeconds(5);
        var header =
                new ProductDefinitionDraftRoot(
                        f.root,
                        f.tenant,
                        product,
                        "ACCOUNT",
                        "BANKING",
                        "ACCOUNT_TARIFF_V1",
                        1,
                        "DRAFT",
                        6,
                        UUID.randomUUID(),
                        null,
                        f.stamp.minusSeconds(100));
        when(drafts.findByTenantIdAndRootId(f.tenant, f.root)).thenReturn(inside("root", header));
        var selected =
                new PublishHistory(UUID.randomUUID(), f.tenant, f.root, actionId, 5, "PUBLISH");
        when(history
                        .findTop2ByTenantIdAndRootIdAndEventTypeOrderByPublicStateVersionDescHistoryIdDesc(
                                f.tenant, f.root, "PUBLISH"))
                .thenReturn(inside("history", selected).flux());
        var control =
                new io.fireflysoftware.core.product.models.entities.definition
                        .ProductDefinitionPublicationControl(
                        publication, f.tenant, f.root, state, state.equals("ACTIVE") ? 0 : 1);
        when(publicationControls.findTop2ByTenantIdAndRootIdAndState(f.tenant, f.root, "ACTIVE"))
                .thenReturn(
                        inside(
                                        "active",
                                        state.equals("ACTIVE")
                                                ? List.of(control)
                                                : List
                                                        .<io.fireflysoftware.core.product.models
                                                                        .entities.definition
                                                                        .ProductDefinitionPublicationControl>
                                                                of())
                                .flatMapMany(Flux::fromIterable));
        when(publicationControls.findByTenantIdAndRootIdAndPublicationId(
                        f.tenant, f.root, publication))
                .thenReturn(inside("publication-control", control));
        var receipt =
                new io.fireflysoftware.core.product.interfaces.dtos.definition
                        .ProductDefinitionLifecycleMessages.PublicReceipt(
                        new io.fireflysoftware.core.product.interfaces.dtos.definition
                                .ProductDefinitionLifecycleContracts.Scope(
                                f.tenant, f.root, UUID.randomUUID()),
                        io.fireflysoftware.core.product.interfaces.dtos.definition
                                .ProductDefinitionCommand.PUBLISH,
                        actionId,
                        recordedAt.toString(),
                        "PUBLISHED",
                        5,
                        f.revision,
                        publication,
                        productVersion,
                        7L,
                        "ACTIVE",
                        0,
                        "NOT_ADMITTED");
        when(actions.findByTenantIdAndRootIdAndActionId(f.tenant, f.root, actionId))
                .thenReturn(
                        inside(
                                "action",
                                new PublicPublishAction(
                                        actionId,
                                        f.tenant,
                                        f.root,
                                        "PUBLISH",
                                        "PUBLIC",
                                        ProductDefinitionCommandReceiptCodec.encode(receipt))));
        when(publications.findByTenantIdAndRootIdAndPublicationId(f.tenant, f.root, publication))
                .thenReturn(
                        inside(
                                "publication",
                                new PublicationMetadata(
                                        publication,
                                        f.tenant,
                                        f.root,
                                        review,
                                        f.revision,
                                        productVersion,
                                        f.publicDigest,
                                        "NOT_ADMITTED",
                                        recordedAt)));
        when(versions.findByTenantIdAndRootIdAndPublicationId(f.tenant, f.root, publication))
                .thenReturn(
                        inside(
                                "version-identity",
                                new ProductDefinitionLifecycleRows.VersionIdentity(
                                        productVersion,
                                        f.tenant,
                                        f.root,
                                        product,
                                        publication,
                                        7L,
                                        recordedAt)));
        when(decisions.findByTenantIdAndRootIdAndReviewId(f.tenant, f.root, review))
                .thenReturn(
                        inside(
                                "decision",
                                new DecisionMetadata(
                                        review, f.tenant, f.root, f.submission, 4, 1, "APPROVED")));
        when(submissions.findByTenantIdAndRootIdAndSubmissionId(f.tenant, f.root, f.submission))
                .thenReturn(
                        inside(
                                "submission",
                                new SubmissionMetadata(
                                        f.submission,
                                        f.tenant,
                                        f.root,
                                        f.revision,
                                        f.publicDigest,
                                        4)));
        return publication;
    }

    @Test
    void currentActivePublicationCanBeResumedEvenWhileANewerDraftIsBeingEdited() {
        UUID publication = published("ACTIVE");
        var result = service().discover(f.tenant, f.root).block();
        assertThat(result.state()).isEqualTo(PublicationDiscoveryState.ACTIVE);
        assertThat(result.publication().publicationId()).isEqualTo(publication);
        verifyNoInteractions(f.costs);
        verify(publications, never()).existsByTenantIdAndRootId(any(), any());
        assertThat(f.order).endsWith("native-live", "commit", "reauthorize");
    }

    @Test
    void withdrawnLatestPublicationIsReturnedAsRetainedMetadataOnly() {
        UUID publication = published("WITHDRAWN");
        var result = service().discover(f.tenant, f.root).block();
        assertThat(result.state()).isEqualTo(PublicationDiscoveryState.RETAINED_WITHDRAWN);
        assertThat(result.publication().publicationId()).isEqualTo(publication);
        assertThat(result.publication().stateVersion()).isEqualTo(1);
        verifyNoInteractions(f.costs);
    }

    @Test
    void ambiguousHighestHistoryFailsBeforeAnyPublicationIsSelected() {
        var one =
                new PublishHistory(
                        UUID.randomUUID(), f.tenant, f.root, UUID.randomUUID(), 2, "PUBLISH");
        var two =
                new PublishHistory(
                        UUID.randomUUID(), f.tenant, f.root, UUID.randomUUID(), 2, "PUBLISH");
        when(history
                        .findTop2ByTenantIdAndRootIdAndEventTypeOrderByPublicStateVersionDescHistoryIdDesc(
                                f.tenant, f.root, "PUBLISH"))
                .thenReturn(inside("history", List.of(one, two)).flatMapMany(Flux::fromIterable));
        ProductDefinitionPublicationSnapshotsTest.assertUnavailable(
                () -> service().discover(f.tenant, f.root).block());
        verifyNoInteractions(actions, publications);
    }
}

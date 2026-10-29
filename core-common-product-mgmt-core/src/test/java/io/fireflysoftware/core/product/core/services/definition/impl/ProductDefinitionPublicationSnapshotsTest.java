package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.ReadObservation;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationReadinessView.PublicationReadinessStatus;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionCostControl;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionDraftRoot;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionPublicationReadRows.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

class ProductDefinitionPublicationSnapshotsTest {
    final UUID tenant = UUID.randomUUID(), rootId = UUID.randomUUID(), product = UUID.randomUUID();
    final UUID revision = UUID.randomUUID(),
            reviewId = UUID.randomUUID(),
            submissionId = UUID.randomUUID();
    final UUID costHead = UUID.randomUUID();
    final String digest = "a".repeat(64);
    final Instant time = Instant.parse("2026-10-25T00:00:00Z");
    final ReadObservation observation = new ReadObservation(time, time.plusSeconds(30));
    final RevisionMetadata publicHead = new RevisionMetadata(revision, tenant, rootId, digest);
    final ReviewMetadata review = new ReviewMetadata(reviewId, tenant, rootId, revision, digest);
    final ReviewCostIdentity costIdentity =
            new ReviewCostIdentity(reviewId, tenant, rootId, costHead);
    final SubmissionMetadata submission =
            new SubmissionMetadata(submissionId, tenant, rootId, revision, digest, 2);
    final DecisionMetadata decision =
            new DecisionMetadata(reviewId, tenant, rootId, submissionId, 2, 1, "APPROVED");
    final ProductDefinitionCostControl control =
            new ProductDefinitionCostControl(rootId, tenant, costHead, 1);

    ProductDefinitionDraftRoot header(String phase, UUID active, UUID head, int version) {
        return new ProductDefinitionDraftRoot(
                rootId,
                tenant,
                product,
                "ACCOUNT",
                "BANKING",
                "ACCOUNT_TARIFF_V1",
                1,
                phase,
                version,
                head,
                active,
                time);
    }

    @Test
    void exactApprovedCurrentTupleReturnsOnlyReadyCoordinates() {
        var view =
                ProductDefinitionPublicationSnapshots.readiness(
                        tenant,
                        rootId,
                        header("IN_REVIEW", reviewId, revision, 2),
                        publicHead,
                        review,
                        costIdentity,
                        decision,
                        submission,
                        control,
                        observation);
        assertThat(view.status()).isEqualTo(PublicationReadinessStatus.READY);
        assertThat(view.selection().reviewId()).isEqualTo(reviewId);
        assertThat(view.selection().publicVersion()).isEqualTo(2);
        assertThat(view.selection().costVersion()).isEqualTo(1);
    }

    @Test
    void draftOrNoActiveApprovalDoesNotRequirePrivateRows() {
        for (var header :
                List.of(
                        header("DRAFT", null, revision, 2),
                        header("IN_REVIEW", null, revision, 2))) {
            var view =
                    ProductDefinitionPublicationSnapshots.readiness(
                            tenant,
                            rootId,
                            header,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            observation);
            assertThat(view.status()).isEqualTo(PublicationReadinessStatus.NOT_READY);
            assertThat(view.selection()).isNull();
        }
    }

    @Test
    void changedCostHeadOrVersionProducesNotReadyWithoutCoordinates() {
        var changed = new ProductDefinitionCostControl(rootId, tenant, UUID.randomUUID(), 2);
        var view =
                ProductDefinitionPublicationSnapshots.readiness(
                        tenant,
                        rootId,
                        header("IN_REVIEW", reviewId, revision, 2),
                        publicHead,
                        review,
                        costIdentity,
                        decision,
                        submission,
                        changed,
                        observation);
        assertThat(view.status()).isEqualTo(PublicationReadinessStatus.NOT_READY);
        assertThat(view.selection()).isNull();
    }

    @Test
    void laterPublicHeadDoesNotSilentlyReuseAnOlderApproval() {
        UUID newer = UUID.randomUUID();
        var view =
                ProductDefinitionPublicationSnapshots.readiness(
                        tenant,
                        rootId,
                        header("IN_REVIEW", reviewId, newer, 3),
                        new RevisionMetadata(newer, tenant, rootId, "b".repeat(64)),
                        review,
                        costIdentity,
                        decision,
                        submission,
                        control,
                        observation);
        assertThat(view.status()).isEqualTo(PublicationReadinessStatus.NOT_READY);
        assertThat(view.selection()).isNull();
    }

    @Test
    void nonApprovedDecisionIsNotReadyAndRevealsNoReason() {
        var rejected =
                new DecisionMetadata(reviewId, tenant, rootId, submissionId, 2, 1, "REJECTED");
        var view =
                ProductDefinitionPublicationSnapshots.readiness(
                        tenant,
                        rootId,
                        header("IN_REVIEW", reviewId, revision, 2),
                        publicHead,
                        review,
                        costIdentity,
                        rejected,
                        submission,
                        control,
                        observation);
        assertThat(view.status()).isEqualTo(PublicationReadinessStatus.NOT_READY);
        assertThat(view.selection()).isNull();
    }

    @Test
    void missingActiveApprovalEvidenceIsUnavailable() {
        assertUnavailable(
                () ->
                        ProductDefinitionPublicationSnapshots.readiness(
                                tenant,
                                rootId,
                                header("IN_REVIEW", reviewId, revision, 2),
                                publicHead,
                                review,
                                costIdentity,
                                null,
                                submission,
                                control,
                                observation));
    }

    @Test
    void foreignScopeAndContradictoryImmutableSelectionAreUnavailable() {
        var foreign = new ReviewMetadata(reviewId, UUID.randomUUID(), rootId, revision, digest);
        assertUnavailable(
                () ->
                        ProductDefinitionPublicationSnapshots.readiness(
                                tenant,
                                rootId,
                                header("IN_REVIEW", reviewId, revision, 2),
                                publicHead,
                                foreign,
                                costIdentity,
                                decision,
                                submission,
                                control,
                                observation));
        var contradictory =
                new SubmissionMetadata(submissionId, tenant, rootId, UUID.randomUUID(), digest, 2);
        assertUnavailable(
                () ->
                        ProductDefinitionPublicationSnapshots.readiness(
                                tenant,
                                rootId,
                                header("IN_REVIEW", reviewId, revision, 2),
                                publicHead,
                                review,
                                costIdentity,
                                decision,
                                contradictory,
                                control,
                                observation));
    }

    @Test
    void latestUsesUniquePositiveNativePublicVersionAndRejectsAmbiguity() {
        var highest = history(5);
        var older = history(3);
        assertThat(
                        ProductDefinitionPublicationSnapshots.latestPublish(
                                tenant,
                                rootId,
                                header("DRAFT", null, revision, 6),
                                List.of(highest, older)))
                .contains(highest);
        for (var rows :
                List.of(
                        List.of(history(5), history(5)),
                        List.of(history(0)),
                        List.of(history(3), history(5)),
                        List.of(history(7)),
                        List.of(highest, older, history(1)))) {
            assertUnavailable(
                    () ->
                            ProductDefinitionPublicationSnapshots.latestPublish(
                                    tenant, rootId, header("DRAFT", null, revision, 6), rows));
        }
        assertThat(
                        ProductDefinitionPublicationSnapshots.latestPublish(
                                tenant, rootId, header("DRAFT", null, revision, 6), List.of()))
                .isEmpty();
    }

    PublishHistory history(int version) {
        return new PublishHistory(
                UUID.randomUUID(), tenant, rootId, UUID.randomUUID(), version, "PUBLISH");
    }

    static void assertUnavailable(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
    }
}

package io.fireflysoftware.core.product.core.services.definition.impl;

import static io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionPublicationSnapshotsTest.assertUnavailable;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.ReadObservation;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCommand;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleContracts.Scope;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationDiscoveryView.PublicationDiscoveryState;
import io.fireflysoftware.core.product.models.entities.definition.ProductDefinitionPublicationControl;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionDraftRoot;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionLifecycleRows.VersionIdentity;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionPublicationReadRows.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class ProductDefinitionPublicationDiscoverySnapshotsTest {
    final UUID tenant = UUID.randomUUID(), root = UUID.randomUUID(), product = UUID.randomUUID();
    final UUID publication = UUID.randomUUID(),
            productVersion = UUID.randomUUID(),
            revision = UUID.randomUUID();
    final UUID review = UUID.randomUUID(),
            submission = UUID.randomUUID(),
            actionId = UUID.randomUUID();
    final Instant time = Instant.parse("2026-10-25T00:00:00Z");
    final String digest = "a".repeat(64);
    final ReadObservation observation =
            new ReadObservation(time.plusSeconds(1), time.plusSeconds(30));
    final ProductDefinitionDraftRoot header =
            new ProductDefinitionDraftRoot(
                    root,
                    tenant,
                    product,
                    "ACCOUNT",
                    "BANKING",
                    "ACCOUNT_TARIFF_V1",
                    1,
                    "PUBLISHED",
                    6,
                    revision,
                    review,
                    time.minusSeconds(60));
    final PublishHistory history =
            new PublishHistory(UUID.randomUUID(), tenant, root, actionId, 5, "PUBLISH");
    final PublicReceipt receipt =
            new PublicReceipt(
                    new Scope(tenant, root, UUID.randomUUID()),
                    ProductDefinitionCommand.PUBLISH,
                    actionId,
                    time.toString(),
                    "PUBLISHED",
                    5,
                    revision,
                    publication,
                    productVersion,
                    7L,
                    "ACTIVE",
                    0,
                    "NOT_ADMITTED");
    final PublicPublishAction action =
            new PublicPublishAction(
                    actionId,
                    tenant,
                    root,
                    "PUBLISH",
                    "PUBLIC",
                    ProductDefinitionCommandReceiptCodec.encode(receipt));
    final PublicationMetadata published =
            new PublicationMetadata(
                    publication,
                    tenant,
                    root,
                    review,
                    revision,
                    productVersion,
                    digest,
                    "NOT_ADMITTED",
                    time);
    final VersionIdentity identity =
            new VersionIdentity(productVersion, tenant, root, product, publication, 7L, time);
    final ReviewMetadata reviewed = new ReviewMetadata(review, tenant, root, revision, digest);
    final ReviewCostIdentity cost = new ReviewCostIdentity(review, tenant, root, UUID.randomUUID());
    final DecisionMetadata decision =
            new DecisionMetadata(review, tenant, root, submission, 4, 2, "APPROVED");
    final SubmissionMetadata submitted =
            new SubmissionMetadata(submission, tenant, root, revision, digest, 4);
    final ProductDefinitionPublicationControl active = control("ACTIVE", 0);

    ProductDefinitionPublicationControl control(String state, int version) {
        return new ProductDefinitionPublicationControl(publication, tenant, root, state, version);
    }

    ProductDefinitionPublicationSnapshots.PublicationEvidence evidence(
            PublicPublishAction a,
            PublicationMetadata p,
            VersionIdentity i,
            ProductDefinitionPublicationControl c) {
        return new ProductDefinitionPublicationSnapshots.PublicationEvidence(
                history, a, p, i, c, reviewed, cost, decision, submitted);
    }

    @Test
    void coherentLatestActivePublicationIsDiscoveredFromNativeHistory() {
        var view =
                ProductDefinitionPublicationSnapshots.discovery(
                        tenant,
                        root,
                        header,
                        Optional.of(history),
                        List.of(active),
                        true,
                        evidence(action, published, identity, active),
                        observation);
        assertThat(view.state()).isEqualTo(PublicationDiscoveryState.ACTIVE);
        assertThat(view.publication().publicationId()).isEqualTo(publication);
        assertThat(view.publication().stateVersion()).isZero();
    }

    @Test
    void retainedWithdrawalRemainsExplicitAndIsNotAnActivePublication() {
        var withdrawn = control("WITHDRAWN", 1);
        var view =
                ProductDefinitionPublicationSnapshots.discovery(
                        tenant,
                        root,
                        header,
                        Optional.of(history),
                        List.of(),
                        true,
                        evidence(action, published, identity, withdrawn),
                        observation);
        assertThat(view.state()).isEqualTo(PublicationDiscoveryState.RETAINED_WITHDRAWN);
        assertThat(view.publication().stateVersion()).isEqualTo(1);
    }

    @Test
    void noneRequiresNoPublicationRowsAndNoActiveControl() {
        var view =
                ProductDefinitionPublicationSnapshots.discovery(
                        tenant,
                        root,
                        header,
                        Optional.empty(),
                        List.of(),
                        false,
                        null,
                        observation);
        assertThat(view.state()).isEqualTo(PublicationDiscoveryState.NONE);
        assertThat(view.publication()).isNull();
        assertUnavailable(
                () ->
                        ProductDefinitionPublicationSnapshots.discovery(
                                tenant,
                                root,
                                header,
                                Optional.empty(),
                                List.of(active),
                                false,
                                null,
                                observation));
    }

    @Test
    void retainedLegacyRowsWithoutOrderingEvidenceAreUnavailable() {
        assertUnavailable(
                () ->
                        ProductDefinitionPublicationSnapshots.discovery(
                                tenant,
                                root,
                                header,
                                Optional.empty(),
                                List.of(),
                                true,
                                null,
                                observation));
    }

    @Test
    void multipleOrContradictoryActiveControlsAreUnavailable() {
        var other =
                new ProductDefinitionPublicationControl(
                        UUID.randomUUID(), tenant, root, "ACTIVE", 0);
        for (var controls : List.of(List.of(active, other), List.of(other)))
            assertUnavailable(
                    () ->
                            ProductDefinitionPublicationSnapshots.discovery(
                                    tenant,
                                    root,
                                    header,
                                    Optional.of(history),
                                    controls,
                                    true,
                                    evidence(action, published, identity, active),
                                    observation));
        assertUnavailable(
                () ->
                        ProductDefinitionPublicationSnapshots.discovery(
                                tenant,
                                root,
                                header,
                                Optional.of(history),
                                List.of(active),
                                true,
                                evidence(action, published, identity, control("WITHDRAWN", 1)),
                                observation));
    }

    @Test
    void strictCanonicalPublicPublishReceiptIsRequired() {
        var noncanonical =
                new PublicPublishAction(
                        actionId,
                        tenant,
                        root,
                        "PUBLISH",
                        "PUBLIC",
                        action.canonicalReceipt() + " ");
        assertUnavailable(
                () ->
                        ProductDefinitionPublicationSnapshots.discovery(
                                tenant,
                                root,
                                header,
                                Optional.of(history),
                                List.of(active),
                                true,
                                evidence(noncanonical, published, identity, active),
                                observation));
    }

    @Test
    void foreignActionAndUnrelatedPublicationSelectionAreUnavailable() {
        var foreign =
                new PublicPublishAction(
                        actionId,
                        UUID.randomUUID(),
                        root,
                        "PUBLISH",
                        "PUBLIC",
                        action.canonicalReceipt());
        assertUnavailable(
                () ->
                        ProductDefinitionPublicationSnapshots.discovery(
                                tenant,
                                root,
                                header,
                                Optional.of(history),
                                List.of(active),
                                true,
                                evidence(foreign, published, identity, active),
                                observation));
        var unrelated =
                new PublicationMetadata(
                        publication,
                        tenant,
                        root,
                        UUID.randomUUID(),
                        revision,
                        productVersion,
                        digest,
                        "NOT_ADMITTED",
                        time);
        assertUnavailable(
                () ->
                        ProductDefinitionPublicationSnapshots.discovery(
                                tenant,
                                root,
                                header,
                                Optional.of(history),
                                List.of(active),
                                true,
                                evidence(action, unrelated, identity, active),
                                observation));
    }

    @Test
    void publishedVersionMustBelongToTheOwningProductAndExactPublication() {
        var unrelated =
                new VersionIdentity(
                        productVersion, tenant, root, UUID.randomUUID(), publication, 7L, time);
        assertUnavailable(
                () ->
                        ProductDefinitionPublicationSnapshots.discovery(
                                tenant,
                                root,
                                header,
                                Optional.of(history),
                                List.of(active),
                                true,
                                evidence(action, published, unrelated, active),
                                observation));
    }

    @Test
    void withdrawnStateMustHaveAnActualAdvancedControlVersion() {
        assertUnavailable(
                () ->
                        ProductDefinitionPublicationSnapshots.discovery(
                                tenant,
                                root,
                                header,
                                Optional.of(history),
                                List.of(),
                                true,
                                evidence(action, published, identity, control("WITHDRAWN", 0)),
                                observation));
    }

    @Test
    void historyActionAndReceiptMustBindTheSameCommandOccurrence() {
        var otherReceipt =
                new PublicReceipt(
                        receipt.scope(),
                        receipt.command(),
                        UUID.randomUUID(),
                        receipt.recordedAt(),
                        receipt.phase(),
                        receipt.publicVersion(),
                        receipt.revisionId(),
                        receipt.publicationId(),
                        receipt.productVersionId(),
                        receipt.versionNumber(),
                        receipt.publicationState(),
                        receipt.publicationStateVersion(),
                        receipt.operationalAdmission());
        var inconsistent =
                new PublicPublishAction(
                        actionId,
                        tenant,
                        root,
                        "PUBLISH",
                        "PUBLIC",
                        ProductDefinitionCommandReceiptCodec.encode(otherReceipt));
        assertUnavailable(
                () ->
                        ProductDefinitionPublicationSnapshots.discovery(
                                tenant,
                                root,
                                header,
                                Optional.of(history),
                                List.of(active),
                                true,
                                evidence(inconsistent, published, identity, active),
                                observation));
    }
}

package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCommand;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.PublicReceipt;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationDiscoveryView;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationDiscoveryView.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationReadinessView;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationReadinessView.*;
import io.fireflysoftware.core.product.models.entities.definition.ProductDefinitionPublicationControl;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionCostControl;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionDraftRoot;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionLifecycleRows.VersionIdentity;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionPublicationReadRows.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class ProductDefinitionPublicationSnapshots {
    private ProductDefinitionPublicationSnapshots() {}

    static ProductDefinitionPublicationReadinessView readiness(
            UUID tenant,
            UUID root,
            ProductDefinitionDraftRoot header,
            RevisionMetadata revision,
            ReviewMetadata review,
            ReviewCostIdentity identity,
            DecisionMetadata decision,
            SubmissionMetadata submission,
            ProductDefinitionCostControl control,
            ReadObservation observation) {
        header(tenant, root, header);
        if (!"IN_REVIEW".equals(header.phase()) || header.activeReviewId() == null)
            return notReady(tenant, root, observation);
        require(
                revision != null
                        && review != null
                        && identity != null
                        && decision != null
                        && submission != null
                        && control != null);
        require(
                owner(tenant, root, revision.tenantId(), revision.rootId())
                        && owner(tenant, root, review.tenantId(), review.rootId())
                        && owner(tenant, root, identity.tenantId(), identity.rootId())
                        && owner(tenant, root, decision.tenantId(), decision.rootId())
                        && owner(tenant, root, submission.tenantId(), submission.rootId())
                        && owner(tenant, root, control.tenantId(), control.rootId()));
        UUID active = header.activeReviewId();
        require(
                active.equals(review.reviewId())
                        && active.equals(identity.reviewId())
                        && active.equals(decision.reviewId())
                        && id(identity.costRevisionId())
                        && header.publicHead().equals(revision.revisionId())
                        && digest(revision.publicDigest())
                        && id(submission.submissionId())
                        && id(submission.revisionId())
                        && submission.submissionId().equals(decision.submissionId())
                        && submission.revisionId().equals(review.revisionId())
                        && digest(review.publicDigest())
                        && review.publicDigest().equals(submission.publicDigest())
                        && positive(decision.publicVersion())
                        && nonnegative(decision.costVersion())
                        && decision.publicVersion().equals(submission.publicVersion())
                        && decision.state() != null
                        && Set.of("APPROVED", "REJECTED").contains(decision.state())
                        && nonnegative(control.costStateVersion())
                        && id(control.costHead()));
        if (!"APPROVED".equals(decision.state())
                || !header.publicHead().equals(review.revisionId())
                || !header.publicStateVersion().equals(decision.publicVersion())
                || !control.costHead().equals(identity.costRevisionId())
                || !control.costStateVersion().equals(decision.costVersion()))
            return notReady(tenant, root, observation);
        require(revision.publicDigest().equals(review.publicDigest()));
        return new ProductDefinitionPublicationReadinessView(
                tenant,
                root,
                PublicationReadinessStatus.READY,
                new PublicationReadySelection(
                        active, decision.publicVersion(), decision.costVersion()),
                observation,
                OperationalAdmission.NOT_ADMITTED);
    }

    static Optional<PublishHistory> latestPublish(
            UUID tenant, UUID root, ProductDefinitionDraftRoot header, List<PublishHistory> rows) {
        header(tenant, root, header);
        require(rows != null && rows.size() <= 2);
        for (var row : rows) {
            require(
                    row != null
                            && owner(tenant, root, row.tenantId(), row.rootId())
                            && id(row.historyId())
                            && id(row.actionId())
                            && "PUBLISH".equals(row.eventType())
                            && positive(row.publicStateVersion())
                            && row.publicStateVersion() <= header.publicStateVersion());
        }
        if (rows.size() == 2)
            require(
                    rows.get(0).publicStateVersion() > rows.get(1).publicStateVersion()
                            && !rows.get(0).historyId().equals(rows.get(1).historyId())
                            && !rows.get(0).actionId().equals(rows.get(1).actionId()));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    record PublicationEvidence(
            PublishHistory history,
            PublicPublishAction action,
            PublicationMetadata publication,
            VersionIdentity identity,
            ProductDefinitionPublicationControl control,
            ReviewMetadata review,
            ReviewCostIdentity cost,
            DecisionMetadata decision,
            SubmissionMetadata submission) {}

    static ProductDefinitionPublicationDiscoveryView discovery(
            UUID tenant,
            UUID root,
            ProductDefinitionDraftRoot header,
            Optional<PublishHistory> latest,
            List<ProductDefinitionPublicationControl> active,
            boolean anyPublications,
            PublicationEvidence evidence,
            ReadObservation observation) {
        header(tenant, root, header);
        require(latest != null && active != null && active.size() <= 1);
        for (var row : active)
            require(
                    row != null
                            && owner(tenant, root, row.tenantId(), row.rootId())
                            && id(row.publicationId())
                            && "ACTIVE".equals(row.state())
                            && nonnegative(row.stateVersion()));
        if (latest.isEmpty()) {
            require(!anyPublications && active.isEmpty() && evidence == null);
            return new ProductDefinitionPublicationDiscoveryView(
                    tenant,
                    root,
                    PublicationDiscoveryState.NONE,
                    null,
                    observation,
                    OperationalAdmission.NOT_ADMITTED);
        }
        latestPublish(tenant, root, header, List.of(latest.get()));
        require(anyPublications && evidence != null && latest.get().equals(evidence.history()));
        var action = evidence.action();
        var publication = evidence.publication();
        var identity = evidence.identity();
        var control = evidence.control();
        require(
                action != null
                        && publication != null
                        && identity != null
                        && control != null
                        && owner(tenant, root, action.tenantId(), action.rootId())
                        && owner(tenant, root, publication.tenantId(), publication.rootId())
                        && owner(tenant, root, identity.tenantId(), identity.rootId())
                        && owner(tenant, root, control.tenantId(), control.rootId())
                        && id(action.actionId())
                        && id(publication.publicationId())
                        && id(publication.reviewId())
                        && id(publication.revisionId())
                        && id(publication.productVersionId())
                        && id(identity.productVersionId())
                        && id(identity.publicationId())
                        && id(identity.productId())
                        && id(control.publicationId())
                        && action.actionId().equals(latest.get().actionId())
                        && "PUBLISH".equals(action.command())
                        && "PUBLIC".equals(action.visibility()));
        PublicReceipt receipt = publishReceipt(tenant, root, header, latest.get(), action);
        require(
                owner(tenant, root, receipt.scope().tenantId(), receipt.scope().rootId())
                        && receipt.actionId().equals(action.actionId())
                        && receipt.publicVersion().equals(latest.get().publicStateVersion())
                        && receipt.publicationId().equals(publication.publicationId())
                        && receipt.revisionId().equals(publication.revisionId())
                        && receipt.productVersionId().equals(publication.productVersionId())
                        && "NOT_ADMITTED".equals(publication.operationalAdmission())
                        && publication.createdAt() != null
                        && Instant.parse(receipt.recordedAt()).equals(publication.createdAt())
                        && identity.productVersionId().equals(publication.productVersionId())
                        && identity.publicationId().equals(publication.publicationId())
                        && identity.productId().equals(header.productId())
                        && receipt.versionNumber().equals(identity.versionNumber())
                        && publication.createdAt().equals(identity.recordedAt())
                        && control.publicationId().equals(publication.publicationId())
                        && nonnegative(control.stateVersion()));
        var review = evidence.review();
        var cost = evidence.cost();
        var decision = evidence.decision();
        var submission = evidence.submission();
        require(
                review != null
                        && cost != null
                        && decision != null
                        && submission != null
                        && owner(tenant, root, review.tenantId(), review.rootId())
                        && owner(tenant, root, cost.tenantId(), cost.rootId())
                        && owner(tenant, root, decision.tenantId(), decision.rootId())
                        && owner(tenant, root, submission.tenantId(), submission.rootId())
                        && id(review.reviewId())
                        && id(review.revisionId())
                        && id(submission.submissionId())
                        && id(submission.revisionId())
                        && id(decision.submissionId())
                        && publication.reviewId().equals(review.reviewId())
                        && review.reviewId().equals(cost.reviewId())
                        && id(cost.costRevisionId())
                        && review.reviewId().equals(decision.reviewId())
                        && "APPROVED".equals(decision.state())
                        && nonnegative(decision.costVersion())
                        && positive(decision.publicVersion())
                        && decision.publicVersion() == receipt.publicVersion() - 1
                        && decision.submissionId().equals(submission.submissionId())
                        && decision.publicVersion().equals(submission.publicVersion())
                        && review.revisionId().equals(submission.revisionId())
                        && publication.revisionId().equals(review.revisionId())
                        && digest(publication.publicDigest())
                        && publication.publicDigest().equals(review.publicDigest())
                        && review.publicDigest().equals(submission.publicDigest()));
        PublicationDiscoveryState state;
        if ("ACTIVE".equals(control.state())) {
            require(active.size() == 1 && control.equals(active.get(0)));
            state = PublicationDiscoveryState.ACTIVE;
        } else {
            require(
                    "WITHDRAWN".equals(control.state())
                            && control.stateVersion() > 0
                            && active.isEmpty());
            state = PublicationDiscoveryState.RETAINED_WITHDRAWN;
        }
        return new ProductDefinitionPublicationDiscoveryView(
                tenant,
                root,
                state,
                new DiscoveredPublication(publication.publicationId(), control.stateVersion()),
                observation,
                OperationalAdmission.NOT_ADMITTED);
    }

    static PublicReceipt publishReceipt(
            UUID tenant,
            UUID root,
            ProductDefinitionDraftRoot header,
            PublishHistory history,
            PublicPublishAction action) {
        latestPublish(tenant, root, header, List.of(history));
        require(
                action != null
                        && owner(tenant, root, action.tenantId(), action.rootId())
                        && id(action.actionId())
                        && action.actionId().equals(history.actionId())
                        && "PUBLISH".equals(action.command())
                        && "PUBLIC".equals(action.visibility()));
        PublicReceipt receipt;
        try {
            receipt =
                    (PublicReceipt)
                            ProductDefinitionCommandReceiptCodec.decode(
                                    action.canonicalReceipt(), ProductDefinitionCommand.PUBLISH);
        } catch (RuntimeException invalid) {
            throw unavailable();
        }
        require(
                owner(tenant, root, receipt.scope().tenantId(), receipt.scope().rootId())
                        && receipt.actionId().equals(action.actionId())
                        && receipt.publicVersion().equals(history.publicStateVersion()));
        return receipt;
    }

    static void header(UUID tenant, UUID root, ProductDefinitionDraftRoot value) {
        require(
                id(tenant)
                        && id(root)
                        && value != null
                        && owner(tenant, root, value.tenantId(), value.rootId())
                        && id(value.productId())
                        && id(value.publicHead())
                        && nonnegative(value.publicStateVersion())
                        && "ACCOUNT_TARIFF_V1".equals(value.profileCode())
                        && Integer.valueOf(1).equals(value.profileVersion())
                        && "BANKING".equals(value.businessArea())
                        && value.phase() != null
                        && Set.of("DRAFT", "IN_REVIEW", "PUBLISHED").contains(value.phase())
                        && (value.activeReviewId() == null || id(value.activeReviewId())));
    }

    private static ProductDefinitionPublicationReadinessView notReady(
            UUID tenant, UUID root, ReadObservation observation) {
        return new ProductDefinitionPublicationReadinessView(
                tenant,
                root,
                PublicationReadinessStatus.NOT_READY,
                null,
                observation,
                OperationalAdmission.NOT_ADMITTED);
    }

    static boolean owner(UUID tenant, UUID root, UUID actualTenant, UUID actualRoot) {
        return Objects.equals(tenant, actualTenant) && Objects.equals(root, actualRoot);
    }

    static boolean id(UUID value) {
        return value != null && !value.equals(new UUID(0, 0));
    }

    static boolean positive(Integer value) {
        return value != null && value > 0;
    }

    static boolean nonnegative(Integer value) {
        return value != null && value >= 0;
    }

    static boolean digest(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    static void require(boolean valid) {
        if (!valid) throw unavailable();
    }

    static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Product publication metadata unavailable");
    }
}

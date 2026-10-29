package io.fireflysoftware.core.product.models.entities.definition.lifecycle;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

public final class ProductDefinitionPublicationReadRows {
    private ProductDefinitionPublicationReadRows() {}

    @Table(value = "product_definition_revision", schema = "public")
    public record RevisionMetadata(
            @Id @Column("revision_id") UUID revisionId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("public_digest") String publicDigest) {}

    @Table(value = "product_definition_review", schema = "public")
    public record ReviewMetadata(
            @Id @Column("review_id") UUID reviewId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("revision_id") UUID revisionId,
            @Column("public_digest") String publicDigest) {}

    @Table(value = "product_definition_review_private", schema = "public")
    public record ReviewCostIdentity(
            @Id @Column("review_id") UUID reviewId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("cost_revision_id") UUID costRevisionId) {}

    @Table(value = "product_definition_review_decision", schema = "public")
    public record DecisionMetadata(
            @Id @Column("review_id") UUID reviewId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("submission_id") UUID submissionId,
            @Column("public_version") Integer publicVersion,
            @Column("cost_version") Integer costVersion,
            @Column("state") String state) {}

    @Table(value = "product_definition_submission", schema = "public")
    public record SubmissionMetadata(
            @Id @Column("submission_id") UUID submissionId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("revision_id") UUID revisionId,
            @Column("public_digest") String publicDigest,
            @Column("public_version") Integer publicVersion) {}

    @Table(value = "product_definition_history", schema = "public")
    public record PublishHistory(
            @Id @Column("history_id") UUID historyId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("action_id") UUID actionId,
            @Column("public_state_version") Integer publicStateVersion,
            @Column("event_type") String eventType) {}

    @Table(value = "product_definition_action", schema = "public")
    public record PublicPublishAction(
            @Id @Column("action_id") UUID actionId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("command") String command,
            @Column("visibility") String visibility,
            @Column("canonical_receipt") String canonicalReceipt) {}

    @Table(value = "product_definition_publication", schema = "public")
    public record PublicationMetadata(
            @Id @Column("publication_id") UUID publicationId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("review_id") UUID reviewId,
            @Column("revision_id") UUID revisionId,
            @Column("product_version_id") UUID productVersionId,
            @Column("public_digest") String publicDigest,
            @Column("operational_admission") String operationalAdmission,
            @Column("created_at") Instant createdAt) {}
}

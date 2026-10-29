package io.fireflysoftware.core.product.models.entities.definition.lifecycle;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(value = "product_definition_deposit_publication_prepared_snapshot", schema = "public")
public record DepositPublicationPreparationSnapshot(
        @Column("tenant_id") UUID tenantId, @Id @Column("root_id") UUID rootId,
        @Column("product_id") UUID productId, @Column("review_id") UUID reviewId,
        @Column("submission_id") UUID submissionId, @Column("revision_id") UUID revisionId,
        @Column("public_digest") String publicDigest, @Column("canonical_definition") String canonicalDefinition,
        @Column("encoding_version") String encodingVersion, @Column("public_version") Integer publicVersion,
        @Column("cost_revision_id") UUID costRevisionId, @Column("cost_digest") String costDigest,
        @Column("cost_version") Integer costVersion, @Column("selection_fingerprint") String selectionFingerprint) {}

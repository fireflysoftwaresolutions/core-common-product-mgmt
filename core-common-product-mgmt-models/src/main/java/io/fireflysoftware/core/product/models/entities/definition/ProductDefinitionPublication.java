package io.fireflysoftware.core.product.models.entities.definition;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table(value = "product_definition_publication", schema = "public")
public record ProductDefinitionPublication(
        @Id @Column("publication_id") UUID publicationId,
        @Column("tenant_id") UUID tenantId,
        @Column("root_id") UUID rootId,
        @Column("review_id") UUID reviewId,
        @Column("revision_id") UUID revisionId,
        @Column("product_version_id") UUID productVersionId,
        @Column("canonical_definition") String canonicalDefinition,
        @Column("public_digest") String publicDigest,
        @Column("encoding_version") String encodingVersion,
        @Column("operational_admission") String operationalAdmission,
        @Column("created_at") Instant createdAt) {}

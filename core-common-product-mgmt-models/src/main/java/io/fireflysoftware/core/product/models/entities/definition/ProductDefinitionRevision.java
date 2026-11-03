package io.fireflysoftware.core.product.models.entities.definition;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table(value = "product_definition_revision", schema = "public")
public record ProductDefinitionRevision(
        @Id @Column("revision_id") UUID revisionId,
        @Column("tenant_id") UUID tenantId,
        @Column("root_id") UUID rootId,
        @Column("canonical_definition") String canonicalDefinition,
        @Column("public_digest") String publicDigest,
        @Column("encoding_version") String encodingVersion,
        @Column("source_publication_id") UUID sourcePublicationId,
        @Column("source_publication_digest") String sourcePublicationDigest,
        @Column("created_at") Instant createdAt) {}

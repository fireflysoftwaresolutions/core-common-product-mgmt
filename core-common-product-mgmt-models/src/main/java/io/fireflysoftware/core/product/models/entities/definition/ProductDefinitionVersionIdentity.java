package io.fireflysoftware.core.product.models.entities.definition;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table(value = "product_definition_version_identity", schema = "public")
public record ProductDefinitionVersionIdentity(
        @Id @Column("product_version_id") UUID productVersionId,
        @Column("tenant_id") UUID tenantId,
        @Column("root_id") UUID rootId,
        @Column("product_id") UUID productId,
        @Column("publication_id") UUID publicationId) {}

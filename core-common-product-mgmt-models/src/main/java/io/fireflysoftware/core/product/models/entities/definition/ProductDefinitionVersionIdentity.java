package io.fireflysoftware.core.product.models.entities.definition;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table(value = "product_version", schema = "public")
public record ProductDefinitionVersionIdentity(
        @Id @Column("product_version_id") UUID productVersionId,
        @Column("product_id") UUID productId) {}

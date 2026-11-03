package io.fireflysoftware.core.product.models.entities.definition;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table(value = "product_definition_publication_control", schema = "public")
public record ProductDefinitionPublicationControl(
        @Id @Column("publication_id") UUID publicationId,
        @Column("tenant_id") UUID tenantId,
        @Column("root_id") UUID rootId,
        @Column("state") String state,
        @Column("state_version") Integer stateVersion) {}

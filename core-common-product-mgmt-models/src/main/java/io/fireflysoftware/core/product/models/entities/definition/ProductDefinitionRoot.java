package io.fireflysoftware.core.product.models.entities.definition;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table(value = "product_definition_root", schema = "public")
public record ProductDefinitionRoot(
        @Id @Column("root_id") UUID rootId,
        @Column("tenant_id") UUID tenantId,
        @Column("product_id") UUID productId,
        @Column("product_code") String productCode,
        @Column("business_area") String businessArea,
        @Column("profile_code") String profileCode,
        @Column("profile_version") Integer profileVersion,
        @Column("phase") String phase,
        @Column("public_state_version") Integer publicStateVersion,
        @Column("public_head") UUID publicHead,
        @Column("created_at") Instant createdAt) {}

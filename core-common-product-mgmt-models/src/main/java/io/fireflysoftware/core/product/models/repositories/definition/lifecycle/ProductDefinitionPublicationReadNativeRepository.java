package io.fireflysoftware.core.product.models.repositories.definition.lifecycle;

import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionDraftRoot;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@NoRepositoryBean
public interface ProductDefinitionPublicationReadNativeRepository
        extends Repository<ProductDefinitionDraftRoot, UUID> {
    record ReadContext(
            @Column("context_id") UUID contextId,
            @Column("recorded_at") Instant recordedAt,
            @Column("admission_valid_until") Instant admissionValidUntil) {}

    @Query(
            "SELECT context_id,recorded_at,admission_valid_until FROM"
                + " public.open_product_definition_publication_readiness(:tenantId,:rootId,:actorIssuer,:actorId,:actorUntil,:permitUntil,:policyProfile)")
    Mono<ReadContext> openReadiness(
            UUID tenantId,
            UUID rootId,
            String actorIssuer,
            String actorId,
            long actorUntil,
            long permitUntil,
            String policyProfile);

    @Query("SELECT public.assert_product_definition_publication_readiness_live(:contextId) AS live")
    Mono<Boolean> isReadinessCurrent(UUID contextId);

    @Query(
            "SELECT context_id,recorded_at,admission_valid_until FROM"
                + " public.open_product_definition_publication_discovery(:tenantId,:rootId,:actorIssuer,:actorId,:actorUntil,:permitUntil,:policyProfile)")
    Mono<ReadContext> openDiscovery(
            UUID tenantId,
            UUID rootId,
            String actorIssuer,
            String actorId,
            long actorUntil,
            long permitUntil,
            String policyProfile);

    @Query("SELECT public.assert_product_definition_publication_discovery_live(:contextId) AS live")
    Mono<Boolean> isDiscoveryCurrent(UUID contextId);
}

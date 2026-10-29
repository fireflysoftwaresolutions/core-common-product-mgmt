package io.fireflysoftware.core.product.models.repositories.definition.lifecycle;

import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionDraftRoot;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@NoRepositoryBean
public interface ProductDefinitionReviewReadNativeRepository
        extends Repository<ProductDefinitionDraftRoot, UUID> {
    record ReadContext(
            @org.springframework.data.relational.core.mapping.Column("context_id") UUID contextId,
            @org.springframework.data.relational.core.mapping.Column("recorded_at")
                    Instant recordedAt) {}

    @Query("SELECT recorded_at,db_now_ms,admission_until_millis AS admission_until_ms FROM public.product_definition_private_read_admission(:contextId)")
    Mono<ProductDefinitionCommandNativeRepository.NativeBudgetStamp> admission(
            @org.springframework.data.repository.query.Param("contextId") UUID contextId);

    @Query(
            "SELECT context_id, recorded_at FROM"
                + " public.open_product_definition_review_read(:tenantId,:rootId,:submissionId,:actorIssuer,:actorId,:actorUntil,:permitUntil,:policyProfile)")
    Mono<ReadContext> open(
            UUID tenantId,
            UUID rootId,
            UUID submissionId,
            String actorIssuer,
            String actorId,
            long actorUntil,
            long permitUntil,
            String policyProfile);

    @Query("SELECT public.assert_product_definition_review_read_live(:contextId)")
    Mono<Boolean> isCurrent(UUID contextId);

    @Query(
            "SELECT context_id, recorded_at FROM"
                + " public.open_product_definition_cost_read(:tenantId,:rootId,:actorIssuer,:actorId,:actorUntil,:permitUntil,:policyProfile)")
    Mono<ReadContext> openCost(
            UUID tenantId,
            UUID rootId,
            String actorIssuer,
            String actorId,
            long actorUntil,
            long permitUntil,
            String policyProfile);

    @Query("SELECT public.assert_product_definition_cost_read_live(:contextId) AS live")
    Mono<Boolean> isCostCurrent(UUID contextId);

    @Query(
            "SELECT context_id, recorded_at FROM"
                + " public.open_product_definition_current_review_read(:tenantId,:rootId,:actorIssuer,:actorId,:actorUntil,:permitUntil,:policyProfile)")
    Mono<ReadContext> openCurrent(
            UUID tenantId,
            UUID rootId,
            String actorIssuer,
            String actorId,
            long actorUntil,
            long permitUntil,
            String policyProfile);

    @Query("SELECT public.assert_product_definition_current_review_read_live(:contextId) AS live")
    Mono<Boolean> isCurrentSelectionLive(UUID contextId);
}

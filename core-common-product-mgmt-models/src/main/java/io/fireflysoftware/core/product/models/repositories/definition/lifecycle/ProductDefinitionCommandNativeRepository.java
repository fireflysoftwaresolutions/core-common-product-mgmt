package io.fireflysoftware.core.product.models.repositories.definition.lifecycle;

import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionDraftRoot;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@NoRepositoryBean
public interface ProductDefinitionCommandNativeRepository
        extends Repository<ProductDefinitionDraftRoot, UUID> {
    record NativeAdmission(
            @Column("command_id") UUID commandId,
            @Column("replay") Boolean replay,
            @Column("command_recorded_at") Instant commandRecordedAt,
            @Column("action_id") UUID actionId,
            @Column("retained_action_recorded_at") Instant retainedActionRecordedAt) {}

    @Query(
            "SELECT command_id,replay,command_recorded_at,action_id,retained_action_recorded_at"
                + " FROM"
                + " public.begin_product_definition_command(:tenantId,:rootId,:operationId,:command,:actorIssuer,:actorId,:actorUntil,:permitUntil,:policyProfile,:originalIntent,:intentDigest,:resourcePlan)")
    Mono<NativeAdmission> begin(
            @Param("tenantId") UUID tenantId,
            @Param("rootId") UUID rootId,
            @Param("operationId") UUID operationId,
            @Param("command") String command,
            @Param("actorIssuer") String actorIssuer,
            @Param("actorId") String actorId,
            @Param("actorUntil") long actorUntil,
            @Param("permitUntil") long permitUntil,
            @Param("policyProfile") String policyProfile,
            @Param("originalIntent") String originalIntent,
            @Param("intentDigest") String intentDigest,
            @Param("resourcePlan") String resourcePlan);

    record NativeBudgetStamp(
            @Column("recorded_at") Instant recordedAt,
            @Column("db_now_ms") Long dbNowMillis,
            @Column("admission_until_ms") Long admissionUntilMillis) {}

    @Query("SELECT recorded_at,db_now_ms,admission_until_ms FROM public.product_definition_command_admission(:commandId)")
    Mono<NativeBudgetStamp> admission(@Param("commandId") UUID commandId);

    @Query("SELECT public.assert_product_definition_command_live(:commandId) AS live")
    Mono<Boolean> isCurrent(@Param("commandId") UUID commandId);

    @Query("SELECT public.finish_product_definition_command(:commandId) AS completed")
    Mono<Boolean> finish(@Param("commandId") UUID commandId);
}

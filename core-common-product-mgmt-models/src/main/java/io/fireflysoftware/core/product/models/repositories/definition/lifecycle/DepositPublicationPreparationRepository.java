package io.fireflysoftware.core.product.models.repositories.definition.lifecycle;

import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionDraftRoot;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;

@NoRepositoryBean
public interface DepositPublicationPreparationRepository extends Repository<ProductDefinitionDraftRoot, UUID> {
    record NativeContext(@Column("context_id") UUID contextId,
            @Column("observed_at_ms") Long observedAtMillis,
            @Column("effective_until_ms") Long effectiveUntilMillis,
            @Column("request_digest") String requestDigest) {}

    @Query("SELECT context_id,observed_at_ms,effective_until_ms,request_digest FROM public.open_product_definition_deposit_publish_preparation(:request,:issuer,:actor,:actorUntil,:permitUntil,:requestUntil)")
    Mono<NativeContext> open(@Param("request") String request, @Param("issuer") String issuer,
            @Param("actor") String actor, @Param("actorUntil") long actorUntil,
            @Param("permitUntil") long permitUntil, @Param("requestUntil") long requestUntil);

    @Query("SELECT public.assert_product_definition_deposit_publish_preparation_live(:contextId) AS live")
    Mono<Boolean> isCurrent(@Param("contextId") UUID contextId);

}

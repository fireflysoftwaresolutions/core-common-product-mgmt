package io.fireflysoftware.core.product.models.repositories.definition;

import io.fireflysoftware.core.product.models.entities.definition.ProductDefinitionRoot;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionReadContextRepository
        extends Repository<ProductDefinitionRoot, UUID> {
    record ReadStamp(
            @Column("observed_at_epoch_millis") Long observedAtEpochMillis,
            @Column("valid_until_epoch_millis") Long validUntilEpochMillis) {}

    @Query(
            "SELECT observed_at_epoch_millis, valid_until_epoch_millis FROM"
                + " public.open_product_definition_serving_read(:tenant, :root, :actorUntil,"
                + " :permitUntil)")
    Mono<ReadStamp> open(
            @Param("tenant") UUID tenant,
            @Param("root") UUID root,
            @Param("actorUntil") long actorUntil,
            @Param("permitUntil") long permitUntil);

    @Query("SELECT public.product_definition_serving_context_matches(:tenant, :root)")
    Mono<Boolean> isCurrent(@Param("tenant") UUID tenant, @Param("root") UUID root);
}

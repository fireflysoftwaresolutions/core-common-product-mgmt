package io.fireflysoftware.core.product.models.repositories.definition;

import io.fireflysoftware.core.product.models.entities.definition.ProductDefinitionPublication;

import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionPublicationRepository
        extends Repository<ProductDefinitionPublication, UUID> {
    Mono<ProductDefinitionPublication> findByTenantIdAndRootIdAndPublicationId(
            UUID tenantId, UUID rootId, UUID publicationId);
}

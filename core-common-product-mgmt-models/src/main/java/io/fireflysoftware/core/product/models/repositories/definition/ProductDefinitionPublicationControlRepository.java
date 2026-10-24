package io.fireflysoftware.core.product.models.repositories.definition;

import io.fireflysoftware.core.product.models.entities.definition.ProductDefinitionPublicationControl;

import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionPublicationControlRepository
        extends Repository<ProductDefinitionPublicationControl, UUID> {
    Mono<ProductDefinitionPublicationControl> findByTenantIdAndRootIdAndPublicationId(
            UUID tenantId, UUID rootId, UUID publicationId);
}

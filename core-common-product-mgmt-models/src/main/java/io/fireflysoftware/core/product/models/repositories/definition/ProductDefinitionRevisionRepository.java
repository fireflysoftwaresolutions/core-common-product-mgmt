package io.fireflysoftware.core.product.models.repositories.definition;

import io.fireflysoftware.core.product.models.entities.definition.ProductDefinitionRevision;

import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionRevisionRepository
        extends Repository<ProductDefinitionRevision, UUID> {
    Mono<ProductDefinitionRevision> findByTenantIdAndRootIdAndRevisionId(
            UUID tenantId, UUID rootId, UUID revisionId);
}

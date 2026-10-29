package io.fireflysoftware.core.product.models.repositories.definition;

import io.fireflysoftware.core.product.models.entities.definition.ProductDefinitionRoot;

import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionRootRepository extends Repository<ProductDefinitionRoot, UUID> {
    Mono<ProductDefinitionRoot> findByTenantIdAndRootId(UUID tenantId, UUID rootId);
}

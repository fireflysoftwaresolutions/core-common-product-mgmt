package io.fireflysoftware.core.product.models.repositories.definition;

import io.fireflysoftware.core.product.models.entities.definition.ProductDefinitionVersionIdentity;

import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionVersionIdentityRepository
        extends Repository<ProductDefinitionVersionIdentity, UUID> {
    Mono<ProductDefinitionVersionIdentity> findByProductVersionIdAndProductId(
            UUID productVersionId, UUID productId);
}

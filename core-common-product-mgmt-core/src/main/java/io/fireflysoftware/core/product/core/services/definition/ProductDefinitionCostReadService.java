package io.fireflysoftware.core.product.core.services.definition;

import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCostView;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionCostReadService {
    Mono<ProductDefinitionCostView> readCurrent(UUID tenantId, UUID rootId);
}

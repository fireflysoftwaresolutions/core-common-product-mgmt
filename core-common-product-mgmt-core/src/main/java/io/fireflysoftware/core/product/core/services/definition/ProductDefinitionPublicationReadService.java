package io.fireflysoftware.core.product.core.services.definition;

import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationDiscoveryView;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationReadinessView;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionPublicationReadService {
    Mono<ProductDefinitionPublicationReadinessView> readiness(UUID tenantId, UUID rootId);

    Mono<ProductDefinitionPublicationDiscoveryView> discover(UUID tenantId, UUID rootId);
}

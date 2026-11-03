package io.fireflysoftware.core.product.core.services.definition;

import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionReviewSelectionView;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionReviewReadService {
    Mono<ProductDefinitionReviewSelectionView> readCurrentSelection(UUID tenantId, UUID rootId);

    Mono<ProductDefinitionReviewSelectionView> readSelection(
            UUID tenantId, UUID rootId, UUID submissionId);
}

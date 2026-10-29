package io.fireflysoftware.core.product.models.repositories.definition.lifecycle;

import io.fireflysoftware.core.product.models.entities.definition.lifecycle.DepositPublicationPreparationSnapshot;
import java.util.UUID;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import reactor.core.publisher.Mono;

@NoRepositoryBean
public interface DepositPublicationPreparationSnapshotRepository
        extends Repository<DepositPublicationPreparationSnapshot, UUID> {
    Mono<DepositPublicationPreparationSnapshot> findByTenantIdAndRootIdAndReviewIdAndPublicVersionAndCostVersion(
            UUID tenantId, UUID rootId, UUID reviewId, int publicVersion, int costVersion);
}

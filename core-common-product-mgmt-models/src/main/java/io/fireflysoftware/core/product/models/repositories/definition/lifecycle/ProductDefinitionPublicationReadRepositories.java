package io.fireflysoftware.core.product.models.repositories.definition.lifecycle;

import io.fireflysoftware.core.product.models.entities.definition.ProductDefinitionPublicationControl;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionCostControl;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionDraftRoot;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionLifecycleRows.VersionIdentity;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionPublicationReadRows.*;

import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

public final class ProductDefinitionPublicationReadRepositories {
    private final RevisionMetadataRepository revisionMetadata;
    private final ReviewMetadataRepository reviewMetadata;
    private final ReviewCostIdentityRepository reviewCostIdentity;
    private final DecisionMetadataRepository decisionMetadata;
    private final SubmissionMetadataRepository submissionMetadata;
    private final PublishHistoryRepository publishHistory;
    private final PublicPublishActionRepository publicPublishAction;
    private final PublicationMetadataRepository publicationMetadata;

    public ProductDefinitionPublicationReadRepositories(R2dbcRepositoryFactory factory) {
        Objects.requireNonNull(factory);
        revisionMetadata = factory.getRepository(RevisionMetadataRepository.class);
        reviewMetadata = factory.getRepository(ReviewMetadataRepository.class);
        reviewCostIdentity = factory.getRepository(ReviewCostIdentityRepository.class);
        decisionMetadata = factory.getRepository(DecisionMetadataRepository.class);
        submissionMetadata = factory.getRepository(SubmissionMetadataRepository.class);
        publishHistory = factory.getRepository(PublishHistoryRepository.class);
        publicPublishAction = factory.getRepository(PublicPublishActionRepository.class);
        publicationMetadata = factory.getRepository(PublicationMetadataRepository.class);
    }

    @NoRepositoryBean
    public interface RootMetadataRepository extends Repository<ProductDefinitionDraftRoot, UUID> {
        Mono<ProductDefinitionDraftRoot> findByTenantIdAndRootId(UUID tenantId, UUID rootId);
    }

    @NoRepositoryBean
    public interface CostControlMetadataRepository
            extends Repository<ProductDefinitionCostControl, UUID> {
        Mono<ProductDefinitionCostControl> findByTenantIdAndRootId(UUID tenantId, UUID rootId);
    }

    @NoRepositoryBean
    public interface VersionIdentityMetadataRepository extends Repository<VersionIdentity, UUID> {
        Mono<VersionIdentity> findByTenantIdAndRootIdAndPublicationId(
                UUID tenantId, UUID rootId, UUID publicationId);
    }

    @NoRepositoryBean
    public interface PublicationControlMetadataRepository
            extends Repository<ProductDefinitionPublicationControl, UUID> {
        Mono<ProductDefinitionPublicationControl> findByTenantIdAndRootIdAndPublicationId(
                UUID tenantId, UUID rootId, UUID publicationId);

        Flux<ProductDefinitionPublicationControl> findTop2ByTenantIdAndRootIdAndState(
                UUID tenantId, UUID rootId, String state);
    }

    public RevisionMetadataRepository revisionMetadata() {
        return revisionMetadata;
    }

    @NoRepositoryBean
    public interface RevisionMetadataRepository extends Repository<RevisionMetadata, UUID> {
        Mono<RevisionMetadata> findByTenantIdAndRootIdAndRevisionId(
                UUID tenantId, UUID rootId, UUID revisionId);
    }

    public ReviewMetadataRepository reviewMetadata() {
        return reviewMetadata;
    }

    @NoRepositoryBean
    public interface ReviewMetadataRepository extends Repository<ReviewMetadata, UUID> {
        Mono<ReviewMetadata> findByTenantIdAndRootIdAndReviewId(
                UUID tenantId, UUID rootId, UUID reviewId);
    }

    public ReviewCostIdentityRepository reviewCostIdentity() {
        return reviewCostIdentity;
    }

    @NoRepositoryBean
    public interface ReviewCostIdentityRepository extends Repository<ReviewCostIdentity, UUID> {
        Mono<ReviewCostIdentity> findByTenantIdAndRootIdAndReviewId(
                UUID tenantId, UUID rootId, UUID reviewId);
    }

    public DecisionMetadataRepository decisionMetadata() {
        return decisionMetadata;
    }

    @NoRepositoryBean
    public interface DecisionMetadataRepository extends Repository<DecisionMetadata, UUID> {
        Mono<DecisionMetadata> findByTenantIdAndRootIdAndReviewId(
                UUID tenantId, UUID rootId, UUID reviewId);
    }

    public SubmissionMetadataRepository submissionMetadata() {
        return submissionMetadata;
    }

    @NoRepositoryBean
    public interface SubmissionMetadataRepository extends Repository<SubmissionMetadata, UUID> {
        Mono<SubmissionMetadata> findByTenantIdAndRootIdAndSubmissionId(
                UUID tenantId, UUID rootId, UUID submissionId);
    }

    public PublishHistoryRepository publishHistory() {
        return publishHistory;
    }

    @NoRepositoryBean
    public interface PublishHistoryRepository extends Repository<PublishHistory, UUID> {
        Mono<PublishHistory> findByTenantIdAndRootIdAndHistoryId(
                UUID tenantId, UUID rootId, UUID historyId);

        Flux<PublishHistory>
                findTop2ByTenantIdAndRootIdAndEventTypeOrderByPublicStateVersionDescHistoryIdDesc(
                        UUID tenantId, UUID rootId, String eventType);
    }

    public PublicPublishActionRepository publicPublishAction() {
        return publicPublishAction;
    }

    @NoRepositoryBean
    public interface PublicPublishActionRepository extends Repository<PublicPublishAction, UUID> {
        Mono<PublicPublishAction> findByTenantIdAndRootIdAndActionId(
                UUID tenantId, UUID rootId, UUID actionId);
    }

    public PublicationMetadataRepository publicationMetadata() {
        return publicationMetadata;
    }

    @NoRepositoryBean
    public interface PublicationMetadataRepository extends Repository<PublicationMetadata, UUID> {
        Mono<PublicationMetadata> findByTenantIdAndRootIdAndPublicationId(
                UUID tenantId, UUID rootId, UUID publicationId);

        Mono<Boolean> existsByTenantIdAndRootId(UUID tenantId, UUID rootId);
    }
}

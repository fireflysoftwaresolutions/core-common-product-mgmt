/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.core.product.models.repositories.definition.lifecycle;

import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.impl.ProductDefinitionDraftWritesImpl;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.ConnectionFactory;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction;
import reactor.core.publisher.Mono;
import java.util.function.Function;

import org.springframework.context.ApplicationContext;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.Duration;
import java.util.Objects;

public final class ProductDefinitionCommandPersistence implements AutoCloseable {
    private final ConnectionPool pool;
    private final ProductDefinitionReviewReadNativeRepository reviewReadNative;
    private final DepositPublicationPreparationRepository publicationPreparation;
    private final DepositPublicationPreparationSnapshotRepository publicationPreparationSnapshots;
    private final TransactionalOperator readTransactions;
    private final LifecycleActionRepository lifecycleActionRepository;
    private final LifecycleActionPrivateRepository lifecycleActionPrivateRepository;
    private final LifecycleSubmissionRepository lifecycleSubmissionRepository;
    private final LifecycleReviewRepository lifecycleReviewRepository;
    private final LifecycleReviewPrivateRepository lifecycleReviewPrivateRepository;
    private final LifecycleDecisionRepository lifecycleDecisionRepository;
    private final LifecycleRevisionRepository lifecycleRevisionRepository;
    private final LifecycleCostRevisionRepository lifecycleCostRevisionRepository;
    private final LifecycleVersionIdentityRepository lifecycleVersionIdentityRepository;
    private final LifecyclePublicationRepository lifecyclePublicationRepository;
    private final LifecyclePublicationControlRepository lifecyclePublicationControlRepository;
    private final ProductDefinitionCommandNativeRepository productDefinitionCommandNativeRepository;

    private final R2dbcEntityTemplate operations;
    private final R2dbcTransactionManager manager;
    private final TransactionalOperator transactions;
    private final BoundedRootTransaction bounded;
    private final DefaultTransactionDefinition boundedCommand, boundedRead, boundedPreparation;
    private final ProductDefinitionDraftRepository drafts;
    private final ProductDefinitionCostControlRepository costs;

    public ProductDefinitionCommandPersistence(
            ConnectionPool pool, R2dbcConverter converter, ApplicationContext context) {
        this(pool, converter, context, false);
    }

    public ProductDefinitionCommandPersistence(
            ConnectionPool pool, R2dbcConverter converter, ApplicationContext context, boolean boundedMode) {
        this.pool = Objects.requireNonNull(pool);
        ConnectionFactory transactionFactory = boundedMode ? new OwnedLeaseConnectionFactory(pool) : pool;
        operations =
                new R2dbcEntityTemplate(
                        DatabaseClient.create(transactionFactory),
                        PostgresDialect.INSTANCE,
                        Objects.requireNonNull(converter));
        operations.setBeanFactory(context);
        operations.setApplicationContext(context);
        var factory = new R2dbcRepositoryFactory(operations);
        factory.setBeanFactory(context);
        drafts =
                factory.getRepository(
                        ProductDefinitionDraftRepository.class,
                        RepositoryFragments.just(new ProductDefinitionDraftWritesImpl(operations)));
        costs = factory.getRepository(ProductDefinitionCostControlRepository.class);
        lifecycleActionRepository = factory.getRepository(LifecycleActionRepository.class);
        lifecycleActionPrivateRepository =
                factory.getRepository(LifecycleActionPrivateRepository.class);
        lifecycleSubmissionRepository = factory.getRepository(LifecycleSubmissionRepository.class);
        lifecycleReviewRepository = factory.getRepository(LifecycleReviewRepository.class);
        lifecycleReviewPrivateRepository =
                factory.getRepository(LifecycleReviewPrivateRepository.class);
        lifecycleDecisionRepository = factory.getRepository(LifecycleDecisionRepository.class);
        lifecycleRevisionRepository = factory.getRepository(LifecycleRevisionRepository.class);
        lifecycleCostRevisionRepository =
                factory.getRepository(LifecycleCostRevisionRepository.class);
        lifecycleVersionIdentityRepository =
                factory.getRepository(LifecycleVersionIdentityRepository.class);
        lifecyclePublicationRepository =
                factory.getRepository(LifecyclePublicationRepository.class);
        lifecyclePublicationControlRepository =
                factory.getRepository(LifecyclePublicationControlRepository.class);
        productDefinitionCommandNativeRepository =
                factory.getRepository(ProductDefinitionCommandNativeRepository.class);

        reviewReadNative = factory.getRepository(ProductDefinitionReviewReadNativeRepository.class);
        publicationPreparation = factory.getRepository(DepositPublicationPreparationRepository.class);
        publicationPreparationSnapshots = factory.getRepository(DepositPublicationPreparationSnapshotRepository.class);
        manager = new R2dbcTransactionManager(transactionFactory);
        manager.setEnforceReadOnly(true);
        var readDefinition = new DefaultTransactionDefinition();
        readDefinition.setName("product-definition-review-selection");
        readDefinition.setReadOnly(true);
        readDefinition.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        readDefinition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        readDefinition.setTimeout(10);
        readTransactions = TransactionalOperator.create(manager, readDefinition);
        var definition = new DefaultTransactionDefinition();
        definition.setName("product-definition-lifecycle-command");
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        definition.setTimeout((int) Duration.ofSeconds(10).toSeconds());
        transactions = TransactionalOperator.create(manager, definition);
        bounded = boundedMode ? new DefaultBoundedRootTransaction(
                BoundedR2dbcResources.verify((OwnedLeaseConnectionFactory)transactionFactory,
                        operations.getDatabaseClient(), operations, manager),
                new CleanupPolicy(Duration.ofSeconds(2), Duration.ofSeconds(2))) : null;
        boundedCommand = new DefaultTransactionDefinition();
        boundedCommand.setName("product-definition-term-pc-command");
        boundedCommand.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        boundedCommand.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        boundedCommand.setTimeout(30);
        boundedRead = new DefaultTransactionDefinition(boundedCommand);
        boundedRead.setName("product-definition-term-pc-private-read");
        boundedRead.setReadOnly(true);
        boundedRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        boundedPreparation = new DefaultTransactionDefinition(boundedRead);
        boundedPreparation.setName("product-definition-deposit-publication-preparation");
        // Native admission metadata is inserted; business repositories remain projection-only.
        boundedPreparation.setReadOnly(false);
    }

    public boolean boundedTransactions() {
        return bounded != null;
    }

    public <T> Mono<T> executeBounded(R2dbcOperationBudget budget, boolean readOnly,
            Function<RootTransactionScope, Mono<T>> work) {
        return Mono.defer(() -> {
            if (bounded == null) return Mono.error(new IllegalStateException("PRODUCT_BOUNDED_PERSISTENCE_REQUIRED"));
            return bounded.execute(budget, readOnly ? boundedRead : boundedCommand, work)
                    .map(RootCompletion::value);
        });
    }

    public <T> Mono<T> executePreparation(R2dbcOperationBudget originalBudget,
            Function<RootTransactionScope, Mono<T>> work) {
        return Mono.defer(() -> {
            if (bounded == null) return Mono.error(new IllegalStateException("PRODUCT_BOUNDED_PERSISTENCE_REQUIRED"));
            return bounded.execute(originalBudget, boundedPreparation, work).map(RootCompletion::value);
        });
    }

    public ProductDefinitionReviewReadNativeRepository reviewReadNative() {
        return reviewReadNative;
    }

    public DepositPublicationPreparationRepository publicationPreparation() {
        return publicationPreparation;
    }

    public DepositPublicationPreparationSnapshotRepository publicationPreparationSnapshots() {
        return publicationPreparationSnapshots;
    }

    public TransactionalOperator readTransactions() {
        return readTransactions;
    }

    public ConnectionPool connectionFactory() {
        return pool;
    }

    public R2dbcEntityTemplate operations() {
        return operations;
    }

    public R2dbcTransactionManager transactionManager() {
        return manager;
    }

    public TransactionalOperator transactions() {
        return transactions;
    }

    public ProductDefinitionDraftRepository drafts() {
        return drafts;
    }

    public ProductDefinitionCostControlRepository costs() {
        return costs;
    }

    public LifecycleActionRepository lifecycleActionRepository() {
        return lifecycleActionRepository;
    }

    public LifecycleActionPrivateRepository lifecycleActionPrivateRepository() {
        return lifecycleActionPrivateRepository;
    }

    public LifecycleSubmissionRepository lifecycleSubmissionRepository() {
        return lifecycleSubmissionRepository;
    }

    public LifecycleReviewRepository lifecycleReviewRepository() {
        return lifecycleReviewRepository;
    }

    public LifecycleReviewPrivateRepository lifecycleReviewPrivateRepository() {
        return lifecycleReviewPrivateRepository;
    }

    public LifecycleDecisionRepository lifecycleDecisionRepository() {
        return lifecycleDecisionRepository;
    }

    public LifecycleRevisionRepository lifecycleRevisionRepository() {
        return lifecycleRevisionRepository;
    }

    public LifecycleCostRevisionRepository lifecycleCostRevisionRepository() {
        return lifecycleCostRevisionRepository;
    }

    public LifecycleVersionIdentityRepository lifecycleVersionIdentityRepository() {
        return lifecycleVersionIdentityRepository;
    }

    public LifecyclePublicationRepository lifecyclePublicationRepository() {
        return lifecyclePublicationRepository;
    }

    public LifecyclePublicationControlRepository lifecyclePublicationControlRepository() {
        return lifecyclePublicationControlRepository;
    }

    public ProductDefinitionCommandNativeRepository productDefinitionCommandNativeRepository() {
        return productDefinitionCommandNativeRepository;
    }

    public void close() {
        pool.dispose();
    }

    @Override
    public String toString() {
        return "ProductDefinitionCommandPersistence";
    }
}

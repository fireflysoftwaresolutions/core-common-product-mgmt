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
    private final ProductDefinitionDraftRepository drafts;
    private final ProductDefinitionCostControlRepository costs;

    public ProductDefinitionCommandPersistence(
            ConnectionPool pool, R2dbcConverter converter, ApplicationContext context) {
        this.pool = Objects.requireNonNull(pool);
        operations =
                new R2dbcEntityTemplate(
                        DatabaseClient.create(pool),
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
        manager = new R2dbcTransactionManager(pool);
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
    }

    public ProductDefinitionReviewReadNativeRepository reviewReadNative() {
        return reviewReadNative;
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

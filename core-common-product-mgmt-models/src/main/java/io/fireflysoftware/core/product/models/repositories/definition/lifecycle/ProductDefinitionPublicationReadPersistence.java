package io.fireflysoftware.core.product.models.repositories.definition.lifecycle;

import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionPublicationReadRepositories.*;
import io.r2dbc.pool.ConnectionPool;

import org.springframework.context.ApplicationContext;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.Objects;

public final class ProductDefinitionPublicationReadPersistence implements AutoCloseable {
    private final ConnectionPool pool;
    private final R2dbcEntityTemplate operations;
    private final R2dbcTransactionManager manager;
    private final TransactionalOperator readTransactions;
    private final ProductDefinitionPublicationReadNativeRepository nativeReads;
    private final ProductDefinitionPublicationReadRepositories rows;
    private final RootMetadataRepository roots;
    private final CostControlMetadataRepository controls;
    private final VersionIdentityMetadataRepository identities;
    private final PublicationControlMetadataRepository publications;

    public ProductDefinitionPublicationReadPersistence(
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
        rows = new ProductDefinitionPublicationReadRepositories(factory);
        roots = factory.getRepository(RootMetadataRepository.class);
        controls = factory.getRepository(CostControlMetadataRepository.class);
        identities = factory.getRepository(VersionIdentityMetadataRepository.class);
        publications = factory.getRepository(PublicationControlMetadataRepository.class);
        nativeReads = factory.getRepository(ProductDefinitionPublicationReadNativeRepository.class);
        manager = new R2dbcTransactionManager(pool);
        manager.setEnforceReadOnly(true);
        var definition = new DefaultTransactionDefinition();
        definition.setName("product-definition-publication-metadata");
        definition.setReadOnly(true);
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        definition.setTimeout(10);
        readTransactions = TransactionalOperator.create(manager, definition);
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

    public TransactionalOperator readTransactions() {
        return readTransactions;
    }

    public ProductDefinitionPublicationReadNativeRepository publicationReadNative() {
        return nativeReads;
    }

    public ProductDefinitionPublicationReadRepositories publicationReads() {
        return rows;
    }

    public RootMetadataRepository drafts() {
        return roots;
    }

    public CostControlMetadataRepository costs() {
        return controls;
    }

    public VersionIdentityMetadataRepository lifecycleVersionIdentityRepository() {
        return identities;
    }

    public PublicationControlMetadataRepository lifecyclePublicationControlRepository() {
        return publications;
    }

    public void close() {
        pool.dispose();
    }

    @Override
    public String toString() {
        return "ProductDefinitionPublicationReadPersistence";
    }
}

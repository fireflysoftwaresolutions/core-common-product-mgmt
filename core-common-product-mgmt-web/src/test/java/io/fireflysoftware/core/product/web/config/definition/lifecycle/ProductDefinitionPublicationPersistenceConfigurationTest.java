package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandPersistence;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

class ProductDefinitionPublicationPersistenceConfigurationTest {
    final String prefix = "io.fireflysoftware.core.product.";

    Class<?> type(String name) {
        try {
            return Class.forName(prefix + name);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Missing dedicated publication reader boundary", missing);
        }
    }

    Class<?> persistenceType() {
        return type(
                "models.repositories.definition.lifecycle.ProductDefinitionPublicationReadPersistence");
    }

    ApplicationContextRunner owner() {
        return new ProductDefinitionOwnerCommandConfigurationTest()
                .owner()
                .withUserConfiguration(
                        type(
                                "web.config.definition.lifecycle.ProductDefinitionPublicationReadConfiguration"));
    }

    ApplicationContextRunner enabled() {
        return owner().withPropertyValues(
                        "product.definitions.commands.enabled=true",
                        "product.definitions.commands.username=owner_commands",
                        "product.definitions.commands.password=" + UUID.randomUUID(),
                        "product.definitions.publication-read.enabled=true",
                        "product.definitions.publication-read.username=publication_metadata",
                        "product.definitions.publication-read.password=" + UUID.randomUUID());
    }

    Object call(Object object, String method) {
        return ReflectionTestUtils.invokeMethod(object, method);
    }

    @Test
    void disabledNeedsNoNewCredentialsAndKeepsTheOwnerReadFactory() {
        owner().run(
                        context -> {
                            assertThat(context)
                                    .hasNotFailed()
                                    .hasSingleBean(ConnectionFactory.class)
                                    .doesNotHaveBean(persistenceType());
                            assertThat(
                                            context.getBean(R2dbcEntityOperations.class)
                                                    .getDatabaseClient()
                                                    .getConnectionFactory())
                                    .isSameAs(context.getBean(ConnectionFactory.class));
                        });
    }

    @Test
    void commandsAloneDoNotEnablePublicationMetadataCredentialsOrPool() {
        owner().withPropertyValues(
                        "product.definitions.commands.enabled=true",
                        "product.definitions.commands.username=owner_commands",
                        "product.definitions.commands.password=" + UUID.randomUUID())
                .run(
                        context ->
                                assertThat(context)
                                        .hasNotFailed()
                                        .doesNotHaveBean(persistenceType())
                                        .hasSingleBean(ProductDefinitionCommandPersistence.class));
    }

    @Test
    void metadataFlagCannotEnableTheCommandAuthorityByItself() {
        owner().withPropertyValues("product.definitions.publication-read.enabled=true")
                .run(
                        context ->
                                assertThat(context)
                                        .hasNotFailed()
                                        .doesNotHaveBean(persistenceType()));
    }

    @Test
    void enabledPoolRetainsActualOwnerTargetTlsAndConverterWithoutRebindingBoot() {
        enabled()
                .run(
                        context -> {
                            assertThat(context)
                                    .hasNotFailed()
                                    .hasSingleBean(ConnectionFactory.class)
                                    .hasSingleBean(persistenceType());
                            var read = context.getBean(ConnectionFactory.class);
                            var command =
                                    context.getBean(ProductDefinitionCommandPersistence.class);
                            var metadata = context.getBean(persistenceType());
                            var pool = (ConnectionPool) call(metadata, "connectionFactory");
                            var template = (R2dbcEntityTemplate) call(metadata, "operations");
                            var manager =
                                    (R2dbcTransactionManager) call(metadata, "transactionManager");
                            assertThat(pool)
                                    .isNotSameAs(read)
                                    .isNotSameAs(command.connectionFactory());
                            assertThat(template.getDatabaseClient().getConnectionFactory())
                                    .isSameAs(pool);
                            assertThat(manager.getConnectionFactory()).isSameAs(pool);
                            assertThat(manager.isEnforceReadOnly()).isTrue();
                            assertThat(template.getConverter())
                                    .isSameAs(context.getBean(R2dbcConverter.class));
                            assertThat(ReflectionTestUtils.getField(template, "entityCallbacks"))
                                    .isNotNull();
                            assertThat(
                                            context.getBean(R2dbcEntityOperations.class)
                                                    .getDatabaseClient()
                                                    .getConnectionFactory())
                                    .isSameAs(read);
                            var options =
                                    org.springframework.boot.r2dbc.ConnectionFactoryBuilder
                                            .derivedFrom(pool)
                                            .buildOptions();
                            assertThat(options.getValue(ConnectionFactoryOptions.HOST))
                                    .isEqualTo("127.0.0.1");
                            assertThat(options.getValue(ConnectionFactoryOptions.PORT))
                                    .isEqualTo(59992);
                            assertThat(options.getValue(ConnectionFactoryOptions.DATABASE))
                                    .isEqualTo("actual_owner_db");
                            assertThat(options.getValue(ConnectionFactoryOptions.USER))
                                    .isEqualTo("publication_metadata");
                            assertThat(
                                            options.getValue(
                                                    io.r2dbc.postgresql
                                                            .PostgresqlConnectionFactoryProvider
                                                            .SSL_MODE))
                                    .isEqualTo(io.r2dbc.postgresql.client.SSLMode.REQUIRE);
                            assertThat(pool.getMetrics().orElseThrow().allocatedSize()).isZero();
                            assertThat(pool.getMetrics().orElseThrow().acquiredSize()).isZero();
                            assertThat(context.getBeansOfType(ConnectionFactoryOptions.class))
                                    .isEmpty();
                            assertThat(metadata.toString())
                                    .isEqualTo("ProductDefinitionPublicationReadPersistence");
                            call(metadata, "close");
                            assertThat(pool.isDisposed()).isTrue();
                            assertThat(command.connectionFactory().isDisposed()).isFalse();
                        });
    }

    @Test
    void actualOwnerLifecycleServiceUsesTheSameClockAndTheDedicatedReader() {
        enabled()
                .withUserConfiguration(ProductDefinitionLifecycleConfiguration.class)
                .withBean(
                        org.fireflyframework.security.spi.SecurityContextPort.class,
                        () -> () -> reactor.core.publisher.Mono.empty())
                .withBean(
                        "productDefinitionCommandPolicy",
                        org.fireflyframework.security.spi.PolicyDecisionPort.class,
                        () ->
                                (principal, action, resource, attributes) ->
                                        reactor.core.publisher.Mono.just(
                                                org.fireflyframework.security.api.domain.Decision
                                                        .deny("not configured")))
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            var service =
                                    context.getBean(
                                            io.fireflysoftware.core.product.core.services.definition
                                                    .ProductDefinitionPublicationReadService.class);
                            var authority =
                                    context.getBean(
                                            io.fireflysoftware.core.product.core.services.definition
                                                    .ProductDefinitionPublicationReadAuthorization
                                                    .class);
                            var clock =
                                    context.getBean(
                                            "productDefinitionLifecycleClock",
                                            java.time.Clock.class);
                            assertThat(ReflectionTestUtils.getField(service, "clock"))
                                    .isSameAs(clock);
                            assertThat(ReflectionTestUtils.getField(authority, "clock"))
                                    .isSameAs(clock);
                            assertThat(ReflectionTestUtils.getField(service, "persistence"))
                                    .isSameAs(context.getBean(persistenceType()));
                            assertThat(
                                            context.getBeansOfType(
                                                    io.fireflysoftware.core.product.models
                                                            .repositories.definition.lifecycle
                                                            .ProductDefinitionPublicationReadRepositories
                                                            .RootMetadataRepository.class))
                                    .isEmpty();
                            var template =
                                    (R2dbcEntityTemplate)
                                            call(context.getBean(persistenceType()), "operations");
                            var callbacks =
                                    (org.springframework.data.mapping.callback
                                                    .ReactiveEntityCallbacks)
                                            ReflectionTestUtils.getField(
                                                    template, "entityCallbacks");
                            var audited =
                                    callbacks
                                            .callback(
                                                    org.springframework.data.r2dbc.mapping.event
                                                            .BeforeConvertCallback.class,
                                                    new ProductDefinitionOwnerCommandConfigurationTest
                                                            .OwnerAudit(null, null),
                                                    org.springframework.data.relational.core.sql
                                                            .SqlIdentifier.unquoted("owner_audit"))
                                            .block(java.time.Duration.ofSeconds(2));
                            assertThat(audited.createdAt()).isNotNull();
                            var definition =
                                    (org.springframework.transaction.TransactionDefinition)
                                            ReflectionTestUtils.getField(
                                                    call(
                                                            context.getBean(persistenceType()),
                                                            "readTransactions"),
                                                    "transactionDefinition");
                            assertThat(definition.isReadOnly()).isTrue();
                            assertThat(definition.getIsolationLevel())
                                    .isEqualTo(
                                            org.springframework.transaction.TransactionDefinition
                                                    .ISOLATION_REPEATABLE_READ);
                            assertThat(definition.getTimeout()).isEqualTo(10);
                        });
    }

    @Test
    void metadataRepositoriesExposeOnlyBoundedReadMethods() {
        var group =
                io.fireflysoftware.core.product.models.repositories.definition.lifecycle
                        .ProductDefinitionPublicationReadRepositories.class;
        for (var repository : group.getDeclaredClasses()) {
            assertThat(repository)
                    .hasAnnotation(org.springframework.data.repository.NoRepositoryBean.class);
            assertThat(
                            java.util.Arrays.stream(repository.getMethods())
                                    .map(java.lang.reflect.Method::getName))
                    .allMatch(name -> name.startsWith("find") || name.startsWith("exists"));
        }
    }

    @Test
    void enabledMissingReaderCredentialsFailsClosedWithSanitizedReason() {
        owner().withPropertyValues(
                        "product.definitions.commands.enabled=true",
                        "product.definitions.commands.username=owner_commands",
                        "product.definitions.commands.password=" + UUID.randomUUID(),
                        "product.definitions.publication-read.enabled=true")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .hasStackTraceContaining(
                                                "PRODUCT_DEFINITION_PUBLICATION_READ_CREDENTIALS_REQUIRED"));
    }

    @Test
    void readerCannotReuseEitherExistingSqlIdentity() {
        for (String username : java.util.List.of("owner_reader", "owner_commands"))
            enabled()
                    .withPropertyValues("product.definitions.publication-read.username=" + username)
                    .run(
                            context ->
                                    assertThat(context)
                                            .hasFailed()
                                            .getFailure()
                                            .hasStackTraceContaining(
                                                    "PRODUCT_DEFINITION_DISTINCT_PUBLICATION_READ_IDENTITY_REQUIRED"));
    }

    @Test
    void commandContainerDoesNotAssemblePublicationMetadataQueries() {
        assertThat(
                        java.util.Arrays.stream(
                                        ProductDefinitionCommandPersistence.class.getMethods())
                                .map(java.lang.reflect.Method::getName))
                .doesNotContain("publicationReadNative", "publicationReads");
    }

    @Test
    void publicationPropertiesDoNotPrintCredentials() {
        var properties =
                org.springframework.beans.BeanUtils.instantiateClass(
                        type(
                                "web.config.definition.lifecycle.ProductDefinitionPublicationReadProperties"));
        ReflectionTestUtils.invokeMethod(properties, "setUsername", "private-reader-name");
        ReflectionTestUtils.invokeMethod(properties, "setPassword", UUID.randomUUID().toString());
        assertThat(properties.toString()).isEqualTo("ProductDefinitionPublicationReadProperties");
    }
}

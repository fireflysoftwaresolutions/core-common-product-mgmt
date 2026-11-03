package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.models.config.R2dbcConfiguration;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandPersistence;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import java.util.UUID;

class ProductDefinitionOwnerCommandConfigurationTest {
    ApplicationContextRunner owner() {
        return new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration
                                        .class,
                                org.springframework.boot.r2dbc.autoconfigure
                                        .R2dbcTransactionManagerAutoConfiguration.class,
                                org.springframework.boot.data.r2dbc.autoconfigure
                                        .DataR2dbcAutoConfiguration.class,
                                org.fireflyframework.core.config.R2dbcTransactionAutoConfiguration
                                        .class))
                .withUserConfiguration(
                        R2dbcConfiguration.class,
                        OwnerRepositories.class,
                        ProductDefinitionCommandConfiguration.class)
                .withPropertyValues(
                        "DB_HOST=127.0.0.1",
                        "DB_PORT=59992",
                        "DB_NAME=actual_owner_db",
                        "DB_USERNAME=owner_reader",
                        "DB_PASSWORD=" + UUID.randomUUID(),
                        "DB_SSL_MODE=require",
                        "spring.r2dbc.url=r2dbc:postgresql://127.0.0.1:59993/unused_boot_target",
                        "spring.r2dbc.pool.initial-size=0");
    }

    @Test
    void actualOwnerOffRetainsDirectReadFactoryWithoutCommandSecrets() {
        owner().run(
                        context -> {
                            assertThat(context)
                                    .hasNotFailed()
                                    .hasSingleBean(ConnectionFactory.class)
                                    .doesNotHaveBean(ProductDefinitionCommandPersistence.class);
                            assertThat(context.getBean(ConnectionFactory.class))
                                    .isInstanceOf(
                                            io.r2dbc.postgresql.PostgresqlConnectionFactory.class);
                            assertThat(
                                            context.getBean(R2dbcEntityOperations.class)
                                                    .getDatabaseClient()
                                                    .getConnectionFactory())
                                    .isSameAs(context.getBean(ConnectionFactory.class));
                        });
    }

    @Test
    void actualOwnerEnabledUsesDbSettingsInsteadOfUnrelatedBootUrl() {
        owner().withPropertyValues(
                        "product.definitions.commands.enabled=true",
                        "product.definitions.commands.username=owner_commands",
                        "product.definitions.commands.password=" + UUID.randomUUID())
                .run(
                        context -> {
                            assertThat(context)
                                    .hasNotFailed()
                                    .hasSingleBean(ConnectionFactory.class)
                                    .hasSingleBean(ProductDefinitionCommandPersistence.class);
                            var read = context.getBean(ConnectionFactory.class);
                            assertThat(read)
                                    .isInstanceOf(
                                            io.r2dbc.postgresql.PostgresqlConnectionFactory.class);
                            var boundary =
                                    context.getBean(ProductDefinitionCommandPersistence.class);
                            assertThat(
                                            context.getBeansOfType(
                                                    io.fireflysoftware.core.product.models
                                                            .repositories.definition.lifecycle
                                                            .ProductDefinitionPublicationReadRepositories
                                                            .ReviewMetadataRepository.class))
                                    .isEmpty();
                            assertThat(
                                            boundary.connectionFactory()
                                                    .getMetrics()
                                                    .orElseThrow()
                                                    .allocatedSize())
                                    .isZero();
                            assertThat(context.getBeansOfType(ConnectionFactoryOptions.class))
                                    .isEmpty();
                            assertThat(
                                            boundary.connectionFactory()
                                                    .getMetrics()
                                                    .orElseThrow()
                                                    .allocatedSize())
                                    .isZero();
                            assertThat(
                                            boundary.connectionFactory()
                                                    .getMetrics()
                                                    .orElseThrow()
                                                    .acquiredSize())
                                    .isZero();
                            var options =
                                    org.springframework.boot.r2dbc.ConnectionFactoryBuilder
                                            .derivedFrom(boundary.connectionFactory())
                                            .buildOptions();
                            assertThat(options.getValue(ConnectionFactoryOptions.HOST))
                                    .isEqualTo("127.0.0.1");
                            assertThat(options.getValue(ConnectionFactoryOptions.PORT))
                                    .isEqualTo(59992);
                            assertThat(options.getValue(ConnectionFactoryOptions.DATABASE))
                                    .isEqualTo("actual_owner_db");
                            assertThat(options.getValue(ConnectionFactoryOptions.USER))
                                    .isEqualTo("owner_commands");
                            assertThat(
                                            options.getValue(
                                                    io.r2dbc.postgresql
                                                            .PostgresqlConnectionFactoryProvider
                                                            .SSL_MODE))
                                    .isEqualTo(io.r2dbc.postgresql.client.SSLMode.REQUIRE);
                            assertThat(boundary.operations().getConverter())
                                    .isSameAs(context.getBean(R2dbcConverter.class));
                            assertThat(
                                            context.getBean(R2dbcEntityOperations.class)
                                                    .getDatabaseClient()
                                                    .getConnectionFactory())
                                    .isSameAs(read);
                            boundary.close();
                            assertThat(boundary.connectionFactory().isDisposed()).isTrue();
                        });
    }

    @Test
    void ownerSourceCannotProvideOptionsForAnotherFactoryOrPrintCredentials() {
        owner().run(
                        context -> {
                            var source =
                                    context.getBean(
                                            io.fireflysoftware.core.product.models.config
                                                    .ProductDefinitionReadConnectionSource.class);
                            assertThat(source.readFactory())
                                    .isSameAs(context.getBean(ConnectionFactory.class));
                            assertThatThrownBy(
                                            () ->
                                                    source.optionsFor(
                                                            org.mockito.Mockito.mock(
                                                                    ConnectionFactory.class)))
                                    .isInstanceOf(IllegalStateException.class)
                                    .hasMessage("PRODUCT_DEFINITION_READ_FACTORY_BINDING_REQUIRED");
                            assertThat(source.toString())
                                    .isEqualTo("ProductDefinitionReadConnectionSource");
                        });
    }

    @Test
    void actualOwnerCommandTemplateRetainsItsConfiguredAuditingCallbacks() {
        owner().withPropertyValues(
                        "product.definitions.commands.enabled=true",
                        "product.definitions.commands.username=owner_commands",
                        "product.definitions.commands.password=" + UUID.randomUUID())
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            var template =
                                    context.getBean(ProductDefinitionCommandPersistence.class)
                                            .operations();
                            var callbacks =
                                    (org.springframework.data.mapping.callback
                                                    .ReactiveEntityCallbacks)
                                            org.springframework.test.util.ReflectionTestUtils
                                                    .getField(template, "entityCallbacks");
                            var audited =
                                    callbacks
                                            .callback(
                                                    org.springframework.data.r2dbc.mapping.event
                                                            .BeforeConvertCallback.class,
                                                    new OwnerAudit(null, null),
                                                    org.springframework.data.relational.core.sql
                                                            .SqlIdentifier.unquoted("owner_audit"))
                                            .block(java.time.Duration.ofSeconds(2));
                            assertThat(audited.createdAt()).isNotNull();
                        });
    }

    @org.springframework.data.relational.core.mapping.Table("owner_audit")
    record OwnerAudit(
            @org.springframework.data.annotation.Id UUID id,
            @org.springframework.data.annotation.CreatedDate java.time.LocalDateTime createdAt) {}

    @Configuration(proxyBeanMethods = false)
    @EnableR2dbcRepositories(basePackages = "io.fireflysoftware.core.product.models.repositories")
    @org.springframework.data.r2dbc.config.EnableR2dbcAuditing
    static class OwnerRepositories {}
}

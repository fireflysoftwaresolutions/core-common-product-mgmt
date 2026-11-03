package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.models.repositories.definition.ProductDefinitionRootRepository;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.*;
import io.r2dbc.spi.ConnectionFactory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;

import java.util.UUID;

class ProductDefinitionCommandConfigurationTest {
    private final String password = UUID.randomUUID().toString();

    private ApplicationContextRunner runner() {
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
                        ReadRepositories.class, ProductDefinitionCommandConfiguration.class)
                .withPropertyValues(
                        "spring.r2dbc.url=r2dbc:postgresql://127.0.0.1:59991/product_contract?sslMode=DISABLE",
                        "spring.r2dbc.username=contract_reader",
                        "spring.r2dbc.password=" + password,
                        "spring.r2dbc.pool.initial-size=0");
    }

    private ApplicationContextRunner enabled() {
        return runner().withPropertyValues(
                        "product.definitions.commands.enabled=true",
                        "product.definitions.commands.username=contract_commands",
                        "product.definitions.commands.password=" + UUID.randomUUID());
    }

    private void assertReadGraph(
            org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        assertThat(context)
                .hasNotFailed()
                .hasSingleBean(ConnectionFactory.class)
                .hasSingleBean(R2dbcEntityOperations.class)
                .hasSingleBean(ReactiveTransactionManager.class)
                .hasSingleBean(ProductDefinitionRootRepository.class);
        var read = context.getBean(ConnectionFactory.class);
        var options =
                org.springframework.boot.r2dbc.ConnectionFactoryBuilder.derivedFrom(read)
                        .buildOptions();
        assertThat(options.getValue(io.r2dbc.spi.ConnectionFactoryOptions.USER))
                .isEqualTo("contract_reader");
        assertThat(options.getValue(io.r2dbc.spi.ConnectionFactoryOptions.DATABASE))
                .isEqualTo("product_contract");
        assertThat(options.getValue(io.r2dbc.spi.ConnectionFactoryOptions.PORT)).isEqualTo(59991);
        assertThat(
                        context.getBean(R2dbcEntityOperations.class)
                                .getDatabaseClient()
                                .getConnectionFactory())
                .isSameAs(read);
        assertThat(
                        ((R2dbcTransactionManager)
                                        context.getBean(ReactiveTransactionManager.class))
                                .getConnectionFactory())
                .isSameAs(read);
    }

    @Test
    void disabledDefaultPreservesReadAutoconfigurationAndNeedsNoCommandCredentials() {
        runner().run(
                        context -> {
                            assertReadGraph(context);
                            assertThat(context)
                                    .doesNotHaveBean(ProductDefinitionCommandPersistence.class)
                                    .doesNotHaveBean(ProductDefinitionDraftRepository.class);
                        });
    }

    @Test
    void enabledCommandsKeepOneReadGraphAndOneScopedRepositoryPerContract() {
        enabled()
                .run(
                        context -> {
                            assertReadGraph(context);
                            assertThat(context)
                                    .hasSingleBean(ProductDefinitionCommandPersistence.class)
                                    .hasSingleBean(ProductDefinitionDraftRepository.class)
                                    .hasSingleBean(ProductDefinitionCostControlRepository.class);
                            var boundary =
                                    context.getBean(ProductDefinitionCommandPersistence.class);
                            assertThat(boundary.connectionFactory())
                                    .isNotSameAs(context.getBean(ConnectionFactory.class));
                            assertThat(
                                            boundary.operations()
                                                    .getDatabaseClient()
                                                    .getConnectionFactory())
                                    .isSameAs(boundary.connectionFactory());
                            assertThat(boundary.transactionManager().getConnectionFactory())
                                    .isSameAs(boundary.connectionFactory());
                            assertThat(boundary.operations().getConverter())
                                    .isSameAs(context.getBean(R2dbcConverter.class));
                            assertThat(boundary.drafts())
                                    .isSameAs(
                                            context.getBean(
                                                    ProductDefinitionDraftRepository.class));
                            assertThat(boundary.costs())
                                    .isSameAs(
                                            context.getBean(
                                                    ProductDefinitionCostControlRepository.class));
                            assertThat(boundary.toString())
                                    .doesNotContain(password, "contract_commands");
                        });
    }

    @Test
    void enabledWithoutExplicitCredentialsFailsClosed() {
        runner().withPropertyValues("product.definitions.commands.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void reusingReadIdentityFailsClosed() {
        runner().withPropertyValues(
                        "product.definitions.commands.enabled=true",
                        "product.definitions.commands.username=contract_reader",
                        "product.definitions.commands.password=" + UUID.randomUUID())
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void commandFactoryRetainsActualReadTargetDriverAndTlsOptions() {
        enabled()
                .run(
                        context -> {
                            assertThat(context)
                                    .hasNotFailed()
                                    .hasSingleBean(ProductDefinitionCommandPersistence.class);
                            var read =
                                    org.springframework.boot.r2dbc.ConnectionFactoryBuilder
                                            .derivedFrom(context.getBean(ConnectionFactory.class))
                                            .buildOptions();
                            var command =
                                    org.springframework.boot.r2dbc.ConnectionFactoryBuilder
                                            .derivedFrom(
                                                    context.getBean(
                                                                    ProductDefinitionCommandPersistence
                                                                            .class)
                                                            .connectionFactory())
                                            .buildOptions();
                            for (var option :
                                    java.util.List.of(
                                            io.r2dbc.spi.ConnectionFactoryOptions.DRIVER,
                                            io.r2dbc.spi.ConnectionFactoryOptions.HOST,
                                            io.r2dbc.spi.ConnectionFactoryOptions.PORT,
                                            io.r2dbc.spi.ConnectionFactoryOptions.DATABASE,
                                            io.r2dbc.spi.Option.valueOf("sslMode")))
                                assertThat(command.getValue(option))
                                        .isEqualTo(read.getValue(option));
                            assertThat(command.getValue(io.r2dbc.spi.ConnectionFactoryOptions.USER))
                                    .isEqualTo("contract_commands");
                        });
    }

    @Test
    void contextDisposesOnlyOwnedCommandPool() {
        var retained =
                new java.util.concurrent.atomic.AtomicReference<io.r2dbc.pool.ConnectionPool>();
        var read = new java.util.concurrent.atomic.AtomicReference<ConnectionFactory>();
        enabled()
                .run(
                        context -> {
                            assertThat(context)
                                    .hasSingleBean(ProductDefinitionCommandPersistence.class);
                            retained.set(
                                    (io.r2dbc.pool.ConnectionPool)
                                            context.getBean(
                                                            ProductDefinitionCommandPersistence
                                                                    .class)
                                                    .connectionFactory());
                            read.set(context.getBean(ConnectionFactory.class));
                            context.getBean(ProductDefinitionCommandPersistence.class).close();
                            assertThat(retained.get().isDisposed()).isTrue();
                            assertThat(((io.r2dbc.pool.ConnectionPool) read.get()).isDisposed())
                                    .isFalse();
                        });
    }

    @Test
    void commandTemplateRetainsConfiguredJsonbAndIdentifierConversions() {
        enabled()
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            var converter =
                                    context.getBean(ProductDefinitionCommandPersistence.class)
                                            .operations()
                                            .getConverter();
                            var id = UUID.randomUUID();
                            assertThat(
                                            converter
                                                    .getConversionService()
                                                    .convert(new Identifier(id), UUID.class))
                                    .isEqualTo(id);
                            var node =
                                    new com.fasterxml.jackson.databind.ObjectMapper()
                                            .createObjectNode()
                                            .put("value", "Ω");
                            var json =
                                    converter
                                            .getConversionService()
                                            .convert(node, io.r2dbc.postgresql.codec.Json.class);
                            assertThat(json.asString()).isEqualTo(node.toString());
                        });
    }

    @Test
    void hiddenTemplateUsesActualAuditingCallbacksAndConfiguredSharedUtcProvider() {
        enabled()
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            var operations =
                                    context.getBean(ProductDefinitionCommandPersistence.class)
                                            .operations();
                            var callbacks =
                                    (org.springframework.data.mapping.callback
                                                    .ReactiveEntityCallbacks)
                                            org.springframework.test.util.ReflectionTestUtils
                                                    .getField(operations, "entityCallbacks");
                            assertThat(callbacks).isNotNull();
                            var audited =
                                    callbacks
                                            .callback(
                                                    org.springframework.data.r2dbc.mapping.event
                                                            .BeforeConvertCallback.class,
                                                    new AuditedMarker(null, null),
                                                    org.springframework.data.relational.core.sql
                                                            .SqlIdentifier.unquoted("audit_marker"))
                                            .block(java.time.Duration.ofSeconds(2));
                            assertThat(audited.createdAt())
                                    .isEqualTo(
                                            java.time.OffsetDateTime.parse("2026-10-24T12:00:00Z"));
                            assertThat(audited.createdAt().getOffset())
                                    .isEqualTo(java.time.ZoneOffset.UTC);
                        });
    }

    record Identifier(UUID value) {}

    @org.springframework.data.relational.core.mapping.Table("audit_marker")
    record AuditedMarker(
            @org.springframework.data.annotation.Id UUID id,
            @org.springframework.data.annotation.CreatedDate java.time.OffsetDateTime createdAt) {}

    @org.springframework.data.convert.WritingConverter
    static class IdentifierConverter
            implements org.springframework.core.convert.converter.Converter<Identifier, UUID> {
        public UUID convert(Identifier value) {
            return value.value();
        }
    }

    @org.springframework.data.convert.WritingConverter
    static class JsonConverter
            implements org.springframework.core.convert.converter.Converter<
                    com.fasterxml.jackson.databind.JsonNode, io.r2dbc.postgresql.codec.Json> {
        public io.r2dbc.postgresql.codec.Json convert(
                com.fasterxml.jackson.databind.JsonNode value) {
            return io.r2dbc.postgresql.codec.Json.of(value.toString());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableR2dbcRepositories(basePackages = "io.fireflysoftware.core.product.models.repositories")
    @org.springframework.data.r2dbc.config.EnableR2dbcAuditing(
            dateTimeProviderRef = "productTestUtc")
    static class ReadRepositories {
        @org.springframework.context.annotation.Bean
        org.springframework.data.auditing.DateTimeProvider productTestUtc() {
            return new org.fireflyframework.core.auditing.UtcOffsetDateTimeProvider(
                    java.time.Clock.fixed(
                            java.time.Instant.parse("2026-10-24T12:00:00Z"),
                            java.time.ZoneId.of("America/New_York")));
        }

        @org.springframework.context.annotation.Bean
        org.springframework.data.r2dbc.convert.R2dbcCustomConversions configuredConversions() {
            return org.springframework.data.r2dbc.convert.R2dbcCustomConversions.of(
                    org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE,
                    java.util.List.of(new IdentifierConverter(), new JsonConverter()));
        }
    }
}

package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import io.fireflysoftware.core.product.models.config.ProductDefinitionReadConnectionSource;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionPublicationReadPersistence;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.r2dbc.ConnectionFactoryBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.convert.R2dbcConverter;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "product.definitions",
        name = {"commands.enabled", "publication-read.enabled"},
        havingValue = "true")
@EnableConfigurationProperties(ProductDefinitionPublicationReadProperties.class)
public class ProductDefinitionPublicationReadConfiguration {
    @Bean(name = "productDefinitionPublicationReadPersistence", destroyMethod = "close")
    public ProductDefinitionPublicationReadPersistence productDefinitionPublicationReadPersistence(
            ConnectionFactory readFactory,
            R2dbcConverter converter,
            ApplicationContext context,
            ProductDefinitionPublicationReadProperties properties,
            ProductDefinitionCommandProperties commands,
            @Qualifier("productDefinitionReadConnectionSource")
                    ProductDefinitionReadConnectionSource ownerSource) {
        String username = properties.getUsername();
        if (username == null
                || username.isBlank()
                || !username.equals(username.strip())
                || properties.getPassword() == null
                || properties.getPassword().isBlank())
            throw new IllegalStateException(
                    "PRODUCT_DEFINITION_PUBLICATION_READ_CREDENTIALS_REQUIRED");
        ConnectionFactoryOptions options;
        try {
            options = ownerSource.optionsFor(readFactory);
        } catch (RuntimeException unavailable) {
            throw new IllegalStateException("PRODUCT_DEFINITION_READ_OPTIONS_UNAVAILABLE");
        }
        var readUser = options.getValue(ConnectionFactoryOptions.USER);
        if (readUser == null
                || readUser.toString().isBlank()
                || username.equals(readUser.toString())
                || username.equals(commands.getUsername()))
            throw new IllegalStateException(
                    "PRODUCT_DEFINITION_DISTINCT_PUBLICATION_READ_IDENTITY_REQUIRED");
        if (!"postgresql".equals(options.getValue(ConnectionFactoryOptions.DRIVER)))
            throw new IllegalStateException("PRODUCT_DEFINITION_POSTGRESQL_REQUIRED");
        ConnectionFactory commandFactory;
        try {
            commandFactory =
                    ConnectionFactoryBuilder.withOptions(options.mutate())
                            .username(username)
                            .password(properties.getPassword())
                            .build();
        } catch (RuntimeException invalid) {
            throw new IllegalStateException(
                    "PRODUCT_DEFINITION_PUBLICATION_READ_FACTORY_UNAVAILABLE");
        }
        var pool =
                new ConnectionPool(
                        ConnectionPoolConfiguration.builder(commandFactory)
                                .name("product-definition-publication-read")
                                .initialSize(0)
                                .maxSize(2)
                                .maxAcquireTime(Duration.ofSeconds(10))
                                .maxCreateConnectionTime(Duration.ofSeconds(5))
                                .maxIdleTime(Duration.ofMinutes(5))
                                .build());
        try {
            return new ProductDefinitionPublicationReadPersistence(pool, converter, context);
        } catch (RuntimeException invalid) {
            pool.dispose();
            throw new IllegalStateException(
                    "PRODUCT_DEFINITION_PUBLICATION_READ_PERSISTENCE_UNAVAILABLE");
        }
    }
}

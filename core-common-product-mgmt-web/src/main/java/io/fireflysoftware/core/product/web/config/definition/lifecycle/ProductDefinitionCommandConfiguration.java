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

package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import io.fireflysoftware.core.product.models.config.ProductDefinitionReadConnectionSource;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.*;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

import org.springframework.beans.factory.ObjectProvider;
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
        prefix = "product.definitions.commands",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(ProductDefinitionCommandProperties.class)
public class ProductDefinitionCommandConfiguration {
    @Bean(name = "productDefinitionCommandPersistence", destroyMethod = "close")
    public ProductDefinitionCommandPersistence productDefinitionCommandPersistence(
            ConnectionFactory readFactory,
            R2dbcConverter converter,
            ApplicationContext context,
            ProductDefinitionCommandProperties properties,
            @Qualifier("productDefinitionReadConnectionSource")
                    ObjectProvider<ProductDefinitionReadConnectionSource> ownerSources) {
        String username = properties.getUsername();
        if (username == null
                || username.isBlank()
                || !username.equals(username.strip())
                || properties.getPassword() == null
                || properties.getPassword().isBlank())
            throw new IllegalStateException("PRODUCT_DEFINITION_COMMAND_CREDENTIALS_REQUIRED");
        ConnectionFactoryOptions options;
        try {
            var ownerSource = ownerSources.getIfAvailable();
            options =
                    ownerSource == null
                            ? ConnectionFactoryBuilder.derivedFrom(readFactory).buildOptions()
                            : ownerSource.optionsFor(readFactory);
        } catch (RuntimeException unavailable) {
            throw new IllegalStateException("PRODUCT_DEFINITION_READ_OPTIONS_UNAVAILABLE");
        }
        var readUser = options.getValue(ConnectionFactoryOptions.USER);
        if (readUser == null
                || readUser.toString().isBlank()
                || username.equals(readUser.toString()))
            throw new IllegalStateException(
                    "PRODUCT_DEFINITION_DISTINCT_COMMAND_IDENTITY_REQUIRED");
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
            throw new IllegalStateException("PRODUCT_DEFINITION_COMMAND_FACTORY_UNAVAILABLE");
        }
        var pool =
                new ConnectionPool(
                        ConnectionPoolConfiguration.builder(commandFactory)
                                .name("product-definition-command")
                                .initialSize(0)
                                .maxSize(4)
                                .maxAcquireTime(Duration.ofSeconds(10))
                                .maxCreateConnectionTime(Duration.ofSeconds(5))
                                .maxIdleTime(Duration.ofMinutes(5))
                                .build());
        try {
            return new ProductDefinitionCommandPersistence(pool, converter, context);
        } catch (RuntimeException invalid) {
            pool.dispose();
            throw new IllegalStateException("PRODUCT_DEFINITION_COMMAND_PERSISTENCE_UNAVAILABLE");
        }
    }

    @Bean(name = "productDefinitionDraftRepository")
    public ProductDefinitionDraftRepository productDefinitionDraftRepository(
            ProductDefinitionCommandPersistence persistence) {
        return persistence.drafts();
    }

    @Bean(name = "productDefinitionCostControlRepository")
    public ProductDefinitionCostControlRepository productDefinitionCostControlRepository(
            ProductDefinitionCommandPersistence persistence) {
        return persistence.costs();
    }
}

/*
 * Copyright 2025 Firefly Software Foundation
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

package io.fireflysoftware.core.product.models.config;

import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

import java.util.Objects;

public final class ProductDefinitionReadConnectionSource {
    private final ConnectionFactoryOptions options;
    private final PostgresqlConnectionFactory factory;

    public ProductDefinitionReadConnectionSource(ConnectionFactoryOptions options) {
        this.options = Objects.requireNonNull(options);
        this.factory = new PostgresqlConnectionFactoryProvider().create(options);
    }

    public PostgresqlConnectionFactory readFactory() {
        return factory;
    }

    public ConnectionFactoryOptions optionsFor(ConnectionFactory expectedReadFactory) {
        if (expectedReadFactory != factory)
            throw new IllegalStateException("PRODUCT_DEFINITION_READ_FACTORY_BINDING_REQUIRED");
        return options;
    }

    @Override
    public String toString() {
        return "ProductDefinitionReadConnectionSource";
    }
}

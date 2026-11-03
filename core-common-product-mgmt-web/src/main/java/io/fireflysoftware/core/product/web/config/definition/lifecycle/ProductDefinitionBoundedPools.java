package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import io.fireflysoftware.core.product.models.config.ProductDefinitionReadConnectionSource;
import io.r2dbc.pool.*;
import io.r2dbc.spi.*;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.r2dbc.ConnectionFactoryBuilder;

/** Creates only a new owner pool; it never changes the existing owner factory or TLS options. */
final class ProductDefinitionBoundedPools {
    private ProductDefinitionBoundedPools(){}
    static void credentials(String username,String password){
        if(username==null || username.isBlank() || !username.equals(username.strip()) || password==null || password.isBlank())
            throw new IllegalStateException("PRODUCT_INSURANCE_CREDENTIALS_REQUIRED");
    }
    static ConnectionPool create(String name,ConnectionFactory owner,ProductDefinitionReadConnectionSource source,String username,String password,String...forbidden){
        credentials(username,password);
        for(String other:forbidden)if(username.equals(other))throw new IllegalStateException("PRODUCT_INSURANCE_DISTINCT_IDENTITY_REQUIRED");
        final ConnectionFactoryOptions options;
        try{options=Objects.requireNonNull(source).optionsFor(owner);}catch(RuntimeException invalid){throw new IllegalStateException("PRODUCT_DEFINITION_READ_OPTIONS_UNAVAILABLE");}
        var original=options.getValue(ConnectionFactoryOptions.USER);
        if(original==null || original.toString().isBlank() || username.equals(original.toString()))throw new IllegalStateException("PRODUCT_INSURANCE_DISTINCT_IDENTITY_REQUIRED");
        if(!"postgresql".equals(options.getValue(ConnectionFactoryOptions.DRIVER)))throw new IllegalStateException("PRODUCT_DEFINITION_POSTGRESQL_REQUIRED");
        try{
            var factory=ConnectionFactoryBuilder.withOptions(options.mutate()).username(username).password(password).build();
            return new ConnectionPool(ConnectionPoolConfiguration.builder(factory).name(name).initialSize(0).maxSize(2)
                    .maxAcquireTime(Duration.ofSeconds(5)).maxCreateConnectionTime(Duration.ofSeconds(5)).maxIdleTime(Duration.ofMinutes(5)).build());
        }catch(RuntimeException invalid){throw new IllegalStateException("PRODUCT_INSURANCE_POOL_UNAVAILABLE");}
    }
}

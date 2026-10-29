package io.fireflysoftware.core.product.web.config.definition;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionReadAuthorizationImpl;
import io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionServingServiceImpl;
import io.fireflysoftware.core.product.models.repositories.definition.*;
import io.r2dbc.spi.ConnectionFactory;

import org.fireflyframework.security.rs.ResourceServerProperties;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.Clock;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class ProductDefinitionServingConfiguration {
    @Bean
    public ProductDefinitionReadAuthorization productDefinitionReadAuthorization(
            SecurityContextPort principals,
            @Qualifier("productDefinitionReadPolicy") PolicyDecisionPort policies,
            ResourceServerProperties trust) {
        String issuer = trust.getIssuer();
        return new ProductDefinitionReadAuthorizationImpl(
                principals,
                policies,
                Clock.systemUTC(),
                issuer == null || issuer.isBlank() ? Set.of() : Set.of(issuer));
    }

    @Bean
    public ProductDefinitionServingService productDefinitionServingService(
            ProductDefinitionReadAuthorization authorization,
            ProductDefinitionRootRepository roots,
            ProductDefinitionRevisionRepository revisions,
            ProductDefinitionPublicationRepository publications,
            ProductDefinitionPublicationControlRepository controls,
            ProductDefinitionReadContextRepository context,
            ProductDefinitionVersionIdentityRepository versions,
            R2dbcEntityOperations operations,
            ConnectionFactory connectionFactory) {
        if (operations.getDatabaseClient().getConnectionFactory() != connectionFactory) {
            throw new IllegalStateException(
                    "Product reads require the same ConnectionFactory as Spring Data repositories");
        }
        var manager = new R2dbcTransactionManager(connectionFactory);
        manager.setEnforceReadOnly(true);
        var definition = new DefaultTransactionDefinition();
        definition.setName("product-definition-serving-read");
        definition.setReadOnly(true);
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        definition.setTimeout(10);
        return new ProductDefinitionServingServiceImpl(
                authorization,
                roots,
                revisions,
                publications,
                controls,
                context,
                versions,
                TransactionalOperator.create(manager, definition));
    }
}

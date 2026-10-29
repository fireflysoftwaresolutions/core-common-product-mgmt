package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.core.services.definition.impl.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandPersistence;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionPublicationReadPersistence;

import org.fireflyframework.security.rs.ResourceServerProperties;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

import java.time.Clock;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "product.definitions.commands",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties({
    ProductDefinitionCommandProperties.class,
    ResourceServerProperties.class
})
public class ProductDefinitionLifecycleConfiguration {
    @Bean(name = "productDefinitionLifecycleClock")
    public Clock productDefinitionLifecycleClock() {
        return Clock.systemUTC();
    }

    @Bean
    public ProductDefinitionCommandAuthorizationImpl productDefinitionCommandAuthorization(
            SecurityContextPort principals,
            @Qualifier("productDefinitionCommandPolicy") PolicyDecisionPort policies,
            ResourceServerProperties trust,
            ProductDefinitionCommandProperties commands,
            @Qualifier("productDefinitionLifecycleClock") Clock clock) {
        var issuer = trust.getIssuer();
        return new ProductDefinitionCommandAuthorizationImpl(
                principals,
                policies,
                clock,
                issuer == null || issuer.isBlank() ? Set.of() : Set.of(issuer),
                commands.getPolicyProfile());
    }

    @Bean
    public ProductDefinitionLifecycleService productDefinitionLifecycleService(
            ProductDefinitionCommandAuthorization authorization,
            ProductDefinitionCommandPersistence persistence) {
        return new ProductDefinitionLifecycleServiceImpl(authorization, persistence);
    }

    @Bean
    public ProductDefinitionReviewReadService productDefinitionReviewReadService(
            ProductDefinitionReviewAuthorization authorization,
            ProductDefinitionCommandPersistence persistence) {
        return new ProductDefinitionReviewReadServiceImpl(authorization, persistence);
    }

    @Bean
    public ProductDefinitionCostReadService productDefinitionCostReadService(
            ProductDefinitionCostReadAuthorization authorization,
            ProductDefinitionCommandPersistence persistence) {
        return new ProductDefinitionCostReadServiceImpl(authorization, persistence);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "product.definitions.publication-read",
            name = "enabled",
            havingValue = "true")
    public ProductDefinitionPublicationReadService productDefinitionPublicationReadService(
            ProductDefinitionPublicationReadAuthorization authorization,
            ProductDefinitionPublicationReadPersistence persistence,
            @Qualifier("productDefinitionLifecycleClock") Clock clock) {
        return new ProductDefinitionPublicationReadServiceImpl(authorization, persistence, clock);
    }
}

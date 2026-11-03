package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.core.services.definition.impl.*;
import io.fireflysoftware.core.product.models.config.ProductDefinitionReadConnectionSource;
import io.r2dbc.spi.ConnectionFactory;
import org.fireflyframework.security.rs.ResourceServerProperties;
import org.fireflyframework.security.spi.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import java.time.Clock;
import java.util.*;
import java.util.function.LongSupplier;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="product.definitions.debit-card-read",name="enabled",havingValue="true")
@EnableConfigurationProperties({DebitCardPurposeReadProperties.class,DebitCardReferenceProperties.class,ProductDefinitionCommandProperties.class,ProductDefinitionPublicationReadProperties.class,ResourceServerProperties.class})
public class DebitCardPurposeConfiguration {
    @Bean
    @ConditionalOnProperty(prefix="product.definitions.debit-card-read",name="current-tenant-binding-enabled",havingValue="true")
    public DebitCardTenantBindingResolver debitCardTenantBindingResolver(DebitCardPurposeReadProperties properties,
            SecurityContextPort principals,org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager clients){
        return new io.fireflysoftware.core.product.web.adapters.identity.impl.GeneratedConfigDebitCardTenantBindingResolverImpl(principals,clients,
            properties.getCurrentTenantBindingEndpoint(),properties.getCurrentTenantBindingRegistrationId(),properties.getCurrentTenantBindingIssuer(),
            properties.getCurrentTenantBindingPlatformTenant(),properties.getTenantClaim(),Clock.systemUTC());
    }
    @Bean
    @ConditionalOnProperty(prefix="product.definitions.debit-card-read.references",name="enabled",havingValue="true")
    public DebitCardPurposeReferenceResolver debitCardPurposeReferenceResolver(DebitCardReferenceProperties properties,
            SecurityContextPort principals,org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager clients){
        return new io.fireflysoftware.core.product.web.adapters.card.impl.GeneratedDebitCardPurposeReferenceResolverImpl(principals,clients,properties,Clock.systemUTC(),System::nanoTime);
    }
    @Bean(destroyMethod="close")
    public DebitCardPurposeRuntime debitCardPurposeRuntime(DebitCardPurposeReferenceResolver references,
            DebitCardPurposeReadProperties properties,ProductDefinitionCommandProperties commands,ProductDefinitionPublicationReadProperties publication,
            ResourceServerProperties trust,SecurityContextPort principals,@Qualifier("productDefinitionCommandPolicy") PolicyDecisionPort policies,
            ConnectionFactory readFactory,R2dbcConverter converter,ApplicationContext context,
            @Qualifier("productDefinitionReadConnectionSource") ProductDefinitionReadConnectionSource ownerSource){
        if(!commands.isEnabled())throw new IllegalStateException("PRODUCT_COMMAND_CONFIGURATION_REQUIRED");
        ProductDefinitionBoundedPools.credentials(properties.getUsername(),properties.getPassword());
        if(properties.getUsername().equals(commands.getUsername()) || properties.getUsername().equals(publication.getUsername()))
            throw new IllegalStateException("PRODUCT_DEBIT_CARD_DISTINCT_IDENTITY_REQUIRED");
        if(trust.getIssuer()==null || trust.getIssuer().isBlank() || !trust.getAudiences().contains(properties.getAudience())
                || !trust.isTenantRequired() || !trust.getTenantClaimPaths().contains(properties.getTenantClaim()))
            throw new IllegalStateException("PRODUCT_DEBIT_CARD_CALLER_TRUST_REQUIRED");
        Clock clock=Clock.systemUTC();LongSupplier nanos=System::nanoTime;
        var authority=new DebitCardPurposeAuthorizationImpl(principals,policies,trust.getIssuer(),properties.getAudience(),properties.getTenantClaim(),properties.getClientClaim(),properties.getClientId(),clock,nanos,properties.isCurrentTenantBindingEnabled()?context.getBean(DebitCardTenantBindingResolver.class):null);
        Objects.requireNonNull(references);
        var pool=ProductDefinitionBoundedPools.create("product-debit-card-read",readFactory,ownerSource,properties.getUsername(),properties.getPassword(),commands.getUsername(),publication.getUsername());
        try{
            var transaction=new io.fireflysoftware.core.product.models.repositories.definition.purpose.impl.DebitCardPurposeTransactionImpl(pool,converter,context,clock,nanos);
            var store=new DebitCardPurposeReadStoreImpl(transaction,context,clock,nanos);
            return new DebitCardPurposeRuntime(pool,new DebitCardPurposeServiceImpl(authority,store,references,clock,nanos));
        }catch(RuntimeException invalid){pool.dispose();throw new IllegalStateException("PRODUCT_DEBIT_CARD_PURPOSE_RUNTIME_UNAVAILABLE");}
    }
}

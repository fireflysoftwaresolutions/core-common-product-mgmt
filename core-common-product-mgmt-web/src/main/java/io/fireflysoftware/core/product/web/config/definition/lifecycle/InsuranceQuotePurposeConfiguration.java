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
@ConditionalOnProperty(prefix="product.definitions.insurance-quote-read",name="enabled",havingValue="true")
@EnableConfigurationProperties({InsuranceQuotePurposeReadProperties.class,ProductDefinitionCommandProperties.class,ProductDefinitionPublicationReadProperties.class,ResourceServerProperties.class})
public class InsuranceQuotePurposeConfiguration {
    @Bean(destroyMethod="close")
    public InsuranceQuotePurposeRuntime insuranceQuotePurposeRuntime(InsuranceQuotePurposeReferenceResolver references,
            InsuranceQuotePurposeReadProperties properties,ProductDefinitionCommandProperties commands,ProductDefinitionPublicationReadProperties publication,
            ResourceServerProperties trust,SecurityContextPort principals,@Qualifier("productDefinitionCommandPolicy") PolicyDecisionPort policies,
            ConnectionFactory readFactory,R2dbcConverter converter,ApplicationContext context,
            @Qualifier("productDefinitionReadConnectionSource") ProductDefinitionReadConnectionSource ownerSource){
        if(!commands.isEnabled())throw new IllegalStateException("PRODUCT_COMMAND_CONFIGURATION_REQUIRED");
        ProductDefinitionBoundedPools.credentials(properties.getUsername(),properties.getPassword());
        if(properties.getUsername().equals(commands.getUsername()) || properties.getUsername().equals(publication.getUsername()))
            throw new IllegalStateException("PRODUCT_INSURANCE_DISTINCT_IDENTITY_REQUIRED");
        if(trust.getIssuer()==null || trust.getIssuer().isBlank() || !trust.getAudiences().contains(properties.getAudience())
                || !trust.isTenantRequired() || !trust.getTenantClaimPaths().contains(properties.getTenantClaim()))
            throw new IllegalStateException("PRODUCT_INSURANCE_CALLER_TRUST_REQUIRED");
        Clock clock=Clock.systemUTC();LongSupplier nanos=System::nanoTime;
        var authority=new InsuranceQuotePurposeAuthorization(principals,policies,trust.getIssuer(),properties.getAudience(),properties.getTenantClaim(),properties.getClientClaim(),properties.getClientId(),clock,nanos);
        Objects.requireNonNull(references);
        var pool=ProductDefinitionBoundedPools.create("product-insurance-quote-read",readFactory,ownerSource,properties.getUsername(),properties.getPassword(),commands.getUsername(),publication.getUsername());
        try{
            var transaction=new io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeTransaction(pool,converter,context,clock,nanos);
            var store=new InsuranceQuotePurposeReadStoreImpl(transaction,context,clock,nanos);
            return new InsuranceQuotePurposeRuntime(pool,new InsuranceQuotePurposeServiceImpl(authority,store,references,clock,nanos));
        }catch(RuntimeException invalid){pool.dispose();throw new IllegalStateException("PRODUCT_INSURANCE_PURPOSE_RUNTIME_UNAVAILABLE");}
    }
}

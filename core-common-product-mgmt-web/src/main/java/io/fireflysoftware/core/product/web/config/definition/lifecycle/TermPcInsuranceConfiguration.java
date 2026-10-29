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
@ConditionalOnProperty(prefix="product.definitions.term-pc",name="enabled",havingValue="true")
@EnableConfigurationProperties({TermPcInsuranceProperties.class,ProductDefinitionCommandProperties.class,ResourceServerProperties.class})
public class TermPcInsuranceConfiguration {
    @Bean(destroyMethod="close")
    public TermPcInsuranceRuntime termPcInsuranceRuntime(ProductDefinitionCommandProperties commands,TermPcInsuranceProperties properties,
            ResourceServerProperties trust,SecurityContextPort principals,@Qualifier("productDefinitionCommandPolicy") PolicyDecisionPort policies,
            ConnectionFactory readFactory,R2dbcConverter converter,ApplicationContext context,
            @Qualifier("productDefinitionReadConnectionSource") ProductDefinitionReadConnectionSource ownerSource){
        if(!commands.isEnabled())throw new IllegalStateException("PRODUCT_COMMAND_CONFIGURATION_REQUIRED");
        ProductDefinitionBoundedPools.credentials(commands.getUsername(),commands.getPassword());
        var backend=GovernedDefinitionProfile.TERM_PC_INSURANCE;
        if(!backend.policyProfile().equals(properties.getPolicyProfile()) || trust.getIssuer()==null || trust.getIssuer().isBlank())
            throw new IllegalStateException("PRODUCT_TERM_PC_POLICY_TRUST_REQUIRED");
        Clock clock=Clock.systemUTC();LongSupplier nanos=System::nanoTime;
        var authority=new ProductDefinitionCommandAuthorizationImpl(principals,policies,clock,Set.of(trust.getIssuer()),properties.getPolicyProfile(),backend);
        var pool=ProductDefinitionBoundedPools.create("product-definition-term-pc",readFactory,ownerSource,commands.getUsername(),commands.getPassword());
        try{return new TermPcInsuranceRuntime(new io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandPersistence(pool,converter,context,true),authority,clock,nanos);}
        catch(RuntimeException invalid){pool.dispose();throw new IllegalStateException("PRODUCT_TERM_PC_RUNTIME_UNAVAILABLE");}
    }
}

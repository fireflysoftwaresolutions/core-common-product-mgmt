package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import io.fireflysoftware.core.product.core.services.definition.DepositAccountDocumentReferenceResolver;
import io.fireflysoftware.core.product.web.adapters.document.ProductDocumentOwnerTokenProvider;
import io.fireflysoftware.core.product.web.adapters.document.impl.SdkDepositAccountDocumentReferenceResolver;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"product.definitions.deposit.enabled","product.definitions.deposit.document-references.enabled"},havingValue="true")
@EnableConfigurationProperties(DepositDocumentOwnerProperties.class)
public class DepositDocumentOwnerConfiguration {
    @Bean
    public DepositAccountDocumentReferenceResolver depositAccountDocumentReferenceResolver(
            ProductDocumentOwnerTokenProvider tokens,DepositDocumentOwnerProperties properties) {
        return new SdkDepositAccountDocumentReferenceResolver(tokens,properties.getConfigurationBase(),properties.getMastersBase(),
            properties.getConfigurationIssuer(),properties.getMastersIssuer(),Clock.systemUTC(),System::nanoTime);
    }
}

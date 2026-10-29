package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("product.definitions.deposit.document-references")
public final class DepositDocumentOwnerProperties {
    private boolean enabled;
    private URI configurationBase,mastersBase;
    private String configurationIssuer,mastersIssuer;
    public boolean isEnabled(){return enabled;}
    public void setEnabled(boolean value){enabled=value;}
    public URI getConfigurationBase(){return configurationBase;}
    public void setConfigurationBase(URI value){configurationBase=value;}
    public URI getMastersBase(){return mastersBase;}
    public void setMastersBase(URI value){mastersBase=value;}
    public String getConfigurationIssuer(){return configurationIssuer;}
    public void setConfigurationIssuer(String value){configurationIssuer=value;}
    public String getMastersIssuer(){return mastersIssuer;}
    public void setMastersIssuer(String value){mastersIssuer=value;}
    @Override public String toString(){return "DepositDocumentOwnerProperties";}
}

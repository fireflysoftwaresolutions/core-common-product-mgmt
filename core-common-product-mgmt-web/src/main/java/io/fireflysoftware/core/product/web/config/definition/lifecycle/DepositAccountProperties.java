package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("product.definitions.deposit")
public final class DepositAccountProperties {
    private boolean enabled;
    private String policyProfile;
    public boolean isEnabled(){return enabled;}
    public void setEnabled(boolean value){enabled=value;}
    public String getPolicyProfile(){return policyProfile;}
    public void setPolicyProfile(String value){policyProfile=value;}
    @Override public String toString(){return "DepositAccountProperties";}
}

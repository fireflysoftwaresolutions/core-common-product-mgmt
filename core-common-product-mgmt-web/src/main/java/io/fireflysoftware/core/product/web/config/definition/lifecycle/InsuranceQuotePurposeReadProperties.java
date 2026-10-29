package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("product.definitions.insurance-quote-read")
public final class InsuranceQuotePurposeReadProperties {
    private boolean enabled;
    private String username;
    private String password;
    private String audience;
    private String tenantClaim;
    private String clientClaim;
    private String clientId;
    public boolean isEnabled(){return enabled;}
    public void setEnabled(boolean value){enabled=value;}
    public String getUsername(){return username;}
    public void setUsername(String value){username=value;}
    public String getPassword(){return password;}
    public void setPassword(String value){password=value;}
    public String getAudience(){return audience;}
    public void setAudience(String value){audience=value;}
    public String getTenantClaim(){return tenantClaim;}
    public void setTenantClaim(String value){tenantClaim=value;}
    public String getClientClaim(){return clientClaim;}
    public void setClientClaim(String value){clientClaim=value;}
    public String getClientId(){return clientId;}
    public void setClientId(String value){clientId=value;}
    @Override public String toString(){return "InsuranceQuotePurposeReadProperties";}
}

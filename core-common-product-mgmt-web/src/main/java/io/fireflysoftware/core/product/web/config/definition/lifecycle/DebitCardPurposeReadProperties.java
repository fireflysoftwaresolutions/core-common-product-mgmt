package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("product.definitions.debit-card-read")
public final class DebitCardPurposeReadProperties {
    private boolean enabled;
    private boolean currentTenantBindingEnabled;
    private java.net.URI currentTenantBindingEndpoint;
    private String currentTenantBindingRegistrationId;
    private String currentTenantBindingIssuer;
    private String currentTenantBindingPlatformTenant;
    private String username;
    private String password;
    private String audience;
    private String tenantClaim;
    private String clientClaim;
    private String clientId;
    public java.net.URI getCurrentTenantBindingEndpoint(){return currentTenantBindingEndpoint;}
    public void setCurrentTenantBindingEndpoint(java.net.URI value){currentTenantBindingEndpoint=value;}
    public String getCurrentTenantBindingRegistrationId(){return currentTenantBindingRegistrationId;}
    public void setCurrentTenantBindingRegistrationId(String value){currentTenantBindingRegistrationId=value;}
    public String getCurrentTenantBindingIssuer(){return currentTenantBindingIssuer;}
    public void setCurrentTenantBindingIssuer(String value){currentTenantBindingIssuer=value;}
    public String getCurrentTenantBindingPlatformTenant(){return currentTenantBindingPlatformTenant;}
    public void setCurrentTenantBindingPlatformTenant(String value){currentTenantBindingPlatformTenant=value;}
    public boolean isCurrentTenantBindingEnabled(){return currentTenantBindingEnabled;}
    public void setCurrentTenantBindingEnabled(boolean value){currentTenantBindingEnabled=value;}
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
    @Override public String toString(){return "DebitCardPurposeReadProperties";}
}

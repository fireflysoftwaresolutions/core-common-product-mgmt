package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("product.definitions.publication-read")
public final class ProductDefinitionPublicationReadProperties {
    private boolean enabled;
    private String username;
    private String password;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "ProductDefinitionPublicationReadProperties";
    }
}

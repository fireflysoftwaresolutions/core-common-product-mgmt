/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("product.definitions.commands")
public final class ProductDefinitionCommandProperties {
    private boolean enabled;
    private String username;
    private String password;
    private String policyProfile;
    private String policyBaseUrl;
    private String policyPath;

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

    public String getPolicyProfile() {
        return policyProfile;
    }

    public void setPolicyProfile(String value) {
        policyProfile = value;
    }

    public String getPolicyBaseUrl() {
        return policyBaseUrl;
    }

    public void setPolicyBaseUrl(String value) {
        policyBaseUrl = value;
    }

    public String getPolicyPath() {
        return policyPath;
    }

    public void setPolicyPath(String value) {
        policyPath = value;
    }

    @Override
    public String toString() {
        return "ProductDefinitionCommandProperties";
    }
}

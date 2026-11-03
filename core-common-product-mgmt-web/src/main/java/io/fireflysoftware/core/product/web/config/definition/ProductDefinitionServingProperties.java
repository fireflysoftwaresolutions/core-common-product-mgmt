package io.fireflysoftware.core.product.web.config.definition;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "product.definitions.serving")
public record ProductDefinitionServingProperties(String policyBaseUrl, String policyPath) {}

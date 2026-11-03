package io.fireflysoftware.core.product.core.services.definition;

import org.fireflyframework.security.api.domain.SecurityPrincipal;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionPublicationReadAuthorization {
    enum Target {
        READINESS(
                "product:definitions:publish",
                "product.definition.publication.readiness",
                "readiness"),
        DISCOVERY(
                "product:definitions:withdraw",
                "product.definition.publication.discovery",
                "current");

        private final String scope;
        private final String action;
        private final String resourceSuffix;

        Target(String scope, String action, String resourceSuffix) {
            this.scope = scope;
            this.action = action;
            this.resourceSuffix = resourceSuffix;
        }

        public String requiredScope() {
            return scope;
        }

        public String action() {
            return action;
        }

        public String resourceSuffix() {
            return resourceSuffix;
        }
    }

    record Scope(UUID tenantId, UUID rootId, Target target) {}

    record Admission(
            SecurityPrincipal principal,
            Scope scope,
            long actorExpiresAtEpochMillis,
            long validUntilEpochMillis) {}

    Mono<Admission> authorize(Scope scope);

    Mono<Void> reauthorize(Admission admission);
}

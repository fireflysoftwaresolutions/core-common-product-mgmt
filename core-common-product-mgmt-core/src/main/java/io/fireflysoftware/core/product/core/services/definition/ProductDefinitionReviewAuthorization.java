package io.fireflysoftware.core.product.core.services.definition;

import org.fireflyframework.security.api.domain.SecurityPrincipal;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionReviewAuthorization {
    enum Target {
        EXACT,
        CURRENT
    }

    record Scope(UUID tenantId, UUID rootId, UUID submissionId, Target target) {
        public Scope(UUID tenantId, UUID rootId, UUID submissionId) {
            this(tenantId, rootId, submissionId, Target.EXACT);
        }

        public static Scope current(UUID tenantId, UUID rootId) {
            return new Scope(tenantId, rootId, null, Target.CURRENT);
        }
    }

    record Admission(
            SecurityPrincipal principal,
            Scope scope,
            long actorExpiresAtEpochMillis,
            long validUntilEpochMillis) {}

    default GovernedDefinitionProfile definitionProfile() { return GovernedDefinitionProfile.ACCOUNT_TARIFF; }

    Mono<Admission> authorize(Scope scope);

    Mono<Void> reauthorize(Admission admission);
}

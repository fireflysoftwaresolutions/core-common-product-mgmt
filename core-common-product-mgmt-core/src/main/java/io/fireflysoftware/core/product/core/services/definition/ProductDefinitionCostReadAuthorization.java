package io.fireflysoftware.core.product.core.services.definition;

import org.fireflyframework.security.api.domain.SecurityPrincipal;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionCostReadAuthorization {
    record Scope(UUID tenantId, UUID rootId) {}

    record Admission(
            SecurityPrincipal principal,
            Scope scope,
            long actorExpiresAtEpochMillis,
            long validUntilEpochMillis) {}

    Mono<Admission> authorize(Scope scope);

    Mono<Void> reauthorize(Admission admission);
}

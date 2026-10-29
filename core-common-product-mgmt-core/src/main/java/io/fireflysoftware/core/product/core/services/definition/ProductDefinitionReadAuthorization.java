package io.fireflysoftware.core.product.core.services.definition;

import org.fireflyframework.security.api.domain.SecurityPrincipal;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionReadAuthorization {
    enum Operation {
        ROOT,
        REVISION,
        PUBLICATION
    }

    record Scope(UUID tenantId, UUID rootId, Operation operation, UUID resourceId) {}

    record Admission(
            SecurityPrincipal principal,
            Scope scope,
            long actorExpiresAtEpochMillis,
            long validUntilEpochMillis) {}

    Mono<Admission> authorize(Scope scope);

    Mono<Void> reauthorize(Admission admission);
}

package io.fireflysoftware.core.product.core.services.definition;

import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCommand;

import org.fireflyframework.security.api.domain.SecurityPrincipal;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionCommandAuthorization {
    String POLICY_PROFILE = "product-definition-human-staff-v1";

    record Scope(
            UUID tenantId,
            UUID rootId,
            UUID operationId,
            ProductDefinitionCommand command,
            UUID targetId) {}

    record Admission(
            SecurityPrincipal principal,
            Scope scope,
            long actorExpiresAtEpochMillis,
            long validUntilEpochMillis) {}

    Mono<Admission> authorize(Scope scope);

    Mono<Void> reauthorize(Admission admission);
}

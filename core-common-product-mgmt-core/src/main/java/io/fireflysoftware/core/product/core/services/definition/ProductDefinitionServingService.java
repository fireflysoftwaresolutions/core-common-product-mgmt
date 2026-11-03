package io.fireflysoftware.core.product.core.services.definition;

import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.*;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionServingService {
    Mono<RootView> readRoot(UUID tenantId, UUID rootId);

    Mono<RevisionView> readRevision(UUID tenantId, UUID rootId, UUID revisionId);

    Mono<PublicationView> readPublication(UUID tenantId, UUID rootId, UUID publicationId);
}

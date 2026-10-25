package io.fireflysoftware.core.product.core.services.definition;

import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCommand;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.Receipt;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionLifecycleService {
    Mono<Receipt> execute(
            UUID tenantId, UUID rootId, ProductDefinitionCommand command, String body);
}

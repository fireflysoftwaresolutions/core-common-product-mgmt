package io.fireflysoftware.core.product.core.services.definition;

import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.Parsed;
import java.util.UUID;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import reactor.core.publisher.Mono;

/** Internal preparation only; neither publication permission nor document-current authority. */
public interface PreparedDepositPublicationService {
    record Resources(UUID actionId, UUID publicationId, UUID productVersionId,
            UUID historyId, UUID auditId, UUID publicEventId) {}

    Mono<PreparedDepositPublication> prepare(
            Parsed instruction, Resources resources, R2dbcOperationBudget originalBudget);
}

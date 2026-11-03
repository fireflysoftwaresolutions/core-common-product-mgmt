package io.fireflysoftware.core.product.core.services.definition;

import io.fireflysoftware.core.product.core.services.definition.DepositAccountDocumentReferenceResolver.*;
import java.time.Instant;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import reactor.core.publisher.Mono;

/** Required current masters template/layout observation; wire only a genuine generated owner SDK. */
public interface DepositAccountTemplateReferenceProvider {
    Mono<Observation> observe(Target target,R2dbcOperationBudget originalBudget,Instant inheritedUntil);
}

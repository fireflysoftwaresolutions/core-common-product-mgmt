package io.fireflysoftware.core.product.core.services.definition;

import io.fireflysoftware.core.product.core.services.definition.DepositAccountDocumentReferenceResolver.*;
import java.time.Instant;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import reactor.core.publisher.Mono;

/** Authenticated current configuration-owned issuer observation. */
public interface ProductDocumentIssuerReferenceProvider {
    Mono<Observation> observe(Target target,R2dbcOperationBudget originalBudget,Instant inheritedUntil);
}

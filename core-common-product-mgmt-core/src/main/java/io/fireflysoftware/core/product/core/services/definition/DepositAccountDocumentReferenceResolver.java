package io.fireflysoftware.core.product.core.services.definition;

import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.DocumentRequirement;
import java.time.Instant;
import java.util.UUID;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import reactor.core.publisher.Mono;

/** Current owner-reference binder. It does not establish product publication or financial admission. */
public interface DepositAccountDocumentReferenceResolver {
    Mono<Observation> verify(Target target,R2dbcOperationBudget originalBudget,Instant inheritedUntil);

    record Scope(UUID tenantId,UUID rootId,UUID productId,UUID revisionId,UUID publicationId,String definitionDigest) {}
    record Target(Scope scope,String purpose,DocumentRequirement document) {}
    /** Transport data; construction alone establishes neither native evidence nor authority. */
    record Observation(Target target,Instant observedAt,Instant nativeUntil,Instant effectiveUntil) {
        @Override public String toString(){return "DepositAccountDocumentReferenceObservation";}
    }
}

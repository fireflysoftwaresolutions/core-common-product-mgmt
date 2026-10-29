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
    enum WindowEvidence { NATIVE_AND_EFFECTIVE, EFFECTIVE_ONLY }
    /** Transport data. EFFECTIVE_ONLY has no independently observed native deadline. */
    record Observation(Target target,Instant observedAt,Instant nativeUntil,Instant effectiveUntil,WindowEvidence windowEvidence) {
        public Observation(Target target,Instant observedAt,Instant nativeUntil,Instant effectiveUntil) {
            this(target,observedAt,nativeUntil,effectiveUntil,WindowEvidence.NATIVE_AND_EFFECTIVE);
        }
        public static Observation effectiveOnly(Target target,Instant observedAt,Instant effectiveUntil) {
            return new Observation(target,observedAt,null,effectiveUntil,WindowEvidence.EFFECTIVE_ONLY);
        }
        @Override public String toString(){return "DepositAccountDocumentReferenceObservation";}
    }
}

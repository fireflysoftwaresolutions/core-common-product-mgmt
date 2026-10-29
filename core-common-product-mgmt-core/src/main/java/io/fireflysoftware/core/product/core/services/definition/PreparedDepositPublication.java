package io.fireflysoftware.core.product.core.services.definition;

import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.ProductDefinition;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleContracts.Selection;
import java.time.Instant;
import java.util.UUID;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;

/** Exact read snapshot. Current owners and a separate native CAS admission are still required. */
public record PreparedDepositPublication(
        io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleContracts.Scope requestScope,
        io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleContracts.Actor publisher,
        UUID productId, UUID reviewId, Selection selection, String selectionFingerprint,
        ProductDefinition definition, String canonicalDefinition, String intentDigest, long versionNumber,
        PreparedDepositPublicationService.Resources resources, R2dbcOperationBudget originalBudget,
        Instant observedAt, Instant effectiveUntil, NativePreparation nativePreparation) {
    public record NativePreparation(UUID contextId, String requestDigest,
            long actorUntilMillis, long permitUntilMillis, long requestUntilMillis) {
        public NativePreparation {
            if(contextId==null || contextId.equals(new UUID(0,0)) || requestDigest==null
                    || !requestDigest.matches("[0-9a-f]{64}") || actorUntilMillis<1
                    || permitUntilMillis<1 || requestUntilMillis<1)
                throw new IllegalArgumentException("Exact original native preparation required");
        }
    }
    @Override public String toString() { return "PreparedDepositPublication"; }
}

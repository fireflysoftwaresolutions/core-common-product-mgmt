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
        Instant observedAt, Instant effectiveUntil) {
    @Override public String toString() { return "PreparedDepositPublication"; }
}

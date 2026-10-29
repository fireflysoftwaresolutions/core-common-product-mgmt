package io.fireflysoftware.core.product.interfaces.dtos.definition;

import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.ProductDefinition;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleContracts.Selection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ProductDefinitionReviewSelectionView(
        Selection selection,
        String selectionFingerprint,
        ProductDefinition definition,
        CostTerms cost,
        String costCompatibility,
        String validationBoundary,
        @Schema(ref = "#/components/schemas/FireflyInstant") Instant observedAt,
        java.util.UUID activeReviewId) {
    public ProductDefinitionReviewSelectionView(
            Selection selection,
            String fingerprint,
            ProductDefinition definition,
            CostTerms cost,
            String compatibility,
            String boundary,
            Instant observedAt) {
        this(selection, fingerprint, definition, cost, compatibility, boundary, observedAt, null);
    }
}

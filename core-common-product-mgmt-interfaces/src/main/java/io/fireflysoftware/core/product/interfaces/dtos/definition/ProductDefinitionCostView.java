package io.fireflysoftware.core.product.interfaces.dtos.definition;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record ProductDefinitionCostView(
        UUID tenantId,
        UUID rootId,
        UUID publicRevisionId,
        String publicDigest,
        Integer publicVersion,
        String phase,
        DefinitionContracts.ProductDefinition publicDefinition,
        UUID costRevisionId,
        String costDigest,
        Integer costVersion,
        CostTerms cost,
        String costCompatibility,
        String validationBoundary,
        @Schema(ref = "#/components/schemas/FireflyInstant") Instant observedAt) {}

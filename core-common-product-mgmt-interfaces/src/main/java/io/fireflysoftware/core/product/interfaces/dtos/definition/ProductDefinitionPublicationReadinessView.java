package io.fireflysoftware.core.product.interfaces.dtos.definition;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.OperationalAdmission;
import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.ReadObservation;

import java.util.UUID;

public record ProductDefinitionPublicationReadinessView(
        UUID tenantId,
        UUID rootId,
        PublicationReadinessStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) PublicationReadySelection selection,
        ReadObservation observation,
        OperationalAdmission operationalAdmission) {
    public enum PublicationReadinessStatus {
        READY,
        NOT_READY
    }

    public record PublicationReadySelection(UUID reviewId, int publicVersion, int costVersion) {
        public PublicationReadySelection {
            if (!validId(reviewId) || publicVersion <= 0 || costVersion < 0)
                throw new IllegalArgumentException("PRODUCT_PUBLICATION_SELECTION_REQUIRED");
        }
    }

    public ProductDefinitionPublicationReadinessView {
        if (!validId(tenantId)
                || !validId(rootId)
                || status == null
                || (status == PublicationReadinessStatus.READY) != (selection != null)
                || observation == null
                || observation.databaseRecordedAt() == null
                || observation.admissionValidUntil() == null
                || !observation.admissionValidUntil().isAfter(observation.databaseRecordedAt())
                || operationalAdmission != OperationalAdmission.NOT_ADMITTED)
            throw new IllegalArgumentException("PRODUCT_PUBLICATION_READINESS_REQUIRED");
    }

    private static boolean validId(UUID value) {
        return value != null && !value.equals(new UUID(0, 0));
    }
}

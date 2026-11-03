package io.fireflysoftware.core.product.interfaces.dtos.definition;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.OperationalAdmission;
import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.ReadObservation;

import java.util.UUID;

public record ProductDefinitionPublicationDiscoveryView(
        UUID tenantId,
        UUID rootId,
        PublicationDiscoveryState state,
        @JsonInclude(JsonInclude.Include.NON_NULL) DiscoveredPublication publication,
        ReadObservation observation,
        OperationalAdmission operationalAdmission) {
    public enum PublicationDiscoveryState {
        NONE,
        ACTIVE,
        RETAINED_WITHDRAWN
    }

    public record DiscoveredPublication(UUID publicationId, int stateVersion) {
        public DiscoveredPublication {
            if (!validId(publicationId) || stateVersion < 0)
                throw new IllegalArgumentException("PRODUCT_PUBLICATION_CONTROL_REQUIRED");
        }
    }

    public ProductDefinitionPublicationDiscoveryView {
        if (!validId(tenantId)
                || !validId(rootId)
                || state == null
                || (state == PublicationDiscoveryState.NONE) != (publication == null)
                || (state == PublicationDiscoveryState.RETAINED_WITHDRAWN
                        && publication.stateVersion() == 0)
                || observation == null
                || observation.databaseRecordedAt() == null
                || observation.admissionValidUntil() == null
                || !observation.admissionValidUntil().isAfter(observation.databaseRecordedAt())
                || operationalAdmission != OperationalAdmission.NOT_ADMITTED)
            throw new IllegalArgumentException("PRODUCT_PUBLICATION_DISCOVERY_REQUIRED");
    }

    private static boolean validId(UUID value) {
        return value != null && !value.equals(new UUID(0, 0));
    }
}

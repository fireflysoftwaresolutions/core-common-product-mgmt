package io.fireflysoftware.core.product.interfaces.dtos.definition;

import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.Common;

import java.time.Instant;
import java.util.UUID;

public final class AccountTariffServingContracts {
    private AccountTariffServingContracts() {}

    public enum OperationalAdmission {
        NOT_ADMITTED
    }

    public record ReadObservation(Instant databaseRecordedAt, Instant admissionValidUntil) {}

    public record PublicDefinition(Common common, AccountTariffTerms terms) {}

    public record RetainedDefinition(
            String encodingVersion, String publicDigest, PublicDefinition definition) {}

    public record SourcePublication(UUID publicationId, String publicDigest) {}

    public record RootState(
            UUID tenantId,
            UUID rootId,
            UUID productId,
            String productCode,
            String businessArea,
            String profileCode,
            Integer profileVersion,
            String phase,
            Integer publicStateVersion,
            UUID publicHead,
            Instant createdAt) {}

    public record RetainedRevision(
            UUID revisionId,
            RetainedDefinition definition,
            SourcePublication sourcePublication,
            Instant createdAt) {}

    public record RetainedPublication(
            UUID publicationId,
            UUID reviewId,
            UUID revisionId,
            UUID productVersionId,
            RetainedDefinition definition,
            Instant createdAt) {}

    public record PublicationControl(String state, Integer stateVersion) {}

    public record RootView(
            RootState root,
            RetainedRevision publicHead,
            ReadObservation observation,
            OperationalAdmission operationalAdmission) {}

    public record RevisionView(
            RootState root,
            RetainedRevision revision,
            ReadObservation observation,
            OperationalAdmission operationalAdmission) {}

    public record PublicationView(
            RootState root,
            RetainedPublication publication,
            PublicationControl control,
            ReadObservation observation,
            OperationalAdmission operationalAdmission) {}
}

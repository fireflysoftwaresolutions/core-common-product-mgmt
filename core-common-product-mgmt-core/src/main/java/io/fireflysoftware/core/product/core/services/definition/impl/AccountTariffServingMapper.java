package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.core.services.definition.DefinitionCanonical;
import io.fireflysoftware.core.product.core.services.definition.DefinitionProfiles;
import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffTerms;
import io.fireflysoftware.core.product.models.entities.definition.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

final class AccountTariffServingMapper {
    RootView root(
            ProductDefinitionRoot root,
            ProductDefinitionRevision revision,
            ReadObservation observation) {
        var state = rootState(root);
        require(Objects.equals(root.publicHead(), revision.revisionId()));
        return new RootView(
                state,
                retainedRevision(root, revision),
                observation(observation),
                OperationalAdmission.NOT_ADMITTED);
    }

    RevisionView revision(
            ProductDefinitionRoot root,
            ProductDefinitionRevision revision,
            ReadObservation observation) {
        return new RevisionView(
                rootState(root),
                retainedRevision(root, revision),
                observation(observation),
                OperationalAdmission.NOT_ADMITTED);
    }

    PublicationView publication(
            ProductDefinitionRoot root,
            ProductDefinitionPublication publication,
            ProductDefinitionRevision revision,
            ProductDefinitionPublicationControl control,
            ReadObservation observation) {
        var state = rootState(root);
        var retained = retainedRevision(root, revision);
        scope(root, publication.tenantId(), publication.rootId());
        scope(root, control.tenantId(), control.rootId());
        require(
                publication.publicationId() != null
                        && publication.reviewId() != null
                        && publication.productVersionId() != null
                        && publication.createdAt() != null);
        require(Objects.equals(publication.revisionId(), revision.revisionId()));
        require(Objects.equals(publication.publicationId(), control.publicationId()));
        require("NOT_ADMITTED".equals(publication.operationalAdmission()));
        require(
                publication.canonicalDefinition() != null
                        && publication
                                .canonicalDefinition()
                                .equals(revision.canonicalDefinition()));
        require(
                Objects.equals(publication.publicDigest(), revision.publicDigest())
                        && Objects.equals(
                                publication.encodingVersion(), revision.encodingVersion()));
        require(
                control.state() != null
                        && !control.state().isBlank()
                        && control.state().length() <= 16
                        && control.stateVersion() != null
                        && control.stateVersion() >= 0);
        return new PublicationView(
                state,
                new RetainedPublication(
                        publication.publicationId(),
                        publication.reviewId(),
                        publication.revisionId(),
                        publication.productVersionId(),
                        retained.definition(),
                        publication.createdAt()),
                new PublicationControl(control.state(), control.stateVersion()),
                observation(observation),
                OperationalAdmission.NOT_ADMITTED);
    }

    private RootState rootState(ProductDefinitionRoot root) {
        require(root != null);
        if (!"ACCOUNT_TARIFF_V1".equals(root.profileCode())
                || !Integer.valueOf(1).equals(root.profileVersion())
                || !"BANKING".equals(root.businessArea())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product definition not found");
        }
        require(
                root.rootId() != null
                        && root.tenantId() != null
                        && root.productId() != null
                        && root.publicHead() != null
                        && root.createdAt() != null);
        require(
                root.productCode() != null
                        && !root.productCode().isBlank()
                        && root.phase() != null
                        && !root.phase().isBlank()
                        && root.phase().length() <= 20);
        require(root.publicStateVersion() != null && root.publicStateVersion() >= 0);
        return new RootState(
                root.tenantId(),
                root.rootId(),
                root.productId(),
                root.productCode(),
                root.businessArea(),
                root.profileCode(),
                root.profileVersion(),
                root.phase(),
                root.publicStateVersion(),
                root.publicHead(),
                root.createdAt());
    }

    private RetainedRevision retainedRevision(
            ProductDefinitionRoot root, ProductDefinitionRevision revision) {
        require(revision != null && revision.revisionId() != null && revision.createdAt() != null);
        scope(root, revision.tenantId(), revision.rootId());
        require(
                (revision.sourcePublicationId() == null)
                        == (revision.sourcePublicationDigest() == null));
        require(
                revision.sourcePublicationDigest() == null
                        || revision.sourcePublicationDigest().matches("[0-9a-f]{64}"));
        var definition =
                definition(
                        root,
                        revision.canonicalDefinition(),
                        revision.publicDigest(),
                        revision.encodingVersion());
        var source =
                revision.sourcePublicationId() == null
                        ? null
                        : new SourcePublication(
                                revision.sourcePublicationId(), revision.sourcePublicationDigest());
        return new RetainedRevision(
                revision.revisionId(), definition, source, revision.createdAt());
    }

    private RetainedDefinition definition(
            ProductDefinitionRoot root, String raw, String digest, String encoding) {
        require(DefinitionCanonical.ENCODING.equals(encoding));
        // Bound the character count before UTF-8 allocation; retained values still require
        // canonical bytes.
        require(
                raw != null
                        && raw.length() <= 131072
                        && raw.getBytes(StandardCharsets.UTF_8).length <= 131072);
        try {
            var canonical = DefinitionProfiles.canonical(raw);
            require(raw.equals(canonical.json()) && canonical.digest().equals(digest));
            var decoded = DefinitionProfiles.decode(raw);
            require(decoded.terms() instanceof AccountTariffTerms);
            require(
                    Objects.equals(root.productCode(), decoded.common().code())
                            && Objects.equals(root.businessArea(), decoded.common().businessArea())
                            && Objects.equals(root.profileCode(), decoded.common().profileCode())
                            && Objects.equals(
                                    root.profileVersion(), decoded.common().profileVersion()));
            var terms = (AccountTariffTerms) decoded.terms();
            require(
                    "CURRENT".equals(terms.accountKind())
                            && "NONE".equals(terms.interest())
                            && "NOT_OFFERED".equals(terms.overdraft()));
            return new RetainedDefinition(
                    encoding, digest, new PublicDefinition(decoded.common(), terms));
        } catch (IllegalArgumentException error) {
            throw unavailable();
        }
    }

    private static void scope(ProductDefinitionRoot root, UUID tenantId, UUID rootId) {
        require(Objects.equals(root.tenantId(), tenantId) && Objects.equals(root.rootId(), rootId));
    }

    private static ReadObservation observation(ReadObservation observation) {
        require(
                observation != null
                        && observation.databaseRecordedAt() != null
                        && observation.admissionValidUntil() != null
                        && observation
                                .databaseRecordedAt()
                                .isBefore(observation.admissionValidUntil()));
        return observation;
    }

    private static void require(boolean valid) {
        if (!valid) throw unavailable();
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Retained product definition unavailable");
    }
}

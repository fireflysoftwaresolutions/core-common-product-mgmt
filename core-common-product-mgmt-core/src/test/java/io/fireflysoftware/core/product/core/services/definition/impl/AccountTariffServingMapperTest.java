package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.core.services.definition.DefinitionCanonical;
import io.fireflysoftware.core.product.core.services.definition.DefinitionProfiles;
import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.*;
import io.fireflysoftware.core.product.models.entities.definition.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

class AccountTariffServingMapperTest {
    static final UUID TENANT = UUID.randomUUID(),
            ROOT = UUID.randomUUID(),
            PRODUCT = UUID.randomUUID();
    static final UUID REVISION = UUID.randomUUID(), PUBLICATION = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
    static final ReadObservation OBSERVATION = new ReadObservation(NOW, NOW.plusSeconds(20));
    final AccountTariffServingMapper mapper = new AccountTariffServingMapper();

    String canonical(String family) {
        try (var input = getClass().getResourceAsStream("/definition/v1/" + family + ".json")) {
            return DefinitionProfiles.canonical(
                            new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .json();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    String canonical() {
        return canonical("account");
    }

    String digest(String json) {
        return DefinitionProfiles.canonical(json).digest();
    }

    ProductDefinitionRoot root() {
        return new ProductDefinitionRoot(
                ROOT,
                TENANT,
                PRODUCT,
                "ACCOUNT_TARIFF_V1_DEMO",
                "BANKING",
                "ACCOUNT_TARIFF_V1",
                1,
                "DRAFT",
                3,
                REVISION,
                NOW);
    }

    ProductDefinitionRevision revision(
            String json, String hash, String encoding, UUID source, String sourceHash) {
        return new ProductDefinitionRevision(
                REVISION, TENANT, ROOT, json, hash, encoding, source, sourceHash, NOW);
    }

    ProductDefinitionRevision revision() {
        return revision(canonical(), digest(canonical()), DefinitionCanonical.ENCODING, null, null);
    }

    ProductDefinitionPublication publication() {
        return new ProductDefinitionPublication(
                PUBLICATION,
                TENANT,
                ROOT,
                UUID.randomUUID(),
                REVISION,
                UUID.randomUUID(),
                canonical(),
                digest(canonical()),
                DefinitionCanonical.ENCODING,
                "NOT_ADMITTED",
                NOW);
    }

    ProductDefinitionPublicationControl control() {
        return new ProductDefinitionPublicationControl(PUBLICATION, TENANT, ROOT, "CURRENT", 4);
    }

    void status(Supplier<?> action, int expected) {
        assertThatThrownBy(action::get)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        error -> {
                            var failure = (ResponseStatusException) error;
                            assertThat(failure.getStatusCode().value()).isEqualTo(expected);
                            assertThat(failure.getReason())
                                    .doesNotContain(
                                            "Synthetic", "SELECT", "actor", "canonical_definition");
                        });
    }

    @Test
    void rootServesExactPublicHeadWithoutOperationalAdmissionOrCurrencyScale() {
        var view = mapper.root(root(), revision(), OBSERVATION);
        assertThat(view.root().publicHead()).isEqualTo(REVISION);
        assertThat(view.publicHead().revisionId()).isEqualTo(REVISION);
        assertThat(view.publicHead().definition().publicDigest()).isEqualTo(digest(canonical()));
        assertThat(
                        view.publicHead()
                                .definition()
                                .definition()
                                .terms()
                                .openingMinimum()
                                .minorUnits())
                .isEqualTo("0");
        assertThat(view.publicHead().definition().definition().terms().interest())
                .isEqualTo("NONE");
        assertThat(view.publicHead().definition().definition().terms().overdraft())
                .isEqualTo("NOT_OFFERED");
        assertThat(view.operationalAdmission()).isEqualTo(OperationalAdmission.NOT_ADMITTED);
        assertThat(view.observation()).isEqualTo(OBSERVATION);
        String json =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .findAndRegisterModules()
                        .valueToTree(view)
                        .toString();
        assertThat(json)
                .doesNotContain(
                        "actorId",
                        "actorIssuer",
                        "private",
                        "costHead",
                        "currencyScale",
                        "activeReview");
    }

    @Test
    void exactHistoricalRevisionRetainsSourcePublicationProvenanceWithoutRequiringHeadEquality() {
        var historical =
                new ProductDefinitionRevision(
                        UUID.randomUUID(),
                        TENANT,
                        ROOT,
                        canonical(),
                        digest(canonical()),
                        DefinitionCanonical.ENCODING,
                        PUBLICATION,
                        "b".repeat(64),
                        NOW);
        var view = mapper.revision(root(), historical, OBSERVATION);
        assertThat(view.revision().revisionId())
                .isEqualTo(historical.revisionId())
                .isNotEqualTo(REVISION);
        assertThat(view.revision().sourcePublication().publicationId()).isEqualTo(PUBLICATION);
        assertThat(view.revision().sourcePublication().publicDigest()).isEqualTo("b".repeat(64));
        assertThat(view.operationalAdmission()).isEqualTo(OperationalAdmission.NOT_ADMITTED);
    }

    @Test
    void publicationCombinesExactRetainedRevisionAndObservedCurrentControl() {
        var view = mapper.publication(root(), publication(), revision(), control(), OBSERVATION);
        assertThat(view.publication().publicationId()).isEqualTo(PUBLICATION);
        assertThat(view.publication().revisionId()).isEqualTo(REVISION);
        assertThat(view.control().state()).isEqualTo("CURRENT");
        assertThat(view.control().stateVersion()).isEqualTo(4);
        assertThat(view.operationalAdmission()).isEqualTo(OperationalAdmission.NOT_ADMITTED);
    }

    @Test
    void malformedNoncanonicalWrongDigestEncodingAndOversizedStoredDefinitionsAreUnavailable() {
        for (var row :
                java.util.List.of(
                        revision(
                                "{invalid",
                                "a".repeat(64),
                                DefinitionCanonical.ENCODING,
                                null,
                                null),
                        revision(
                                " " + canonical(),
                                digest(canonical()),
                                DefinitionCanonical.ENCODING,
                                null,
                                null),
                        revision(
                                canonical(),
                                "a".repeat(64),
                                DefinitionCanonical.ENCODING,
                                null,
                                null),
                        revision(canonical(), digest(canonical()), "unknown", null, null),
                        revision(
                                " ".repeat(131073),
                                "a".repeat(64),
                                DefinitionCanonical.ENCODING,
                                null,
                                null))) {
            status(() -> mapper.revision(root(), row, OBSERVATION), 503);
        }
    }

    @Test
    void unsupportedRootIsNotFoundAndContradictorySupportedRootIsUnavailable() {
        var unsupported =
                new ProductDefinitionRoot(
                        ROOT,
                        TENANT,
                        PRODUCT,
                        "LOAN",
                        "LENDING",
                        "FIXED_RATE_INSTALLMENT_LOAN_V1",
                        1,
                        "DRAFT",
                        0,
                        REVISION,
                        NOW);
        status(() -> mapper.root(unsupported, revision(), OBSERVATION), 404);
        var wrongCode =
                new ProductDefinitionRoot(
                        ROOT,
                        TENANT,
                        PRODUCT,
                        "OTHER",
                        "BANKING",
                        "ACCOUNT_TARIFF_V1",
                        1,
                        "DRAFT",
                        0,
                        REVISION,
                        NOW);
        status(() -> mapper.root(wrongCode, revision(), OBSERVATION), 503);
        var wrongProfile =
                revision(
                        canonical("loan"),
                        digest(canonical("loan")),
                        DefinitionCanonical.ENCODING,
                        null,
                        null);
        status(() -> mapper.root(root(), wrongProfile, OBSERVATION), 503);
    }

    @Test
    void wrongRootTenantHeadAndPartialSourceProvenanceAreUnavailable() {
        var row = revision();
        status(
                () ->
                        mapper.root(
                                root(),
                                new ProductDefinitionRevision(
                                        UUID.randomUUID(),
                                        TENANT,
                                        ROOT,
                                        row.canonicalDefinition(),
                                        row.publicDigest(),
                                        row.encodingVersion(),
                                        null,
                                        null,
                                        NOW),
                                OBSERVATION),
                503);
        status(
                () ->
                        mapper.revision(
                                root(),
                                new ProductDefinitionRevision(
                                        REVISION,
                                        UUID.randomUUID(),
                                        ROOT,
                                        row.canonicalDefinition(),
                                        row.publicDigest(),
                                        row.encodingVersion(),
                                        null,
                                        null,
                                        NOW),
                                OBSERVATION),
                503);
        status(
                () ->
                        mapper.revision(
                                root(),
                                new ProductDefinitionRevision(
                                        REVISION,
                                        TENANT,
                                        UUID.randomUUID(),
                                        row.canonicalDefinition(),
                                        row.publicDigest(),
                                        row.encodingVersion(),
                                        null,
                                        null,
                                        NOW),
                                OBSERVATION),
                503);
        status(
                () ->
                        mapper.revision(
                                root(),
                                revision(
                                        canonical(),
                                        digest(canonical()),
                                        DefinitionCanonical.ENCODING,
                                        PUBLICATION,
                                        null),
                                OBSERVATION),
                503);
    }

    @Test
    void inconsistentPublicationRevisionControlAndAdmissionAreUnavailable() {
        var p = publication();
        var wrongRevision =
                new ProductDefinitionPublication(
                        PUBLICATION,
                        TENANT,
                        ROOT,
                        p.reviewId(),
                        UUID.randomUUID(),
                        p.productVersionId(),
                        p.canonicalDefinition(),
                        p.publicDigest(),
                        p.encodingVersion(),
                        "NOT_ADMITTED",
                        NOW);
        status(
                () -> mapper.publication(root(), wrongRevision, revision(), control(), OBSERVATION),
                503);
        var wrongControl =
                new ProductDefinitionPublicationControl(
                        UUID.randomUUID(), TENANT, ROOT, "CURRENT", 4);
        status(() -> mapper.publication(root(), p, revision(), wrongControl, OBSERVATION), 503);
        var admitted =
                new ProductDefinitionPublication(
                        PUBLICATION,
                        TENANT,
                        ROOT,
                        p.reviewId(),
                        REVISION,
                        p.productVersionId(),
                        p.canonicalDefinition(),
                        p.publicDigest(),
                        p.encodingVersion(),
                        "ADMITTED",
                        NOW);
        status(() -> mapper.publication(root(), admitted, revision(), control(), OBSERVATION), 503);
        var changed = canonical().replace("Synthetic definition", "Different retained definition");
        var inconsistent =
                new ProductDefinitionPublication(
                        PUBLICATION,
                        TENANT,
                        ROOT,
                        p.reviewId(),
                        REVISION,
                        p.productVersionId(),
                        changed,
                        digest(changed),
                        p.encodingVersion(),
                        "NOT_ADMITTED",
                        NOW);
        status(
                () -> mapper.publication(root(), inconsistent, revision(), control(), OBSERVATION),
                503);
    }

    @Test
    void invalidObservationOrNegativeVersionsAreUnavailable() {
        status(() -> mapper.root(root(), revision(), new ReadObservation(NOW, NOW)), 503);
        var badRoot =
                new ProductDefinitionRoot(
                        ROOT,
                        TENANT,
                        PRODUCT,
                        "ACCOUNT_TARIFF_V1_DEMO",
                        "BANKING",
                        "ACCOUNT_TARIFF_V1",
                        1,
                        "DRAFT",
                        -1,
                        REVISION,
                        NOW);
        status(() -> mapper.root(badRoot, revision(), OBSERVATION), 503);
        status(
                () ->
                        mapper.publication(
                                root(),
                                publication(),
                                revision(),
                                new ProductDefinitionPublicationControl(
                                        PUBLICATION, TENANT, ROOT, "CURRENT", -1),
                                OBSERVATION),
                503);
    }
}

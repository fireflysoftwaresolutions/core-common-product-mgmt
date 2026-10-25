package io.fireflysoftware.core.product.core.services.definition;

import static io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCommand.*;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCommand;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

class ProductDefinitionInstructionCodecTest {
    private final UUID tenant = UUID.randomUUID(),
            root = UUID.randomUUID(),
            operation = UUID.randomUUID();

    ObjectNode body(ProductDefinitionCommand command) {
        var node = (ObjectNode) DefinitionCanonical.tree(Map.of("operationId", operation));
        var definition = ProfileFixtures.load("account");
        var publicValue = DefinitionProfiles.canonical(definition.toString());
        var cost =
                DefinitionCanonical.tree(
                        Map.of(
                                "schemaVersion",
                                1,
                                "businessArea",
                                "BANKING",
                                "profileCode",
                                "ACCOUNT_TARIFF_V1",
                                "mode",
                                "NONE",
                                "lines",
                                List.of()));
        switch (command) {
            case CREATE_DRAFT -> {
                node.put("productId", UUID.randomUUID().toString());
                node.put("categoryId", UUID.randomUUID().toString());
                node.set("definition", definition);
                node.set("cost", cost);
            }
            case EDIT_PUBLIC -> {
                node.put("expectedPublicVersion", 3);
                node.put("expectedPublicHead", UUID.randomUUID().toString());
                node.set("definition", definition);
            }
            case EDIT_COST -> {
                node.put("expectedCostVersion", 2);
                node.put("expectedCostHead", UUID.randomUUID().toString());
                node.put("publicRevisionId", UUID.randomUUID().toString());
                node.put("publicDigest", publicValue.digest());
                node.set("publicDefinition", definition);
                node.set("cost", cost);
            }
            case SUBMIT_PUBLIC -> {
                node.put("expectedPublicVersion", 3);
                node.put("expectedPublicHead", UUID.randomUUID().toString());
            }
            case APPROVE_SELECTION, REJECT_SELECTION -> {
                node.put("submissionId", UUID.randomUUID().toString());
                node.put("revisionId", UUID.randomUUID().toString());
                node.put("publicDigest", publicValue.digest());
                node.put("publicVersion", 3);
                node.put("costRevisionId", UUID.randomUUID().toString());
                node.put(
                        "costDigest",
                        CostRules.canonical(cost.toString(), publicValue.json()).digest());
                node.put("costVersion", 2);
                node.put("selectionFingerprint", "a".repeat(64));
                if (command == REJECT_SELECTION) node.put("reason", "Selection needs revision");
            }
            case PUBLISH -> {
                node.put("versionNumber", 1);
                node.put("reviewId", UUID.randomUUID().toString());
                node.put("expectedPublicVersion", 3);
                node.put("expectedCostVersion", 2);
            }
            case WITHDRAW_PUBLICATION -> {
                node.put("publicationId", UUID.randomUUID().toString());
                node.put("expectedStateVersion", 1);
            }
        }
        return node;
    }

    @Test
    void allEightClosedBodiesRetainExactScopeKindAndFullOriginalIntent() {
        for (var command : ProductDefinitionCommand.values()) {
            var requested = body(command);
            var parsed =
                    ProductDefinitionInstructionCodec.parse(
                            tenant, root, command, requested.toString());
            assertThat(parsed).isNotNull();
            assertThat(parsed.instruction().scope().tenantId()).isEqualTo(tenant);
            assertThat(parsed.instruction().scope().operationId()).isEqualTo(operation);
            assertThat(parsed.instruction().command()).isEqualTo(command);
            var retained = DefinitionCanonical.parse(parsed.canonicalIntent());
            assertThat(retained.get("request")).isEqualTo(requested);
            assertThat(parsed.intentDigest()).isEqualTo(DefinitionCanonical.intentDigest(retained));
        }
    }

    @Test
    void duplicateAndTrailingTokensAreRejectedBeforeFingerprinting() {
        var value = body(SUBMIT_PUBLIC).toString();
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionInstructionCodec.parse(
                                        tenant, root, SUBMIT_PUBLIC, value + "{}"));
        String duplicate = value.replace("{", "{\"operationId\":\"" + UUID.randomUUID() + "\",");
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionInstructionCodec.parse(
                                        tenant, root, SUBMIT_PUBLIC, duplicate));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "actorId",
                "principal_kind",
                "human",
                "recordedAt",
                "accepted",
                "costStateVersion"
            })
    void callerAuthorityAuditAndUnknownFieldsAreNotAccepted(String name) {
        var value = body(SUBMIT_PUBLIC);
        value.put(name, "caller-supplied");
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionInstructionCodec.parse(
                                        tenant, root, SUBMIT_PUBLIC, value.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"3\"", "3.0", "true", "null", "-1", "2147483648"})
    void versionCoercionFractionNullNegativeAndOverflowAreRejected(String value) {
        var raw =
                body(SUBMIT_PUBLIC)
                        .toString()
                        .replace(
                                "\"expectedPublicVersion\":3",
                                "\"expectedPublicVersion\":" + value);
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionInstructionCodec.parse(
                                        tenant, root, SUBMIT_PUBLIC, raw));
    }

    @Test
    void missingRequiredVersionDoesNotBecomeZero() {
        var value = body(SUBMIT_PUBLIC);
        value.remove("expectedPublicVersion");
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionInstructionCodec.parse(
                                        tenant, root, SUBMIT_PUBLIC, value.toString()));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "00000000-0000-0000-0000-000000000000",
                "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA",
                "1-1-1-1-1"
            })
    void identifiersMustBeExactNonzeroCanonicalUuids(String id) {
        var value = body(SUBMIT_PUBLIC);
        value.put("operationId", id);
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionInstructionCodec.parse(
                                        tenant, root, SUBMIT_PUBLIC, value.toString()));
    }

    @Test
    void incompleteDraftAndUnknownNestedProfileShapeAreRejected() {
        var value = body(CREATE_DRAFT);
        value.remove("cost");
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionInstructionCodec.parse(
                                        tenant, root, CREATE_DRAFT, value.toString()));
        var unknown = body(CREATE_DRAFT);
        ((ObjectNode) unknown.at("/definition/terms")).put("unknownFeature", true);
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionInstructionCodec.parse(
                                        tenant, root, CREATE_DRAFT, unknown.toString()));
    }

    @Test
    void costSnapshotDigestAndProfileMustMatchBeforePersistence() {
        var value = body(EDIT_COST);
        value.put("publicDigest", "f".repeat(64));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionInstructionCodec.parse(
                                        tenant, root, EDIT_COST, value.toString()));
        var profile = body(EDIT_COST);
        ((ObjectNode) profile.get("cost")).put("businessArea", "LENDING");
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionInstructionCodec.parse(
                                        tenant, root, EDIT_COST, profile.toString()));
    }

    @Test
    void sourcePublicationProvenanceIsPairedAndCanonical() {
        var value = body(EDIT_PUBLIC);
        value.put("sourcePublicationId", UUID.randomUUID().toString());
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionInstructionCodec.parse(
                                        tenant, root, EDIT_PUBLIC, value.toString()));
    }

    @Test
    void rejectDecisionRequiresReasonAndPayloadBudgetIsBounded() {
        var value = body(REJECT_SELECTION);
        value.remove("reason");
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionInstructionCodec.parse(
                                        tenant, root, REJECT_SELECTION, value.toString()));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionInstructionCodec.parse(
                                        tenant,
                                        root,
                                        SUBMIT_PUBLIC,
                                        " ".repeat(131073) + body(SUBMIT_PUBLIC)));
    }

    @Test
    void replayFingerprintChangesWithOriginalOperationOrTerms() {
        var value = body(SUBMIT_PUBLIC);
        var first =
                ProductDefinitionInstructionCodec.parse(
                        tenant, root, SUBMIT_PUBLIC, value.toString());
        assertThat(first).isNotNull();
        value.put("operationId", UUID.randomUUID().toString());
        var other =
                ProductDefinitionInstructionCodec.parse(
                        tenant, root, SUBMIT_PUBLIC, value.toString());
        assertThat(other).isNotNull();
        assertThat(first.intentDigest()).isNotEqualTo(other.intentDigest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"1\"", "1.0", "null", "0", "-1", "9223372036854775808"})
    void publicationVersionIsAnExactPositiveSignedLong(String token) {
        String value =
                body(PUBLISH)
                        .toString()
                        .replace("\"versionNumber\":1", "\"versionNumber\":" + token);
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionInstructionCodec.parse(
                                        tenant, root, PUBLISH, value));
    }

    @Test
    void largestPositiveVersionRemainsExactInOriginalIntent() {
        var body = body(PUBLISH);
        body.put("versionNumber", Long.MAX_VALUE);
        var value = ProductDefinitionInstructionCodec.parse(tenant, root, PUBLISH, body.toString());
        assertThat(value).isNotNull();
        assertThat(value.canonicalIntent()).contains("9223372036854775807");
    }

    @Test
    void initialLegacyShellNameFitsActualVarcharWithoutReducingPublicProfileEditLimit() {
        var create = body(CREATE_DRAFT);
        ((ObjectNode) create.at("/definition/common")).put("name", "x".repeat(256));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionInstructionCodec.parse(
                                        tenant, root, CREATE_DRAFT, create.toString()));
        var edit = body(EDIT_PUBLIC);
        ((ObjectNode) edit.at("/definition/common")).put("name", "x".repeat(256));
        assertThat(
                        ProductDefinitionInstructionCodec.parse(
                                tenant, root, EDIT_PUBLIC, edit.toString()))
                .isNotNull();
    }

    @Test
    void checkerDecisionRequiresExactFingerprintReturnedByProtectedSelectionRead() {
        var value = body(APPROVE_SELECTION);
        value.remove("selectionFingerprint");
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionInstructionCodec.parse(
                                        tenant, root, APPROVE_SELECTION, value.toString()));
    }
}

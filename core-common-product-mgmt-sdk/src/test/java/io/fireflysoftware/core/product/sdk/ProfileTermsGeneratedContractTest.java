package io.fireflysoftware.core.product.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;

import io.fireflysoftware.core.product.core.services.definition.DefinitionProfiles;
import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionInstructionCodec;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCommand;
import io.fireflysoftware.core.product.sdk.invoker.ApiClient;
import io.fireflysoftware.core.product.sdk.model.CreateDraftBody;
import io.fireflysoftware.core.product.sdk.model.EditCostBody;
import io.fireflysoftware.core.product.sdk.model.EditPublicBody;
import io.fireflysoftware.core.product.sdk.model.ProductDefinition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

class ProfileTermsGeneratedContractTest {
    private final com.fasterxml.jackson.databind.ObjectMapper mapper =
            ApiClient.createDefaultMapper(null);
    private final UUID tenant = UUID.randomUUID();
    private final UUID root = UUID.randomUUID();

    private String fixture(String family) throws Exception {
        return Files.readString(
                Path.of(
                        "..",
                        "core-common-product-mgmt-core",
                        "src",
                        "test",
                        "resources",
                        "definition",
                        "v1",
                        family + ".json"));
    }

    @ParameterizedTest
    @CsvSource({
        "account,AccountTariffTerms",
        "loan,FixedRateInstallmentLoanTerms",
        "insurance,TermPcInsuranceTerms"
    })
    void actualDefaultMapperDeserializesAndSerializesEachExistingFamily(
            String family, String expectedType) throws Exception {
        String original = fixture(family);
        var value = mapper.readValue(original, ProductDefinition.class);
        assertEquals(expectedType, value.getTerms().getClass().getSimpleName());
        String wire = mapper.writeValueAsString(value);
        assertEquals(DefinitionProfiles.canonical(original), DefinitionProfiles.canonical(wire));
        var readAgain = mapper.readValue(wire, ProductDefinition.class);
        assertEquals(expectedType, readAgain.getTerms().getClass().getSimpleName());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"account", "loan", "insurance"})
    void typedCreateWirePreservesFamilyAndUnchangedOwnerScope(String family) throws Exception {
        JsonNode input =
                mapper.valueToTree(
                        Map.of(
                                "operationId",
                                UUID.randomUUID(),
                                "productId",
                                UUID.randomUUID(),
                                "categoryId",
                                UUID.randomUUID(),
                                "definition",
                                mapper.readTree(fixture(family)),
                                "cost",
                                noCosts(family)));
        var value = mapper.treeToValue(input, CreateDraftBody.class);
        commandWire(
                family,
                "definition",
                ProductDefinitionCommand.CREATE_DRAFT,
                mapper.writeValueAsString(value));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"account", "loan", "insurance"})
    void typedPublicEditWirePreservesFamilyAndUnchangedOwnerScope(String family) throws Exception {
        JsonNode input =
                mapper.valueToTree(
                        Map.of(
                                "operationId",
                                UUID.randomUUID(),
                                "expectedPublicVersion",
                                0,
                                "expectedPublicHead",
                                UUID.randomUUID(),
                                "definition",
                                mapper.readTree(fixture(family))));
        var value = mapper.treeToValue(input, EditPublicBody.class);
        commandWire(
                family,
                "definition",
                ProductDefinitionCommand.EDIT_PUBLIC,
                mapper.writeValueAsString(value));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"account", "loan", "insurance"})
    void typedCostEditWirePreservesFamilyAndUnchangedOwnerScope(String family) throws Exception {
        String definition = fixture(family);
        JsonNode input =
                mapper.valueToTree(
                        Map.of(
                                "operationId",
                                UUID.randomUUID(),
                                "expectedCostVersion",
                                0,
                                "expectedCostHead",
                                UUID.randomUUID(),
                                "publicRevisionId",
                                UUID.randomUUID(),
                                "publicDigest",
                                DefinitionProfiles.canonical(definition).digest(),
                                "publicDefinition",
                                mapper.readTree(definition),
                                "cost",
                                noCosts(family)));
        var value = mapper.treeToValue(input, EditCostBody.class);
        commandWire(
                family,
                "publicDefinition",
                ProductDefinitionCommand.EDIT_COST,
                mapper.writeValueAsString(value));
    }

    @Test
    void emptyTermsCannotBeGuessedAsOneFamily() throws Exception {
        var value = mapper.readTree(fixture("account")).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) value)
                .set("terms", mapper.createObjectNode());
        assertThrows(
                com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> mapper.treeToValue(value, ProductDefinition.class));
    }

    @Test
    void definedAccountCostLineSurvivesActualDefaultMapperAndClosedCommandCodec() throws Exception {
        var definition = mapper.readTree(fixture("account"));
        var reference =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        definition.at("/common/requirements/documents/0/template");
        var amount =
                Map.of(
                        "minorUnits",
                        "12345",
                        "currencyPolicy",
                        definition.at("/common/market/currencyPolicy"));
        var cost =
                mapper.valueToTree(
                        Map.of(
                                "schemaVersion",
                                1,
                                "businessArea",
                                "BANKING",
                                "profileCode",
                                "ACCOUNT_TARIFF_V1",
                                "mode",
                                "DEFINED",
                                "lines",
                                java.util.List.of(
                                        Map.of(
                                                "code",
                                                "PRIVATE_COST_SENTINEL",
                                                "kind",
                                                "FIXED",
                                                "amount",
                                                amount,
                                                "basis",
                                                "PER_CONTRACT",
                                                "occurrence",
                                                "ON_CREATION",
                                                "payee",
                                                reference.deepCopy().put("kind", "PARTY"),
                                                "allocation",
                                                reference
                                                        .deepCopy()
                                                        .put("kind", "GL_ALLOCATION")))));
        ((com.fasterxml.jackson.databind.node.ObjectNode) cost.at("/lines/0")).putNull("ratio");
        var input =
                mapper.valueToTree(
                        Map.of(
                                "operationId",
                                UUID.randomUUID(),
                                "productId",
                                UUID.randomUUID(),
                                "categoryId",
                                UUID.randomUUID(),
                                "definition",
                                definition,
                                "cost",
                                cost));
        String wire = mapper.writeValueAsString(mapper.treeToValue(input, CreateDraftBody.class));
        assertEquals(
                io.fireflysoftware.core.product.core.services.definition.CostRules.canonical(
                        cost.toString(), definition.toString()),
                io.fireflysoftware.core.product.core.services.definition.CostRules.canonical(
                        mapper.readTree(wire).get("cost").toString(), definition.toString()));
        commandWire("account", "definition", ProductDefinitionCommand.CREATE_DRAFT, wire);
    }

    private void commandWire(
            String family, String field, ProductDefinitionCommand command, String wire)
            throws Exception {
        assertEquals(
                DefinitionProfiles.canonical(fixture(family)),
                DefinitionProfiles.canonical(mapper.readTree(wire).get(field).toString()));
        if (family.equals("account")) {
            assertNotNull(ProductDefinitionInstructionCodec.parse(tenant, root, command, wire));
        } else {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ProductDefinitionInstructionCodec.parse(tenant, root, command, wire));
        }
    }

    private Map<String, Object> noCosts(String family) throws Exception {
        var common = mapper.readTree(fixture(family)).get("common");
        return Map.of(
                "schemaVersion",
                1,
                "businessArea",
                common.get("businessArea").asText(),
                "profileCode",
                common.get("profileCode").asText(),
                "mode",
                "NONE",
                "lines",
                java.util.List.of());
    }
}

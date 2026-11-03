package io.fireflysoftware.core.product.sdk;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.product.core.services.definition.DefinitionProfiles;
import io.fireflysoftware.core.product.core.services.definition.GovernedDefinitionProfile;
import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionInstructionCodec;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCommand;
import io.fireflysoftware.core.product.sdk.invoker.ApiClient;
import io.fireflysoftware.core.product.sdk.model.CreateDraftBody;
import io.fireflysoftware.core.product.sdk.model.ProductDefinition;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DebitCardGeneratedContractTest {
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = ApiClient.createDefaultMapper(null);

    private String fixture() throws Exception {
        return Files.readString(Path.of("..", "core-common-product-mgmt-core", "src", "test", "resources",
                "definition", "v1", "debit-card.json"));
    }

    @Test void actualGeneratedDebitTermsRoundTripRetainsExactProfile() throws Exception {
        var original = fixture();
        var value = mapper.readValue(original, ProductDefinition.class);
        assertEquals("DebitCardTerms", value.getTerms().getClass().getSimpleName());
        var wire = mapper.writeValueAsString(value);
        assertEquals(DefinitionProfiles.canonical(original), DefinitionProfiles.canonical(wire));
        assertEquals("DebitCardTerms", mapper.readValue(wire, ProductDefinition.class).getTerms().getClass().getSimpleName());
    }

    @Test void actualGeneratedDebitDraftPreservesActionAndPrivateCostProfile() throws Exception {
        var action = UUID.randomUUID();
        var original = mapper.readTree(fixture());
        var cost = Map.of("schemaVersion", 1, "businessArea", "BANKING", "profileCode", "DEBIT_CARD_V1",
                "mode", "NONE", "lines", List.of());
        var input = mapper.valueToTree(Map.of("operationId", action, "productId", UUID.randomUUID(),
                "categoryId", UUID.randomUUID(), "definition", original, "cost", cost));
        var wire = mapper.writeValueAsString(mapper.treeToValue(input, CreateDraftBody.class));
        var actual = mapper.readTree(wire);
        assertEquals(action.toString(), actual.get("operationId").asText());
        assertEquals(DefinitionProfiles.canonical(original.toString()), DefinitionProfiles.canonical(actual.get("definition").toString()));
        assertEquals(mapper.valueToTree(cost), actual.get("cost"));
        assertNotNull(ProductDefinitionInstructionCodec.parse(UUID.randomUUID(), UUID.randomUUID(),
                ProductDefinitionCommand.CREATE_DRAFT, wire, GovernedDefinitionProfile.DEBIT_CARD));
    }
}

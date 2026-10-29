/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.core.product.core.services.definition;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CostRulesTest {
    static String costs(String family) {
        var n = ProfileFixtures.load(family);
        var body = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        body.put("schemaVersion", 1);
        body.put("businessArea", n.at("/common/businessArea").asText());
        body.put("profileCode", n.at("/common/profileCode").asText());
        body.put("mode", "DEFINED");
        var line = body.putArray("lines").addObject();
        line.put("code", "PROVIDER");
        line.put("kind", "FIXED");
        line.putObject("amount")
                .put("minorUnits", "12345")
                .set("currencyPolicy", n.at("/common/market/currencyPolicy"));
        line.putNull("ratio");
        line.put("basis", "PER_CONTRACT");
        line.put("occurrence", "ON_CREATION");
        line.set(
                "payee",
                ((com.fasterxml.jackson.databind.node.ObjectNode)
                                n.at("/common/requirements/documents/0/template").deepCopy())
                        .put("kind", "PARTY"));
        line.set(
                "allocation",
                ((com.fasterxml.jackson.databind.node.ObjectNode)
                                n.at("/common/requirements/documents/0/template").deepCopy())
                        .put("kind", "GL_ALLOCATION"));
        return body.toString();
    }

    @ParameterizedTest
    @ValueSource(strings = {"account", "loan", "insurance"})
    void eachFamilyKeepsPrivateCostCanonicalSeparateFromPublicTerms(String family) {
        var publicRaw = ProfileFixtures.load(family).toString();
        var before = DefinitionProfiles.canonical(publicRaw);
        var privateResult = CostRules.canonical(costs(family), publicRaw);
        assertThat(privateResult.json()).contains("12345");
        assertThat(privateResult.digest()).hasSize(64).isNotEqualTo(before.digest());
        assertThat(DefinitionProfiles.canonical(publicRaw)).isEqualTo(before);
        assertThat(before.json()).doesNotContain("12345", "providerCost", "costDigest");
    }

    @Test
    void incompatibleFamilyAndSameCodeDuplicatesAreRejected() {
        var raw = costs("account");
        assertThatThrownBy(() -> CostRules.canonical(raw, ProfileFixtures.load("loan").toString()))
                .hasMessageContaining("COST_DEFINITION_INVALID");
    }

    @Test
    void missingCostBasisCannotLeakRejectedPrivateValue() {
        String raw = costs("account").replace("PER_CONTRACT", "private-cost-token");
        assertThatThrownBy(
                        () -> CostRules.canonical(raw, ProfileFixtures.load("account").toString()))
                .hasMessageContaining("COST_DEFINITION_INVALID")
                .hasMessageNotContaining("private-cost-token");
    }

    @Test
    void explicitlyNoCostsIsDifferentFromIncompleteDefinedCosts() {
        var n = DefinitionCanonical.parse(costs("account"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) n).putArray("lines");
        assertThatThrownBy(
                        () ->
                                CostRules.canonical(
                                        n.toString(), ProfileFixtures.load("account").toString()))
                .hasMessageContaining("COST_DEFINITION_INVALID");
        ((com.fasterxml.jackson.databind.node.ObjectNode) n).put("mode", "NONE");
        assertThat(
                        CostRules.canonical(
                                        n.toString(), ProfileFixtures.load("account").toString())
                                .json())
                .contains("NONE");
    }

    @Test
    void privateRatiosUseTheSameCanonicalRatioEncoding() {
        var node =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        DefinitionCanonical.parse(costs("insurance"));
        var line = (com.fasterxml.jackson.databind.node.ObjectNode) node.at("/lines/0");
        line.put("kind", "RATIO");
        line.putNull("amount");
        line.put("basis", "PREMIUM");
        line.put("ratio", "0.0500");
        var left =
                CostRules.canonical(node.toString(), ProfileFixtures.load("insurance").toString());
        line.put("ratio", "0.05");
        var right =
                CostRules.canonical(node.toString(), ProfileFixtures.load("insurance").toString());
        assertThat(left).isEqualTo(right);
    }
}

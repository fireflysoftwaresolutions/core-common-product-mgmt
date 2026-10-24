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
import org.junit.jupiter.params.provider.CsvSource;

class DefinitionEnvelopeRulesTest {
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "/common/schemaVersion|2",
                "/common/profileVersion|2",
                "/common/profileVersion|\"1\"",
                "/common/code|\"invalid code\"",
                "/common/legalEntityId|\"00000000-0000-0000-0000-000000000000\"",
                "/common/market/currencyPolicy/digest|\"not-a-digest\"",
                "/common/market/channel|\"UNKNOWN\"",
                "/common/requirements/documents|[]",
                "/common/requirements/dependencies|[]",
                "/common/actorId|\"forged-maker\"",
                "/common/operationalAdmission|\"ADMITTED\"",
                "/common/market/currencyPolicy/encodingVersion|\"UNKNOWN\""
            })
    void rejectsUnknownInvalidOrCallerAuthorityFields(String path, String token) {
        assertThatThrownBy(
                        () ->
                                DefinitionProfiles.canonical(
                                        ProfileFixtures.changed("account", path, token)))
                .hasMessageContaining("DEFINITION_");
    }

    @Test
    void publicDefinitionCannotSmuggleCosts() {
        var n = ProfileFixtures.load("account");
        n.putObject("costs").put("amount", "private-cost-token");
        assertThatThrownBy(() -> DefinitionProfiles.canonical(n.toString()))
                .hasMessageContaining("DEFINITION_")
                .hasMessageNotContaining("private-cost-token");
    }

    @Test
    void documentLocaleMustBeWellFormed() {
        var n = ProfileFixtures.load("account");
        ((com.fasterxml.jackson.databind.node.ObjectNode) n.at("/common/requirements/documents/0"))
                .put("locale", "a");
        assertThatThrownBy(() -> DefinitionProfiles.canonical(n.toString()))
                .hasMessageContaining("DEFINITION_");
    }

    @Test
    void ratioFactConstraintsCanonicalizeWithoutChangingArrayOrder() {
        var node = ProfileFixtures.load("account");
        var fact =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        node.at("/common/requirements/facts/0");
        fact.put("kind", "DECIMAL_RATIO");
        fact.put("operator", "BETWEEN");
        fact.putArray("values").add("0.0500").add("0.1000");
        var left = DefinitionProfiles.canonical(node.toString());
        fact.putArray("values").add("0.05").add("0.1");
        assertThat(DefinitionProfiles.canonical(node.toString())).isEqualTo(left);
    }

    @Test
    void typedListsCannotBeChangedAfterDecode() {
        var d = DefinitionProfiles.decode(ProfileFixtures.load("account").toString());
        assertThatThrownBy(() -> d.common().requirements().facts().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

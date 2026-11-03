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

class AccountTariffRulesTest {
    @Test
    void retainsExplicitFreeAndFixedAccountTariffs() {
        var c = DefinitionProfiles.canonical(ProfileFixtures.load("account").toString());
        assertThat(c.json())
                .contains("ACCOUNT_TARIFF_V1", "FREE", "FIXED")
                .doesNotContain("operationalAdmission\":\"ADMITTED");
        assertThat(c.digest()).hasSize(64);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "/terms/interest|\"DAILY_ACCRUAL\"",
                "/terms/overdraft|\"ALLOWED\"",
                "/terms/dailyTransferLimit|{\"minorUnits\":\"1\"}",
                "/terms/tariffs|[]",
                "/terms/ownershipKinds|[\"UNKNOWN\"]",
                "/terms/hiddenTier|true",
                "/common/profileCode|\"TERM_PC_INSURANCE_V1\"",
                "/common/market|{}"
            })
    void rejectsUnsupportedIncompleteOrInconsistentAccountTerms(String path, String token) {
        assertThatThrownBy(
                        () ->
                                DefinitionProfiles.canonical(
                                        ProfileFixtures.changed("account", path, token)))
                .hasMessageContaining("DEFINITION_");
    }

    @Test
    void sameCurrencyIncludesWholePolicyIdentity() {
        assertThatThrownBy(
                        () ->
                                DefinitionProfiles.canonical(
                                        ProfileFixtures.changed(
                                                "account",
                                                "/terms/perTransferLimit/currencyPolicy/version",
                                                "2")))
                .hasMessageContaining("DEFINITION_");
    }

    @Test
    void duplicateTariffCannotMasqueradeAsFullEventCoverage() {
        var n = ProfileFixtures.load("account");
        var a = (com.fasterxml.jackson.databind.node.ArrayNode) n.at("/terms/tariffs");
        a.set(3, a.get(0));
        assertThatThrownBy(() -> DefinitionProfiles.canonical(n.toString()))
                .hasMessageContaining("DEFINITION_");
    }

    @Test
    void requiredNamedEligibilityFactsCannotDisappear() {
        assertThatThrownBy(
                        () ->
                                DefinitionProfiles.canonical(
                                        ProfileFixtures.changed(
                                                "account", "/common/requirements/facts", "[]")))
                .hasMessageContaining("DEFINITION_");
    }
}

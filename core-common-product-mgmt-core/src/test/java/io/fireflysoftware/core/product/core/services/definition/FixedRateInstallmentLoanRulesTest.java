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

class FixedRateInstallmentLoanRulesTest {
    @Test
    void retainsExactFixedLoanTermsAndCanonicalRatio() {
        var c =
                DefinitionProfiles.canonical(
                        ProfileFixtures.changed("loan", "/terms/annualNominalRate", "\"0.0500\""));
        assertThat(c.json())
                .contains("\"annualNominalRate\":\"0.05\"", "FIXED_RATE_INSTALLMENT_LOAN_V1");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "/terms/maximumMonths|601",
                "/terms/minimumMonths|0",
                "/terms/minimumMonths|1.0",
                "/terms/annualNominalRate|\"5\"",
                "/terms/annualNominalRate|0.05",
                "/terms/repaymentConvention|\"REVOLVING\"",
                "/terms/interestConvention|\"ACTUAL_DAILY\"",
                "/terms/graceMonths|1",
                "/terms/repaymentAllocation|null",
                "/terms/calendar|null",
                "/common/businessArea|\"BANKING\"",
                "/common/requirements/facts|[]"
            })
    void rejectsUnsupportedOrIncompleteLoanSemantics(String path, String token) {
        assertThatThrownBy(
                        () ->
                                DefinitionProfiles.canonical(
                                        ProfileFixtures.changed("loan", path, token)))
                .hasMessageContaining("DEFINITION_");
    }

    @Test
    void principalMinimumCannotExceedMaximum() {
        assertThatThrownBy(
                        () ->
                                DefinitionProfiles.canonical(
                                        ProfileFixtures.changed(
                                                "loan",
                                                "/terms/principalMinimum/minorUnits",
                                                "\"10000001\"")))
                .hasMessageContaining("DEFINITION_");
    }

    @Test
    void zeroInterestIsExplicitValidFixedRate() {
        assertThat(
                        DefinitionProfiles.canonical(
                                        ProfileFixtures.changed(
                                                "loan", "/terms/annualNominalRate", "\"0\""))
                                .json())
                .contains("\"annualNominalRate\":\"0\"");
    }
}

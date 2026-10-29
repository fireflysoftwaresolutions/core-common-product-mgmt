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

class TermPcInsuranceRulesTest {
    @Test
    void retainsExactPremiumCoverageAndSharedOccurrenceDeductible() {
        var c = DefinitionProfiles.canonical(ProfileFixtures.load("insurance").toString());
        assertThat(c.json())
                .contains("SHARED_MAX_ONCE_PER_OCCURRENCE", "BUILDING", "CONTENTS", "11000");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "/terms/maximumCoverageDays|367",
                "/terms/totalPremium/minorUnits|\"11001\"",
                "/terms/coverages|[]",
                "/terms/deductibleGroups|[]",
                "/terms/exclusions|[]",
                "/terms/premiumBasis|\"CALLER_RATING_SCRIPT\"",
                "/terms/paymentSchedule|\"IGNORED_INSTALLMENTS\"",
                "/terms/riskKind|\"LIFE\"",
                "/terms/unknownModel|\"script\"",
                "/common/requirements/facts|[]"
            })
    void rejectsIncompleteCoverageUnknownRatingAndUnbalancedPremium(String path, String token) {
        assertThatThrownBy(
                        () ->
                                DefinitionProfiles.canonical(
                                        ProfileFixtures.changed("insurance", path, token)))
                .hasMessageContaining("DEFINITION_");
    }

    @Test
    void aggregateAndDeductibleAreBoundToTheirOwnCoverage() {
        var n = ProfileFixtures.load("insurance");
        ((com.fasterxml.jackson.databind.node.ObjectNode) n.at("/terms/coverages/0/aggregateLimit"))
                .put("minorUnits", "1");
        assertThatThrownBy(() -> DefinitionProfiles.canonical(n.toString()))
                .hasMessageContaining("DEFINITION_");
    }

    @Test
    void unmatchedOrDuplicateCoverageGroupsAreRejected() {
        var n = ProfileFixtures.load("insurance");
        ((com.fasterxml.jackson.databind.node.ObjectNode) n.at("/terms/coverages/0"))
                .put("groupCode", "MISSING");
        assertThatThrownBy(() -> DefinitionProfiles.canonical(n.toString()))
                .hasMessageContaining("DEFINITION_");
    }

    @Test
    void deductibleCannotExceedOccurrenceLimit() {
        var n = ProfileFixtures.load("insurance");
        ((com.fasterxml.jackson.databind.node.ObjectNode) n.at("/terms/coverages/0/deductible"))
                .put("minorUnits", "10000001");
        assertThatThrownBy(() -> DefinitionProfiles.canonical(n.toString()))
                .hasMessageContaining("DEFINITION_");
    }
}

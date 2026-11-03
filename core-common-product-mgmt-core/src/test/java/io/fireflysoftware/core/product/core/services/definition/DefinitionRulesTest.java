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

import com.fasterxml.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DefinitionRulesTest {
    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "999999999999999999999999999999"})
    void acceptsExactBoundedMinorUnitStrings(String value) {
        assertThat(DefinitionRules.minorUnits(value, "/amount").toString()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "-1",
                "+1",
                "01",
                "1.0",
                "1e2",
                " 1",
                "1 ",
                "1000000000000000000000000000000",
                ""
            })
    void rejectsNoncanonicalOrOverflowMoney(String value) {
        assertThatThrownBy(() -> DefinitionRules.minorUnits(value, "/amount"))
                .hasMessageContaining("/amount");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.05", "1", "0.000000000001"})
    void acceptsDecimalRatioWithoutPercentAmbiguity(String value) {
        assertThat(DefinitionRules.ratio(value, "/rate")).isEqualByComparingTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"5", "1.0001", "-0.1", ".5", "0.0000000000001", "1e-2", " 0.1", "+0.1"})
    void rejectsPercentExponentAndOutOfBoundsRatio(String value) {
        assertThatThrownBy(() -> DefinitionRules.ratio(value, "/rate"))
                .hasMessageContaining("/rate");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0", "1e0", "true", "\"1\"", "-1", "2147483648", "null"})
    void exactStateVersionRejectsCoercions(String token) throws Exception {
        assertThatThrownBy(
                        () ->
                                DefinitionRules.stateVersion(
                                        JsonMapper.builder().build().readTree(token)))
                .hasMessageContaining("expectedStateVersion");
    }

    @Test
    void stateVersionKeepsZeroAndMaximum() throws Exception {
        var json = JsonMapper.builder().build();
        assertThat(DefinitionRules.stateVersion(json.readTree("0"))).isZero();
        assertThat(DefinitionRules.stateVersion(json.readTree("2147483647")))
                .isEqualTo(Integer.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "00000000-0000-0000-0000-000000000000",
                "1-1-1-1-1",
                "ABCDEFAB-1234-1234-1234-ABCDEFABCDEF",
                "not-an-id"
            })
    void rejectsNoncanonicalAndUnownedZeroIds(String value) {
        assertThatThrownBy(() -> DefinitionRules.uuid(value, "/id")).hasMessageContaining("/id");
    }

    @Test
    void acceptsCanonicalId() {
        assertThat(DefinitionRules.uuid("12345678-1234-1234-1234-123456789abc", "/id").toString())
                .isEqualTo("12345678-1234-1234-1234-123456789abc");
    }

    @Test
    void textRejectsUnpairedUnicodeAndBoundaryWhitespace() {
        assertThatThrownBy(() -> DefinitionRules.text("bad" + (char) 0xd800, 20, "/name"))
                .hasMessageContaining("/name");
        assertThatThrownBy(() -> DefinitionRules.text(" name", 20, "/name"))
                .hasMessageContaining("/name");
        assertThat(DefinitionRules.text("Home 🏠", 20, "/name")).isEqualTo("Home 🏠");
    }

    @Test
    void effectiveWindowIsExactUtcAndHalfOpen() {
        DefinitionRules.window("2026-11-01T00:00:00.000Z", "2026-12-01T00:00:00.000Z");
        assertThatThrownBy(
                        () ->
                                DefinitionRules.window(
                                        "2026-11-01T00:00:00.000Z", "2026-11-01T00:00:00.000Z"))
                .hasMessageContaining("effectiveTo");
        assertThatThrownBy(() -> DefinitionRules.window("2026-02-30T00:00:00.000Z", null))
                .hasMessageContaining("effectiveFrom");
        assertThatThrownBy(() -> DefinitionRules.window("2026-11-01T01:00:00+01:00", null))
                .hasMessageContaining("effectiveFrom");
    }
}

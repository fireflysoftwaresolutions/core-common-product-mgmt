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

class DefinitionCanonicalTest {
    @Test
    void objectOrderIsStableWhileArrayOrderIsMeaningful() {
        var left = DefinitionCanonical.parse("{\"z\":[\"b\",\"a\"],\"a\":\"0.05\"}");
        var right = DefinitionCanonical.parse("{\"a\":\"0.05\",\"z\":[\"b\",\"a\"]}");
        assertThat(DefinitionCanonical.encode(left))
                .isEqualTo("{\"a\":\"0.05\",\"z\":[\"b\",\"a\"]}");
        assertThat(DefinitionCanonical.digest(left)).isEqualTo(DefinitionCanonical.digest(right));
        assertThat(DefinitionCanonical.digest(left))
                .isNotEqualTo(
                        DefinitionCanonical.digest(
                                DefinitionCanonical.parse("{\"a\":\"0.05\",\"z\":[\"a\",\"b\"]}")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"{\"a\":1,\"a\":2}", "{} {}", "[]", "null", "{\"a\":1.0}", "{\"a\":1e2}"})
    void rejectsDuplicateTrailingNonobjectAndFloatingTokens(String raw) {
        assertThatThrownBy(() -> DefinitionCanonical.parse(raw))
                .hasMessageContaining("DEFINITION_SHAPE");
    }

    @Test
    void closedShapeRejectsUnknownFieldsWithoutEchoingPrivateInput() {
        var tree =
                DefinitionCanonical.parse(
                        "{\"expectedStateVersion\":0,\"secretCost\":\"private-price-token\"}");
        assertThatThrownBy(
                        () ->
                                DefinitionCanonical.closed(
                                        tree, java.util.Set.of("expectedStateVersion"), "/"))
                .hasMessageContaining("DEFINITION_SHAPE")
                .hasMessageNotContaining("private-price-token");
    }

    @Test
    void safeDigestSeparatesNamespaces() {
        var node = DefinitionCanonical.parse("{\"amount\":\"100\"}");
        assertThat(DefinitionCanonical.digest(node))
                .hasSize(64)
                .isNotEqualTo(DefinitionCanonical.intentDigest(node));
    }

    @Test
    void exactPublicCanonicalBytesHaveIndependentSha256Vector() {
        assertThat(
                        DefinitionCanonical.digest(
                                DefinitionCanonical.parse("{\"a\":\"0.05\",\"z\":[\"b\",\"a\"]}")))
                .isEqualTo("89e473beddc618372f7928ed229c3da4c2325870fa0938061efe6e4d55a8782e");
    }
}

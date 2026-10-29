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

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;

final class ProfileFixtures {
    static ObjectNode load(String family) {
        try (var input =
                ProfileFixtures.class.getResourceAsStream("/definition/v1/" + family + ".json")) {
            return (ObjectNode)
                    DefinitionCanonical.parse(
                            new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String changed(String family, String pointer, String token) {
        var root = load(family);
        int split = pointer.lastIndexOf('/');
        var parent = root.at(pointer.substring(0, split));
        var value = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            ((ObjectNode) parent).set(pointer.substring(split + 1), value.readTree(token));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return root.toString();
    }
}

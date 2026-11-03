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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class DefinitionCanonical {
    public static final String ENCODING = "product-definition-canonical-v1";
    private static final ObjectMapper JSON =
            JsonMapper.builder(
                            JsonFactory.builder()
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(32)
                                                    .maxStringLength(16384)
                                                    .maxNumberLength(32)
                                                    .build())
                                    .build())
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(
                            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                            DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                            DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS,
                            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                    .build();

    static {
        var textual =
                JSON.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual);
        for (var input :
                List.of(
                        com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer,
                        com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float,
                        com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean))
            textual.setCoercion(input, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
    }

    private DefinitionCanonical() {}

    public static JsonNode parse(String raw) {
        try {
            if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > 131072) throw shape();
            var value = JSON.readTree(raw);
            if (value == null || !value.isObject()) throw shape();
            validate(value);
            return value;
        } catch (Exception e) {
            throw shape();
        }
    }

    private static void validate(JsonNode node) {
        if (node.isFloatingPointNumber()) throw shape();
        if (node.isTextual()) DefinitionRules.unicode(node.textValue(), "/");
        if (node.isObject())
            node.fieldNames().forEachRemaining(key -> DefinitionRules.unicode(key, "/"));
        if (node.isContainerNode()) node.forEach(DefinitionCanonical::validate);
    }

    public static String encode(JsonNode value) {
        try {
            validate(value);
            return JSON.writeValueAsString(ordered(value));
        } catch (Exception e) {
            throw shape();
        }
    }

    private static JsonNode ordered(JsonNode value) {
        if (value.isObject()) {
            var result = JSON.createObjectNode();
            var keys = new TreeSet<String>();
            value.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) result.set(key, ordered(value.get(key)));
            return result;
        }
        if (value.isArray()) {
            var result = JSON.createArrayNode();
            value.forEach(item -> result.add(ordered(item)));
            return result;
        }
        return value;
    }

    public static String digest(JsonNode value) {
        return hash(ENCODING, encode(value));
    }

    public static String intentDigest(JsonNode value) {
        return hash("product-definition-action-v1", encode(value));
    }

    public static String hash(String namespace, String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            (namespace + "\n" + value)
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static void closed(JsonNode value, Set<String> allowed, String path) {
        if (value == null || !value.isObject()) throw shape();
        value.fieldNames()
                .forEachRemaining(
                        key -> {
                            if (!allowed.contains(key)) throw shape();
                        });
    }

    public static <T> T read(JsonNode value, Class<T> type) {
        try {
            return JSON.treeToValue(value, type);
        } catch (Exception e) {
            throw shape();
        }
    }

    public static JsonNode tree(Object value) {
        return JSON.valueToTree(value);
    }

    private static IllegalArgumentException shape() {
        return new IllegalArgumentException("DEFINITION_SHAPE");
    }
}

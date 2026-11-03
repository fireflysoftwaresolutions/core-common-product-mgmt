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

package io.fireflysoftware.core.product.core.services.cardeligibility;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fireflysoftware.core.product.interfaces.dtos.cardeligibility.CardEnrollmentCapabilityContracts.Request;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CardEnrollmentCapabilityCodec {
    private static final int MAX_BYTES = 8192;
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    static {
        JSON.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxStringLength(MAX_BYTES).maxNestingDepth(4).maxNumberLength(20).build());
    }

    private CardEnrollmentCapabilityCodec() {}

    public static Request decode(byte[] raw) {
        JsonNode value = object(raw, Set.of("actionId", "expectedProductRevision", "reasonCode", "acceptNotAfterEpochMillis"));
        var request = new Request(identifier(value, "actionId"), number(value, "expectedProductRevision"),
                text(value, "reasonCode"), number(value, "acceptNotAfterEpochMillis"));
        CardEnrollmentCapabilityRules.validate(request);
        return request;
    }

    static JsonNode object(byte[] raw, Set<String> fields) {
        try {
            if (raw == null || raw.length == 0 || raw.length > MAX_BYTES) throw invalid();
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
            try (var parser = JSON.createParser(text)) {
                JsonNode value = JSON.readTree(parser);
                if (value == null || !value.isObject() || parser.nextToken() != null) throw invalid();
                var actual = new HashSet<String>();
                value.fieldNames().forEachRemaining(actual::add);
                if (!actual.equals(fields)) throw invalid();
                return value;
            }
        } catch (Exception error) {
            throw invalid();
        }
    }

    static String text(JsonNode value, String name) {
        JsonNode field = value.get(name);
        if (field == null || !field.isTextual()) throw invalid();
        return field.textValue();
    }

    static long number(JsonNode value, String name) {
        JsonNode field = value.get(name);
        if (field == null || !field.isIntegralNumber() || !field.canConvertToLong()) throw invalid();
        return field.longValue();
    }

    static UUID identifier(JsonNode value, String name) {
        try {
            String raw = text(value, name);
            UUID identifier = UUID.fromString(raw);
            if (!identifier.toString().equals(raw) || !CardEnrollmentCapabilityRules.uuid(identifier)) throw invalid();
            return identifier;
        } catch (IllegalArgumentException error) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("EXACT_CARD_ENROLLMENT_CAPABILITY_REQUEST_REQUIRED");
    }
}

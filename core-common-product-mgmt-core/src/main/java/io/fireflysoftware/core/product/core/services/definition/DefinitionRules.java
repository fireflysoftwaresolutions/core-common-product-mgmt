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

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.time.format.*;
import java.util.UUID;

public final class DefinitionRules {
    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withResolverStyle(ResolverStyle.STRICT);

    private DefinitionRules() {}

    public static BigInteger minorUnits(String value, String path) {
        if (value == null || !value.matches("0|[1-9][0-9]{0,29}")) throw invalid(path);
        return new BigInteger(value);
    }

    public static BigDecimal ratio(String value, String path) {
        if (value == null || !value.matches("(?:0|1)(?:\\.[0-9]{1,12})?")) throw invalid(path);
        var result = new BigDecimal(value);
        if (result.compareTo(BigDecimal.ONE) > 0) throw invalid(path);
        return result;
    }

    public static int stateVersion(JsonNode node) {
        if (node == null
                || !node.isIntegralNumber()
                || !node.canConvertToInt()
                || node.intValue() < 0) throw invalid("/expectedStateVersion");
        return node.intValue();
    }

    public static UUID uuid(String value, String path) {
        if (value == null
                || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || value.equals("00000000-0000-0000-0000-000000000000")) throw invalid(path);
        return UUID.fromString(value);
    }

    public static String text(String value, int max, String path) {
        if (value == null || value.isEmpty() || value.codePointCount(0, value.length()) > max)
            throw invalid(path);
        unicode(value, path);
        if (value.codePoints().anyMatch(Character::isISOControl)
                || space(value.codePointAt(0))
                || space(value.codePointBefore(value.length()))) throw invalid(path);
        return value;
    }

    static void unicode(String value, String path) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i)))
                    throw invalid(path);
            } else if (Character.isLowSurrogate(c)) throw invalid(path);
        }
    }

    private static boolean space(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }

    public static void window(String from, String to) {
        var start = instant(from, "/effectiveFrom");
        if (to != null && !instant(to, "/effectiveTo").isAfter(start))
            throw invalid("/effectiveTo");
    }

    private static Instant instant(String value, String path) {
        try {
            if (value == null
                    || !value.matches(
                            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z"))
                throw invalid(path);
            var parsed = LocalDateTime.parse(value, UTC_MILLIS);
            if (parsed.getYear() < 1) throw invalid(path);
            return parsed.toInstant(ZoneOffset.UTC);
        } catch (DateTimeException e) {
            throw invalid(path);
        }
    }

    public static IllegalArgumentException invalid(String path) {
        return new IllegalArgumentException("DEFINITION_RULE at " + path);
    }
}

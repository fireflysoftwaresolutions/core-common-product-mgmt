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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CardEnrollmentCapabilityCodecTest {
    private static final String VALID = "{\"actionId\":\"00000000-0000-0000-0000-000000000009\",\"expectedProductRevision\":7,\"reasonCode\":\"CARD_ENROLLMENT\",\"acceptNotAfterEpochMillis\":1500}";
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static void reject(String value) { assertThrows(IllegalArgumentException.class,() -> CardEnrollmentCapabilityCodec.decode(bytes(value)),value); }

    @Test void exactIntentDecodesWithoutServerScopeOrEvidence() {
        var r = CardEnrollmentCapabilityCodec.decode(bytes(VALID));
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000009"),r.actionId());
        assertEquals(7L,r.expectedProductRevision()); assertEquals("CARD_ENROLLMENT",r.reasonCode()); assertEquals(1500L,r.acceptNotAfterEpochMillis());
    }
    @Test void boundaryRevisionReasonAndDeadlineDecodeExactly() {
        var r = CardEnrollmentCapabilityCodec.decode(bytes(VALID.replace(":7,",":0,").replace("CARD_ENROLLMENT","A".repeat(64)).replace(":1500}",":9223372036854775807}")));
        assertEquals(0L,r.expectedProductRevision()); assertEquals("A".repeat(64),r.reasonCode()); assertEquals(Long.MAX_VALUE,r.acceptNotAfterEpochMillis());
    }
    @Test void unknownCallerScopePolicyStatusAndEvidenceAreRejected() {
        for (String field : new String[]{"tenantId","institutionId","bankId","productId","definitionId","publicationId","actorId","purpose","policy","status","evidence"}) reject(VALID.replace("}",",\""+field+"\":\"ACTIVE\"}"));
    }
    @Test void duplicateEscapedDuplicateMissingAndNullFieldsAreRejected() {
        reject(VALID.replace("}",",\"reasonCode\":\"OTHER\"}"));
        reject(VALID.replace("}",",\"reason\\u0043ode\":\"OTHER\"}"));
        reject(VALID.replace("\"expectedProductRevision\":7,","")); reject(VALID.replace(":7,",":null,"));
    }
    @Test void coercionFractionAndOverflowAreRejected() {
        for (String number : new String[]{"\"7\"","7.0","7e0","true","9223372036854775808","-1"}) reject(VALID.replace(":7,",":"+number+","));
        reject(VALID.replace(":1500}",":0}"));
    }
    @Test void noncanonicalZeroAndWrongTypeIdentifiersAreRejected() {
        reject(VALID.replace("00000000-0000-0000-0000-000000000009","0-0-0-0-9"));
        reject(VALID.replace("00000000-0000-0000-0000-000000000009","00000000-0000-0000-0000-000000000000"));
        reject(VALID.replace("\"00000000-0000-0000-0000-000000000009\"","9"));
        reject(VALID.replace("00000000-0000-0000-0000-000000000009","00000000-0000-0000-0000-00000000000A"));
    }
    @Test void reasonMustBeBoundedUppercaseWithoutLineTerminators() {
        for (String reason : new String[]{"","lower","A-B","A".repeat(65),"A\\n","A\\r","A\u2028","A\u2029"}) reject(VALID.replace("CARD_ENROLLMENT",reason));
    }
    @Test void nonobjectTrailingDocumentsAndDeepUnknownTreesAreRejected() {
        for (String json : new String[]{"null","[]","1",VALID+"{}",VALID+" true",VALID.replace("}",",\"tree\":[[[[[1]]]]]}"),"{"}) reject(json);
    }
    @Test void malformedUtf8AndOversizedInputAreRejected() {
        assertThrows(IllegalArgumentException.class,() -> CardEnrollmentCapabilityCodec.decode(null));
        assertThrows(IllegalArgumentException.class,() -> CardEnrollmentCapabilityCodec.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class,() -> CardEnrollmentCapabilityCodec.decode(new byte[]{(byte)0xc3,0x28}));
        assertThrows(IllegalArgumentException.class,() -> CardEnrollmentCapabilityCodec.decode(bytes(" ".repeat(8193)+VALID)));
    }
}

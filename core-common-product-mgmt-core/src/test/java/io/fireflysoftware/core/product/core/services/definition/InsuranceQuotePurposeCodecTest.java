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

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InsuranceQuotePurposeCodecTest {
    private static Class<?> codec() {
        try { return Class.forName("io.fireflysoftware.core.product.core.services.definition.InsuranceQuotePurposeCodec"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Typed insurance quote-purpose codec missing",missing); }
    }
    private static Object call(String name,Class<?> type,Object value) {
        try { return codec().getMethod(name,type).invoke(null,value); }
        catch (InvocationTargetException failure) { if(failure.getCause() instanceof RuntimeException e)throw e;throw new AssertionError(failure.getCause()); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Object decode(String raw){return call("decode",String.class,raw);}
    private static String encode(Object value){return (String)call("encode",value.getClass(),value);}
    private static String digest(Object value){return (String)call("digest",value.getClass(),value);}
    private static ObjectNode body() {
        var root=(ObjectNode)DefinitionCanonical.parse("{}");
        root.put("encodingVersion","product-insurance-quote-configuration-1");
        var scope=root.putObject("scope");scope.put("tenantId","11111111-2222-3333-4444-555555555555");scope.put("rootId","22222222-2222-3333-4444-555555555555");scope.put("productId","33333333-2222-3333-4444-555555555555");scope.put("revisionId","44444444-2222-3333-4444-555555555555");scope.put("publicationId","55555555-2222-3333-4444-555555555555");
        var definition=ProfileFixtures.load("insurance");root.set("definition",definition);root.put("publicDigest",DefinitionProfiles.canonical(definition.toString()).digest());return root;
    }
    private static ObjectNode terms(ObjectNode body){return (ObjectNode)body.get("definition").get("terms");}
    @Test void fixedPremiumPurposeRoundTripsCanonicalBytesAndIndependentDigest() {
        Object value=decode(body().toString());String canonical=encode(value);
        assertEquals(canonical,encode(decode(canonical)));
        byte[] bytes=(byte[])call("bytes",value.getClass(),value);
        assertArrayEquals(canonical.getBytes(StandardCharsets.UTF_8),bytes);
        assertEquals(canonical,encode(call("decodeBytes",byte[].class,bytes)));
        assertEquals("a8cb6bd76dc4f98cb0a4175044d165bd23bc34ef0c4587b2d4f50dfc50c2b053",digest(value));
    }
    @Test void preservesFullRequiredSourceContractsWithoutApplicantApproval() {
        String canonical=encode(decode(body().toString()));
        for(String code:java.util.List.of("INSURED_OBJECT","UNDERWRITING_APPROVAL","REFERRAL_AUTHORITY","COVERAGE_WORDING","EXCLUSION_WORDING","PREMIUM_TAX","contractDigest","sourceOwner","maxAgeSeconds"))assertTrue(canonical.contains(code));
        assertFalse(canonical.contains("applicantEligible"));assertFalse(canonical.contains("applicantApproved"));assertFalse(canonical.contains("privateCost"));
    }
    @Test void fixedPremiumAndTaxArePreservedRatherThanRepricedAsPercentage() {
        var value=DefinitionCanonical.parse(encode(decode(body().toString())));var t=value.get("definition").get("terms");
        assertEquals("FIXED_PER_POLICY",t.get("premiumBasis").textValue());assertEquals("10000",t.get("basePremium").get("minorUnits").textValue());assertEquals("11000",t.get("totalPremium").get("minorUnits").textValue());
    }
    @ParameterizedTest @ValueSource(strings={"tenantId","rootId","productId","revisionId","publicationId"})
    void everyScopeIdentityChangesDigest(String field) {
        var a=body();var b=body();((ObjectNode)b.get("scope")).put(field,"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");assertNotEquals(digest(decode(a.toString())),digest(decode(b.toString())));
    }
    @ParameterizedTest @ValueSource(strings={"00000000-0000-0000-0000-000000000000","1-1-1-1-1","AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE","not-an-id"})
    void rejectsNoncanonicalScope(String id){var b=body();((ObjectNode)b.get("scope")).put("tenantId",id);assertThrows(IllegalArgumentException.class,()->decode(b.toString()));}
    @Test void rejectsBankingDefinitionEvenWhenItsDigestIsCorrect(){var b=body();var def=ProfileFixtures.load("account");b.set("definition",def);b.put("publicDigest",DefinitionProfiles.canonical(def.toString()).digest());assertThrows(IllegalArgumentException.class,()->decode(b.toString()));}
    @Test void rejectsGenericActiveStatusInsteadOfPurpose(){assertThrows(IllegalArgumentException.class,()->decode("{\"productStatus\":\"ACTIVE\"}"));}
    @Test void rejectsChangedPublicDigest(){var b=body();b.put("publicDigest","0".repeat(64));assertThrows(IllegalArgumentException.class,()->decode(b.toString()));}
    @Test void rejectsPercentagePricing(){var b=body();terms(b).put("premiumBasis","PERCENT_OF_RISK");assertThrows(IllegalArgumentException.class,()->decode(b.toString()));}
    @Test void rejectsMissingMandatorySourceFact(){var b=body();((ObjectNode)b.get("definition").get("common").get("requirements")).putArray("facts");assertThrows(IllegalArgumentException.class,()->decode(b.toString()));}
    @Test void rejectsNonEvidenceMandatoryFact(){var b=body();((ObjectNode)b.get("definition").get("common").get("requirements").get("facts").get(1)).put("evidenceRequired",false);assertThrows(IllegalArgumentException.class,()->decode(b.toString()));}
    @ParameterizedTest @ValueSource(strings={"{} {}","{\"encodingVersion\":\"x\",\"encodingVersion\":\"x\"}","[]"})
    void rejectsTrailingDuplicateAndWrongShape(String raw){assertThrows(IllegalArgumentException.class,()->decode(raw));}
    @Test void rejectsUnknownPurposeFields(){var b=body();b.put("approved",true);assertThrows(IllegalArgumentException.class,()->decode(b.toString()));}
    @Test void rejectsUnknownScopeFields(){var b=body();((ObjectNode)b.get("scope")).put("actor","caller");assertThrows(IllegalArgumentException.class,()->decode(b.toString()));}
    @Test void rejectsByteTrailingData(){var v=decode(body().toString());byte[] good=(byte[])call("bytes",v.getClass(),v);byte[] bad=Arrays.copyOf(good,good.length+1);assertThrows(IllegalArgumentException.class,()->call("decodeBytes",byte[].class,bad));}
    @Test void rejectsNoncanonicalBytes(){var v=decode(body().toString());byte[] bad=(encode(v)+" ").getBytes(StandardCharsets.UTF_8);assertThrows(IllegalArgumentException.class,()->call("decodeBytes",byte[].class,bad));}
    @Test void rejectsMalformedUtf8(){assertThrows(IllegalArgumentException.class,()->call("decodeBytes",byte[].class,new byte[]{(byte)0xc3,0x28}));}
    @Test void rejectsOversizedInput(){assertThrows(IllegalArgumentException.class,()->decode(" ".repeat(131073)));}

    @Test void typedObservationCannotChangeTheImmutableConfigurationDigest() {
        var config=(io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.Configuration)decode(body().toString());
        var now=java.time.Instant.parse("2026-10-26T12:00:00Z");
        var a=new io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.Observation(config,now,now.plusSeconds(1));
        var b=new io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.Observation(config,now.plusMillis(1),now.plusSeconds(2));
        assertEquals(digest(a.configuration()),digest(b.configuration()));
        assertEquals("InsuranceQuotePurposeObservation",a.toString());
        assertThrows(UnsupportedOperationException.class,()->config.definition().common().requirements().facts().clear());
    }
    @ParameterizedTest @ValueSource(longs={-1,0,30001})
    void typedObservationRejectsEmptyOrUnboundedWindow(long millis) {
        var config=(io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.Configuration)decode(body().toString());
        var now=java.time.Instant.parse("2026-10-26T12:00:00Z");
        assertThrows(IllegalArgumentException.class,()->new io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.Observation(config,now,now.plusMillis(millis)));
    }
    @Test void typedObservationRejectsSubMillisecondAuthorityShape() {
        var config=(io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.Configuration)decode(body().toString());
        var now=java.time.Instant.parse("2026-10-26T12:00:00Z");
        assertThrows(IllegalArgumentException.class,()->new io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.Observation(config,now.plusNanos(1),now.plusSeconds(1)));
    }

    @Test void observationPreservesActualPostgresMicrosecondRecordedAt() {
        var config=(io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.Configuration)decode(body().toString());
        var recorded=java.time.Instant.parse("2026-10-26T12:00:00.123456Z");
        var value=new io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.Observation(config,recorded,java.time.Instant.parse("2026-10-26T12:00:01.123Z"));
        assertEquals(recorded,value.observedAt());
    }

    @Test void observationPreservesAnEarlierExactMicrosecondReferenceHorizon() {
        var config=(io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.Configuration)decode(body().toString());
        var recorded=java.time.Instant.parse("2026-10-26T12:00:00.123456Z");var until=recorded.plusSeconds(1);
        var value=new io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.Observation(config,recorded,until);
        assertEquals(until,value.effectiveUntil());
    }

    @Test void purposeSourceTypeExposesOnlyTermPcDefinitionRatherThanTheGenericUnion() {
        var configuration=decode(body().toString());var component=java.util.Arrays.stream(configuration.getClass().getRecordComponents()).filter(c->c.getName().equals("definition")).findFirst().orElseThrow();
        assertEquals("TermPcDefinition",component.getType().getSimpleName());
    }
}

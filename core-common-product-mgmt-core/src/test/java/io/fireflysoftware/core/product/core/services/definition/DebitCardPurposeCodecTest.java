package io.fireflysoftware.core.product.core.services.definition;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DebitCardPurposeCodecTest {
    private static Object call(String name,Class<?> type,Object value) {
        try {return Class.forName("io.fireflysoftware.core.product.core.services.definition.DebitCardPurposeCodec").getMethod(name,type).invoke(null,value);}
        catch (InvocationTargetException failure) {if(failure.getCause() instanceof RuntimeException error)throw error;throw new AssertionError(failure.getCause());}
        catch (ReflectiveOperationException missing) {throw new AssertionError("Current debit configuration codec required",missing);}
    }
    private static Object decode(String raw){return call("decode",String.class,raw);}
    private static String encode(Object value){return (String)call("encode",value.getClass(),value);}
    private static ObjectNode body(){
        var root=(ObjectNode)DefinitionCanonical.parse("{}");root.put("encodingVersion","product-debit-card-configuration-1");
        var scope=root.putObject("scope");int index=1;
        for(String key:java.util.List.of("tenantId","rootId","productId","revisionId","publicationId"))scope.put(key,new java.util.UUID(0,index++).toString());
        var definition=ProfileFixtures.load("debit-card");root.set("definition",definition);root.put("publicDigest",DefinitionProfiles.canonical(definition.toString()).digest());return root;
    }
    @Test void exactDebitConfigurationRoundTripsAndUsesIndependentDomainSeparatedDigest() throws Exception {
        var value=decode(body().toString());String canonical=encode(value);assertEquals(canonical,encode(decode(canonical)));
        byte[] bytes=(byte[])call("bytes",value.getClass(),value);assertArrayEquals(canonical.getBytes(StandardCharsets.UTF_8),bytes);
        assertEquals(canonical,encode(call("decodeBytes",byte[].class,bytes)));
        var expected=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("product-debit-card-configuration-1\n"+canonical).getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected,call("digest",value.getClass(),value));
    }
    @Test void configurationKeepsLimitsAndRequiredFactsWithoutClaimingEligibilityOrIssuance(){
        var canonical=encode(decode(body().toString()));var terms=DefinitionCanonical.parse(canonical).path("definition").path("terms");
        assertEquals("DEBIT",terms.path("cardKind").asText());assertEquals("10000",terms.path("perPurchaseLimit").path("minorUnits").asText());
        assertEquals("100000",terms.path("dailyPurchaseLimit").path("minorUnits").asText());assertEquals("CARD_ENROLLMENT",terms.path("enrollmentPurpose").asText());
        for(String fact:java.util.List.of("KYC","OWNERSHIP_MANDATE","contractDigest","sourceOwner","maxAgeSeconds"))assertTrue(canonical.contains(fact));
        for(String absent:java.util.List.of("applicantEligible","issuerReceipt","privateCost","verificationAccepted"))assertFalse(canonical.contains(absent));
    }
    @ParameterizedTest @ValueSource(strings={"account","insurance"})
    void anotherProfileCannotBecomeDebitConfigurationDespiteCorrectDigest(String family){var b=body();var definition=ProfileFixtures.load(family);b.set("definition",definition);b.put("publicDigest",DefinitionProfiles.canonical(definition.toString()).digest());assertThrows(IllegalArgumentException.class,()->decode(b.toString()));}
    @ParameterizedTest @ValueSource(strings={"tenantId","rootId","productId","revisionId","publicationId"})
    void everyScopeIdentityIsBoundToConfigurationDigest(String field){var a=decode(body().toString());var b=body();((ObjectNode)b.get("scope")).put(field,"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");var other=decode(b.toString());assertNotEquals(call("digest",a.getClass(),a),call("digest",other.getClass(),other));}
    @Test void rejectsAbsentMandatoryOwnerFacts(){var b=body();((ObjectNode)b.path("definition").path("common").path("requirements")).putArray("facts");assertThrows(IllegalArgumentException.class,()->decode(b.toString()));}
    @Test void rejectsChangedPublicDefinitionDigest(){var b=body();b.put("publicDigest","0".repeat(64));assertThrows(IllegalArgumentException.class,()->decode(b.toString()));}
    @Test void rejectsCallerAuthorityFields(){var b=body();b.put("eligible",true);assertThrows(IllegalArgumentException.class,()->decode(b.toString()));}
    @Test void rejectsNoncanonicalOrMalformedBytes(){var value=decode(body().toString());assertThrows(IllegalArgumentException.class,()->call("decodeBytes",byte[].class,(encode(value)+" ").getBytes(StandardCharsets.UTF_8)));assertThrows(IllegalArgumentException.class,()->call("decodeBytes",byte[].class,new byte[]{(byte)0xc3,0x28}));}
}

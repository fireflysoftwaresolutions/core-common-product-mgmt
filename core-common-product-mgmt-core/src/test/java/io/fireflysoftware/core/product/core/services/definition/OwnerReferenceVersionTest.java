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
import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Actual original owner strings are version data, never caller/approval authority. */
class OwnerReferenceVersionTest {
    private ObjectNode document(ObjectNode definition){return (ObjectNode)definition.get("common").get("requirements").get("documents").get(0).get("template");}
    private ObjectNode opaque(String family,String value){var n=ProfileFixtures.load(family);var ref=document(n);ref.remove("version");ref.put("versionFormat","OPAQUE");ref.put("versionText",value);return n;}
    private Object typed(ExactReference ref){try{return ref.getClass().getMethod("ownerVersion").invoke(ref);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError("Lossless typed owner version missing",e);}}
    private Object value(Object v){try{return v.getClass().getMethod("value").invoke(v);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    @ParameterizedTest @ValueSource(strings={"account:aa7b3673ee309ed7676cf6cabdb392c4976217186c0c4bf03548c1c705ec58d1","insurance:e7a042dafaa961ff6d883f34aeef0f188692627a375727249ba78d64234bdbc7","loan:516bd8c2a49ee3a86dccc9d8362eb60f9cf721de19e2338a3cb349bf7e130afc"})
    void allLegacyNumericCanonicalVectorsRemainExact(String golden){String[] parts=golden.split(":",2);assertEquals(parts[1],DefinitionProfiles.canonical(ProfileFixtures.load(parts[0]).toString()).digest());}
    @ParameterizedTest @ValueSource(strings={"v1.2.3","1","01","Release_Candidate-2"})
    void actualDocumentStringVersionSurvivesWithoutCoercion(String version){var n=opaque("insurance",version);var decoded=DefinitionProfiles.decode(n.toString());var reference=decoded.common().requirements().documents().getFirst().template();assertNull(reference.version());var typed=typed(reference);assertEquals("OpaqueOwnerVersion",typed.getClass().getSimpleName());assertEquals(version,value(typed));var round=DefinitionProfiles.canonical(n.toString()).json();var actual=document((ObjectNode)DefinitionCanonical.parse(round));assertEquals(version,actual.get("versionText").textValue());assertEquals("OPAQUE",actual.get("versionFormat").textValue());assertFalse(actual.has("version"));}
    @Test void numericVersionKeepsItsExistingTypedValueAndFiveFieldWireShape(){var definition=DefinitionProfiles.decode(ProfileFixtures.load("insurance").toString());var reference=definition.common().requirements().documents().getFirst().template();var owner=typed(reference);assertEquals("NumericOwnerVersion",owner.getClass().getSimpleName());assertEquals(1L,value(owner));var tree=DefinitionCanonical.tree(reference);assertEquals(5,tree.size());assertEquals(1L,tree.get("version").longValue());assertFalse(tree.has("versionFormat"));assertFalse(tree.has("versionText"));}
    @Test void opaqueNumericLookingStringCannotAliasNumericPublicationVersion(){var numeric=DefinitionProfiles.decode(ProfileFixtures.load("insurance").toString()).common().requirements().documents().getFirst().template();var textual=DefinitionProfiles.decode(opaque("insurance","1").toString()).common().requirements().documents().getFirst().template();assertNotEquals(typed(numeric),typed(textual));assertNotEquals(DefinitionProfiles.canonical(ProfileFixtures.load("insurance").toString()).digest(),DefinitionProfiles.canonical(opaque("insurance","1").toString()).digest());}
    @Test void opaqueDocumentIdentityAndDigestRemainTheActualDeclaredOwnerTuple(){var n=opaque("insurance","v1.2.3");var expected=document(n);var actual=DefinitionProfiles.decode(n.toString()).common().requirements().documents().getFirst().template();assertEquals(expected.get("owner").textValue(),actual.owner());assertEquals(expected.get("kind").textValue(),actual.kind());assertEquals(expected.get("id").textValue(),actual.id());assertEquals(expected.get("digest").textValue(),actual.digest());}
    @Test void opaqueTextRemainsCaseSensitive(){assertNotEquals(DefinitionProfiles.canonical(opaque("insurance","Release-1").toString()).digest(),DefinitionProfiles.canonical(opaque("insurance","release-1").toString()).digest());}
    @Test void numericAndOpaqueFieldsCannotBeCombined(){var n=opaque("insurance","v1");document(n).put("version",1);assertThrows(IllegalArgumentException.class,()->DefinitionProfiles.decode(n.toString()));}
    @Test void bankAndLoanDocumentContractsRetainNumericRequirement(){for(String family:List.of("account","loan"))assertThrows(IllegalArgumentException.class,()->DefinitionProfiles.decode(opaque(family,"v1.2.3").toString()));}
    @Test void numericTaxPolicyCannotAdoptOpaqueDocumentVersion(){var n=ProfileFixtures.load("insurance");var ref=(ObjectNode)n.get("terms").get("taxComponents").get(0).get("policy");ref.remove("version");ref.put("versionFormat","OPAQUE");ref.put("versionText","v1.2.3");assertThrows(IllegalArgumentException.class,()->DefinitionProfiles.decode(n.toString()));}
    @ParameterizedTest @ValueSource(strings={"", "opaque", "NUMERIC"})
    void missingOrWrongOpaqueDiscriminatorRefuses(String format){var n=opaque("insurance","v1.2.3");document(n).put("versionFormat",format);assertThrows(IllegalArgumentException.class,()->DefinitionProfiles.decode(n.toString()));}
    @Test void missingVersionTextRefuses(){var n=opaque("insurance","v1.2.3");document(n).remove("versionText");assertThrows(IllegalArgumentException.class,()->DefinitionProfiles.decode(n.toString()));}
    @Test void arbitraryVersionTextSizeAndControlCharactersRefuse(){for(String v:List.of(" ","v\n1","v\u00001","x".repeat(256)))assertThrows(IllegalArgumentException.class,()->DefinitionProfiles.decode(opaque("insurance",v).toString()));}
    @Test void opaqueNumberMustNotBeCoercedIntoString(){var n=opaque("insurance","v1");document(n).put("versionText",1);assertThrows(IllegalArgumentException.class,()->DefinitionProfiles.decode(n.toString()));}

    @ParameterizedTest @ValueSource(strings={"template","issuer","layout"})
    void allThreeDocumentReferenceSlotsRetainOpaqueOwnerVersion(String slot){var n=ProfileFixtures.load("insurance");var ref=(ObjectNode)n.get("common").get("requirements").get("documents").get(0).get(slot);ref.remove("version");ref.put("versionFormat","OPAQUE");ref.put("versionText","v1.2.3");var decoded=DefinitionProfiles.decode(n.toString());var doc=decoded.common().requirements().documents().getFirst();var actual=switch(slot){case "template"->doc.template();case "issuer"->doc.issuer();default->doc.layout();};assertEquals("v1.2.3",value(typed(actual)));}
    @Test void legacyFiveArgumentConstructorRetainsNumericAccessor(){var ref=new ExactReference("core-common-reference-master-data","PREMIUM_TAX","33333333-3333-3333-3333-333333333333",7L,"b".repeat(64));assertEquals(7L,ref.version());assertEquals(7L,value(typed(ref)));assertEquals(5,DefinitionCanonical.tree(ref).size());}
    @Test void missingNumericAndOpaqueVersionsNeverBecomeDefaults(){var n=ProfileFixtures.load("insurance");document(n).remove("version");assertThrows(IllegalArgumentException.class,()->DefinitionProfiles.decode(n.toString()));}

    @Test void opaquePublicAndPurposeDigestsMatchIndependentCanonicalGolden() {
        var definition=opaque("insurance","v1.2.3");assertEquals("4e41e2f8a7ca99430ad0694ec1afd9146f8368feba6395d11c3d3354a01a86b7",DefinitionProfiles.canonical(definition.toString()).digest());
        var body=(ObjectNode)DefinitionCanonical.parse("{}");body.put("encodingVersion","product-insurance-quote-configuration-1");body.put("publicDigest","4e41e2f8a7ca99430ad0694ec1afd9146f8368feba6395d11c3d3354a01a86b7");body.set("definition",definition);
        var scope=body.putObject("scope");scope.put("tenantId","11111111-2222-3333-4444-555555555555");scope.put("rootId","22222222-2222-3333-4444-555555555555");scope.put("productId","33333333-2222-3333-4444-555555555555");scope.put("revisionId","44444444-2222-3333-4444-555555555555");scope.put("publicationId","55555555-2222-3333-4444-555555555555");
        var value=InsuranceQuotePurposeCodec.decode(body.toString());assertEquals("38f22c2951eb011e437033fe29cf101431c7123fcbdbed79166cbe036b94755e",InsuranceQuotePurposeCodec.digest(value));assertArrayEquals(InsuranceQuotePurposeCodec.bytes(value),InsuranceQuotePurposeCodec.bytes(InsuranceQuotePurposeCodec.decodeBytes(InsuranceQuotePurposeCodec.bytes(value))));
    }
}

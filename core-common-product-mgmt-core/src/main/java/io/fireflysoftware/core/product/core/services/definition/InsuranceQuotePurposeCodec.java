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

import static io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.TermPcInsuranceTerms;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/** Canonical configuration bytes exclude observation times, callers and policy decisions. */
public final class InsuranceQuotePurposeCodec {
    public static final String ENCODING="product-insurance-quote-configuration-1";
    private static final int MAX_BYTES=131072;
    private static final Set<String> REQUIRED_FACTS=Set.of("INSURED_OBJECT","UNDERWRITING_APPROVAL","REFERRAL_AUTHORITY");
    private InsuranceQuotePurposeCodec() {}

    public static Configuration decode(String raw) {
        var root=DefinitionCanonical.parse(raw);
        DefinitionCanonical.closed(root,Set.of("encodingVersion","scope","publicDigest","definition"),"/");
        require(ENCODING.equals(text(root,"encodingVersion")));
        var scope=root.get("scope");DefinitionCanonical.closed(scope,Set.of("tenantId","rootId","productId","revisionId","publicationId"),"/scope");
        var graph=new Scope(id(scope,"tenantId"),id(scope,"rootId"),id(scope,"productId"),id(scope,"revisionId"),id(scope,"publicationId"));
        require(root.get("definition")!=null);
        var definition=DefinitionProfiles.decode(root.get("definition").toString());
        return validated(new Configuration(graph,text(root,"publicDigest"),definition));
    }

    public static String encode(Configuration supplied) {
        var value=validated(supplied);var root=(ObjectNode)DefinitionCanonical.parse("{}");
        root.put("encodingVersion",ENCODING);root.set("scope",DefinitionCanonical.tree(value.scope()));
        root.put("publicDigest",value.publicDigest());root.set("definition",DefinitionCanonical.tree(value.definition()));
        String encoded=DefinitionCanonical.encode(root);require(encoded.getBytes(StandardCharsets.UTF_8).length<=MAX_BYTES);return encoded;
    }
    public static byte[] bytes(Configuration value){return encode(value).getBytes(StandardCharsets.UTF_8);}
    public static String digest(Configuration value){return DefinitionCanonical.hash(ENCODING,encode(value));}
    public static Configuration decodeBytes(byte[] bytes) {
        require(bytes!=null && bytes.length<=MAX_BYTES);
        final String raw;
        try {raw=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException error){throw invalid();}
        var value=decode(raw);require(Arrays.equals(bytes,bytes(value)));return value;
    }
    private static Configuration validated(Configuration value) {
        require(value!=null);
        String raw=DefinitionCanonical.encode(DefinitionCanonical.tree(value.definition()));
        var definition=DefinitionProfiles.decode(raw);var common=definition.common();
        require("INSURANCE".equals(common.businessArea()) && "TERM_PC_INSURANCE_V1".equals(common.profileCode())
                && Integer.valueOf(1).equals(common.profileVersion()) && definition.terms() instanceof TermPcInsuranceTerms);
        require(value.publicDigest().equals(DefinitionProfiles.canonical(raw).digest()));
        for(String code:REQUIRED_FACTS)require(common.requirements().facts().stream().anyMatch(f->code.equals(f.code()) && Boolean.TRUE.equals(f.evidenceRequired())));
        return new Configuration(value.scope(),value.publicDigest(),definition);
    }
    private static String text(JsonNode node,String field){var v=node.get(field);require(v!=null && v.isTextual());return v.textValue();}
    private static UUID id(JsonNode node,String field){return DefinitionRules.uuid(text(node,field),"/scope/"+field);}
    private static void require(boolean value){if(!value)throw invalid();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("INSURANCE_QUOTE_PURPOSE_SHAPE");}
}

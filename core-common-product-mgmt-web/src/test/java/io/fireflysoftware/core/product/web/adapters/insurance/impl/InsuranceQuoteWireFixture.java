/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.product.web.adapters.insurance.impl;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts;
import io.fireflysoftware.core.product.web.config.definition.lifecycle.InsuranceQuoteReferenceProperties;
import io.fireflysoftware.core.product.web.config.definition.lifecycle.InsuranceQuoteReferenceProperties.*;
import io.fireflysoftware.masters.core.services.currency.policy.CurrencyPolicyRules;
import io.fireflysoftware.masters.interfaces.dtos.currency.policy.CurrencyPolicyPublishRequest;
import io.fireflysoftware.masters.interfaces.dtos.currency.v1.CurrencyDTO;
import io.fireflysoftware.masters.interfaces.enums.commons.v1.StatusEnum;
import io.fireflysoftware.masters.interfaces.dtos.insurance.reference.InsuranceReferenceContracts.*;
import io.fireflysoftware.masters.interfaces.protocol.insurance.reference.InsuranceReferenceCodec;
import io.fireflysoftware.masters.interfaces.dtos.document.layout.v1.*;
import io.fireflysoftware.masters.core.services.document.layout.v1.DocumentLayoutRules;
import io.fireflysoftware.common.config.core.authority.DocumentIssuerCodec;
import io.fireflysoftware.common.config.interfaces.documentissuer.DocumentIssuerContracts;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;

/** Pure actual owner codecs and controlled wire data; no native source, permission or provider. */
final class InsuranceQuoteWireFixture {
    static final Instant NOW=Instant.parse("2026-11-01T12:00:00Z");
    static final ObjectMapper JSON=new ObjectMapper();
    final InsuranceQuotePurposeContracts.Scope scope=new InsuranceQuotePurposeContracts.Scope(id(1),id(2),id(3),id(4),id(5));
    final CurrencyPolicyRules.Canonical currencySource,currencyEncoding;
    final CurrencyPolicyReference currency;
    final Revision tax,coverage,exclusion;
    final List<InsuranceSelection> routes;
    final DocumentLayoutRules.Canonical layout;
    final DocumentIssuerContracts.Definition issuer;
    final String issuerDigest;
    final InsuranceQuotePurposeContracts.Configuration configuration;
    static UUID id(int value){return new UUID(0,value);}
    InsuranceQuoteWireFixture(){
        var metadata=CurrencyDTO.builder().currencyId(id(7)).isoCode("USD").currencyName("US Dollar").symbol("$").decimalPrecision(2).isMajor(true).status(StatusEnum.ACTIVE).build();
        currencySource=CurrencyPolicyRules.source(metadata);var request=new CurrencyPolicyPublishRequest(id(8).toString(),id(7).toString(),"USD",currencySource.digest(),0L,2,NOW.minusSeconds(60).toEpochMilli(),NOW.plusSeconds(3600).toEpochMilli(),"urn:synthetic:currency-policy");currencyEncoding=CurrencyPolicyRules.definition(request,currencySource,1);
        currency=new CurrencyPolicyReference(id(6).toString(),id(7).toString(),"USD",1L,currencyEncoding.digest(),"MASTERS_CURRENCY_POLICY_V1");
        var applies=new Applicability(List.of("US"),List.of("TERM_PC_INSURANCE_V1"),List.of(Event.NEW_BUSINESS));var nativeCurrency=new io.fireflysoftware.masters.interfaces.dtos.currency.policy.CurrencyPolicyReference(currency.policyId(),currency.currencyId(),currency.isoCode(),currency.version(),currency.digest(),currency.encodingVersion());
        tax=revision(Kind.PREMIUM_TAX,10,new PremiumTax(TaxBasis.NET_PREMIUM_EXCLUSIVE,100000,Rounding.HALF_UP,2,nativeCurrency,applies));coverage=revision(Kind.COVERAGE_WORDING,11,new Wording(id(15),"en-US","text/plain","Synthetic current property coverage wording.",applies));exclusion=revision(Kind.EXCLUSION_WORDING,12,new Wording(id(15),"en-US","text/plain","Synthetic current property exclusion wording.",applies));
        routes=List.of(selection(tax),selection(coverage),selection(exclusion));
        var source=new DocumentLayoutMasterReference(id(30).toString(),id(31).toString(),"PROPERTY_CONTRACT","release.01","en-US","text/html","MASTERS_JSON_V1","a".repeat(64),"b".repeat(64),"c".repeat(64),DocumentLayoutRules.EMPTY_STYLE_DIGEST);
        var notice=new DocumentLayoutMasterReference(id(32).toString(),id(33).toString(),"NOTICE_GENERAL","notice.01","en-US","text/html","MASTERS_JSON_V1","d".repeat(64),"e".repeat(64),"f".repeat(64),DocumentLayoutRules.EMPTY_STYLE_DIGEST);
        var profile=new DocumentLayoutOutputProfileMetadata(new DocumentLayoutOutputProfileReference("SYNTHETIC_A4","1","text/html","2".repeat(64)),"AUTHORED_PAGES","A4","PORTRAIT",List.of("document-page"),16,262144);
        layout=DocumentLayoutRules.canonical(new DocumentLayoutDefinition(source,profile,new DocumentLayoutSemanticDefinition("AUTHORED_PAGES","A4","PORTRAIT",List.of(new DocumentLayoutNoticeReference(DocumentLayoutNoticeReference.Role.GENERAL_DISCLOSURE,DocumentLayoutNoticeReference.Placement.FOOTER,notice)))));
        var branding=new DocumentIssuerContracts.SourceSnapshot(scope.tenantId().toString(),4,id(22).toString(),7,9,"bank-east","Bank East",true,true,"https://assets.example/logo.svg","#123456","Synthetic quote metadata footer.");
        issuer=new DocumentIssuerContracts.Definition(id(20).toString(),1,"INSURANCE_DOCS","Bank East Insurance","documents@example.test",List.of(new DocumentIssuerContracts.Use(DocumentIssuerContracts.Purpose.INSURANCE_TERM_PC_QUOTE,"CONTRACT","en-US")),branding,NOW.minusSeconds(60).toEpochMilli(),NOW.plusSeconds(3600).toEpochMilli());issuerDigest=DocumentIssuerCodec.definitionDigest(issuer);
        try(var input=getClass().getResourceAsStream("/insurance/quote-definition.json")){
            var root=(ObjectNode)JSON.readTree(Objects.requireNonNull(input));replaceCurrency(root);
            var terms=(ObjectNode)root.get("terms");((ObjectNode)terms.path("taxComponents").get(0)).set("policy",JSON.valueToTree(reference(tax)));for(var entry:terms.path("coverages"))((ObjectNode)entry).set("wording",JSON.valueToTree(reference(coverage)));((ArrayNode)terms.get("exclusions")).set(0,JSON.valueToTree(reference(exclusion)));
            var document=(ObjectNode)root.path("common").path("requirements").path("documents").get(0);document.set("template",JSON.valueToTree(new ExactReference("core-common-reference-master-data","DOCUMENT_TEMPLATE",id(30).toString(),null,"a".repeat(64),"OPAQUE","release.01")));document.set("layout",JSON.valueToTree(new ExactReference("core-common-reference-master-data","DOCUMENT_LAYOUT",id(34).toString(),null,layout.digest(),"OPAQUE","release.01")));document.set("issuer",JSON.valueToTree(new ExactReference("core-common-config-mgmt","DOCUMENT_ISSUER",id(21).toString(),1L,issuerDigest)));
            String raw=root.toString();configuration=new InsuranceQuotePurposeContracts.Configuration(scope,DefinitionProfiles.canonical(raw).digest(),DefinitionProfiles.decode(raw));InsuranceQuotePurposeCodec.digest(configuration);
        }catch(Exception e){throw new AssertionError(e);}
    }
    private void replaceCurrency(JsonNode node){if(node.isObject()&&node.has("policyId")&&node.has("currencyId")){var replacement=JSON.<ObjectNode>valueToTree(currency);((ObjectNode)node).removeAll();((ObjectNode)node).setAll(replacement);}else if(node.isContainerNode())node.forEach(this::replaceCurrency);}
    private static Revision revision(Kind kind,int value,Body body){return new Revision(kind,id(value),id(value+100),1,kind.name()+"_DEMO",NOW.minusSeconds(60),NOW.plusSeconds(3600),body,new Provenance(Origin.SANDBOX,"urn:synthetic:source:"+value,"source.1","9".repeat(64),"urn:synthetic:rights:"+value));}
    static ExactReference reference(Revision revision){return new ExactReference("core-common-reference-master-data",revision.kind().name(),revision.recordId().toString(),revision.revision(),InsuranceReferenceCodec.digest(revision));}
    static InsuranceSelection selection(Revision revision){return new InsuranceSelection(reference(revision),revision.publicationId(),4L);}
    InsuranceQuoteReferenceProperties properties(){return new InsuranceQuoteReferenceProperties(true,URI.create("https://configuration.example"),"configuration-documents",URI.create("https://masters.example"),"masters-references",List.of(new CurrencySelection(currency,2)),routes);}
    static String time(Instant instant){return new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(instant);}
    ObjectNode currencyResponse(){var r=JSON.createObjectNode();r.set("target",JSON.createObjectNode().set("reference",JSON.valueToTree(currency)));((ObjectNode)r.get("target")).put("postedScale",2);var p=JSON.createObjectNode();p.set("reference",JSON.valueToTree(currency));p.put("canonicalEncoding",currencyEncoding.encoding()).put("sourceEncoding",currencySource.encoding()).put("sourceDigest",currencySource.digest()).put("postedScale",2).put("effectiveFromEpochMillis",NOW.minusSeconds(60).toEpochMilli()).put("effectiveToEpochMillis",NOW.plusSeconds(3600).toEpochMilli()).put("provenanceReference","urn:synthetic:currency-policy").put("publicationActionId",id(8).toString()).put("publishedAtEpochMillis",NOW.minusSeconds(30).toEpochMilli()).put("lifecycle","PUBLISHED").put("stateVersion",0).put("observedAtEpochMillis",NOW.toEpochMilli()).put("effectiveNow",true);r.set("publication",p);r.put("recordedAt",time(NOW)).put("effectiveUntil",time(NOW.plusSeconds(4)));return r;}
    ObjectNode insuranceResponse(Revision revision){try{var target=JSON.createObjectNode().put("kind",revision.kind().name()).put("recordId",revision.recordId().toString()).put("publicationId",revision.publicationId().toString()).put("revision",revision.revision()).put("definitionDigest",InsuranceReferenceCodec.digest(revision)).put("generation",4);var definition=(ObjectNode)JSON.readTree(InsuranceReferenceCodec.encode(revision));definition.remove(List.of("encodingVersion","scope"));var control=target.deepCopy();control.remove("definitionDigest");control.put("state","ACTIVE");var publication=JSON.createObjectNode();publication.set("definition",definition);publication.set("control",control);publication.put("observedAt",time(NOW)).put("effectiveUntil",time(NOW.plusSeconds(4)));var root=JSON.createObjectNode();root.set("reference",target);root.set("publication",publication);return root;}catch(Exception e){throw new AssertionError(e);}}
    ObjectNode issuerResponse(JsonNode request){try{var root=JSON.createObjectNode();var reference=request.deepCopy();((ObjectNode)reference).put("issuerId",issuer.issuerId());root.set("reference",reference);root.set("definition",JSON.readTree(DocumentIssuerCodec.definitionJson(issuer)));root.put("generation",2).put("sourceGeneration",9).put("observedAtEpochMillis",NOW.toEpochMilli()).put("nativeUntilEpochMillis",NOW.plusSeconds(4).toEpochMilli()).put("effectiveUntilEpochMillis",NOW.plusSeconds(4).toEpochMilli());return root;}catch(Exception e){throw new AssertionError(e);}}
    ObjectNode templateResponse(JsonNode request){try{var root=JSON.createObjectNode();root.set("target",request);root.set("definition",JSON.readTree(layout.json()));root.put("observedAtEpochMillis",NOW.toEpochMilli()).put("effectiveUntilEpochMillis",NOW.plusSeconds(4).toEpochMilli());return root;}catch(Exception e){throw new AssertionError(e);}}
}

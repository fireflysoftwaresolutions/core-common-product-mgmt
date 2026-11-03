/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.product.web.adapters.card.impl;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;
import io.fireflysoftware.core.product.web.config.definition.lifecycle.DebitCardReferenceProperties.*;
import io.fireflysoftware.common.reference.master.data.sdk.model.CurrencyPolicyCurrent;
import io.fireflysoftware.common.reference.master.data.sdk.model.DocumentReferenceCurrent;
import io.fireflysoftware.common.reference.master.data.sdk.model.DocumentReferenceCurrentTarget;
import io.fireflysoftware.common.config.sdk.model.DocumentIssuerObservation;
import io.fireflysoftware.common.config.sdk.model.DocumentIssuerPublicationReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Consumer consistency checks of genuine current owner responses; never a permission source. */
final class DebitCardOwnerObservationsImpl {
    record Stamp(String exactAuthority,Instant observedAt,Instant until){}
    static Stamp currency(CurrencySelection selected,CurrencyPolicyCurrent current,Instant started,Instant now){
        var target=Objects.requireNonNull(current.getTarget());var value=Objects.requireNonNull(current.getPublication());var expected=selected.reference();
        require(currency(expected,target.getReference())&&currency(expected,value.getReference())&&Objects.equals(selected.postedScale(),target.getPostedScale())&&Objects.equals(selected.postedScale(),value.getPostedScale()));
        var observed=current.getRecordedAt();var until=current.getEffectiveUntil();window(observed,until,started,now,5);
        require("PUBLISHED".equals(value.getLifecycle())&&Long.valueOf(0).equals(value.getStateVersion())&&Boolean.TRUE.equals(value.getEffectiveNow()));
        require(value.getObservedAtEpochMillis()!=null&&value.getObservedAtEpochMillis()>=started.toEpochMilli()&&value.getObservedAtEpochMillis()<=now.toEpochMilli()
            &&value.getEffectiveFromEpochMillis()!=null&&value.getEffectiveFromEpochMillis()<=observed.toEpochMilli()&&value.getPublishedAtEpochMillis()!=null&&value.getPublishedAtEpochMillis()<=observed.toEpochMilli());
        if(value.getEffectiveToEpochMillis()!=null)require(value.getEffectiveToEpochMillis()>=until.toEpochMilli());
        DefinitionRules.uuid(value.getPublicationActionId(),"/currency/publicationActionId");
        require(value.getSourceEncoding()!=null&&value.getSourceEncoding().startsWith("MASTERS_CURRENCY_SOURCE_V1\n")&&value.getSourceEncoding().getBytes(StandardCharsets.UTF_8).length<=1048576&&sha(value.getSourceEncoding()).equals(value.getSourceDigest()));
        var source=DefinitionCanonical.parse(value.getSourceEncoding().substring("MASTERS_CURRENCY_SOURCE_V1\n".length()));
        DefinitionCanonical.closed(source,Set.of("currencyId","isoCode","currencyName","symbol","decimalPrecision","isMajor","status","dateCreated","dateUpdated"),"/currency/source");
        require(expected.currencyId().equals(source.path("currencyId").textValue())&&expected.isoCode().equals(source.path("isoCode").textValue())&&"ACTIVE".equals(source.path("status").textValue())&&source.path("decimalPrecision").isIntegralNumber()&&source.path("decimalPrecision").intValue()==selected.postedScale());
        require(value.getSourceEncoding().equals("MASTERS_CURRENCY_SOURCE_V1\n"+DefinitionCanonical.encode(source)));
        var definition=new TreeMap<String,Object>();definition.put("currencyId",expected.currencyId());definition.put("isoCode",expected.isoCode());definition.put("version",expected.version());definition.put("sourceEncoding",value.getSourceEncoding());definition.put("sourceDigest",value.getSourceDigest());definition.put("postedScale",selected.postedScale());definition.put("effectiveFromEpochMillis",value.getEffectiveFromEpochMillis());definition.put("effectiveToEpochMillis",value.getEffectiveToEpochMillis());definition.put("provenanceReference",value.getProvenanceReference());
        String encoded="MASTERS_CURRENCY_POLICY_V1\n"+DefinitionCanonical.encode(DefinitionCanonical.tree(definition));require(encoded.equals(value.getCanonicalEncoding())&&sha(encoded).equals(expected.digest()));
        return new Stamp(expected.digest()+"/"+value.getPublicationActionId()+"/"+value.getPublishedAtEpochMillis()+"/"+value.getStateVersion(),observed,until);
    }
    private static boolean currency(CurrencyPolicyReference expected,io.fireflysoftware.common.reference.master.data.sdk.model.CurrencyPolicyReference actual){return actual!=null&&Objects.equals(expected.policyId(),actual.getPolicyId())&&Objects.equals(expected.currencyId(),actual.getCurrencyId())&&Objects.equals(expected.isoCode(),actual.getIsoCode())&&Objects.equals(expected.version(),actual.getVersion())&&Objects.equals(expected.digest(),actual.getDigest())&&Objects.equals(expected.encodingVersion(),actual.getEncodingVersion());}
    static Stamp template(DocumentReferenceCurrentTarget requested,DocumentReferenceCurrent current,Instant started,Instant now){
        require(requested.equals(current.getTarget()));var definition=Objects.requireNonNull(current.getDefinition());var source=Objects.requireNonNull(definition.getSourceReference());
        require(requested.getTemplate().getId().equals(source.getRevisionId())&&requested.getTemplate().getVersionText().equals(source.getVersion())&&requested.getTemplate().getDigest().equals(source.getSourceDigest())&&requested.getLocale().equals(source.getLocaleCode()));
        require(source.getTemplateId()!=null&&definition.getProfileMetadata()!=null&&definition.getProfileMetadata().getReference()!=null&&definition.getSemanticDefinition()!=null&&definition.getSemanticDefinition().getLegalNotices()!=null&&!definition.getSemanticDefinition().getLegalNotices().isEmpty());
        String encoded=DefinitionCanonical.encode(DefinitionCanonical.tree(definition));require(encoded.getBytes(StandardCharsets.UTF_8).length<=32768&&requested.getLayout().getDigest().equals(DefinitionCanonical.hash("MASTERS_DOCUMENT_LAYOUT_V1",encoded)));
        var observed=epoch(current.getObservedAtEpochMillis());var until=epoch(current.getEffectiveUntilEpochMillis());window(observed,until,started,now,5);return new Stamp(requested.getLayout().getDigest()+"/"+requested.getTemplate().getDigest(),observed,until);
    }
    static Stamp issuer(DocumentIssuerPublicationReference requested,DocumentIssuerObservation current,Instant started,Instant now){
        var reference=Objects.requireNonNull(current.getReference());var definition=Objects.requireNonNull(current.getDefinition());var source=Objects.requireNonNull(definition.getSource());
        require(requested.getTenantId().equals(reference.getTenantId())&&requested.getPublicationId().equals(reference.getPublicationId())&&requested.getRevision().equals(reference.getRevision())&&requested.getDefinitionDigest().equals(reference.getDefinitionDigest())&&requested.getUse().equals(reference.getUse())&&reference.getIssuerId()!=null&&reference.getIssuerId().equals(definition.getIssuerId())&&requested.getRevision().equals(definition.getRevision())&&definition.getUses()!=null&&definition.getUses().contains(requested.getUse()));
        require(current.getGeneration()!=null&&current.getGeneration()>=2&&current.getSourceGeneration()!=null&&current.getSourceGeneration()>0&&current.getSourceGeneration().equals(source.getSourceGeneration())&&requested.getTenantId().equals(source.getTenantId())&&Boolean.TRUE.equals(source.getTenantActive())&&Boolean.TRUE.equals(source.getBrandingActive()));
        DefinitionRules.uuid(reference.getIssuerId(),"/issuer/issuerId");DefinitionRules.uuid(source.getBrandingId(),"/issuer/brandingId");
        // This is the existing owner token protocol; every source/presentation field is digest-bound.
        var uses=new ArrayList<String>();for(var use:definition.getUses())uses.add(record(string(use.getPurpose().getValue()),string(use.getDocumentType()),string(use.getLocale())));
        String sourceFrame=record(string(source.getTenantId()),integer(source.getTenantVersion()),string(source.getBrandingId()),integer(source.getBrandingVersion()),integer(source.getSourceGeneration()),string(source.getTenantCode()),string(source.getTenantName()),bool(source.getTenantActive()),bool(source.getBrandingActive()),string(source.getLogoUrl()),string(source.getPrimaryColor()),string(source.getFooterText()));
        String body=record(string(definition.getIssuerId()),integer(definition.getRevision()),string(definition.getIssuerCode()),string(definition.getDisplayName()),string(definition.getContactEmail()),"A"+uses.size()+":"+String.join("",uses),sourceFrame,integer(definition.getEffectiveFromEpochMillis()),integer(definition.getEffectiveUntilEpochMillis()));
        require(sha(string("FF_DOCUMENT_ISSUER_DEFINITION_V1")+body).equals(requested.getDefinitionDigest()));
        var observed=epoch(current.getObservedAtEpochMillis());var nativeUntil=epoch(current.getNativeUntilEpochMillis());var until=epoch(current.getEffectiveUntilEpochMillis());window(observed,nativeUntil,started,now,5);window(observed,until,started,now,5);
        require(!until.isAfter(nativeUntil)&&!observed.isBefore(epoch(definition.getEffectiveFromEpochMillis()))&&!until.isAfter(epoch(definition.getEffectiveUntilEpochMillis())));
        return new Stamp(requested.getDefinitionDigest()+"/"+reference.getIssuerId()+"/"+current.getGeneration()+"/"+current.getSourceGeneration(),observed,until);
    }
    private static String record(String... fields){return "R"+fields.length+":"+String.join("",fields);}
    private static String string(String value){require(value!=null&&value.codePoints().noneMatch(c->Character.isISOControl(c)||c>=0xD800&&c<=0xDFFF));return "S"+value.getBytes(StandardCharsets.UTF_8).length+":"+value;}
    private static String integer(Long value){require(value!=null);return "I"+value+";";}
    private static String bool(Boolean value){require(value!=null);return value?"B1;":"B0;";}
    private static String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw unavailable();}}
    private static Instant epoch(Long value){require(value!=null&&value>=0&&value<=253402300799999L);return Instant.ofEpochMilli(value);}
    private static void window(Instant observed,Instant until,Instant started,Instant now,int seconds){require(observed!=null&&until!=null&&!observed.isBefore(started.truncatedTo(java.time.temporal.ChronoUnit.MILLIS))&&!observed.isAfter(now)&&now.isBefore(until)&&observed.isBefore(until)&&!until.isAfter(observed.plusSeconds(seconds)));}
    private static void require(boolean value){if(!value)throw unavailable();}
    private static IllegalStateException unavailable(){return new IllegalStateException("DEBIT_CARD_REFERENCE_OBSERVATION_UNAVAILABLE");}
}

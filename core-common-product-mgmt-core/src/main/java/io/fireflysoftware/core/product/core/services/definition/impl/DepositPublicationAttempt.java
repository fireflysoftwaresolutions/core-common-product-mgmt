package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.core.services.definition.DepositAccountDocumentReferenceResolver.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.DocumentRequirement;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCommand;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandNativeRepository.NativeBudgetStamp;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;

/** One running publication attempt; none of these transport facts grants publication authority. */
final class DepositPublicationAttempt {
    private record Span(Instant observed,Instant until,long before) {}
    private final PreparedDepositPublication prepared;
    private final R2dbcOperationBudget original;
    private final Clock clock;
    private final LongSupplier nanos;
    private final Instant started;
    private final long startedNanos;
    private final List<Span> spans=new ArrayList<>();
    private final List<Observation> observations=new ArrayList<>();
    private Instant cap;

    DepositPublicationAttempt(PreparedDepositPublication prepared,R2dbcOperationBudget original,
            ProductDefinitionCommandAuthorization.Admission publisher,long beforePreparation,
            Clock clock,LongSupplier nanos) {
        this.prepared=Objects.requireNonNull(prepared);this.original=Objects.requireNonNull(original);
        this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
        started=clock.instant();startedNanos=nanos.getAsLong();original.remaining();
        require(prepared.originalBudget()==original && prepared.nativePreparation()!=null
            && prepared.requestScope()!=null && publisher!=null && publisher.principal()!=null
            && prepared.publisher()!=null && prepared.publisher().issuer().equals(publisher.principal().issuer())
            && prepared.publisher().subject().equals(publisher.principal().subject())
            && prepared.requestScope().tenantId().toString().equals(publisher.principal().tenantId())
            && publisher.scope().command()==ProductDefinitionCommand.PUBLISH
            && publisher.scope().tenantId().equals(prepared.requestScope().tenantId())
            && publisher.scope().rootId().equals(prepared.requestScope().rootId())
            && publisher.scope().operationId().equals(prepared.requestScope().operationId())
            && publisher.scope().targetId().equals(prepared.reviewId()));
        var canonical=DefinitionProfiles.canonical(prepared.canonicalDefinition());
        require(canonical.json().equals(prepared.canonicalDefinition())
            && canonical.digest().equals(prepared.selection().publicDigest())
            && DefinitionProfiles.decode(canonical.json()).equals(prepared.definition())
            && prepared.requestScope().tenantId().equals(prepared.selection().tenantId())
            && prepared.requestScope().rootId().equals(prepared.selection().rootId())
            && prepared.selectionFingerprint().equals(DefinitionCanonical.hash("product-definition-selection-v1",
                DefinitionCanonical.encode(DefinitionCanonical.tree(prepared.selection())))));
        GovernedDefinitionProfile.DEPOSIT_ACCOUNT.require(prepared.definition().common());
        for(var id:List.of(prepared.productId(),prepared.reviewId(),prepared.selection().revisionId(),
                prepared.nativePreparation().contextId()))DefinitionRules.uuid(id.toString(),"/publication/identity");
        var resources=prepared.resources();var unique=new HashSet<UUID>();
        for(var id:List.of(resources.actionId(),resources.publicationId(),resources.productVersionId(),
                resources.historyId(),resources.auditId(),resources.publicEventId())) {
            DefinitionRules.uuid(id.toString(),"/publication/resources");require(unique.add(id));
        }
        require(prepared.versionNumber()>0 && prepared.intentDigest()!=null
            && prepared.intentDigest().matches("[0-9a-f]{64}")
            && !documents().isEmpty() && documents().size()<=50);
        checkWindow(prepared.observedAt(),prepared.effectiveUntil(),Duration.ofSeconds(30));
        cap=minimum(prepared.effectiveUntil(),Instant.ofEpochMilli(publisher.actorExpiresAtEpochMillis()),
            Instant.ofEpochMilli(publisher.validUntilEpochMillis()),
            Instant.ofEpochMilli(prepared.nativePreparation().requestUntilMillis()),
            Instant.ofEpochMilli(prepared.nativePreparation().actorUntilMillis()),
            Instant.ofEpochMilli(prepared.nativePreparation().permitUntilMillis()));
        spans.add(new Span(prepared.observedAt(),prepared.effectiveUntil(),beforePreparation));remaining();
    }

    PreparedDepositPublication prepared(){return prepared;}
    List<DocumentRequirement> documents(){return prepared.definition().common().requirements().documents();}
    Target target(DocumentRequirement document){
        return new Target(new Scope(prepared.requestScope().tenantId(),prepared.requestScope().rootId(),
            prepared.productId(),prepared.selection().revisionId(),prepared.resources().publicationId(),
            prepared.selection().publicDigest()),"DEPOSIT_ACCOUNT_ENROLLMENT",document);
    }
    void observe(DocumentRequirement document,Observation value,long beforeCall) {
        remaining();require(observations.size()<documents().size()
            && documents().get(observations.size()).equals(document) && value!=null
            && target(document).equals(value.target()) && value.windowEvidence()!=null);
        checkWindow(value.observedAt(),value.effectiveUntil(),Duration.ofSeconds(5));
        if(value.windowEvidence()==WindowEvidence.EFFECTIVE_ONLY)require(value.nativeUntil()==null);
        else {
            checkWindow(value.observedAt(),value.nativeUntil(),Duration.ofSeconds(5));
            require(!value.effectiveUntil().isAfter(value.nativeUntil()));
        }
        observations.add(value);spans.add(new Span(value.observedAt(),value.effectiveUntil(),beforeCall));
        cap=minimum(cap,value.effectiveUntil());remaining();
    }
    void observeNative(NativeBudgetStamp value,Instant recordedAt,long beforeCall) {
        remaining();require(value!=null && value.recordedAt()!=null && value.recordedAt().equals(recordedAt)
            && value.dbNowMillis()!=null && value.admissionUntilMillis()!=null
            && value.recordedAt().toEpochMilli()==value.dbNowMillis());
        var observed=Instant.ofEpochMilli(value.dbNowMillis());var until=Instant.ofEpochMilli(value.admissionUntilMillis());
        checkWindow(observed,until,Duration.ofSeconds(30));require(!until.isAfter(cap));
        spans.add(new Span(observed,until,beforeCall));cap=minimum(cap,until);remaining();
    }
    Duration remaining() {
        var left=original.remaining();var now=clock.instant();long tick=nanos.getAsLong();
        require(!now.isBefore(started) && now.isBefore(cap));left=minimum(left,Duration.between(now,cap));
        for(var span:spans) {
            long elapsed=Math.subtractExact(tick,span.before());
            long lifetime=Math.subtractExact(Duration.between(span.observed(),span.until()).toNanos(),1_000_000L);
            require(elapsed>=0 && elapsed<lifetime && !now.isBefore(span.observed()) && now.isBefore(span.until()));
            left=minimum(left,Duration.ofNanos(lifetime-elapsed));
        }
        return left;
    }
    Duration ownerRemaining(){return minimum(remaining(),Duration.ofSeconds(5));}
    Instant effectiveUntil(){return minimum(cap,clock.instant().plus(remaining())).truncatedTo(ChronoUnit.MILLIS);}
    R2dbcOperationBudget narrowed(){remaining();return R2dbcOperationBudget.observed(started,effectiveUntil(),startedNanos,
        Duration.ofSeconds(30),clock,nanos);}
    String evidence() {
        require(observations.size()==documents().size());remaining();
        var data=new LinkedHashMap<String,Object>();data.put("encoding","deposit-publication-admission-v1");
        data.put("scope",prepared.requestScope());data.put("productId",prepared.productId());
        data.put("selection",prepared.selection());data.put("selectionFingerprint",prepared.selectionFingerprint());
        data.put("reviewId",prepared.reviewId());data.put("intentDigest",prepared.intentDigest());
        data.put("resources",prepared.resources());data.put("nativePreparation",prepared.nativePreparation());
        data.put("preparedObservedAtMillis",prepared.observedAt().toEpochMilli());
        data.put("preparedEffectiveUntilMillis",prepared.effectiveUntil().toEpochMilli());
        data.put("effectiveUntilMillis",effectiveUntil().toEpochMilli());
        ArrayNode values=DefinitionCanonical.tree(List.of()).deepCopy();
        var exactDocuments=DefinitionCanonical.parse(prepared.canonicalDefinition()).at("/common/requirements/documents");
        for(int i=0;i<observations.size();i++) {
            var value=observations.get(i);ObjectNode row=(ObjectNode)DefinitionCanonical.tree(Map.of("target",value.target()));
            ((ObjectNode)row.get("target")).set("document",exactDocuments.get(i).deepCopy());
            row.put("observedAtMillis",value.observedAt().toEpochMilli());
            if(value.nativeUntil()==null)row.putNull("nativeUntilMillis");else row.put("nativeUntilMillis",value.nativeUntil().toEpochMilli());
            row.put("effectiveUntilMillis",value.effectiveUntil().toEpochMilli());
            row.put("windowEvidence",value.windowEvidence().name());values.add(row);
        }
        data.put("documents",values);return DefinitionCanonical.encode(DefinitionCanonical.tree(data));
    }
    private void checkWindow(Instant observed,Instant until,Duration maximum) {
        require(observed!=null && until!=null && observed.getNano()%1_000_000==0 && until.getNano()%1_000_000==0
            && !observed.isAfter(clock.instant()) && until.isAfter(observed)
            && Duration.between(observed,until).compareTo(maximum)<=0);
    }
    private static Instant minimum(Instant...values){return Arrays.stream(values).min(Instant::compareTo).orElseThrow();}
    private static Duration minimum(Duration a,Duration b){return a.compareTo(b)<0?a:b;}
    private static void require(boolean condition){if(!condition)throw new IllegalStateException("PRODUCT_DEPOSIT_PUBLICATION_UNAVAILABLE");}
}

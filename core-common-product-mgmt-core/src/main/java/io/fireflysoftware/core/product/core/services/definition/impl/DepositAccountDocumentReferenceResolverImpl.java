package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.core.services.definition.DepositAccountDocumentReferenceResolver.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.ExactReference;
import java.time.*;
import java.util.*;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import reactor.core.publisher.Mono;

/** Binds genuine owner observations to one exact product intent without admitting the product. */
public final class DepositAccountDocumentReferenceResolverImpl implements DepositAccountDocumentReferenceResolver {
    private final ProductDocumentIssuerReferenceProvider issuer;
    private final DepositAccountTemplateReferenceProvider templates;
    private final Clock clock;
    private final LongSupplier nanos;
    private record Span(Observation observation,long beforeNanos) {}

    public DepositAccountDocumentReferenceResolverImpl(ProductDocumentIssuerReferenceProvider issuer,
            DepositAccountTemplateReferenceProvider templates,Clock clock,LongSupplier nanos) {
        this.issuer=Objects.requireNonNull(issuer);this.templates=Objects.requireNonNull(templates);
        this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
    }

    @Override public Mono<Observation> verify(Target target,R2dbcOperationBudget originalBudget,Instant inheritedUntil) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(()->Mono.defer(()->{
            validate(target);Objects.requireNonNull(originalBudget);Objects.requireNonNull(inheritedUntil);
            remaining(originalBudget,inheritedUntil,List.of());long beforeIssuer=nanos.getAsLong();
            return Mono.defer(()->issuer.observe(target,originalBudget,inheritedUntil))
                .timeout(remaining(originalBudget,inheritedUntil,List.of())).switchIfEmpty(Mono.error(unavailable()))
                .flatMap(issued->{
                    validate(target,issued);var first=List.of(new Span(issued,beforeIssuer));
                    remaining(originalBudget,inheritedUntil,first);Instant until=earliest(inheritedUntil,issued.effectiveUntil());
                    long beforeTemplates=nanos.getAsLong();
                    return Mono.defer(()->templates.observe(target,originalBudget,until))
                        .timeout(remaining(originalBudget,until,first)).switchIfEmpty(Mono.error(unavailable()))
                        .map(layout->{
                            validate(target,layout);var spans=List.of(first.getFirst(),new Span(layout,beforeTemplates));
                            var left=remaining(originalBudget,until,spans);
                            Instant effective=earliest(earliest(until,layout.effectiveUntil()),clock.instant().plus(left));
                            effective=effective.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
                            Instant observed=issued.observedAt().isAfter(layout.observedAt())?issued.observedAt():layout.observedAt();
                            var result=issued.windowEvidence()==WindowEvidence.EFFECTIVE_ONLY || layout.windowEvidence()==WindowEvidence.EFFECTIVE_ONLY
                                ?Observation.effectiveOnly(target,observed,effective)
                                :new Observation(target,observed,earliest(issued.nativeUntil(),layout.nativeUntil()),effective);
                            remaining(originalBudget,effective,spans);return result;
                        });
                });
        })).onErrorMap(error->unavailable());
    }

    private Duration remaining(R2dbcOperationBudget original,Instant until,List<Span> spans) {
        var result=original.remaining();Instant now=clock.instant();long tick=nanos.getAsLong();
        if(!now.isBefore(until))throw unavailable();result=minimum(result,Duration.between(now,until));
        for(var span:spans) {
            var observation=span.observation();long elapsed=tick-span.beforeNanos();
            long lifetime=Duration.between(observation.observedAt(),observation.effectiveUntil()).toNanos()-1_000_000L;
            if(elapsed<0 || elapsed>=lifetime || !now.isBefore(observation.effectiveUntil()))throw unavailable();
            result=minimum(result,minimum(Duration.ofNanos(lifetime-elapsed),Duration.between(now,observation.effectiveUntil())));
        }
        return result;
    }

    private void validate(Target target,Observation value) {
        if(value==null || !target.equals(value.target()) || value.observedAt()==null || value.windowEvidence()==null
                || value.effectiveUntil()==null || value.observedAt().isAfter(clock.instant())
                || value.observedAt().getNano()%1_000_000!=0
                || value.effectiveUntil().getNano()%1_000_000!=0
                || !value.effectiveUntil().isAfter(value.observedAt()))throw unavailable();
        if(value.windowEvidence()==WindowEvidence.EFFECTIVE_ONLY) {
            if(value.nativeUntil()!=null || Duration.between(value.observedAt(),value.effectiveUntil()).compareTo(Duration.ofSeconds(5))>0)throw unavailable();
        } else if(value.nativeUntil()==null || value.nativeUntil().getNano()%1_000_000!=0
                || !value.nativeUntil().isAfter(value.observedAt()) || value.effectiveUntil().isAfter(value.nativeUntil())
                || Duration.between(value.observedAt(),value.nativeUntil()).compareTo(Duration.ofSeconds(5))>0)throw unavailable();
    }

    private static void validate(Target target) {
        Objects.requireNonNull(target);var scope=Objects.requireNonNull(target.scope());
        for(var id:List.of(scope.tenantId(),scope.rootId(),scope.productId(),scope.revisionId(),scope.publicationId()))
            DefinitionRules.uuid(id.toString(),"/document/scope");
        if(scope.definitionDigest()==null || !scope.definitionDigest().matches("[0-9a-f]{64}")
                || !"DEPOSIT_ACCOUNT_ENROLLMENT".equals(target.purpose()))throw unavailable();
        var document=Objects.requireNonNull(target.document());
        if(document.code()==null || !document.code().matches("[A-Z][A-Z0-9_]{0,63}") || document.documentType()==null
                || !document.documentType().matches("[A-Z][A-Z0-9_]{0,63}") || document.mandatory()==null
                || document.locale()==null || document.locale().isBlank() || document.locale().length()>50)throw unavailable();
        var locale=new Locale.Builder().setLanguageTag(document.locale()).build();
        if(locale.getLanguage().isEmpty() || "und".equals(locale.toLanguageTag()) || !locale.toLanguageTag().equals(document.locale()))throw unavailable();
        reference(document.issuer(),"core-common-config-mgmt","DOCUMENT_ISSUER",false);
        reference(document.template(),"core-common-reference-master-data","DOCUMENT_TEMPLATE",true);
        reference(document.layout(),"core-common-reference-master-data","DOCUMENT_LAYOUT",true);
        if(!document.template().versionText().equals(document.layout().versionText()))throw unavailable();
    }

    private static void reference(ExactReference value,String owner,String kind,boolean opaque) {
        if(value==null || !owner.equals(value.owner()) || !kind.equals(value.kind()) || value.id()==null
                || value.digest()==null
                || !value.digest().matches("[0-9a-f]{64}"))throw unavailable();
        DefinitionRules.uuid(value.id(),"/document/reference");
        if(opaque) {
            if(value.version()!=null || !"OPAQUE".equals(value.versionFormat()) || value.versionText()==null
                    || value.versionText().isBlank())throw unavailable();value.ownerVersion();
        } else if(value.version()==null || value.version()<=0 || value.versionFormat()!=null || value.versionText()!=null)throw unavailable();
    }

    private static Duration minimum(Duration a,Duration b){return a.compareTo(b)<0?a:b;}
    private static Instant earliest(Instant a,Instant b){return a.isBefore(b)?a:b;}
    private static IllegalStateException unavailable(){return new IllegalStateException("DEPOSIT_DOCUMENT_REFERENCES_UNAVAILABLE");}
    @Override public String toString(){return "DepositAccountDocumentReferenceResolver";}
}

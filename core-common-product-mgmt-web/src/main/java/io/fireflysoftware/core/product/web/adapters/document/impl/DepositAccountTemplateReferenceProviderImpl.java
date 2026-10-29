package io.fireflysoftware.core.product.web.adapters.document.impl;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.core.services.definition.DepositAccountDocumentReferenceResolver.*;
import io.fireflysoftware.core.product.core.services.definition.DepositAccountDocumentReferenceResolver.Target;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.ExactReference;
import io.fireflysoftware.common.reference.master.data.sdk.api.DocumentReferenceCurrentControllerApi;
import io.fireflysoftware.common.reference.master.data.sdk.invoker.auth.HttpBearerAuth;
import io.fireflysoftware.common.reference.master.data.sdk.model.DocumentReferenceCurrent;
import io.fireflysoftware.common.reference.master.data.sdk.model.DocumentReferenceCurrentTarget;
import io.fireflysoftware.common.reference.master.data.sdk.model.DocumentReferenceExact;
import io.fireflysoftware.common.reference.master.data.sdk.model.DocumentReferenceProductScope;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import reactor.core.publisher.Mono;

/** Genuine generated Masters client; the owner supplies an effective cap, not a nativeUntil field. */
public final class DepositAccountTemplateReferenceProviderImpl implements DepositAccountTemplateReferenceProvider {
    private final DocumentReferenceCurrentControllerApi masters;
    private final Clock clock;
    private final LongSupplier nanos;

    public DepositAccountTemplateReferenceProviderImpl(DocumentReferenceCurrentControllerApi masters,Clock clock,LongSupplier nanos) {
        this.masters=Objects.requireNonNull(masters);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
    }

    @Override public Mono<Observation> observe(Target target,R2dbcOperationBudget originalBudget,Instant inheritedUntil) {
        return TransactionSynchronizationManager.forCurrentTransaction()
            .map(TransactionSynchronizationManager::isActualTransactionActive)
            .onErrorResume(NoTransactionException.class,error->Mono.just(false))
            .flatMap(active->active?Mono.error(unavailable()):Mono.defer(()->{
                var request=request(target);remaining(originalBudget,inheritedUntil);authenticated();long before=nanos.getAsLong();
                return Mono.defer(()->masters.observeCurrentDocumentReferences(request))
                    .timeout(remaining(originalBudget,inheritedUntil)).switchIfEmpty(Mono.error(unavailable()))
                    .map(value->{
                        validate(request,value);var left=remaining(originalBudget,inheritedUntil);
                        Instant observed=Instant.ofEpochMilli(value.getObservedAtEpochMillis());
                        Instant effective=Instant.ofEpochMilli(value.getEffectiveUntilEpochMillis());
                        long elapsed=nanos.getAsLong()-before;
                        long lifetime=Duration.between(observed,effective).toNanos()-1_000_000L;
                        if(elapsed<0 || elapsed>=lifetime)throw unavailable();
                        Instant now=clock.instant();effective=earliest(effective,earliest(inheritedUntil,
                            earliest(now.plus(left),now.plusNanos(lifetime-elapsed)))).truncatedTo(ChronoUnit.MILLIS);
                        if(!now.isBefore(effective) || !observed.isBefore(effective))throw unavailable();
                        remaining(originalBudget,effective);return Observation.effectiveOnly(target,observed,effective);
                    });
            })).onErrorMap(error->unavailable());
    }

    private void authenticated() {
        if(masters.getApiClient()==null || !(masters.getApiClient().getAuthentication("bearerAuth")instanceof HttpBearerAuth bearer)
                || bearer.getBearerToken()==null || bearer.getBearerToken().isBlank())throw unavailable();
    }

    private static DocumentReferenceCurrentTarget request(Target target) {
        Objects.requireNonNull(target);var scope=Objects.requireNonNull(target.scope());
        for(var id:List.of(scope.tenantId(),scope.rootId(),scope.productId(),scope.revisionId(),scope.publicationId()))
            DefinitionRules.uuid(id.toString(),"/document/scope");
        if(scope.definitionDigest()==null || !scope.definitionDigest().matches("[0-9a-f]{64}")
                || !"DEPOSIT_ACCOUNT_ENROLLMENT".equals(target.purpose()))throw unavailable();
        var doc=Objects.requireNonNull(target.document());
        if(doc.documentType()==null || !doc.documentType().matches("[A-Z][A-Z0-9_]{0,63}")
                || doc.locale()==null || doc.locale().isBlank() || doc.locale().length()>50)throw unavailable();
        var locale=new Locale.Builder().setLanguageTag(doc.locale()).build();
        if(locale.getLanguage().isEmpty() || "und".equals(locale.toLanguageTag()) || !locale.toLanguageTag().equals(doc.locale()))throw unavailable();
        var template=reference(doc.template(),DocumentReferenceExact.KindEnum.DOCUMENT_TEMPLATE);
        var layout=reference(doc.layout(),DocumentReferenceExact.KindEnum.DOCUMENT_LAYOUT);
        if(template.getId().equals(layout.getId()) || !template.getVersionText().equals(layout.getVersionText()))throw unavailable();
        return new DocumentReferenceCurrentTarget().product(new DocumentReferenceProductScope().tenantId(scope.tenantId())
            .rootId(scope.rootId()).productId(scope.productId()).revisionId(scope.revisionId()).publicationId(scope.publicationId()).definitionDigest(scope.definitionDigest()))
            .purpose(DocumentReferenceCurrentTarget.PurposeEnum.DEPOSIT_ACCOUNT_ENROLLMENT).documentType(doc.documentType()).locale(doc.locale()).template(template).layout(layout);
    }

    private static DocumentReferenceExact reference(ExactReference value,DocumentReferenceExact.KindEnum kind) {
        if(value==null || !"core-common-reference-master-data".equals(value.owner()) || !kind.getValue().equals(value.kind())
                || value.id()==null || value.version()!=null || !"OPAQUE".equals(value.versionFormat()) || value.versionText()==null
                || !value.versionText().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,19}") || value.digest()==null
                || !value.digest().matches("[0-9a-f]{64}"))throw unavailable();
        var id=DefinitionRules.uuid(value.id(),"/document/reference");
        return new DocumentReferenceExact().owner(DocumentReferenceExact.OwnerEnum.CORE_COMMON_REFERENCE_MASTER_DATA).kind(kind)
            .id(id).versionFormat(DocumentReferenceExact.VersionFormatEnum.OPAQUE).versionText(value.versionText()).digest(value.digest());
    }

    private void validate(DocumentReferenceCurrentTarget requested,DocumentReferenceCurrent value) {
        if(value==null || !requested.equals(value.getTarget()) || value.getDefinition()==null)throw unavailable();
        long observed=epoch(value.getObservedAtEpochMillis()),until=epoch(value.getEffectiveUntilEpochMillis());
        if(observed>clock.instant().toEpochMilli() || until<=observed || until-observed>5000)throw unavailable();
        var definition=value.getDefinition();var source=definition.getSourceReference();
        if(source==null || definition.getProfileMetadata()==null || definition.getProfileMetadata().getReference()==null
                || definition.getSemanticDefinition()==null || definition.getSemanticDefinition().getLegalNotices()==null
                || definition.getSemanticDefinition().getLegalNotices().isEmpty()
                || !requested.getTemplate().getId().equals(source.getRevisionId())
                || !requested.getTemplate().getVersionText().equals(source.getVersion())
                || !requested.getTemplate().getDigest().equals(source.getSourceDigest()) || !requested.getLocale().equals(source.getLocaleCode()))throw unavailable();
        if(source.getTemplateId()==null)throw unavailable();DefinitionRules.uuid(source.getTemplateId().toString(),"/document/source/templateId");
        String canonical=DefinitionCanonical.encode(DefinitionCanonical.tree(definition));
        if(canonical.getBytes(StandardCharsets.UTF_8).length>32768
                || !requested.getLayout().getDigest().equals(DefinitionCanonical.hash("MASTERS_DOCUMENT_LAYOUT_V1",canonical)))throw unavailable();
    }

    private Duration remaining(R2dbcOperationBudget original,Instant until) {
        Objects.requireNonNull(original);Objects.requireNonNull(until);var left=original.remaining();var now=clock.instant();
        if(!now.isBefore(until))throw unavailable();var wall=Duration.between(now,until);return left.compareTo(wall)<0?left:wall;
    }
    private static long epoch(Long value){if(value==null || value<0 || value>253402300799999L)throw unavailable();return value;}
    private static Instant earliest(Instant a,Instant b){return a.isBefore(b)?a:b;}
    private static IllegalStateException unavailable(){return new IllegalStateException("PRODUCT_DOCUMENT_TEMPLATE_UNAVAILABLE");}
    @Override public String toString(){return "DepositAccountTemplateReferenceProvider";}
}

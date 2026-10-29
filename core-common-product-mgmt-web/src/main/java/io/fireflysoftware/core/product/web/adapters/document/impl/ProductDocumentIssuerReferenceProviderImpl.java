package io.fireflysoftware.core.product.web.adapters.document.impl;

import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import io.fireflysoftware.core.product.core.services.definition.ProductDocumentIssuerReferenceProvider;
import io.fireflysoftware.core.product.core.services.definition.DefinitionRules;
import io.fireflysoftware.core.product.core.services.definition.DepositAccountDocumentReferenceResolver.*;
import io.fireflysoftware.common.config.sdk.api.DocumentIssuerApi;
import io.fireflysoftware.common.config.sdk.model.*;
import java.time.*;
import java.util.*;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import reactor.core.publisher.Mono;

/** Uses the original generated configuration SDK with an explicitly configured authenticated client. */
public final class ProductDocumentIssuerReferenceProviderImpl implements ProductDocumentIssuerReferenceProvider {
    private final DocumentIssuerApi issuers;
    private final Clock clock;
    private final LongSupplier nanos;

    public ProductDocumentIssuerReferenceProviderImpl(DocumentIssuerApi issuers,Clock clock,LongSupplier nanos) {
        this.issuers=Objects.requireNonNull(issuers);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
    }

    @Override public Mono<Observation> observe(Target target,R2dbcOperationBudget originalBudget,Instant inheritedUntil) {
        return TransactionSynchronizationManager.forCurrentTransaction()
            .map(TransactionSynchronizationManager::isActualTransactionActive)
            .onErrorResume(NoTransactionException.class,error->Mono.just(false))
            .flatMap(active->active?Mono.error(unavailable()):Mono.defer(()->{
            var reference=request(target);remaining(originalBudget,inheritedUntil);long before=nanos.getAsLong();
            return Mono.defer(()->issuers.observeCurrentDocumentIssuer(reference.getTenantId(),reference))
                .timeout(remaining(originalBudget,inheritedUntil)).switchIfEmpty(Mono.error(unavailable()))
                .map(value->{
                    validate(reference,value);var originalLeft=remaining(originalBudget,inheritedUntil);
                    Instant observed=Instant.ofEpochMilli(value.getObservedAtEpochMillis());
                    Instant nativeUntil=Instant.ofEpochMilli(value.getNativeUntilEpochMillis());
                    Instant effectiveUntil=Instant.ofEpochMilli(value.getEffectiveUntilEpochMillis());
                    long elapsed=nanos.getAsLong()-before;
                    long lifetime=Duration.between(observed,effectiveUntil).toNanos()-1_000_000L;
                    if(elapsed<0 || elapsed>=lifetime)throw unavailable();
                    Instant now=clock.instant();
                    effectiveUntil=earliest(effectiveUntil,earliest(inheritedUntil,
                        earliest(now.plus(originalLeft),now.plusNanos(lifetime-elapsed))));
                    effectiveUntil=effectiveUntil.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
                    if(!now.isBefore(effectiveUntil) || !observed.isBefore(effectiveUntil))throw unavailable();
                    remaining(originalBudget,effectiveUntil);
                    return new Observation(target,observed,nativeUntil,effectiveUntil);
                });
        })).onErrorMap(error->unavailable());
    }

    private static DocumentIssuerPublicationReference request(Target target) {
        Objects.requireNonNull(target);Objects.requireNonNull(target.scope());Objects.requireNonNull(target.scope().tenantId());
        DefinitionRules.uuid(target.scope().tenantId().toString(),"/document/tenantId");
        if(!"DEPOSIT_ACCOUNT_ENROLLMENT".equals(target.purpose()))throw unavailable();
        var document=Objects.requireNonNull(target.document());var issuer=Objects.requireNonNull(document.issuer());
        if(!"core-common-config-mgmt".equals(issuer.owner()) || !"DOCUMENT_ISSUER".equals(issuer.kind())
                || issuer.id()==null
                || issuer.version()==null || issuer.version()<=0 || issuer.versionFormat()!=null || issuer.versionText()!=null
                || issuer.digest()==null || !issuer.digest().matches("[0-9a-f]{64}") || document.documentType()==null
                || !document.documentType().matches("[A-Z][A-Z0-9_]{0,63}") || document.locale()==null
                || document.locale().isBlank() || document.locale().length()>50)throw unavailable();
        DefinitionRules.uuid(issuer.id(),"/document/issuer/publicationId");
        var locale=new Locale.Builder().setLanguageTag(document.locale()).build();
        if(locale.getLanguage().isEmpty() || "und".equals(locale.toLanguageTag()) || !locale.toLanguageTag().equals(document.locale()))throw unavailable();
        var use=new DocumentIssuerUse().purpose(DocumentIssuerUse.PurposeEnum.DEPOSIT_ACCOUNT_ENROLLMENT)
            .documentType(document.documentType()).locale(document.locale());
        return new DocumentIssuerPublicationReference().tenantId(target.scope().tenantId().toString())
            .publicationId(issuer.id()).revision(issuer.version()).definitionDigest(issuer.digest()).use(use);
    }

    private void validate(DocumentIssuerPublicationReference requested,DocumentIssuerObservation value) {
        if(value==null || value.getReference()==null || value.getDefinition()==null || value.getGeneration()==null
                || value.getGeneration()<2 || value.getSourceGeneration()==null || value.getSourceGeneration()<=0)throw unavailable();
        var actual=value.getReference();var definition=value.getDefinition();
        if(!requested.getTenantId().equals(actual.getTenantId()) || !requested.getPublicationId().equals(actual.getPublicationId())
                || !requested.getRevision().equals(actual.getRevision()) || !requested.getDefinitionDigest().equals(actual.getDefinitionDigest())
                || !requested.getUse().equals(actual.getUse()) || actual.getIssuerId()==null
                || !actual.getIssuerId().equals(definition.getIssuerId()) || !requested.getRevision().equals(definition.getRevision())
                || definition.getUses()==null || !definition.getUses().contains(requested.getUse()) || definition.getSource()==null
                || !requested.getTenantId().equals(definition.getSource().getTenantId())
                || !Boolean.TRUE.equals(definition.getSource().getTenantActive()) || !Boolean.TRUE.equals(definition.getSource().getBrandingActive())
                || !value.getSourceGeneration().equals(definition.getSource().getSourceGeneration()))throw unavailable();
        DefinitionRules.uuid(actual.getIssuerId(),"/document/issuerId");
        long observed=epoch(value.getObservedAtEpochMillis()),nativeUntil=epoch(value.getNativeUntilEpochMillis()),until=epoch(value.getEffectiveUntilEpochMillis());
        if(observed>clock.instant().toEpochMilli() || nativeUntil<=observed || nativeUntil-observed>5000
                || until<=observed || until>nativeUntil || epoch(definition.getEffectiveFromEpochMillis())>observed
                || epoch(definition.getEffectiveUntilEpochMillis())<until)throw unavailable();
    }

    private Duration remaining(R2dbcOperationBudget original,Instant until) {
        Objects.requireNonNull(original);Objects.requireNonNull(until);var left=original.remaining();var now=clock.instant();
        if(!now.isBefore(until))throw unavailable();var absolute=Duration.between(now,until);return left.compareTo(absolute)<0?left:absolute;
    }
    private static long epoch(Long value){if(value==null || value<0 || value>253402300799999L)throw unavailable();return value;}
    private static Instant earliest(Instant a,Instant b){return a.isBefore(b)?a:b;}
    private static IllegalStateException unavailable(){return new IllegalStateException("PRODUCT_DOCUMENT_ISSUER_UNAVAILABLE");}
    @Override public String toString(){return "ProductDocumentIssuerReferenceProvider";}
}

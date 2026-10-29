package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionCommandAuthorization.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleContracts.Selection;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.DepositPublicationPreparationRepository.*;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.DepositPublicationPreparationSnapshot;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.*;
import reactor.core.publisher.Mono;

/**
 * First pass only; PUBLISH remains refused. Owned client budgets do not protect against
 * arbitrary sessions forcing deferred SQL constraints early. Native commit expiry remains open.
 */
public final class PreparedDepositPublicationServiceImpl implements PreparedDepositPublicationService {
    private final ProductDefinitionCommandAuthorization authorization;
    private final ProductDefinitionCommandPersistence persistence;
    private final Clock clock;
    private final LongSupplier nanos;

    public PreparedDepositPublicationServiceImpl(ProductDefinitionCommandAuthorization authorization,
            ProductDefinitionCommandPersistence persistence, Clock clock, LongSupplier nanos) {
        this.authorization=Objects.requireNonNull(authorization);this.persistence=Objects.requireNonNull(persistence);
        this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
        if(authorization.definitionProfile()!=GovernedDefinitionProfile.DEPOSIT_ACCOUNT || !persistence.boundedTransactions())
            throw new IllegalArgumentException("Matching bounded deposit publication preparation required");
    }

    private record Request(String encoding, String canonicalIntent, String intentDigest,
            ProductDefinitionLifecycleContracts.Scope scope, UUID reviewId,
            Integer expectedPublicVersion, Integer expectedCostVersion, Long versionNumber, Resources resources) {}

    @Override public Mono<PreparedDepositPublication> prepare(Parsed instruction, Resources resources,
            R2dbcOperationBudget originalBudget) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(()->Mono.defer(()->{
            Objects.requireNonNull(originalBudget).remaining();
            var request=request(instruction,resources);var scope=request.scope();
            var requested=new Scope(scope.tenantId(),scope.rootId(),scope.operationId(),ProductDefinitionCommand.PUBLISH,request.reviewId());
            return authorization.authorize(requested).switchIfEmpty(Mono.error(unavailable())).flatMap(admission->{
                require(admission!=null && requested.equals(admission.scope()) && admission.principal()!=null
                    && scope.tenantId().toString().equals(admission.principal().tenantId())
                    && admission.principal().issuer()!=null && admission.principal().issuer().matches("https?://.+")
                    && admission.principal().subject()!=null && !admission.principal().subject().isBlank());
                var window=new Window(originalBudget,admission);
                String encoded=DefinitionCanonical.encode(DefinitionCanonical.tree(request));
                String digest=DefinitionCanonical.hash("deposit-publication-preparation-v1",encoded);
                var repo=persistence.publicationPreparation();var failures=new ProductDefinitionBoundedErrors();
                return persistence.executePreparation(originalBudget,root->{
                    root.narrow(window.narrowed());long before=nanos.getAsLong();
                    return failures.capture(repo.open(encoded,admission.principal().issuer(),admission.principal().subject(),
                            admission.actorExpiresAtEpochMillis(),admission.validUntilEpochMillis(),window.requestUntil.toEpochMilli())
                        .switchIfEmpty(Mono.error(unavailable())).flatMap(context->{
                            window.observe(context,digest,before);root.narrow(window.narrowed());
                            return live(repo,context.contextId(),window).then(Mono.defer(()->persistence.publicationPreparationSnapshots()
                                    .findByTenantIdAndRootIdAndReviewIdAndPublicVersionAndCostVersion(scope.tenantId(),scope.rootId(),
                                    request.reviewId(),request.expectedPublicVersion(),request.expectedCostVersion())))
                                .switchIfEmpty(Mono.error(conflict())).map(row->snapshot(request,row,originalBudget,window))
                                .flatMap(value->live(repo,context.contextId(),window).thenReturn(value));
                        }));
                }).onErrorMap(RootTransactionException.class,error->failures.resolve(error,()->originalBudget))
                    .flatMap(value->{window.remaining();return authorization.reauthorize(admission).timeout(window.remaining())
                        .then(Mono.defer(()->{window.remaining();return Mono.just(value);}));});
            }).timeout(originalBudget.remaining());
        })).onErrorMap(ProductDefinitionCommandErrors::safe);
    }

    private static Request request(Parsed parsed, Resources resources) {
        if(parsed==null || parsed.instruction()==null || parsed.instruction().command()!=ProductDefinitionCommand.PUBLISH
                || !(parsed.instruction().body() instanceof PublishBody body) || resources==null)throw unavailable();
        var scope=parsed.instruction().scope();
        var original=DefinitionCanonical.parse(parsed.canonicalIntent());
        var reparsed=ProductDefinitionInstructionCodec.parse(scope.tenantId(),scope.rootId(),ProductDefinitionCommand.PUBLISH,
                DefinitionCanonical.encode(original.get("request")),GovernedDefinitionProfile.DEPOSIT_ACCOUNT);
        require(parsed.equals(reparsed));
        var ids=new HashSet<UUID>();
        for(var id:List.of(resources.actionId(),resources.publicationId(),resources.productVersionId(),resources.historyId(),resources.auditId(),resources.publicEventId())) {
            DefinitionRules.uuid(id.toString(),"/preparation/resources");require(ids.add(id));
        }
        return new Request("deposit-publication-preparation-v1",parsed.canonicalIntent(),parsed.intentDigest(),scope,
                body.reviewId(),body.expectedPublicVersion(),body.expectedCostVersion(),body.versionNumber(),resources);
    }

    private static PreparedDepositPublication snapshot(Request request, DepositPublicationPreparationSnapshot row,
            R2dbcOperationBudget original, Window window) {
        require(row!=null && request.scope().tenantId().equals(row.tenantId()) && request.scope().rootId().equals(row.rootId())
            && request.reviewId().equals(row.reviewId()) && request.expectedPublicVersion().equals(row.publicVersion())
            && request.expectedCostVersion().equals(row.costVersion()) && DefinitionCanonical.ENCODING.equals(row.encodingVersion()));
        for(var id:List.of(row.productId(),row.revisionId(),row.costRevisionId(),row.submissionId()))DefinitionRules.uuid(id.toString(),"/preparation/selection");
        require(row.costDigest()!=null && row.costDigest().matches("[0-9a-f]{64}"));
        var canonical=DefinitionProfiles.canonical(row.canonicalDefinition());
        require(canonical.json().equals(row.canonicalDefinition()) && canonical.digest().equals(row.publicDigest()));
        var definition=DefinitionProfiles.decode(canonical.json());GovernedDefinitionProfile.DEPOSIT_ACCOUNT.require(definition.common());
        var selection=new Selection(row.tenantId(),row.rootId(),row.submissionId(),row.revisionId(),row.publicDigest(),
                row.publicVersion(),row.costRevisionId(),row.costDigest(),row.costVersion());
        String fingerprint=DefinitionCanonical.hash("product-definition-selection-v1",DefinitionCanonical.encode(DefinitionCanonical.tree(selection)));
        require(fingerprint.equals(row.selectionFingerprint()));window.remaining();
        return new PreparedDepositPublication(request.scope(),window.publisher,row.productId(),row.reviewId(),selection,fingerprint,definition,canonical.json(),
                request.intentDigest(),request.versionNumber(),request.resources(),original,window.observedAt,window.cap,
                new PreparedDepositPublication.NativePreparation(window.contextId,window.requestDigest,
                    window.actorUntil,window.permitUntil,window.requestUntil.toEpochMilli()));
    }

    private static Mono<Void> live(DepositPublicationPreparationRepository repo, UUID context, Window window) {
        return Mono.defer(()->{window.remaining();return repo.isCurrent(context).timeout(window.remaining())
            .filter(Boolean.TRUE::equals).switchIfEmpty(Mono.error(unavailable())).then(Mono.fromRunnable(window::remaining));});
    }

    private final class Window {
        final R2dbcOperationBudget original;
        final ProductDefinitionLifecycleContracts.Actor publisher;
        final Instant start,requestUntil;
        final long before;
        final long actorUntil,permitUntil;
        UUID contextId;
        String requestDigest;
        Instant observedAt,cap;
        Window(R2dbcOperationBudget original,Admission admission) {
            this.original=original;publisher=new ProductDefinitionLifecycleContracts.Actor(admission.principal().issuer(),admission.principal().subject());
            actorUntil=admission.actorExpiresAtEpochMillis();permitUntil=admission.validUntilEpochMillis();
            start=clock.instant();before=nanos.getAsLong();
            requestUntil=earliest(start.plus(original.remaining()),start.plusSeconds(30)).truncatedTo(ChronoUnit.MILLIS);
            require(admission.validUntilEpochMillis()<=admission.actorExpiresAtEpochMillis()
                && admission.actorExpiresAtEpochMillis()<=253402300799999L);
            cap=earliest(requestUntil,Instant.ofEpochMilli(admission.validUntilEpochMillis()));remaining();
        }
        Duration remaining() {
            var left=original.remaining();var now=clock.instant();
            long elapsed=nanos.getAsLong()-before;long maximum=Duration.between(start,cap).toNanos();
            require(!now.isBefore(start) && now.isBefore(cap) && elapsed>=0 && elapsed<maximum);
            var wall=Duration.between(now,cap);if(wall.compareTo(left)<0)left=wall;
            var monotonic=Duration.ofNanos(maximum-elapsed);return monotonic.compareTo(left)<0?monotonic:left;
        }
        R2dbcOperationBudget narrowed() {
            remaining();return R2dbcOperationBudget.observed(start,cap,before,Duration.ofSeconds(30),clock,nanos);
        }
        void observe(NativeContext value,String digest,long callBefore) {
            remaining();require(value!=null && value.contextId()!=null && value.observedAtMillis()!=null
                && value.effectiveUntilMillis()!=null && digest.equals(value.requestDigest()));
            DefinitionRules.uuid(value.contextId().toString(),"/preparation/context");
            long observed=value.observedAtMillis(),until=value.effectiveUntilMillis();
            require(observed>=0 && until>observed && until-observed<=30000 && until<=cap.toEpochMilli());
            observedAt=Instant.ofEpochMilli(observed);require(!observedAt.isAfter(clock.instant()));
            long lifetime=Math.multiplyExact(until-observed-1,1_000_000L);require(lifetime>0);
            long offset=Math.subtractExact(callBefore,before);require(offset>=0);
            cap=earliest(cap,earliest(Instant.ofEpochMilli(until),start.plusNanos(Math.addExact(offset,lifetime))))
                .truncatedTo(ChronoUnit.MILLIS);remaining();
            contextId=value.contextId();requestDigest=value.requestDigest();
        }
    }
    private static Instant earliest(Instant a,Instant b){return a.isBefore(b)?a:b;}
    private static void require(boolean condition){if(!condition)throw unavailable();}
    private static RuntimeException unavailable(){return new IllegalStateException("PRODUCT_DEPOSIT_PREPARATION_UNAVAILABLE");}
    private static RuntimeException conflict(){return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Product approved selection changed");}
    @Override public String toString(){return "PreparedDepositPublicationService";}
}

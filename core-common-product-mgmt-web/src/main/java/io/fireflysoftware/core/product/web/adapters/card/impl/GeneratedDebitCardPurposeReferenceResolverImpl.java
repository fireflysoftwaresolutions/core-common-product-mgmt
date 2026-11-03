/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.product.web.adapters.card.impl;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DebitCardPurposeContracts.Configuration;
import io.fireflysoftware.core.product.web.config.definition.lifecycle.DebitCardReferenceProperties;
import io.fireflysoftware.core.product.web.config.definition.lifecycle.DebitCardReferenceProperties.*;
import io.fireflysoftware.common.reference.master.data.sdk.api.*;
import io.fireflysoftware.common.reference.master.data.sdk.model.DocumentReferenceCurrentTarget;
import io.fireflysoftware.common.reference.master.data.sdk.model.DocumentReferenceProductScope;
import io.fireflysoftware.common.reference.master.data.sdk.model.DocumentReferenceExact;
import io.fireflysoftware.common.config.sdk.api.DocumentIssuerApi;
import io.fireflysoftware.common.config.sdk.model.DocumentIssuerUse;
import io.fireflysoftware.common.config.sdk.model.DocumentIssuerPublicationReference;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.*;
import static io.fireflysoftware.core.product.web.adapters.card.impl.DebitCardOwnerObservationsImpl.*;

/** Real typed current owners; static routing selectors cannot substitute for any owner observation. */
public final class GeneratedDebitCardPurposeReferenceResolverImpl implements DebitCardPurposeReferenceResolver {
    private static final String PURPOSE="CARD_ENROLLMENT";
    private final SecurityContextPort principals;
    private final DebitCardOwnerClientsImpl clients;
    private final CurrencyPolicyCurrentControllerApi currencies;
    private final DocumentReferenceCurrentControllerApi templates;
    private final DocumentIssuerApi issuers;
    private final Map<CurrencyPolicyReference,CurrencySelection> currencyRoutes;
    private final Clock clock;
    private final LongSupplier nanos;
    private record Lookup(String key,Supplier<Mono<Stamp>> call){}

    public GeneratedDebitCardPurposeReferenceResolverImpl(SecurityContextPort principals,ReactiveOAuth2AuthorizedClientManager manager,
            DebitCardReferenceProperties properties,Clock clock,LongSupplier nanos){this(principals,manager,properties,clock,nanos,null,null);}
    GeneratedDebitCardPurposeReferenceResolverImpl(SecurityContextPort principals,ReactiveOAuth2AuthorizedClientManager manager,
            DebitCardReferenceProperties properties,Clock clock,LongSupplier nanos,ExchangeFunction mastersExchange,ExchangeFunction configurationExchange){
        this.principals=Objects.requireNonNull(principals);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);Objects.requireNonNull(properties);
        if(!properties.enabled()||properties.currencies().isEmpty()||properties.currencies().size()>256)throw invalid();
        clients=new DebitCardOwnerClientsImpl(principals,manager,clock);var currencyMap=new HashMap<CurrencyPolicyReference,CurrencySelection>();
        for(var route:properties.currencies()){if(route==null||route.reference()==null||route.postedScale()==null||route.postedScale()<0||route.postedScale()>10||currencyMap.put(route.reference(),route)!=null)throw invalid();var reference=route.reference();DefinitionRules.uuid(reference.policyId(),"/currency/policyId");DefinitionRules.uuid(reference.currencyId(),"/currency/currencyId");if(reference.isoCode()==null||!reference.isoCode().matches("[A-Z]{3}")||reference.version()==null||reference.version()<1||reference.digest()==null||!reference.digest().matches("[0-9a-f]{64}")||!"MASTERS_CURRENCY_POLICY_V1".equals(reference.encodingVersion()))throw invalid();}
        currencyRoutes=Map.copyOf(currencyMap);
        var mf=io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient.createDefaultDateFormat();var mm=io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient.createDefaultMapper(mf);
        var masters=clients.transport(io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient.buildWebClientBuilder(mm),properties.mastersEndpoint(),properties.mastersRegistrationId(),mastersExchange);
        var mc=new io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient(masters,mm,mf).setBasePath(DebitCardOwnerClientsImpl.origin(properties.mastersEndpoint()).toASCIIString());
        currencies=new CurrencyPolicyCurrentControllerApi(mc);templates=new DocumentReferenceCurrentControllerApi(mc);
        var cf=io.fireflysoftware.common.config.sdk.invoker.ApiClient.createDefaultDateFormat();var cm=io.fireflysoftware.common.config.sdk.invoker.ApiClient.createDefaultMapper(cf);
        var configuration=clients.transport(io.fireflysoftware.common.config.sdk.invoker.ApiClient.buildWebClientBuilder(cm),properties.configurationEndpoint(),properties.configurationRegistrationId(),configurationExchange);
        issuers=new DocumentIssuerApi(new io.fireflysoftware.common.config.sdk.invoker.ApiClient(configuration,cm,cf).setBasePath(DebitCardOwnerClientsImpl.origin(properties.configurationEndpoint()).toASCIIString()));
    }
    @Override public Mono<VerifiedReferences> verify(Configuration configuration){
        return TransactionSynchronizationManager.forCurrentTransaction().map(TransactionSynchronizationManager::isActualTransactionActive)
            .onErrorResume(NoTransactionException.class,error->Mono.just(false)).flatMap(active->active?Mono.error(unavailable()):Mono.defer(()->{
                String digest=DebitCardPurposeCodec.digest(configuration);Instant started=clock.instant();long before=nanos.getAsLong();
                return Mono.defer(principals::currentPrincipal).switchIfEmpty(Mono.error(unavailable())).flatMap(caller->{
                    var expiry=caller.claim("exp",Instant.class);var nbf=caller.claim("nbf",Instant.class);
                    if(caller.issuer()==null||caller.issuer().isBlank()||caller.subject()==null||caller.subject().isBlank()||caller.tenantId()==null||caller.tenantId().isBlank()
                            ||!caller.hasScope("product:definitions:debit-cards:read")||expiry==null||nbf==null||!clock.instant().isBefore(expiry)||clock.instant().isBefore(nbf)||!nbf.isBefore(expiry))throw unavailable();
                    var window=new Window(started,before,expiry);var selected=currencyRoutes.get(configuration.definition().common().market().currencyPolicy());if(selected==null)throw unavailable();
                    var references=references(configuration);var lookups=lookups(configuration,selected,window);var first=new HashMap<String,String>();
                    Mono<Void> initial=Flux.fromIterable(lookups).concatMap(lookup->observe(caller,lookup,window).doOnNext(stamp->{if(first.put(lookup.key(),stamp.exactAuthority())!=null)throw unavailable();})).then();
                    Mono<Void> recheck=Mono.defer(()->Flux.fromIterable(lookups).concatMap(lookup->observe(caller,lookup,window).doOnNext(stamp->{if(!stamp.exactAuthority().equals(first.get(lookup.key())))throw unavailable();})).then());
                    return clients.within(caller,window::remaining,()->initial.then(recheck).then(requireCaller(caller,window)).then(Mono.fromSupplier(()->new VerifiedReferences(configuration.scope(),digest,selected.reference(),references,clock.instant().truncatedTo(ChronoUnit.MICROS),window.until()))));
                }).timeout(Duration.ofSeconds(5));
            })).onErrorMap(error->unavailable());
    }
    private List<Lookup> lookups(Configuration configuration,CurrencySelection selected,Window window){
        var result=new ArrayList<Lookup>();var currency=selected.reference();
        result.add(new Lookup("currency",()->Mono.defer(()->currencies.observeCurrentCurrencyExecutionPolicy(currency.policyId(),currency.currencyId(),currency.isoCode(),currency.version(),currency.digest(),currency.encodingVersion(),selected.postedScale())).map(value->currency(selected,value,window.started,clock.instant()))));
        for(var document:configuration.definition().common().requirements().documents()){
            var issuer=issuer(configuration,document);var template=template(configuration,document);
            result.add(new Lookup("issuer/"+document.code(),()->Mono.defer(()->issuers.observeCurrentDocumentIssuer(issuer.getTenantId(),issuer)).map(value->DebitCardOwnerObservationsImpl.issuer(issuer,value,window.started,clock.instant()))));
            result.add(new Lookup("template/"+document.code(),()->Mono.defer(()->templates.observeCurrentDocumentReferences(template)).map(value->DebitCardOwnerObservationsImpl.template(template,value,window.started,clock.instant()))));
        }
        return List.copyOf(result);
    }
    private Mono<Stamp> observe(SecurityPrincipal caller,Lookup lookup,Window window){return Mono.defer(()->{window.remaining();return requireCaller(caller,window).then(Mono.defer(lookup.call())).switchIfEmpty(Mono.error(unavailable())).flatMap(stamp->requireCaller(caller,window).then(Mono.fromSupplier(()->{window.narrow(stamp.until());return stamp;})));}).timeout(window.remaining());}
    private Mono<Void> requireCaller(SecurityPrincipal caller,Window window){return Mono.defer(principals::currentPrincipal).switchIfEmpty(Mono.error(unavailable())).flatMap(current->{window.remaining();if(!caller.equals(current))throw unavailable();return Mono.<Void>empty();}).timeout(window.remaining());}
    private static Set<ExactReference> references(Configuration c){var result=new HashSet<ExactReference>();for(var document:c.definition().common().requirements().documents()){result.add(document.template());result.add(document.issuer());result.add(document.layout());}if(result.size()>1024)throw unavailable();return Set.copyOf(result);}
    private static DocumentIssuerPublicationReference issuer(Configuration configuration,DocumentRequirement document){var reference=document.issuer();numeric(reference,Set.of("DOCUMENT_ISSUER"),"core-common-config-mgmt");return new DocumentIssuerPublicationReference().tenantId(configuration.scope().tenantId().toString()).publicationId(reference.id()).revision(reference.version()).definitionDigest(reference.digest()).use(new DocumentIssuerUse().purpose(DocumentIssuerUse.PurposeEnum.valueOf(PURPOSE)).documentType(document.documentType()).locale(document.locale()));}
    private static DocumentReferenceCurrentTarget template(Configuration configuration,DocumentRequirement document){var scope=configuration.scope();var template=opaque(document.template(),DocumentReferenceExact.KindEnum.DOCUMENT_TEMPLATE);var layout=opaque(document.layout(),DocumentReferenceExact.KindEnum.DOCUMENT_LAYOUT);if(template.getId().equals(layout.getId())||!template.getVersionText().equals(layout.getVersionText()))throw unavailable();return new DocumentReferenceCurrentTarget().product(new DocumentReferenceProductScope().tenantId(scope.tenantId()).rootId(scope.rootId()).productId(scope.productId()).revisionId(scope.revisionId()).publicationId(scope.publicationId()).definitionDigest(configuration.publicDigest())).purpose(DocumentReferenceCurrentTarget.PurposeEnum.valueOf(PURPOSE)).documentType(document.documentType()).locale(document.locale()).template(template).layout(layout);}
    private static DocumentReferenceExact opaque(ExactReference reference,DocumentReferenceExact.KindEnum kind){if(reference==null||!"core-common-reference-master-data".equals(reference.owner())||!kind.getValue().equals(reference.kind())||reference.version()!=null||!"OPAQUE".equals(reference.versionFormat())||reference.versionText()==null||!reference.versionText().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,19}")||reference.digest()==null||!reference.digest().matches("[0-9a-f]{64}"))throw unavailable();return new DocumentReferenceExact().owner(DocumentReferenceExact.OwnerEnum.CORE_COMMON_REFERENCE_MASTER_DATA).kind(kind).id(DefinitionRules.uuid(reference.id(),"/document/reference")).versionFormat(DocumentReferenceExact.VersionFormatEnum.OPAQUE).versionText(reference.versionText()).digest(reference.digest());}
    private static void numeric(ExactReference reference,Set<String> kinds,String owner){if(reference==null||!owner.equals(reference.owner())||!kinds.contains(reference.kind())||!(reference.ownerVersion()instanceof NumericOwnerVersion)||reference.digest()==null||!reference.digest().matches("[0-9a-f]{64}"))throw unavailable();DefinitionRules.uuid(reference.id(),"/debit-card/reference");}
    private final class Window{
        private final Instant started;private final long before;private Instant cap;private long monotonicCap;
        Window(Instant started,long before,Instant expiry){this.started=started;this.before=before;cap=expiry.isBefore(started.plusSeconds(5))?expiry:started.plusSeconds(5);monotonicCap=Duration.between(started,cap).toNanos();remaining();}
        Duration remaining(){var now=clock.instant();long elapsed=nanos.getAsLong()-before;if(now.isBefore(started)||!now.isBefore(cap)||elapsed<0||elapsed>=monotonicCap)throw unavailable();var wall=Duration.between(now,cap);var monotonic=Duration.ofNanos(monotonicCap-elapsed);return wall.compareTo(monotonic)<0?wall:monotonic;}
        void narrow(Instant until){remaining();if(until.isBefore(cap)){var now=clock.instant();long elapsed=nanos.getAsLong()-before;monotonicCap=Math.min(monotonicCap,Math.addExact(elapsed,Duration.between(now,until).toNanos()));cap=until;}remaining();}
        Instant until(){var now=clock.instant();var remaining=remaining();return (cap.isBefore(now.plus(remaining))?cap:now.plus(remaining)).truncatedTo(ChronoUnit.MICROS);}
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Explicit current debit reference routing required");}
    private static IllegalStateException unavailable(){return new IllegalStateException("DEBIT_CARD_REFERENCES_UNAVAILABLE");}
    @Override public String toString(){return "GeneratedDebitCardPurposeReferenceResolverImpl";}
}

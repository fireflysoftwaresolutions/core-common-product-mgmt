package io.fireflysoftware.core.product.web.adapters.document.impl;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.core.services.definition.DepositAccountDocumentReferenceResolver.*;
import io.fireflysoftware.core.product.core.services.definition.impl.DepositAccountDocumentReferenceResolverImpl;
import io.fireflysoftware.core.product.web.adapters.document.ProductDocumentOwnerTokenProvider;
import io.fireflysoftware.core.product.web.adapters.document.ProductDocumentOwnerTokenProvider.*;
import java.net.URI;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/** Constructs isolated genuine clients from required verified delegation; it grants no publication authority. */
public final class SdkDepositAccountDocumentReferenceResolver implements DepositAccountDocumentReferenceResolver {
    private static final Duration LIMIT=Duration.ofSeconds(5);
    private final ProductDocumentOwnerTokenProvider tokens;
    private final URI configuration,masters;
    private final String configurationIssuer,mastersIssuer;
    private final Clock clock;
    private final LongSupplier nanos;
    private final ExchangeFunction controlledExchange;

    public SdkDepositAccountDocumentReferenceResolver(ProductDocumentOwnerTokenProvider tokens,URI configuration,URI masters,
            String configurationIssuer,String mastersIssuer,Clock clock,LongSupplier nanos) {
        this(tokens,configuration,masters,configurationIssuer,mastersIssuer,clock,nanos,null);
    }
    SdkDepositAccountDocumentReferenceResolver(ProductDocumentOwnerTokenProvider tokens,URI configuration,URI masters,
            String configurationIssuer,String mastersIssuer,Clock clock,LongSupplier nanos,ExchangeFunction controlledExchange) {
        this.tokens=Objects.requireNonNull(tokens);this.configuration=origin(configuration);this.masters=origin(masters);
        this.configurationIssuer=issuer(configurationIssuer);this.mastersIssuer=issuer(mastersIssuer);
        this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);this.controlledExchange=controlledExchange;
    }

    @Override public Mono<Observation> verify(Target target,R2dbcOperationBudget original,Instant inheritedUntil) {
        return Mono.defer(()->{
            var window=new Window(original,inheritedUntil);
            ProductDocumentIssuerReferenceProvider issuer=(t,b,u)->observe(Owner.CONFIGURATION,t,b,u,window);
            DepositAccountTemplateReferenceProvider template=(t,b,u)->observe(Owner.MASTERS,t,b,u,window);
            return new DepositAccountDocumentReferenceResolverImpl(issuer,template,clock,nanos)
                .verify(target,original,window.until()).timeout(window.remaining()).map(value->trim(value,window));
        }).onErrorMap(error->unavailable());
    }

    private Mono<Observation> observe(Owner owner,Target target,R2dbcOperationBudget original,Instant inherited,Window window) {
        return Mono.defer(()->{
            window.remaining();
            return Mono.defer(()->tokens.authorize(owner,target,original,earliest(inherited,window.until())))
                .switchIfEmpty(Mono.error(unavailable())).timeout(window.remaining()).flatMap(token->{
                    String expected=owner==Owner.CONFIGURATION?configurationIssuer:mastersIssuer;
                    if(token.owner()!=owner||!target.equals(token.target())||!expected.equals(token.issuer())
                            ||!target.scope().tenantId().equals(token.resourceTenant())||!token.until().isAfter(clock.instant())
                            ||token.until().isAfter(Instant.ofEpochMilli(253402300799999L)))throw unavailable();
                    window.narrow(token.until());Instant cap=earliest(inherited,window.until());
                    Mono<Observation> observation;
                    if(owner==Owner.CONFIGURATION) {
                        var format=io.fireflysoftware.common.config.sdk.invoker.ApiClient.createDefaultDateFormat();
                        var mapper=io.fireflysoftware.common.config.sdk.invoker.ApiClient.createDefaultMapper(format);
                        var web=transport(io.fireflysoftware.common.config.sdk.invoker.ApiClient.buildWebClientBuilder(mapper),configuration);
                        var client=new io.fireflysoftware.common.config.sdk.invoker.ApiClient(web,mapper,format);
                        client.setBasePath(configuration.toString());
                        if(!(client.getAuthentication("documentIssuerBearer")instanceof io.fireflysoftware.common.config.sdk.invoker.auth.HttpBearerAuth bearer))throw unavailable();
                        bearer.setBearerToken(token.token());
                        observation=new ProductDocumentIssuerReferenceProviderImpl(new io.fireflysoftware.common.config.sdk.api.DocumentIssuerApi(client),clock,nanos).observe(target,original,cap);
                    } else {
                        var format=io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient.createDefaultDateFormat();
                        var mapper=io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient.createDefaultMapper(format);
                        var web=transport(io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient.buildWebClientBuilder(mapper),masters);
                        var client=new io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient(web,mapper,format);
                        client.setBasePath(masters.toString());
                        if(!(client.getAuthentication("bearerAuth")instanceof io.fireflysoftware.common.reference.master.data.sdk.invoker.auth.HttpBearerAuth bearer))throw unavailable();
                        bearer.setBearerToken(token.token());
                        observation=new DepositAccountTemplateReferenceProviderImpl(new io.fireflysoftware.common.reference.master.data.sdk.api.DocumentReferenceCurrentControllerApi(client),clock,nanos).observe(target,original,cap);
                    }
                    return observation.timeout(window.remaining()).map(value->trim(value,window));
                });
        });
    }

    private WebClient transport(WebClient.Builder builder,URI origin) {
        if(controlledExchange==null)builder.clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false).responseTimeout(LIMIT)));
        else builder.exchangeFunction(controlledExchange);
        return builder.filter((request,next)->{
            URI url=request.url();
            if(!"https".equals(url.getScheme())||url.getHost()==null||!origin.getHost().equalsIgnoreCase(url.getHost())
                    ||port(origin)!=port(url)||url.getRawUserInfo()!=null) return Mono.error(unavailable());
            return next.exchange(request).flatMap(response->response.statusCode().is3xxRedirection()
                ?response.releaseBody().then(Mono.error(unavailable())):Mono.just(response));
        }).build();
    }
    private Observation trim(Observation value,Window window) {
        window.remaining();window.narrow(value.effectiveUntil());Instant until=earliest(value.effectiveUntil(),window.until());
        if(!until.isAfter(value.observedAt()))throw unavailable();
        return value.windowEvidence()==WindowEvidence.EFFECTIVE_ONLY?Observation.effectiveOnly(value.target(),value.observedAt(),until)
            :new Observation(value.target(),value.observedAt(),value.nativeUntil(),until);
    }
    private final class Window {
        private final R2dbcOperationBudget original;
        private final Instant started;
        private final long before;
        private Instant cap;
        Window(R2dbcOperationBudget original,Instant inherited) {
            this.original=Objects.requireNonNull(original);started=clock.instant();before=nanos.getAsLong();
            cap=earliest(Objects.requireNonNull(inherited),started.plus(LIMIT));remaining();
        }
        Duration remaining() {
            var left=original.remaining();Instant now=clock.instant();long elapsed=Math.subtractExact(nanos.getAsLong(),before);
            if(now.isBefore(started)||!now.isBefore(cap)||elapsed<0||elapsed>=LIMIT.toNanos())throw unavailable();
            return minimum(left,minimum(Duration.between(now,cap),Duration.ofNanos(LIMIT.toNanos()-elapsed)));
        }
        Instant until(){Instant now=clock.instant();return earliest(cap,now.plus(remaining())).truncatedTo(ChronoUnit.MILLIS);}
        void narrow(Instant until){cap=earliest(cap,Objects.requireNonNull(until));remaining();}
    }
    private static URI origin(URI value) {
        if(value==null||!"https".equals(value.getScheme())||value.getHost()==null||value.getRawUserInfo()!=null
                ||value.getRawQuery()!=null||value.getRawFragment()!=null||!Set.of("","/").contains(value.getRawPath()))
            throw new IllegalArgumentException("Exact trusted HTTPS owner origin required");
        return URI.create(value.toASCIIString().replaceAll("/$",""));
    }
    private static String issuer(String value){if(value==null||value.isBlank()||value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Trusted owner issuer required");return value;}
    private static int port(URI value){return value.getPort()<0?443:value.getPort();}
    private static Instant earliest(Instant a,Instant b){return a.isBefore(b)?a:b;}
    private static Duration minimum(Duration a,Duration b){return a.compareTo(b)<0?a:b;}
    private static IllegalStateException unavailable(){return new IllegalStateException("PRODUCT_DOCUMENT_OWNER_UNAVAILABLE");}
    @Override public String toString(){return "SdkDepositAccountDocumentReferenceResolver";}
}

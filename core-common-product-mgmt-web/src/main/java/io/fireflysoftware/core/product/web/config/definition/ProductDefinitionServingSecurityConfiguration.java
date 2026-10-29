package io.fireflysoftware.core.product.web.config.definition;

import org.fireflyframework.security.adapter.opa.OpaPolicyDecisionAdapter;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.rs.ResourceServerAutoConfiguration;
import org.fireflyframework.security.rs.ResourceServerProperties;
import org.fireflyframework.security.spi.KeyManagementPort;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    ProductDefinitionServingProperties.class,
    ResourceServerProperties.class
})
public class ProductDefinitionServingSecurityConfiguration {
    @Bean
    @Order(0)
    public SecurityWebFilterChain productDefinitionReadSecurity(
            ServerHttpSecurity http,
            @Qualifier("productDefinitionJwtDecoder") ReactiveJwtDecoder decoder,
            @Qualifier("fireflyJwtAuthenticationConverter")
                    Converter<Jwt, Mono<AbstractAuthenticationToken>> converter,
            ResourceServerProperties trust,
            ProductDefinitionServingProperties serving) {
        stateless(http)
                .securityMatcher(
                        ServerWebExchangeMatchers.pathMatchers(
                                "/api/v1/tenants/*/product-definitions/**"))
                .addFilterAt(
                        (exchange, chain) -> {
                            exchange.getResponse()
                                    .getHeaders()
                                    .setCacheControl("no-store, private");
                            exchange.getResponse().getHeaders().add("Vary", "Authorization");
                            if (!configured(trust, serving)) {
                                exchange.getResponse()
                                        .setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                                return exchange.getResponse().setComplete();
                            }
                            return chain.filter(exchange);
                        },
                        SecurityWebFiltersOrder.FIRST)
                .authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
                .oauth2ResourceServer(
                        oauth ->
                                oauth.jwt(
                                        jwt ->
                                                jwt.jwtDecoder(decoder)
                                                        .jwtAuthenticationConverter(converter)));
        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityWebFilterChain productLegacySecurity(ServerHttpSecurity http) {
        // Existing legacy routes were public before this bounded serving boundary.
        return stateless(http)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }

    @Bean
    public ReactiveJwtDecoder productDefinitionJwtDecoder(
            ResourceServerProperties trust, KeyManagementPort keys) {
        if (!trustedSigning(trust))
            return token ->
                    Mono.error(
                            new JwtException("Current product signing configuration unavailable"));
        // A nonempty trusted JWKS URI is mandatory; the shared factory cannot select its local-key
        // fallback.
        return new ResourceServerAutoConfiguration().fireflyReactiveJwtDecoder(keys, trust);
    }

    @Bean("productDefinitionReadPolicy")
    public PolicyDecisionPort productDefinitionReadPolicy(
            ProductDefinitionServingProperties serving) {
        if (!policyConfigured(serving))
            return (principal, action, resource, context) ->
                    Mono.just(Decision.indeterminate("Current product policy unavailable"));
        var client =
                WebClient.builder()
                        .baseUrl(serving.policyBaseUrl())
                        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(65536))
                        .build();
        return new OpaPolicyDecisionAdapter(client, serving.policyPath());
    }

    private static ServerHttpSecurity stateless(ServerHttpSecurity http) {
        return http.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable);
    }

    static boolean configured(
            ResourceServerProperties trust, ProductDefinitionServingProperties serving) {
        return trustedSigning(trust)
                && policyConfigured(serving)
                && trust.isTenantRequired()
                && trust.getTenantClaimPaths() != null
                && !trust.getTenantClaimPaths().isEmpty()
                && trust.getScopeClaimPaths() != null
                && !trust.getScopeClaimPaths().isEmpty();
    }

    private static boolean trustedSigning(ResourceServerProperties trust) {
        return trust != null
                && endpoint(trust.getIssuer())
                && endpoint(trust.getJwkSetUri())
                && trust.getAudiences() != null
                && !trust.getAudiences().isEmpty()
                && trust.getAudiences().stream()
                        .allMatch(value -> value != null && !value.isBlank());
    }

    private static boolean policyConfigured(ProductDefinitionServingProperties serving) {
        return serving != null
                && endpoint(serving.policyBaseUrl())
                && serving.policyPath() != null
                && serving.policyPath().matches("[A-Za-z0-9_]+(?:/[A-Za-z0-9_]+)*");
    }

    private static boolean endpoint(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            var uri = URI.create(value);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}

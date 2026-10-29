package io.fireflysoftware.core.product.web.config.definition;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionCommandAuthorization;
import io.fireflysoftware.core.product.web.config.definition.lifecycle.ProductDefinitionCommandProperties;

import org.fireflyframework.security.adapter.opa.OpaPolicyDecisionAdapter;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.rs.ResourceServerProperties;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "product.definitions.commands",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties({
    ProductDefinitionCommandProperties.class,
    ResourceServerProperties.class
})
public class ProductDefinitionCommandSecurityConfiguration {
    @Bean
    @Order(-1)
    public SecurityWebFilterChain productDefinitionCommandSecurity(
            ServerHttpSecurity http,
            @Qualifier("productDefinitionJwtDecoder") ReactiveJwtDecoder decoder,
            @Qualifier("fireflyJwtAuthenticationConverter")
                    Converter<Jwt, Mono<AbstractAuthenticationToken>> converter,
            ResourceServerProperties trust,
            ProductDefinitionCommandProperties commands) {
        return http.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityMatcher(
                        ServerWebExchangeMatchers.pathMatchers(
                                "/api/v1/tenants/*/product-definitions/*/commands/**",
                                "/api/v1/tenants/*/product-definitions/*/staff-review/**",
                                "/api/v1/tenants/*/product-definitions/*/staff-cost/**",
                                "/api/v1/tenants/*/product-definitions/*/staff-publication/**",
                                "/api/v1/tenants/*/product-definitions/*/insurance-quote-configurations/**"))
                .addFilterAt(
                        (exchange, chain) -> {
                            exchange.getResponse()
                                    .getHeaders()
                                    .setCacheControl("no-store, private");
                            exchange.getResponse().getHeaders().add("Vary", "Authorization");
                            if (!ProductDefinitionCommandAuthorization.POLICY_PROFILE.equals(
                                            commands.getPolicyProfile())
                                    || !ProductDefinitionServingSecurityConfiguration.configured(
                                            trust, policySettings(commands))) {
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
                                                        .jwtAuthenticationConverter(converter)))
                .build();
    }

    @Bean("productDefinitionCommandPolicy")
    public PolicyDecisionPort productDefinitionCommandPolicy(
            ProductDefinitionCommandProperties commands, ResourceServerProperties trust) {
        if (!ProductDefinitionCommandAuthorization.POLICY_PROFILE.equals(
                        commands.getPolicyProfile())
                || !ProductDefinitionServingSecurityConfiguration.configured(
                        trust, policySettings(commands)))
            return (principal, action, resource, context) ->
                    Mono.just(Decision.indeterminate("Current product staff policy unavailable"));
        var client =
                WebClient.builder()
                        .baseUrl(commands.getPolicyBaseUrl())
                        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(65536))
                        .build();
        return new OpaPolicyDecisionAdapter(client, commands.getPolicyPath());
    }

    private static ProductDefinitionServingProperties policySettings(
            ProductDefinitionCommandProperties commands) {
        return new ProductDefinitionServingProperties(
                commands.getPolicyBaseUrl(), commands.getPolicyPath());
    }
}

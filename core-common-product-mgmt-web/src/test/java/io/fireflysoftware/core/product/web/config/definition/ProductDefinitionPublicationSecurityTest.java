package io.fireflysoftware.core.product.web.config.definition;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionCommandAuthorization;
import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionPublicationReadAuthorization;
import io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionCommandAuthorizationImpl;
import io.fireflysoftware.core.product.web.config.definition.lifecycle.ProductDefinitionCommandProperties;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.core.authority.*;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.fireflyframework.security.rs.*;
import org.fireflyframework.security.webflux.context.ReactorSecurityContextAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class ProductDefinitionPublicationSecurityTest {
    final UUID tenant = UUID.randomUUID(), root = UUID.randomUUID();
    final Instant now = Instant.parse("2026-10-25T00:00:00Z");
    final String issuer = "https://staff.example.invalid";
    final AtomicInteger policies = new AtomicInteger(), decodes = new AtomicInteger();
    boolean permit = true;

    String path(String suffix) {
        return "/api/v1/tenants/"
                + tenant
                + "/product-definitions/"
                + root
                + "/staff-publication/"
                + suffix;
    }

    WebTestClient client() {
        var commands = new ProductDefinitionCommandProperties();
        commands.setEnabled(true);
        commands.setPolicyProfile(ProductDefinitionCommandAuthorization.POLICY_PROFILE);
        commands.setPolicyBaseUrl("https://policy.example.invalid");
        commands.setPolicyPath("product_staff/allow");
        var trust = new ResourceServerProperties();
        trust.setIssuer(issuer);
        trust.setAudiences(List.of("product-owner"));
        trust.setJwkSetUri(issuer + "/jwks");
        trust.setTenantRequired(true);
        trust.setTenantClaimPaths(List.of("tenant_id"));
        trust.setScopeClaimPaths(List.of("scope"));
        var principalFactory =
                new PrincipalFactory(
                        new ConfigurableAuthorityMapper(
                                new AuthorityMappingProperties(List.of(), List.of("scope"), "")));
        var converter =
                new ResourceServerAutoConfiguration()
                        .fireflyJwtAuthenticationConverter(principalFactory, trust);
        ReactiveJwtDecoder decoder =
                value ->
                        Mono.fromSupplier(
                                () -> {
                                    decodes.incrementAndGet();
                                    return Jwt.withTokenValue(value)
                                            .header("alg", "RS256")
                                            .issuer(issuer)
                                            .subject("independently-resumed-maker")
                                            .audience(List.of("product-owner"))
                                            .expiresAt(now.plusSeconds(300))
                                            .claim("tenant_id", tenant.toString())
                                            .claim(
                                                    "scope",
                                                    "product:definitions:publish"
                                                        + " product:definitions:withdraw")
                                            .build();
                                });
        ProductDefinitionPublicationReadAuthorization auth =
                new ProductDefinitionCommandAuthorizationImpl(
                        new ReactorSecurityContextAdapter(),
                        (principal, action, resource, context) -> {
                            policies.incrementAndGet();
                            assertThat(action)
                                    .isIn(
                                            "product.definition.publication.readiness",
                                            "product.definition.publication.discovery");
                            assertThat(principal.scopes())
                                    .doesNotContain(
                                            "product:definitions:review",
                                            "product:definitions:cost:read");
                            assertThat(principal.claims()).doesNotContainKey("principal_kind");
                            return Mono.just(
                                    permit
                                            ? Decision.permit()
                                            : Decision.deny("current legitimate rule"));
                        },
                        Clock.fixed(now, ZoneOffset.UTC),
                        Set.of(issuer),
                        ProductDefinitionCommandAuthorization.POLICY_PROFILE);
        var routes =
                RouterFunctions.route()
                        .GET(
                                path("readiness"),
                                request ->
                                        handle(
                                                auth,
                                                ProductDefinitionPublicationReadAuthorization.Target
                                                        .READINESS))
                        .GET(
                                path("current"),
                                request ->
                                        handle(
                                                auth,
                                                ProductDefinitionPublicationReadAuthorization.Target
                                                        .DISCOVERY))
                        .build();
        var chain =
                new ProductDefinitionCommandSecurityConfiguration()
                        .productDefinitionCommandSecurity(
                                ServerHttpSecurity.http(), decoder, converter, trust, commands);
        var fallback =
                new ProductDefinitionServingSecurityConfiguration()
                        .productLegacySecurity(ServerHttpSecurity.http());
        return WebTestClient.bindToRouterFunction(routes)
                .webFilter(new WebFilterChainProxy(chain, fallback))
                .build();
    }

    Mono<ServerResponse> handle(
            ProductDefinitionPublicationReadAuthorization auth,
            ProductDefinitionPublicationReadAuthorization.Target target) {
        return auth.authorize(
                        new ProductDefinitionPublicationReadAuthorization.Scope(
                                tenant, root, target))
                .flatMap(
                        admission ->
                                auth.reauthorize(admission)
                                        .then(ServerResponse.ok().bodyValue("metadata")))
                .onErrorResume(
                        ResponseStatusException.class,
                        error -> ServerResponse.status(error.getStatusCode()).build());
    }

    @Test
    void bothNewPathsRequireBearerBeforeAnyPolicyRead() {
        var client = client();
        for (String suffix : List.of("readiness", "current"))
            client.get()
                    .uri(path(suffix))
                    .exchange()
                    .expectStatus()
                    .isUnauthorized()
                    .expectHeader()
                    .valueMatches("Cache-Control", ".*no-store.*")
                    .expectHeader()
                    .valueEquals("Vary", "Authorization")
                    .expectHeader()
                    .doesNotExist("Set-Cookie");
        assertThat(policies).hasValue(0);
        assertThat(decodes).hasValue(0);
    }

    @Test
    void actualSharedConverterCarriesMakerScopesWithoutPrivatePermissions() {
        var client = client();
        for (String suffix : List.of("readiness", "current"))
            client.get()
                    .uri(path(suffix))
                    .headers(h -> h.setBearerAuth("ordinary-source-decoder-stub"))
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .doesNotExist("Set-Cookie");
        assertThat(decodes).hasValue(2);
        assertThat(policies).hasValue(4);
    }

    @Test
    void replayReevaluatesCurrentPolicyAndErrorResponsesRemainNoStore() {
        var client = client();
        client.get()
                .uri(path("readiness"))
                .headers(h -> h.setBearerAuth("ordinary-source-decoder-stub"))
                .exchange()
                .expectStatus()
                .isOk();
        permit = false;
        client.get()
                .uri(path("readiness"))
                .headers(h -> h.setBearerAuth("ordinary-source-decoder-stub"))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectHeader()
                .valueMatches("Cache-Control", ".*no-store.*")
                .expectHeader()
                .valueEquals("Vary", "Authorization")
                .expectHeader()
                .doesNotExist("Set-Cookie");
        assertThat(decodes).hasValue(2);
        assertThat(policies).hasValue(3);
    }
}

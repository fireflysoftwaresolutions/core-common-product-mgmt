package io.fireflysoftware.core.product.web.config.definition;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionReadAuthorization.*;
import io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionReadAuthorizationImpl;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.core.authority.*;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.fireflyframework.security.rs.*;
import org.fireflyframework.security.webflux.context.ReactorSecurityContextAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class ProductDefinitionServingSecurityTest {
    static final UUID TENANT = UUID.randomUUID(), ROOT = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
    static final String ISSUER = "https://product.example.test";
    final AtomicInteger decisions = new AtomicInteger(), decodes = new AtomicInteger();
    boolean permit = true;
    String tokenTenant = TENANT.toString(),
            scope = "product:definitions:read",
            tokenIssuer = ISSUER;
    Instant expiry = NOW.plusSeconds(300);

    ResourceServerProperties trust() {
        var properties = new ResourceServerProperties();
        properties.setIssuer(ISSUER);
        properties.setAudiences(List.of("product-owner"));
        properties.setJwkSetUri("https://product.example.test/jwks");
        properties.setTenantClaimPaths(List.of("tenant_id"));
        properties.setTenantRequired(true);
        properties.setScopeClaimPaths(List.of("scope"));
        return properties;
    }

    String path() {
        return "/api/v1/tenants/" + TENANT + "/product-definitions/" + ROOT;
    }

    WebTestClient client(
            ResourceServerProperties trust, ProductDefinitionServingProperties serving) {
        var configuration = new ProductDefinitionServingSecurityConfiguration();
        var factory =
                new PrincipalFactory(
                        new ConfigurableAuthorityMapper(
                                new AuthorityMappingProperties(List.of(), List.of("scope"), "")));
        var converter =
                new ResourceServerAutoConfiguration()
                        .fireflyJwtAuthenticationConverter(factory, trust);
        var decoder =
                (org.springframework.security.oauth2.jwt.ReactiveJwtDecoder)
                        token ->
                                Mono.defer(
                                        () -> {
                                            decodes.incrementAndGet();
                                            if (token.equals("bad"))
                                                return Mono.error(
                                                        new BadJwtException(
                                                                "fixture invalid token"));
                                            var builder =
                                                    Jwt.withTokenValue(token)
                                                            .header("alg", "RS256")
                                                            .issuer(tokenIssuer)
                                                            .subject("actual-shared-principal")
                                                            .audience(List.of("product-owner"))
                                                            .expiresAt(expiry)
                                                            .claim("scope", scope);
                                            if (tokenTenant != null)
                                                builder.claim("tenant_id", tokenTenant);
                                            return Mono.just(builder.build());
                                        });
        var auth =
                new ProductDefinitionReadAuthorizationImpl(
                        new ReactorSecurityContextAdapter(),
                        (principal, action, resource, context) -> {
                            decisions.incrementAndGet();
                            assertThat(principal.tenantId()).isEqualTo(TENANT.toString());
                            assertThat(principal.subject()).isEqualTo("actual-shared-principal");
                            return Mono.just(
                                    permit ? Decision.permit() : Decision.deny("current-denial"));
                        },
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Set.of(ISSUER));
        HandlerFunction<ServerResponse> handler =
                request ->
                        auth.authorize(new Scope(TENANT, ROOT, Operation.ROOT, null))
                                .flatMap(
                                        admission ->
                                                auth.reauthorize(admission)
                                                        .then(
                                                                ServerResponse.ok()
                                                                        .bodyValue(
                                                                                Map.of(
                                                                                        "operationalAdmission",
                                                                                        "NOT_ADMITTED"))))
                                .onErrorResume(
                                        ResponseStatusException.class,
                                        error ->
                                                ServerResponse.status(error.getStatusCode())
                                                        .build());
        var routes =
                RouterFunctions.route()
                        .GET(path(), handler)
                        .GET(
                                "/legacy-public",
                                request -> ServerResponse.ok().bodyValue("legacy-public"))
                        .build();
        var read =
                configuration.productDefinitionReadSecurity(
                        ServerHttpSecurity.http(), decoder, converter, trust, serving);
        var legacy = configuration.productLegacySecurity(ServerHttpSecurity.http());
        return WebTestClient.bindToRouterFunction(routes)
                .webFilter(new WebFilterChainProxy(read, legacy))
                .build();
    }

    WebTestClient client() {
        return client(
                trust(),
                new ProductDefinitionServingProperties(
                        "https://policy.example.test", "product_definitions/allow"));
    }

    @Test
    void newRoutesRequireBearerWhileLegacyPublicBehaviorIsPreserved() {
        var client = client();
        client.get()
                .uri(path())
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .valueMatches(HttpHeaders.CACHE_CONTROL, ".*no-store.*");
        client.get().uri("/legacy-public").exchange().expectStatus().isOk();
        assertThat(decisions.get()).isZero();
    }

    @Test
    void actualSharedTenantConverterAndOwnerPolicyRunOnEveryReplayWithoutSessionReuse() {
        var client = client();
        for (int i = 0; i < 2; i++)
            client.get()
                    .uri(path())
                    .headers(h -> h.setBearerAuth("good"))
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .valueMatches(HttpHeaders.CACHE_CONTROL, ".*no-store.*")
                    .expectHeader()
                    .doesNotExist(HttpHeaders.SET_COOKIE)
                    .expectBody()
                    .jsonPath("$.operationalAdmission")
                    .isEqualTo("NOT_ADMITTED");
        assertThat(decisions.get()).isEqualTo(4);
        client.get()
                .uri(path())
                .cookie("SESSION", "untrusted-prior-session")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        assertThat(decisions.get()).isEqualTo(4);
        assertThat(decodes.get()).isEqualTo(2);
        permit = false;
        client.get()
                .uri(path())
                .headers(h -> h.setBearerAuth("good"))
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void exactScopeCurrentTenantIssuerAndFiniteExpiryRemainOwnerAdmissionRequirements() {
        var client = client();
        scope = "product:definitions:write";
        client.get()
                .uri(path())
                .headers(h -> h.setBearerAuth("good"))
                .exchange()
                .expectStatus()
                .isForbidden();
        scope = "product:definitions:read";
        tokenTenant = UUID.randomUUID().toString();
        client.get()
                .uri(path())
                .headers(h -> h.setBearerAuth("good"))
                .exchange()
                .expectStatus()
                .isForbidden();
        tokenTenant = TENANT.toString();
        tokenIssuer = "https://untrusted.example.test";
        client.get()
                .uri(path())
                .headers(h -> h.setBearerAuth("good"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        tokenIssuer = ISSUER;
        expiry = NOW;
        client.get()
                .uri(path())
                .headers(h -> h.setBearerAuth("good"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        assertThat(decisions.get()).isZero();
    }

    @Test
    void invalidBearerAndUnresolvedSignedTenantFailBeforePolicy() {
        var client = client();
        client.get()
                .uri(path())
                .headers(h -> h.setBearerAuth("bad"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        tokenTenant = null;
        client.get()
                .uri(path())
                .headers(h -> h.setBearerAuth("good"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        assertThat(decisions.get()).isZero();
    }

    @Test
    void missingTrustOrPolicyConfigurationClosesOnlyNewRoutes() {
        for (int missing = 0; missing < 5; missing++) {
            var trust = trust();
            var serving =
                    new ProductDefinitionServingProperties(
                            "https://policy.example.test", "product_definitions/allow");
            switch (missing) {
                case 0 -> trust.setIssuer("");
                case 1 -> trust.setAudiences(List.of());
                case 2 -> trust.setJwkSetUri("");
                case 3 -> trust.setTenantRequired(false);
                case 4 -> serving = new ProductDefinitionServingProperties(null, null);
            }
            var client = client(trust, serving);
            client.get()
                    .uri(path())
                    .headers(h -> h.setBearerAuth("good"))
                    .exchange()
                    .expectStatus()
                    .isEqualTo(503);
            client.get().uri("/legacy-public").exchange().expectStatus().isOk();
        }
        assertThat(decisions.get()).isZero();
        assertThat(decodes.get()).isZero();
    }
}

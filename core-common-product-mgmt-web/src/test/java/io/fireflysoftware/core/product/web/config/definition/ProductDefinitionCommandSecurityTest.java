package io.fireflysoftware.core.product.web.config.definition;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionCommandAuthorizationImpl;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCommand;
import io.fireflysoftware.core.product.web.config.definition.lifecycle.ProductDefinitionCommandProperties;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.core.authority.*;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.fireflyframework.security.rs.*;
import org.fireflyframework.security.webflux.context.ReactorSecurityContextAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.*;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class ProductDefinitionCommandSecurityTest {
    final UUID tenant = UUID.randomUUID(), root = UUID.randomUUID(), operation = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
    static final String ISSUER = "https://staff.example.test";
    final AtomicInteger decisions = new AtomicInteger(), decodes = new AtomicInteger();
    String tokenTenant = tenant.toString(),
            scopes = "product:definitions:write product:definitions:cost:write";
    boolean permit = true;

    ProductDefinitionCommandProperties commands() {
        var value = new ProductDefinitionCommandProperties();
        value.setEnabled(true);
        value.setPolicyProfile(ProductDefinitionCommandAuthorization.POLICY_PROFILE);
        value.setPolicyBaseUrl("https://policy.example.test");
        value.setPolicyPath("product_staff/allow");
        return value;
    }

    String path() {
        return "/api/v1/tenants/" + tenant + "/product-definitions/" + root + "/commands/draft";
    }

    WebTestClient client(ProductDefinitionCommandProperties commands) throws Exception {
        var trust = new ResourceServerProperties();
        trust.setIssuer(ISSUER);
        trust.setAudiences(List.of("product-owner"));
        trust.setJwkSetUri(ISSUER + "/jwks");
        trust.setTenantRequired(true);
        trust.setTenantClaimPaths(List.of("tenant_id"));
        trust.setScopeClaimPaths(List.of("scope"));
        var factory =
                new PrincipalFactory(
                        new ConfigurableAuthorityMapper(
                                new AuthorityMappingProperties(List.of(), List.of("scope"), "")));
        var converter =
                new ResourceServerAutoConfiguration()
                        .fireflyJwtAuthenticationConverter(factory, trust);
        ReactiveJwtDecoder decoder =
                raw ->
                        Mono.defer(
                                () -> {
                                    decodes.incrementAndGet();
                                    if (raw.equals("invalid"))
                                        return Mono.error(
                                                new BadJwtException("fixture invalid signature"));
                                    return Mono.just(
                                            Jwt.withTokenValue(raw)
                                                    .header("alg", "RS256")
                                                    .issuer(ISSUER)
                                                    .subject("shared-staff-subject")
                                                    .audience(List.of("product-owner"))
                                                    .expiresAt(NOW.plusSeconds(300))
                                                    .claim("tenant_id", tokenTenant)
                                                    .claim("scope", scopes)
                                                    .build());
                                });
        var auth =
                new ProductDefinitionCommandAuthorizationImpl(
                        new ReactorSecurityContextAdapter(),
                        (principal, action, resource, context) -> {
                            decisions.incrementAndGet();
                            assertThat(action).isEqualTo("product.definition.draft.create");
                            assertThat(context.get("authorityProfile"))
                                    .isEqualTo(
                                            ProductDefinitionCommandAuthorization.POLICY_PROFILE);
                            assertThat(principal.claims()).doesNotContainKey("principal_kind");
                            return Mono.just(
                                    permit ? Decision.permit() : Decision.deny("current-rule"));
                        },
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Set.of(ISSUER),
                        commands.getPolicyProfile());
        var scope =
                new ProductDefinitionCommandAuthorization.Scope(
                        tenant, root, operation, ProductDefinitionCommand.CREATE_DRAFT, null);
        var routes =
                RouterFunctions.route()
                        .POST(
                                path(),
                                request ->
                                        auth.authorize(scope)
                                                .flatMap(
                                                        admission ->
                                                                auth.reauthorize(admission)
                                                                        .then(
                                                                                ServerResponse.ok()
                                                                                        .bodyValue(
                                                                                                Map
                                                                                                        .of(
                                                                                                                "operationalAdmission",
                                                                                                                "NOT_ADMITTED"))))
                                                .onErrorResume(
                                                        ResponseStatusException.class,
                                                        error ->
                                                                ServerResponse.status(
                                                                                error
                                                                                        .getStatusCode())
                                                                        .build()))
                        .GET("/legacy-public", request -> ServerResponse.ok().bodyValue("legacy"))
                        .build();
        Class<?> type;
        try {
            type =
                    Class.forName(
                            "io.fireflysoftware.core.product.web.config.definition.ProductDefinitionCommandSecurityConfiguration");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Missing dedicated command JWT chain", missing);
        }
        var chain =
                (SecurityWebFilterChain)
                        type.getMethod(
                                        "productDefinitionCommandSecurity",
                                        ServerHttpSecurity.class,
                                        ReactiveJwtDecoder.class,
                                        org.springframework.core.convert.converter.Converter.class,
                                        ResourceServerProperties.class,
                                        ProductDefinitionCommandProperties.class)
                                .invoke(
                                        type.getConstructor().newInstance(),
                                        ServerHttpSecurity.http(),
                                        decoder,
                                        converter,
                                        trust,
                                        commands);
        var fallback =
                new ProductDefinitionServingSecurityConfiguration()
                        .productLegacySecurity(ServerHttpSecurity.http());
        return WebTestClient.bindToRouterFunction(routes)
                .webFilter(new WebFilterChainProxy(chain, fallback))
                .build();
    }

    @Test
    void commandRequiresBearerAndPreservesLegacyPublicRoutes() throws Exception {
        var client = client(commands());
        client.post()
                .uri(path())
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .valueMatches("Cache-Control", ".*no-store.*");
        client.get().uri("/legacy-public").exchange().expectStatus().isOk();
        assertThat(decisions.get()).isZero();
    }

    @Test
    void everyReplayUsesActualSharedConverterAndFreshCurrentPdpWithoutSessionCaching()
            throws Exception {
        var client = client(commands());
        for (int n = 0; n < 2; n++)
            client.post()
                    .uri(path())
                    .headers(headers -> headers.setBearerAuth("valid"))
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .doesNotExist("Set-Cookie");
        assertThat(decisions.get()).isEqualTo(4);
        assertThat(decodes.get()).isEqualTo(2);
        permit = false;
        client.post()
                .uri(path())
                .headers(headers -> headers.setBearerAuth("valid"))
                .exchange()
                .expectStatus()
                .isForbidden();
        assertThat(decisions.get()).isEqualTo(5);
        client.post()
                .uri(path())
                .cookie("SESSION", "previous")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        assertThat(decisions.get()).isEqualTo(5);
    }

    @Test
    void exactCreateScopesAndTenantAreRequiredBeforePdp() throws Exception {
        scopes = "product:definitions:write";
        var client = client(commands());
        client.post()
                .uri(path())
                .headers(headers -> headers.setBearerAuth("valid"))
                .exchange()
                .expectStatus()
                .isForbidden();
        assertThat(decisions.get()).isZero();
        scopes = "product:definitions:write product:definitions:cost:write";
        tokenTenant = UUID.randomUUID().toString();
        client.post()
                .uri(path())
                .headers(headers -> headers.setBearerAuth("valid"))
                .exchange()
                .expectStatus()
                .isForbidden();
        assertThat(decisions.get()).isZero();
    }

    @Test
    void missingStaffPolicyProfileFailsClosedBeforeDecoderAndPdp() throws Exception {
        var settings = commands();
        settings.setPolicyProfile(null);
        client(settings)
                .post()
                .uri(path())
                .headers(headers -> headers.setBearerAuth("valid"))
                .exchange()
                .expectStatus()
                .isEqualTo(503);
        assertThat(decodes.get()).isZero();
        assertThat(decisions.get()).isZero();
    }

    @Test
    void invalidBearerNeverReachesOwnerAuthority() throws Exception {
        client(commands())
                .post()
                .uri(path())
                .headers(headers -> headers.setBearerAuth("invalid"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        assertThat(decisions.get()).isZero();
    }

    @Test
    void privateSelectionReadPathUsesStaffChainBeforeLegacyFallback() throws Exception {
        client(commands())
                .get()
                .uri(
                        "/api/v1/tenants/"
                                + tenant
                                + "/product-definitions/"
                                + root
                                + "/staff-review/selections/"
                                + UUID.randomUUID())
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .valueMatches("Cache-Control", ".*no-store.*");
        assertThat(decisions.get()).isZero();
    }

    @Test
    void ordinaryCostEditorRouteUsesProtectedStaffChainBeforeLegacyFallback() throws Exception {
        client(commands())
                .get()
                .uri(
                        "/api/v1/tenants/"
                                + tenant
                                + "/product-definitions/"
                                + root
                                + "/staff-cost/current")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .valueMatches("Cache-Control", ".*no-store.*");
        assertThat(decisions.get()).isZero();
    }
}

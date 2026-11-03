package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionCommandAuthorization;
import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionCommandAuthorization.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCommand;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.reactive.TransactionContextManager;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class ProductDefinitionCommandAuthorizationTest {
    static final UUID TENANT = UUID.randomUUID(),
            ROOT = UUID.randomUUID(),
            OPERATION = UUID.randomUUID();
    static final String ISSUER = "https://issuer.example.invalid";
    static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    SecurityPrincipal principal() {
        return SecurityPrincipal.builder()
                .subject("actual-shared-subject")
                .issuer(ISSUER)
                .tenantId(TENANT.toString())
                .scopes(
                        Set.of(
                                "product:definitions:write",
                                "product:definitions:cost:write",
                                "product:definitions:cost:read",
                                "product:definitions:review",
                                "product:definitions:publish",
                                "product:definitions:withdraw"))
                .claims(Map.of("exp", NOW.plusSeconds(300)))
                .build();
    }

    Scope scope(ProductDefinitionCommand command) {
        return new Scope(
                TENANT,
                ROOT,
                OPERATION,
                command,
                command.target() == null ? null : UUID.randomUUID());
    }

    ProductDefinitionCommandAuthorization auth(SecurityPrincipal p, PolicyDecisionPort policy) {
        return new ProductDefinitionCommandAuthorizationImpl(
                () -> Mono.justOrEmpty(p),
                policy,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Set.of(ISSUER),
                ProductDefinitionCommandAuthorization.POLICY_PROFILE);
    }

    void status(Mono<?> result, int code) {
        assertThatThrownBy(result::block)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(code));
    }

    @Test
    void eachCommandUsesExactCurrentPolicyActionResourceAndUnchangedPrincipal() {
        var actor = principal();
        var calls = new AtomicInteger();
        for (var command : ProductDefinitionCommand.values()) {
            var requested = scope(command);
            var auth =
                    auth(
                            actor,
                            (p, a, r, c) -> {
                                assertThat(p).isSameAs(actor);
                                assertThat(a).isEqualTo(command.action());
                                String expected =
                                        "tenant/" + TENANT + "/product-definition/" + ROOT;
                                if (command.target() != null)
                                    expected += "/" + command.target() + "/" + requested.targetId();
                                assertThat(r).isEqualTo(expected);
                                assertThat(c)
                                        .containsEntry("operationId", OPERATION.toString())
                                        .containsEntry(
                                                "authorityProfile",
                                                ProductDefinitionCommandAuthorization
                                                        .POLICY_PROFILE)
                                        .doesNotContainKeys("actorKind", "principal_kind", "human");
                                calls.incrementAndGet();
                                return Mono.just(Decision.permit());
                            });
            var admitted = auth.authorize(requested).block();
            assertThat(admitted).isNotNull();
            assertThat(admitted.principal()).isSameAs(actor);
            assertThat(admitted.validUntilEpochMillis())
                    .isEqualTo(NOW.plusSeconds(30).toEpochMilli());
            auth.reauthorize(admitted).block();
        }
        assertThat(calls).hasValue(16);
    }

    @Test
    void completeDraftRequiresPrivateCostPermissionAsWellAsPublicWrite() {
        var p = principal().toBuilder().scopes(Set.of("product:definitions:write")).build();
        status(
                auth(p, (a, b, c, d) -> Mono.just(Decision.permit()))
                        .authorize(scope(ProductDefinitionCommand.CREATE_DRAFT)),
                403);
    }

    @Test
    void publicMakerScopeDoesNotGrantCostMutation() {
        var p = principal().toBuilder().scopes(Set.of("product:definitions:write")).build();
        status(
                auth(p, (a, b, c, d) -> Mono.just(Decision.permit()))
                        .authorize(scope(ProductDefinitionCommand.EDIT_COST)),
                403);
    }

    @Test
    void tenantMismatchAndUnknownIssuerFailBeforePolicy() {
        var calls = new AtomicInteger();
        PolicyDecisionPort policy =
                (a, b, c, d) -> {
                    calls.incrementAndGet();
                    return Mono.just(Decision.permit());
                };
        status(
                auth(principal().toBuilder().tenantId(UUID.randomUUID().toString()).build(), policy)
                        .authorize(scope(ProductDefinitionCommand.EDIT_PUBLIC)),
                403);
        status(
                auth(
                                principal().toBuilder()
                                        .issuer("https://other.example.invalid")
                                        .build(),
                                policy)
                        .authorize(scope(ProductDefinitionCommand.EDIT_PUBLIC)),
                401);
        assertThat(calls).hasValue(0);
    }

    @Test
    void missingExpiredOrUnboundedExpiryCannotAdmit() {
        for (var claims :
                List.<Map<String, Object>>of(
                        Map.of(), Map.of("exp", NOW), Map.of("exp", Instant.MAX)))
            status(
                    auth(
                                    principal().toBuilder().claims(claims).build(),
                                    (a, b, c, d) -> Mono.just(Decision.permit()))
                            .authorize(scope(ProductDefinitionCommand.EDIT_PUBLIC)),
                    401);
    }

    @Test
    void noConfiguredQualifiedStaffPolicyProfileFailsClosed() {
        var auth =
                new ProductDefinitionCommandAuthorizationImpl(
                        () -> Mono.just(principal()),
                        (a, b, c, d) -> Mono.just(Decision.permit()),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Set.of(ISSUER),
                        "");
        status(auth.authorize(scope(ProductDefinitionCommand.EDIT_PUBLIC)), 503);
    }

    @Test
    void callerKindOrHumanAssertionsCannotReplacePolicyGrant() {
        var actor =
                principal().toBuilder()
                        .attributes(Map.of("principal_kind", "STAFF", "human", true))
                        .build();
        status(
                auth(actor, (a, b, c, d) -> Mono.just(Decision.deny("unqualified")))
                        .authorize(scope(ProductDefinitionCommand.APPROVE_SELECTION)),
                403);
    }

    @Test
    void indeterminateOrEmptyPolicyResultCannotPermit() {
        status(
                auth(
                                principal(),
                                (a, b, c, d) ->
                                        Mono.just(Decision.indeterminate("private details")))
                        .authorize(scope(ProductDefinitionCommand.EDIT_PUBLIC)),
                403);
        status(
                auth(principal(), (a, b, c, d) -> Mono.empty())
                        .authorize(scope(ProductDefinitionCommand.EDIT_PUBLIC)),
                503);
        status(
                auth(
                                principal(),
                                (a, b, c, d) ->
                                        Mono.error(new IllegalStateException("private details")))
                        .authorize(scope(ProductDefinitionCommand.EDIT_PUBLIC)),
                503);
    }

    @Test
    void replayRechecksCurrentPolicyAndSuppressesEarlierPermit() {
        var calls = new AtomicInteger();
        var auth =
                auth(
                        principal(),
                        (a, b, c, d) ->
                                Mono.just(
                                        calls.incrementAndGet() == 1
                                                ? Decision.permit()
                                                : Decision.deny("revoked")));
        var admitted = auth.authorize(scope(ProductDefinitionCommand.EDIT_PUBLIC)).block();
        assertThat(admitted).isNotNull();
        status(auth.reauthorize(admitted), 403);
        assertThat(calls).hasValue(2);
    }

    @Test
    void changedCurrentPrincipalCannotUseRetainedAdmission() {
        var calls = new AtomicInteger();
        var original = principal();
        var auth =
                new ProductDefinitionCommandAuthorizationImpl(
                        () ->
                                Mono.just(
                                        calls.incrementAndGet() == 1
                                                ? original
                                                : original.toBuilder()
                                                        .subject("different")
                                                        .build()),
                        (a, b, c, d) -> Mono.just(Decision.permit()),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Set.of(ISSUER),
                        ProductDefinitionCommandAuthorization.POLICY_PROFILE);
        var admitted = auth.authorize(scope(ProductDefinitionCommand.EDIT_PUBLIC)).block();
        assertThat(admitted).isNotNull();
        status(auth.reauthorize(admitted), 401);
    }

    @Test
    void ambientSqlTransactionIsRejectedBeforePrincipalOrPdpIo() {
        var calls = new AtomicInteger();
        var auth =
                new ProductDefinitionCommandAuthorizationImpl(
                        () -> {
                            calls.incrementAndGet();
                            return Mono.just(principal());
                        },
                        (a, b, c, d) -> {
                            calls.incrementAndGet();
                            return Mono.just(Decision.permit());
                        },
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Set.of(ISSUER),
                        ProductDefinitionCommandAuthorization.POLICY_PROFILE);
        var inside =
                TransactionSynchronizationManager.forCurrentTransaction()
                        .flatMap(
                                manager -> {
                                    manager.setActualTransactionActive(true);
                                    return auth.authorize(
                                            scope(ProductDefinitionCommand.EDIT_PUBLIC));
                                });
        status(inside.contextWrite(TransactionContextManager.createTransactionContext()), 503);
        assertThat(calls).hasValue(0);
    }

    @Test
    void missingRequiredTargetOrUnexpectedTargetIsBadScope() {
        status(
                auth(principal(), (a, b, c, d) -> Mono.just(Decision.permit()))
                        .authorize(
                                new Scope(
                                        TENANT,
                                        ROOT,
                                        OPERATION,
                                        ProductDefinitionCommand.PUBLISH,
                                        null)),
                400);
        status(
                auth(principal(), (a, b, c, d) -> Mono.just(Decision.permit()))
                        .authorize(
                                new Scope(
                                        TENANT,
                                        ROOT,
                                        OPERATION,
                                        ProductDefinitionCommand.EDIT_PUBLIC,
                                        UUID.randomUUID())),
                400);
    }

    @Test
    void checkerDecisionRequiresCurrentPrivateCostVisibilityRatherThanFingerprintAlone() {
        var actor = principal().toBuilder().scopes(Set.of("product:definitions:review")).build();
        var requests = new AtomicInteger();
        for (var command :
                List.of(
                        ProductDefinitionCommand.APPROVE_SELECTION,
                        ProductDefinitionCommand.REJECT_SELECTION))
            status(
                    auth(
                                    actor,
                                    (p, a, r, c) -> {
                                        requests.incrementAndGet();
                                        return Mono.just(Decision.permit());
                                    })
                            .authorize(scope(command)),
                    403);
        assertThat(requests.get()).isZero();
    }

    @Test
    void decisionReauthorizationRechecksRevokedPrivateCostReadScope() {
        for (var command :
                List.of(
                        ProductDefinitionCommand.APPROVE_SELECTION,
                        ProductDefinitionCommand.REJECT_SELECTION)) {
            var current = new java.util.concurrent.atomic.AtomicReference<>(principal());
            var requests = new AtomicInteger();
            ProductDefinitionCommandAuthorization authority =
                    new ProductDefinitionCommandAuthorizationImpl(
                            () -> Mono.just(current.get()),
                            (p, a, r, c) -> {
                                requests.incrementAndGet();
                                return Mono.just(Decision.permit());
                            },
                            Clock.fixed(NOW, ZoneOffset.UTC),
                            Set.of(ISSUER),
                            ProductDefinitionCommandAuthorization.POLICY_PROFILE);
            var admission = authority.authorize(scope(command)).block();
            assertThat(admission).isNotNull();
            current.set(
                    current.get().toBuilder().scopes(Set.of("product:definitions:review")).build());
            status(authority.reauthorize(admission), 403);
            assertThat(requests.get()).isEqualTo(1);
        }
    }
}

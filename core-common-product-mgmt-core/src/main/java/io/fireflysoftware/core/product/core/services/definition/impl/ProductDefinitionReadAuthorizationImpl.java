package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionReadAuthorization;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;

public final class ProductDefinitionReadAuthorizationImpl
        implements ProductDefinitionReadAuthorization {
    private static final Instant MAX_EXPIRY = Instant.parse("9999-12-31T23:59:59Z");
    private final SecurityContextPort principals;
    private final PolicyDecisionPort policies;
    private final Clock clock;
    private final Set<String> issuers;

    public ProductDefinitionReadAuthorizationImpl(
            SecurityContextPort principals,
            PolicyDecisionPort policies,
            Clock clock,
            Set<String> issuers) {
        this.principals = Objects.requireNonNull(principals);
        this.policies = Objects.requireNonNull(policies);
        this.clock = Objects.requireNonNull(clock);
        this.issuers = Set.copyOf(issuers);
    }

    @Override
    public Mono<Admission> authorize(Scope scope) {
        return outsideSqlTransaction(
                () -> {
                    validate(scope);
                    return principals
                            .currentPrincipal()
                            .switchIfEmpty(Mono.error(unauthenticated()))
                            .flatMap(
                                    principal -> {
                                        long expiry = validate(principal, scope);
                                        long until =
                                                Math.min(
                                                        expiry,
                                                        Math.addExact(clock.millis(), 30000L));
                                        var admission =
                                                new Admission(principal, scope, expiry, until);
                                        return decide(admission).thenReturn(admission);
                                    });
                });
    }

    @Override
    public Mono<Void> reauthorize(Admission admission) {
        return outsideSqlTransaction(
                () -> {
                    if (admission == null) return Mono.error(unavailable());
                    validate(admission.scope());
                    validate(admission.principal(), admission.scope());
                    return decide(admission);
                });
    }

    private Mono<Void> decide(Admission admission) {
        return Mono.defer(
                () -> {
                    current(admission);
                    var scope = admission.scope();
                    String suffix =
                            switch (scope.operation()) {
                                case ROOT -> "root";
                                case REVISION -> "revision";
                                case PUBLICATION -> "publication";
                            };
                    String resource =
                            "tenant/" + scope.tenantId() + "/product-definition/" + scope.rootId();
                    if (scope.operation() != Operation.ROOT)
                        resource += "/" + suffix + "/" + scope.resourceId();
                    var context = new LinkedHashMap<String, Object>();
                    context.put("tenantId", scope.tenantId().toString());
                    context.put("rootId", scope.rootId().toString());
                    context.put(
                            "resourceId",
                            (scope.resourceId() == null ? scope.rootId() : scope.resourceId())
                                    .toString());
                    context.put("profileCode", "ACCOUNT_TARIFF_V1");
                    context.put("profileVersion", 1);
                    context.put("businessArea", "BANKING");
                    return policies.authorize(
                                    admission.principal(),
                                    "product.definition." + suffix + ".read",
                                    resource,
                                    Map.copyOf(context))
                            .timeout(Duration.ofSeconds(5))
                            .onErrorMap(error -> unavailable())
                            .switchIfEmpty(Mono.error(unavailable()))
                            .flatMap(
                                    decision -> {
                                        current(admission);
                                        return decision.granted()
                                                        && decision.obligations().isEmpty()
                                                ? Mono.<Void>empty()
                                                : Mono.error(forbidden());
                                    });
                });
    }

    private long validate(SecurityPrincipal principal, Scope scope) {
        var now = clock.instant();
        if (principal.subject() == null
                || principal.subject().isBlank()
                || !issuers.contains(principal.issuer())) throw unauthenticated();
        if (!(principal.claims().get("exp") instanceof Instant expiry)
                || !expiry.isAfter(now)
                || expiry.isAfter(MAX_EXPIRY)) throw unauthenticated();
        var nbf = principal.claims().get("nbf");
        if (nbf != null && (!(nbf instanceof Instant instant) || instant.isAfter(now)))
            throw unauthenticated();
        if (principal.tenantId() == null) throw unauthenticated();
        if (!scope.tenantId().toString().equals(principal.tenantId())
                || !principal.hasScope("product:definitions:read")) throw forbidden();
        return expiry.toEpochMilli();
    }

    private void current(Admission admission) {
        long now = clock.millis();
        if (now >= admission.actorExpiresAtEpochMillis()) throw unauthenticated();
        if (now >= admission.validUntilEpochMillis()) throw forbidden();
    }

    private static void validate(Scope scope) {
        if (scope == null
                || scope.tenantId() == null
                || scope.rootId() == null
                || scope.operation() == null
                || (scope.operation() == Operation.ROOT
                        ? scope.resourceId() != null
                        : scope.resourceId() == null))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Exact product read scope required");
    }

    private static <T> Mono<T> outsideSqlTransaction(Supplier<Mono<T>> action) {
        return Mono.defer(
                () ->
                        TransactionSynchronizationManager.forCurrentTransaction()
                                .map(TransactionSynchronizationManager::isActualTransactionActive)
                                .onErrorResume(
                                        NoTransactionException.class, error -> Mono.just(false))
                                .flatMap(
                                        active ->
                                                active
                                                        ? Mono.error(unavailable())
                                                        : Mono.defer(action)));
    }

    private static ResponseStatusException unauthenticated() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Current trusted product identity required");
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Current product read permission required");
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Current product policy unavailable");
    }
}

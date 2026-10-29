package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.core.services.definition.DefinitionCanonical;
import io.fireflysoftware.core.product.core.services.definition.GovernedDefinitionProfile;
import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionCommandAuthorization;
import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionCostReadAuthorization;
import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionPublicationReadAuthorization;
import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionReviewAuthorization;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;

public final class ProductDefinitionCommandAuthorizationImpl
        implements ProductDefinitionCommandAuthorization,
                ProductDefinitionReviewAuthorization,
                ProductDefinitionCostReadAuthorization,
                ProductDefinitionPublicationReadAuthorization {
    private static final Instant MAX_EXPIRY = Instant.parse("9999-12-31T23:59:59Z");
    private final SecurityContextPort principals;
    private final PolicyDecisionPort policies;
    private final Clock clock;
    private final Set<String> issuers;
    private final String profile;
    private final GovernedDefinitionProfile backend;

    public ProductDefinitionCommandAuthorizationImpl(
            SecurityContextPort principals,
            PolicyDecisionPort policies,
            Clock clock,
            Set<String> issuers,
            String profile) {
        this(principals, policies, clock, issuers, profile, GovernedDefinitionProfile.ACCOUNT_TARIFF);
    }

    public ProductDefinitionCommandAuthorizationImpl(
            SecurityContextPort principals, PolicyDecisionPort policies, Clock clock,
            Set<String> issuers, String profile, GovernedDefinitionProfile backend) {
        this.backend = Objects.requireNonNull(backend);
        this.principals = Objects.requireNonNull(principals);
        this.policies = Objects.requireNonNull(policies);
        this.clock = Objects.requireNonNull(clock);
        this.issuers = Set.copyOf(issuers);
        this.profile = profile;
    }

    @Override public GovernedDefinitionProfile definitionProfile() { return backend; }

    public Mono<ProductDefinitionCommandAuthorization.Admission> authorize(
            ProductDefinitionCommandAuthorization.Scope scope) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(
                () -> {
                    validateScope(scope);
                    configured();
                    return principals
                            .currentPrincipal()
                            .switchIfEmpty(Mono.error(unauthenticated()))
                            .flatMap(
                                    principal -> {
                                        long expiry = validatePrincipal(principal, scope);
                                        var admission =
                                                new ProductDefinitionCommandAuthorization.Admission(
                                                        principal,
                                                        scope,
                                                        expiry,
                                                        Math.min(
                                                                expiry,
                                                                Math.addExact(
                                                                        clock.millis(), 30000L)));
                                        return decide(principal, admission).thenReturn(admission);
                                    });
                });
    }

    public Mono<Void> reauthorize(ProductDefinitionCommandAuthorization.Admission admission) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(
                () -> {
                    if (admission == null) return Mono.error(unavailable());
                    validateScope(admission.scope());
                    configured();
                    current(admission);
                    return principals
                            .currentPrincipal()
                            .switchIfEmpty(Mono.error(unauthenticated()))
                            .flatMap(
                                    principal -> {
                                        validatePrincipal(principal, admission.scope());
                                        var original = admission.principal();
                                        if (original == null
                                                || !Objects.equals(
                                                        principal.subject(), original.subject())
                                                || !Objects.equals(
                                                        principal.issuer(), original.issuer())
                                                || !Objects.equals(
                                                        principal.tenantId(), original.tenantId()))
                                            return Mono.error(unauthenticated());
                                        return decide(principal, admission);
                                    });
                });
    }

    private Mono<Void> decide(
            SecurityPrincipal principal,
            ProductDefinitionCommandAuthorization.Admission admission) {
        return Mono.defer(
                () -> {
                    current(admission);
                    var scope = admission.scope();
                    var command = scope.command();
                    String resource =
                            "tenant/" + scope.tenantId() + "/product-definition/" + scope.rootId();
                    if (command.target() != null)
                        resource += "/" + command.target() + "/" + scope.targetId();
                    var context = new LinkedHashMap<String, Object>();
                    context.put("tenantId", scope.tenantId().toString());
                    context.put("rootId", scope.rootId().toString());
                    context.put("operationId", scope.operationId().toString());
                    context.put("command", command.name());
                    context.put("authorityProfile", backend.policyProfile());
                    context.put("profileCode", backend.profileCode());
                    context.put("profileVersion", 1);
                    context.put("businessArea", backend.businessArea());
                    if (scope.targetId() != null)
                        context.put("targetId", scope.targetId().toString());
                    return policies.authorize(
                                    principal, command.action(), resource, Map.copyOf(context))
                            .timeout(Duration.ofSeconds(5))
                            .onErrorMap(error -> unavailable())
                            .switchIfEmpty(Mono.error(unavailable()))
                            .flatMap(
                                    decision -> {
                                        current(admission);
                                        validatePrincipal(principal, scope);
                                        return decision.granted()
                                                        && decision.obligations().isEmpty()
                                                ? Mono.empty()
                                                : Mono.error(forbidden());
                                    });
                });
    }

    private long validatePrincipal(
            SecurityPrincipal principal, ProductDefinitionCommandAuthorization.Scope scope) {
        return validatePrincipal(principal, scope.tenantId(), scope.command().requiredScopes());
    }

    private long validatePrincipal(
            SecurityPrincipal principal, UUID tenantId, Set<String> requiredScopes) {
        if (principal == null
                || principal.subject() == null
                || principal.subject().isBlank()
                || principal.subject().codePointCount(0, principal.subject().length()) > 255
                || principal.issuer() == null
                || principal.issuer().codePointCount(0, principal.issuer().length()) > 512
                || !issuers.contains(principal.issuer())) throw unauthenticated();
        try {
            DefinitionCanonical.encode(
                    DefinitionCanonical.tree(
                            Map.of("subject", principal.subject(), "issuer", principal.issuer())));
        } catch (IllegalArgumentException invalid) {
            throw unauthenticated();
        }
        var now = clock.instant();
        if (!(principal.claims().get("exp") instanceof Instant expiry)
                || !expiry.isAfter(now)
                || expiry.isAfter(MAX_EXPIRY)) throw unauthenticated();
        var nbf = principal.claims().get("nbf");
        if (nbf != null && (!(nbf instanceof Instant value) || value.isAfter(now)))
            throw unauthenticated();
        if (principal.tenantId() == null) throw unauthenticated();
        if (!tenantId.toString().equals(principal.tenantId())
                || !principal.scopes().containsAll(requiredScopes)) throw forbidden();
        return expiry.toEpochMilli();
    }

    public Mono<ProductDefinitionReviewAuthorization.Admission> authorize(
            ProductDefinitionReviewAuthorization.Scope scope) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(
                () -> {
                    validateReviewScope(scope);
                    configured();
                    return principals
                            .currentPrincipal()
                            .switchIfEmpty(Mono.error(unauthenticated()))
                            .flatMap(
                                    principal -> {
                                        long expiry =
                                                validatePrincipal(
                                                        principal,
                                                        scope.tenantId(),
                                                        Set.of(
                                                                "product:definitions:review",
                                                                "product:definitions:cost:read"));
                                        var admission =
                                                new ProductDefinitionReviewAuthorization.Admission(
                                                        principal,
                                                        scope,
                                                        expiry,
                                                        Math.min(
                                                                expiry,
                                                                Math.addExact(
                                                                        clock.millis(), 30000L)));
                                        return decideReview(principal, admission)
                                                .thenReturn(admission);
                                    });
                });
    }

    public Mono<Void> reauthorize(ProductDefinitionReviewAuthorization.Admission admission) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(
                () -> {
                    if (admission == null) return Mono.error(unavailable());
                    validateReviewScope(admission.scope());
                    configured();
                    current(
                            admission.actorExpiresAtEpochMillis(),
                            admission.validUntilEpochMillis());
                    return principals
                            .currentPrincipal()
                            .switchIfEmpty(Mono.error(unauthenticated()))
                            .flatMap(
                                    principal -> {
                                        var original = admission.principal();
                                        if (original == null
                                                || !Objects.equals(
                                                        principal.subject(), original.subject())
                                                || !Objects.equals(
                                                        principal.issuer(), original.issuer())
                                                || !Objects.equals(
                                                        principal.tenantId(), original.tenantId()))
                                            return Mono.error(unauthenticated());
                                        return decideReview(principal, admission);
                                    });
                });
    }

    private Mono<Void> decideReview(
            SecurityPrincipal principal, ProductDefinitionReviewAuthorization.Admission admission) {
        return Mono.defer(
                () -> {
                    var scope = admission.scope();
                    current(
                            admission.actorExpiresAtEpochMillis(),
                            admission.validUntilEpochMillis());
                    validatePrincipal(
                            principal,
                            scope.tenantId(),
                            Set.of("product:definitions:review", "product:definitions:cost:read"));
                    var context = new LinkedHashMap<String, Object>();
                    context.put("tenantId", scope.tenantId().toString());
                    context.put("rootId", scope.rootId().toString());
                    context.put("selectionTarget", scope.target().name());
                    context.put("authorityProfile", backend.policyProfile());
                    context.put("profileCode", backend.profileCode());
                    context.put("profileVersion", 1);
                    context.put("businessArea", backend.businessArea());
                    if (scope.target() == ProductDefinitionReviewAuthorization.Target.EXACT)
                        context.put("submissionId", scope.submissionId().toString());
                    return policies.authorize(
                                    principal,
                                    "product.definition.selection.read",
                                    "tenant/"
                                            + scope.tenantId()
                                            + "/product-definition/"
                                            + scope.rootId()
                                            + "/submission/"
                                            + (scope.target()
                                                            == ProductDefinitionReviewAuthorization
                                                                    .Target.CURRENT
                                                    ? "current"
                                                    : scope.submissionId().toString()),
                                    Map.copyOf(context))
                            .timeout(Duration.ofSeconds(5))
                            .onErrorMap(error -> unavailable())
                            .switchIfEmpty(Mono.error(unavailable()))
                            .flatMap(
                                    decision -> {
                                        current(
                                                admission.actorExpiresAtEpochMillis(),
                                                admission.validUntilEpochMillis());
                                        validatePrincipal(
                                                principal,
                                                scope.tenantId(),
                                                Set.of(
                                                        "product:definitions:review",
                                                        "product:definitions:cost:read"));
                                        return decision.granted()
                                                        && decision.obligations().isEmpty()
                                                ? Mono.empty()
                                                : Mono.error(forbidden());
                                    });
                });
    }

    private static void validateReviewScope(ProductDefinitionReviewAuthorization.Scope scope) {
        if (scope == null
                || !identity(scope.tenantId())
                || !identity(scope.rootId())
                || scope.target() == null
                || (scope.target() == ProductDefinitionReviewAuthorization.Target.EXACT
                        ? !identity(scope.submissionId())
                        : scope.submissionId() != null))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Exact product review scope required");
    }

    public Mono<ProductDefinitionCostReadAuthorization.Admission> authorize(
            ProductDefinitionCostReadAuthorization.Scope scope) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(
                () -> {
                    validateCostScope(scope);
                    configured();
                    return principals
                            .currentPrincipal()
                            .switchIfEmpty(Mono.error(unauthenticated()))
                            .flatMap(
                                    principal -> {
                                        long expiry =
                                                validatePrincipal(
                                                        principal,
                                                        scope.tenantId(),
                                                        Set.of("product:definitions:cost:read"));
                                        var admission =
                                                new ProductDefinitionCostReadAuthorization
                                                        .Admission(
                                                        principal,
                                                        scope,
                                                        expiry,
                                                        Math.min(
                                                                expiry,
                                                                Math.addExact(
                                                                        clock.millis(), 30000L)));
                                        return decideCost(principal, admission)
                                                .thenReturn(admission);
                                    });
                });
    }

    public Mono<Void> reauthorize(ProductDefinitionCostReadAuthorization.Admission admission) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(
                () -> {
                    if (admission == null) return Mono.error(unavailable());
                    validateCostScope(admission.scope());
                    configured();
                    current(
                            admission.actorExpiresAtEpochMillis(),
                            admission.validUntilEpochMillis());
                    return principals
                            .currentPrincipal()
                            .switchIfEmpty(Mono.error(unauthenticated()))
                            .flatMap(
                                    principal -> {
                                        var original = admission.principal();
                                        if (original == null
                                                || !Objects.equals(
                                                        principal.subject(), original.subject())
                                                || !Objects.equals(
                                                        principal.issuer(), original.issuer())
                                                || !Objects.equals(
                                                        principal.tenantId(), original.tenantId()))
                                            return Mono.error(unauthenticated());
                                        return decideCost(principal, admission);
                                    });
                });
    }

    private Mono<Void> decideCost(
            SecurityPrincipal principal,
            ProductDefinitionCostReadAuthorization.Admission admission) {
        return Mono.defer(
                () -> {
                    var scope = admission.scope();
                    current(
                            admission.actorExpiresAtEpochMillis(),
                            admission.validUntilEpochMillis());
                    validatePrincipal(
                            principal, scope.tenantId(), Set.of("product:definitions:cost:read"));
                    var context =
                            Map.<String, Object>of(
                                    "tenantId",
                                    scope.tenantId().toString(),
                                    "rootId",
                                    scope.rootId().toString(),
                                    "authorityProfile",
                                    backend.policyProfile(),
                                    "profileCode",
                                    backend.profileCode(),
                                    "profileVersion",
                                    1,
                                    "businessArea",
                                    backend.businessArea());
                    return policies.authorize(
                                    principal,
                                    "product.definition.cost.read",
                                    "tenant/"
                                            + scope.tenantId()
                                            + "/product-definition/"
                                            + scope.rootId()
                                            + "/cost/current",
                                    context)
                            .timeout(Duration.ofSeconds(5))
                            .onErrorMap(error -> unavailable())
                            .switchIfEmpty(Mono.error(unavailable()))
                            .flatMap(
                                    decision -> {
                                        current(
                                                admission.actorExpiresAtEpochMillis(),
                                                admission.validUntilEpochMillis());
                                        validatePrincipal(
                                                principal,
                                                scope.tenantId(),
                                                Set.of("product:definitions:cost:read"));
                                        return decision.granted()
                                                        && decision.obligations().isEmpty()
                                                ? Mono.empty()
                                                : Mono.error(forbidden());
                                    });
                });
    }

    private static void validateCostScope(ProductDefinitionCostReadAuthorization.Scope scope) {
        if (scope == null || !identity(scope.tenantId()) || !identity(scope.rootId()))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Exact product cost scope required");
    }

    public Mono<ProductDefinitionPublicationReadAuthorization.Admission> authorize(
            ProductDefinitionPublicationReadAuthorization.Scope scope) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(
                () -> {
                    validatePublicationReadScope(scope);
                    configured();
                    return principals
                            .currentPrincipal()
                            .switchIfEmpty(Mono.error(unauthenticated()))
                            .flatMap(
                                    principal -> {
                                        long expiry =
                                                validatePrincipal(
                                                        principal,
                                                        scope.tenantId(),
                                                        Set.of(scope.target().requiredScope()));
                                        var admission =
                                                new ProductDefinitionPublicationReadAuthorization
                                                        .Admission(
                                                        principal,
                                                        scope,
                                                        expiry,
                                                        Math.min(
                                                                expiry,
                                                                Math.addExact(
                                                                        clock.millis(), 30000L)));
                                        return decidePublicationRead(principal, admission)
                                                .thenReturn(admission);
                                    });
                });
    }

    public Mono<Void> reauthorize(
            ProductDefinitionPublicationReadAuthorization.Admission admission) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(
                () -> {
                    if (admission == null) return Mono.error(unavailable());
                    validatePublicationReadScope(admission.scope());
                    configured();
                    current(
                            admission.actorExpiresAtEpochMillis(),
                            admission.validUntilEpochMillis());
                    return principals
                            .currentPrincipal()
                            .switchIfEmpty(Mono.error(unauthenticated()))
                            .flatMap(
                                    principal -> {
                                        var original = admission.principal();
                                        if (original == null
                                                || !Objects.equals(
                                                        principal.subject(), original.subject())
                                                || !Objects.equals(
                                                        principal.issuer(), original.issuer())
                                                || !Objects.equals(
                                                        principal.tenantId(), original.tenantId()))
                                            return Mono.error(unauthenticated());
                                        return decidePublicationRead(principal, admission);
                                    });
                });
    }

    private Mono<Void> decidePublicationRead(
            SecurityPrincipal principal,
            ProductDefinitionPublicationReadAuthorization.Admission admission) {
        return Mono.defer(
                () -> {
                    var scope = admission.scope();
                    current(
                            admission.actorExpiresAtEpochMillis(),
                            admission.validUntilEpochMillis());
                    validatePrincipal(
                            principal, scope.tenantId(), Set.of(scope.target().requiredScope()));
                    var context =
                            Map.<String, Object>of(
                                    "tenantId",
                                    scope.tenantId().toString(),
                                    "rootId",
                                    scope.rootId().toString(),
                                    "authorityProfile",
                                    backend.policyProfile(),
                                    "profileCode",
                                    backend.profileCode(),
                                    "profileVersion",
                                    1,
                                    "businessArea",
                                    backend.businessArea());
                    return policies.authorize(
                                    principal,
                                    scope.target().action(),
                                    "tenant/"
                                            + scope.tenantId()
                                            + "/product-definition/"
                                            + scope.rootId()
                                            + "/publication/"
                                            + scope.target().resourceSuffix(),
                                    context)
                            .timeout(Duration.ofSeconds(5))
                            .onErrorMap(error -> unavailable())
                            .switchIfEmpty(Mono.error(unavailable()))
                            .flatMap(
                                    decision -> {
                                        current(
                                                admission.actorExpiresAtEpochMillis(),
                                                admission.validUntilEpochMillis());
                                        validatePrincipal(
                                                principal,
                                                scope.tenantId(),
                                                Set.of(scope.target().requiredScope()));
                                        return decision.granted()
                                                        && decision.obligations().isEmpty()
                                                ? Mono.empty()
                                                : Mono.error(forbidden());
                                    });
                });
    }

    private static void validatePublicationReadScope(
            ProductDefinitionPublicationReadAuthorization.Scope scope) {
        if (scope == null
                || !identity(scope.tenantId())
                || !identity(scope.rootId())
                || scope.target() == null)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Exact product publication scope required");
    }

    private void configured() {
        if (!backend.policyProfile().equals(profile)) throw unavailable();
    }

    private void current(ProductDefinitionCommandAuthorization.Admission admission) {
        current(admission.actorExpiresAtEpochMillis(), admission.validUntilEpochMillis());
    }

    private void current(long actorUntil, long permitUntil) {
        if (clock.millis() >= actorUntil) throw unauthenticated();
        if (clock.millis() >= permitUntil) throw forbidden();
    }

    private static void validateScope(ProductDefinitionCommandAuthorization.Scope scope) {
        if (scope == null
                || !identity(scope.tenantId())
                || !identity(scope.rootId())
                || !identity(scope.operationId())
                || scope.command() == null
                || (scope.command().target() == null
                        ? scope.targetId() != null
                        : !identity(scope.targetId())))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Exact product command scope required");
    }

    private static boolean identity(UUID id) {
        return id != null && !id.equals(new UUID(0, 0));
    }

    private static ResponseStatusException unauthenticated() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Current trusted product identity required");
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Current product staff permission required");
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Current product staff policy unavailable");
    }
}

package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionReadAuthorization;
import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionReadAuthorization.*;
import io.fireflysoftware.core.product.core.services.definition.ProductDefinitionServingService;
import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.*;
import io.fireflysoftware.core.product.models.entities.definition.*;
import io.fireflysoftware.core.product.models.repositories.definition.*;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class ProductDefinitionServingServiceImpl implements ProductDefinitionServingService {
    private final ProductDefinitionReadAuthorization authorization;
    private final ProductDefinitionRootRepository roots;
    private final ProductDefinitionRevisionRepository revisions;
    private final ProductDefinitionPublicationRepository publications;
    private final ProductDefinitionPublicationControlRepository controls;
    private final ProductDefinitionReadContextRepository context;
    private final ProductDefinitionVersionIdentityRepository versions;
    private final TransactionalOperator transaction;
    private final AccountTariffServingMapper mapper = new AccountTariffServingMapper();

    public ProductDefinitionServingServiceImpl(
            ProductDefinitionReadAuthorization authorization,
            ProductDefinitionRootRepository roots,
            ProductDefinitionRevisionRepository revisions,
            ProductDefinitionPublicationRepository publications,
            ProductDefinitionPublicationControlRepository controls,
            ProductDefinitionReadContextRepository context,
            ProductDefinitionVersionIdentityRepository versions,
            TransactionalOperator transaction) {
        this.authorization = Objects.requireNonNull(authorization);
        this.roots = Objects.requireNonNull(roots);
        this.revisions = Objects.requireNonNull(revisions);
        this.publications = Objects.requireNonNull(publications);
        this.controls = Objects.requireNonNull(controls);
        this.context = Objects.requireNonNull(context);
        this.versions = Objects.requireNonNull(versions);
        this.transaction = Objects.requireNonNull(transaction);
    }

    @Override
    public Mono<RootView> readRoot(UUID tenantId, UUID rootId) {
        var scope = new Scope(tenantId, rootId, Operation.ROOT, null);
        return read(scope, observation -> root(scope).flatMap(row -> publicHead(row, observation)));
    }

    @Override
    public Mono<RevisionView> readRevision(UUID tenantId, UUID rootId, UUID revisionId) {
        var scope = new Scope(tenantId, rootId, Operation.REVISION, revisionId);
        return read(
                scope,
                observation ->
                        root(scope).flatMap(row -> exactRevision(row, revisionId, observation)));
    }

    @Override
    public Mono<PublicationView> readPublication(UUID tenantId, UUID rootId, UUID publicationId) {
        var scope = new Scope(tenantId, rootId, Operation.PUBLICATION, publicationId);
        return read(
                scope,
                observation ->
                        root(scope)
                                .flatMap(row -> exactPublication(row, publicationId, observation)));
    }

    private Mono<RootView> publicHead(ProductDefinitionRoot root, ReadObservation observation) {
        return revisions
                .findByTenantIdAndRootIdAndRevisionId(
                        root.tenantId(), root.rootId(), root.publicHead())
                .switchIfEmpty(Mono.error(unavailable()))
                .map(revision -> mapper.root(root, revision, observation));
    }

    private Mono<RevisionView> exactRevision(
            ProductDefinitionRoot root, UUID revisionId, ReadObservation observation) {
        return revisions
                .findByTenantIdAndRootIdAndRevisionId(root.tenantId(), root.rootId(), revisionId)
                .switchIfEmpty(Mono.error(notFound()))
                .map(
                        revision -> {
                            require(Objects.equals(revisionId, revision.revisionId()));
                            return mapper.revision(root, revision, observation);
                        });
    }

    private Mono<PublicationView> exactPublication(
            ProductDefinitionRoot root, UUID publicationId, ReadObservation observation) {
        return publications
                .findByTenantIdAndRootIdAndPublicationId(
                        root.tenantId(), root.rootId(), publicationId)
                .switchIfEmpty(Mono.error(notFound()))
                .flatMap(
                        publication -> {
                            require(Objects.equals(publicationId, publication.publicationId()));
                            return versionParent(root, publication)
                                    .then(
                                            Mono.defer(
                                                    () ->
                                                            publicationView(
                                                                    root,
                                                                    publication,
                                                                    observation)));
                        });
    }

    private Mono<Void> versionParent(
            ProductDefinitionRoot root, ProductDefinitionPublication publication) {
        require(publication.productVersionId() != null);
        return versions.findByProductVersionIdAndProductId(
                        publication.productVersionId(), root.productId())
                .switchIfEmpty(Mono.error(unavailable()))
                .flatMap(
                        version -> {
                            require(
                                    Objects.equals(
                                                    version.productVersionId(),
                                                    publication.productVersionId())
                                            && Objects.equals(
                                                    version.productId(), root.productId()));
                            return Mono.empty();
                        });
    }

    private Mono<PublicationView> publicationView(
            ProductDefinitionRoot root,
            ProductDefinitionPublication publication,
            ReadObservation observation) {
        return revisions
                .findByTenantIdAndRootIdAndRevisionId(
                        root.tenantId(), root.rootId(), publication.revisionId())
                .switchIfEmpty(Mono.error(unavailable()))
                .flatMap(revision -> publicationControl(root, publication, revision, observation));
    }

    private Mono<PublicationView> publicationControl(
            ProductDefinitionRoot root,
            ProductDefinitionPublication publication,
            ProductDefinitionRevision revision,
            ReadObservation observation) {
        return controls.findByTenantIdAndRootIdAndPublicationId(
                        root.tenantId(), root.rootId(), publication.publicationId())
                .switchIfEmpty(Mono.error(unavailable()))
                .map(
                        control ->
                                mapper.publication(
                                        root, publication, revision, control, observation));
    }

    private Mono<ProductDefinitionRoot> root(Scope scope) {
        return roots.findByTenantIdAndRootId(scope.tenantId(), scope.rootId())
                .switchIfEmpty(Mono.error(notFound()))
                .map(
                        root -> {
                            require(
                                    Objects.equals(scope.tenantId(), root.tenantId())
                                            && Objects.equals(scope.rootId(), root.rootId()));
                            return root;
                        });
    }

    private <T> Mono<T> read(Scope scope, Function<ReadObservation, Mono<T>> load) {
        return Mono.defer(
                        () ->
                                authorization
                                        .authorize(scope)
                                        .flatMap(
                                                admission ->
                                                        inTransaction(scope, admission, load)
                                                                .flatMap(
                                                                        value ->
                                                                                authorization
                                                                                        .reauthorize(
                                                                                                admission)
                                                                                        .thenReturn(
                                                                                                value))))
                .onErrorMap(
                        error -> error instanceof ResponseStatusException ? error : unavailable());
    }

    private <T> Mono<T> inTransaction(
            Scope scope, Admission admission, Function<ReadObservation, Mono<T>> load) {
        // Buffer through commit and cleanup before the final policy call.
        return transaction
                .execute(
                        status ->
                                open(scope, admission)
                                        .flatMap(
                                                observation ->
                                                        Mono.defer(() -> load.apply(observation)))
                                        .flatMap(value -> current(scope).thenReturn(value)))
                .singleOrEmpty();
    }

    private Mono<ReadObservation> open(Scope scope, Admission admission) {
        return context.open(
                        scope.tenantId(),
                        scope.rootId(),
                        admission.actorExpiresAtEpochMillis(),
                        admission.validUntilEpochMillis())
                .switchIfEmpty(Mono.error(unavailable()))
                .map(stamp -> observation(stamp, admission));
    }

    private Mono<Void> current(Scope scope) {
        return context.isCurrent(scope.tenantId(), scope.rootId())
                .filter(Boolean.TRUE::equals)
                .switchIfEmpty(
                        Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "Current product read permission required")))
                .then();
    }

    private static ReadObservation observation(
            ProductDefinitionReadContextRepository.ReadStamp stamp, Admission admission) {
        Long recorded = stamp.observedAtEpochMillis(), until = stamp.validUntilEpochMillis();
        require(
                recorded != null
                        && until != null
                        && recorded >= 0
                        && recorded < until
                        && until <= 253402300799999L
                        && until <= admission.actorExpiresAtEpochMillis()
                        && until <= admission.validUntilEpochMillis()
                        && until - recorded <= 30000);
        return new ReadObservation(Instant.ofEpochMilli(recorded), Instant.ofEpochMilli(until));
    }

    private static void require(boolean valid) {
        if (!valid) throw unavailable();
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Retained product definition unavailable");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Product definition not found");
    }
}

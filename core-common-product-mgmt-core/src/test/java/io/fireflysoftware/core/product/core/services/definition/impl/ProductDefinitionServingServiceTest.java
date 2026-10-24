package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.*;
import io.fireflysoftware.core.product.models.entities.definition.*;
import io.fireflysoftware.core.product.models.repositories.definition.*;

import org.fireflyframework.security.api.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.reactive.*;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

class ProductDefinitionServingServiceTest {
    static final UUID TENANT = UUID.randomUUID(),
            ROOT = UUID.randomUUID(),
            REVISION = UUID.randomUUID(),
            PUBLICATION = UUID.randomUUID(),
            PRODUCT = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
    final List<String> events = new ArrayList<>();
    final ProductDefinitionRootRepository roots = mock(ProductDefinitionRootRepository.class);
    final ProductDefinitionRevisionRepository revisions =
            mock(ProductDefinitionRevisionRepository.class);
    final ProductDefinitionPublicationRepository publications =
            mock(ProductDefinitionPublicationRepository.class);
    final ProductDefinitionPublicationControlRepository controls =
            mock(ProductDefinitionPublicationControlRepository.class);
    final ProductDefinitionReadContextRepository context =
            mock(ProductDefinitionReadContextRepository.class);
    final ProductDefinitionVersionIdentityRepository versions =
            mock(ProductDefinitionVersionIdentityRepository.class);
    final LocalTransactionManager manager = new LocalTransactionManager();
    int permits = 2;
    boolean commitFails;

    String json() {
        try (var input = getClass().getResourceAsStream("/definition/v1/account.json")) {
            return DefinitionProfiles.canonical(
                            new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .json();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    String digest() {
        return DefinitionProfiles.canonical(json()).digest();
    }

    ProductDefinitionRoot root() {
        return new ProductDefinitionRoot(
                ROOT,
                TENANT,
                PRODUCT,
                "ACCOUNT_TARIFF_V1_DEMO",
                "BANKING",
                "ACCOUNT_TARIFF_V1",
                1,
                "DRAFT",
                2,
                REVISION,
                NOW);
    }

    ProductDefinitionRevision revision(UUID id) {
        return new ProductDefinitionRevision(
                id, TENANT, ROOT, json(), digest(), DefinitionCanonical.ENCODING, null, null, NOW);
    }

    ProductDefinitionServingService service() {
        var principal =
                SecurityPrincipal.builder()
                        .issuer("https://product.example.test")
                        .subject("current-actor")
                        .tenantId(TENANT.toString())
                        .scopes(Set.of("product:definitions:read"))
                        .claims(Map.of("exp", NOW.plusSeconds(300)))
                        .build();
        var auth =
                new ProductDefinitionReadAuthorizationImpl(
                        () -> Mono.just(principal),
                        (p, action, resource, metadata) ->
                                outsideTransaction()
                                        .map(
                                                outside -> {
                                                    assertThat(outside).isTrue();
                                                    events.add("pdp");
                                                    return permits-- > 0
                                                            ? Decision.permit()
                                                            : Decision.deny("current-policy");
                                                }),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Set.of(principal.issuer()));
        var definition = new org.springframework.transaction.support.DefaultTransactionDefinition();
        definition.setReadOnly(true);
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new ProductDefinitionServingServiceImpl(
                auth,
                roots,
                revisions,
                publications,
                controls,
                context,
                versions,
                TransactionalOperator.create(manager, definition));
    }

    Mono<Boolean> outsideTransaction() {
        return TransactionSynchronizationManager.forCurrentTransaction()
                .map(m -> !m.isActualTransactionActive())
                .onErrorReturn(org.springframework.transaction.NoTransactionException.class, true);
    }

    void normalRows() {
        when(versions.findByProductVersionIdAndProductId(any(), any()))
                .thenAnswer(
                        invocation ->
                                Mono.just(
                                        new ProductDefinitionVersionIdentity(
                                                invocation.getArgument(0),
                                                invocation.getArgument(1))));
        when(context.open(eq(TENANT), eq(ROOT), anyLong(), anyLong()))
                .thenReturn(
                        Mono.defer(
                                () -> {
                                    events.add("open");
                                    return Mono.just(
                                            new ProductDefinitionReadContextRepository.ReadStamp(
                                                    NOW.toEpochMilli(),
                                                    NOW.plusSeconds(20).toEpochMilli()));
                                }));
        when(context.isCurrent(TENANT, ROOT))
                .thenReturn(
                        Mono.defer(
                                () -> {
                                    events.add("current");
                                    return Mono.just(true);
                                }));
        when(roots.findByTenantIdAndRootId(TENANT, ROOT))
                .thenReturn(
                        Mono.defer(
                                () -> {
                                    events.add("root");
                                    return Mono.just(root());
                                }));
        when(revisions.findByTenantIdAndRootIdAndRevisionId(TENANT, ROOT, REVISION))
                .thenReturn(
                        Mono.defer(
                                () -> {
                                    events.add("revision");
                                    return Mono.just(revision(REVISION));
                                }));
    }

    void status(Mono<?> action, int expected) {
        assertThatThrownBy(action::block)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        error -> {
                            var response = (ResponseStatusException) error;
                            assertThat(response.getStatusCode().value()).isEqualTo(expected);
                            assertThat(response.getReason())
                                    .doesNotContain("SELECT", "secret", "syntax");
                        });
    }

    @Test
    void currentPolicyRunsBeforeTransactionAndAfterCommit() {
        normalRows();
        var view = service().readRoot(TENANT, ROOT).block();
        assertThat(view.publicHead().revisionId()).isEqualTo(REVISION);
        assertThat(view.operationalAdmission()).isEqualTo(OperationalAdmission.NOT_ADMITTED);
        assertThat(view.observation().databaseRecordedAt()).isEqualTo(NOW);
        assertThat(events)
                .containsExactly(
                        "pdp",
                        "begin",
                        "open",
                        "root",
                        "revision",
                        "current",
                        "commit",
                        "cleanup",
                        "pdp");
    }

    @Test
    void initialDenialNeverOpensDatabaseAndFinalDenialReleasesNoContent() {
        permits = 0;
        status(service().readRoot(TENANT, ROOT), 403);
        verifyNoInteractions(roots, revisions, publications, controls, context);
        assertThat(events).containsExactly("pdp");
        events.clear();
        normalRows();
        permits = 1;
        status(service().readRoot(TENANT, ROOT), 403);
        assertThat(events)
                .containsExactly(
                        "pdp",
                        "begin",
                        "open",
                        "root",
                        "revision",
                        "current",
                        "commit",
                        "cleanup",
                        "pdp");
    }

    @Test
    void missingRequestedRootIsNotFoundAndRollsBack() {
        normalRows();
        when(roots.findByTenantIdAndRootId(TENANT, ROOT)).thenReturn(Mono.empty());
        status(service().readRoot(TENANT, ROOT), 404);
        assertThat(events).contains("rollback", "cleanup").doesNotContain("commit", "revision");
    }

    @Test
    void expiryAtCompletionOrCommitFailureNeverReleasesLoadedResult() {
        normalRows();
        when(context.isCurrent(TENANT, ROOT)).thenReturn(Mono.just(false));
        status(service().readRoot(TENANT, ROOT), 403);
        assertThat(events).contains("rollback").doesNotContain("commit");
        normalRows();
        events.clear();
        permits = 2;
        commitFails = true;
        status(service().readRoot(TENANT, ROOT), 503);
        assertThat(events.stream().filter("pdp"::equals).count()).isEqualTo(1);
        assertThat(events).contains("cleanup");
    }

    @Test
    void databaseFailuresAreSanitizedAndWrongScopeIsRejected() {
        normalRows();
        when(roots.findByTenantIdAndRootId(TENANT, ROOT))
                .thenReturn(
                        Mono.error(new DataAccessResourceFailureException("SELECT secret syntax")));
        status(service().readRoot(TENANT, ROOT), 503);
        normalRows();
        events.clear();
        permits = 2;
        var r = root();
        when(roots.findByTenantIdAndRootId(TENANT, ROOT))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionRoot(
                                        ROOT,
                                        UUID.randomUUID(),
                                        r.productId(),
                                        r.productCode(),
                                        r.businessArea(),
                                        r.profileCode(),
                                        1,
                                        r.phase(),
                                        2,
                                        REVISION,
                                        NOW)));
        status(service().readRoot(TENANT, ROOT), 503);
    }

    @Test
    void exactHistoricalRevisionAndPublicationUseRequestedScopedRows() {
        normalRows();
        UUID historical = UUID.randomUUID();
        when(revisions.findByTenantIdAndRootIdAndRevisionId(TENANT, ROOT, historical))
                .thenReturn(Mono.just(revision(historical)));
        assertThat(service().readRevision(TENANT, ROOT, historical).block().revision().revisionId())
                .isEqualTo(historical);
        events.clear();
        permits = 2;
        when(publications.findByTenantIdAndRootIdAndPublicationId(TENANT, ROOT, PUBLICATION))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionPublication(
                                        PUBLICATION,
                                        TENANT,
                                        ROOT,
                                        UUID.randomUUID(),
                                        REVISION,
                                        UUID.randomUUID(),
                                        json(),
                                        digest(),
                                        DefinitionCanonical.ENCODING,
                                        "NOT_ADMITTED",
                                        NOW)));
        when(controls.findByTenantIdAndRootIdAndPublicationId(TENANT, ROOT, PUBLICATION))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionPublicationControl(
                                        PUBLICATION, TENANT, ROOT, "WITHDRAWN", 5)));
        var view = service().readPublication(TENANT, ROOT, PUBLICATION).block();
        assertThat(view.publication().publicationId()).isEqualTo(PUBLICATION);
        assertThat(view.control().state()).isEqualTo("WITHDRAWN");
        assertThat(view.operationalAdmission()).isEqualTo(OperationalAdmission.NOT_ADMITTED);
    }

    @Test
    void wrongVersionParentMissingVersionAndWrongReturnedVersionIdentityAreUnavailable() {
        for (int mismatch = 0; mismatch < 4; mismatch++) {
            normalRows();
            permits = 2;
            events.clear();
            UUID versionId = UUID.randomUUID();
            when(publications.findByTenantIdAndRootIdAndPublicationId(TENANT, ROOT, PUBLICATION))
                    .thenReturn(
                            Mono.just(
                                    new ProductDefinitionPublication(
                                            PUBLICATION,
                                            TENANT,
                                            ROOT,
                                            UUID.randomUUID(),
                                            REVISION,
                                            versionId,
                                            json(),
                                            digest(),
                                            DefinitionCanonical.ENCODING,
                                            "NOT_ADMITTED",
                                            NOW)));
            when(controls.findByTenantIdAndRootIdAndPublicationId(TENANT, ROOT, PUBLICATION))
                    .thenReturn(
                            Mono.just(
                                    new ProductDefinitionPublicationControl(
                                            PUBLICATION, TENANT, ROOT, "CURRENT", 0)));
            Mono<ProductDefinitionVersionIdentity> result =
                    switch (mismatch) {
                        case 0 ->
                                Mono.just(
                                        new ProductDefinitionVersionIdentity(
                                                versionId, UUID.randomUUID()));
                        case 1 -> Mono.empty();
                        case 2 ->
                                Mono.just(
                                        new ProductDefinitionVersionIdentity(
                                                UUID.randomUUID(), PRODUCT));
                        default ->
                                Mono.error(
                                        new DataAccessResourceFailureException(
                                                "permission denied SELECT private_schema secret"));
                    };
            when(versions.findByProductVersionIdAndProductId(versionId, PRODUCT))
                    .thenReturn(result);
            status(service().readPublication(TENANT, ROOT, PUBLICATION), 503);
            assertThat(events).contains("rollback").doesNotContain("commit");
        }
    }

    final class LocalTransactionManager extends AbstractReactiveTransactionManager {
        protected Object doGetTransaction(TransactionSynchronizationManager synchronization) {
            return new Object();
        }

        protected Mono<Void> doBegin(
                TransactionSynchronizationManager synchronization,
                Object tx,
                TransactionDefinition definition) {
            return Mono.fromRunnable(
                    () -> {
                        assertThat(definition.isReadOnly()).isTrue();
                        assertThat(definition.getIsolationLevel())
                                .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                        events.add("begin");
                    });
        }

        protected Mono<Void> doCommit(
                TransactionSynchronizationManager synchronization, GenericReactiveTransaction tx) {
            return Mono.defer(
                    () -> {
                        events.add("commit");
                        return commitFails
                                ? Mono.error(
                                        new DataAccessResourceFailureException("secret commit"))
                                : Mono.empty();
                    });
        }

        protected Mono<Void> doRollback(
                TransactionSynchronizationManager synchronization, GenericReactiveTransaction tx) {
            return Mono.fromRunnable(() -> events.add("rollback"));
        }

        protected Mono<Void> doCleanupAfterCompletion(
                TransactionSynchronizationManager synchronization, Object tx) {
            return Mono.fromRunnable(() -> events.add("cleanup"));
        }
    }
}

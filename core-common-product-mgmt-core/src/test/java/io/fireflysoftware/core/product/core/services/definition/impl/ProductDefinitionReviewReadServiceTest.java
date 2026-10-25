package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.*;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionLifecycleRows.Submission;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.*;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.*;
import org.springframework.transaction.reactive.*;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.*;
import java.util.*;

class ProductDefinitionReviewReadServiceTest {
    final UUID tenant = UUID.randomUUID(),
            root = UUID.randomUUID(),
            submission = UUID.randomUUID(),
            revision = UUID.randomUUID(),
            cost = UUID.randomUUID();
    final Instant stamp = Instant.parse("2026-10-24T12:00:00Z");
    final List<String> order = new ArrayList<>();
    final ProductDefinitionCommandPersistence persistence =
            mock(ProductDefinitionCommandPersistence.class);
    final ProductDefinitionReviewAuthorization auth =
            mock(ProductDefinitionReviewAuthorization.class);
    final ProductDefinitionDraftRepository drafts = mock(ProductDefinitionDraftRepository.class);
    final ProductDefinitionCostControlRepository controls =
            mock(ProductDefinitionCostControlRepository.class);
    final LifecycleRevisionRepository revisions = mock(LifecycleRevisionRepository.class);
    final LifecycleCostRevisionRepository costs = mock(LifecycleCostRevisionRepository.class);
    final LifecycleSubmissionRepository submissions = mock(LifecycleSubmissionRepository.class);
    final ProductDefinitionReviewReadNativeRepository context =
            mock(ProductDefinitionReviewReadNativeRepository.class);
    final String definition, costJson, publicDigest, costDigest;

    ProductDefinitionReviewReadServiceTest() throws Exception {
        String raw =
                new String(
                        Objects.requireNonNull(
                                        getClass()
                                                .getResourceAsStream("/definition/v1/account.json"))
                                .readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
        var canonical = DefinitionProfiles.canonical(raw);
        definition = canonical.json();
        publicDigest = canonical.digest();
        var privateTerms =
                CostRules.canonical(
                        "{\"schemaVersion\":1,\"businessArea\":\"BANKING\",\"profileCode\":\"ACCOUNT_TARIFF_V1\",\"mode\":\"NONE\",\"lines\":[]}",
                        definition);
        costJson = privateTerms.json();
        costDigest = privateTerms.digest();
        when(persistence.drafts()).thenReturn(drafts);
        when(persistence.costs()).thenReturn(controls);
        when(persistence.lifecycleRevisionRepository()).thenReturn(revisions);
        when(persistence.lifecycleCostRevisionRepository()).thenReturn(costs);
        when(persistence.lifecycleSubmissionRepository()).thenReturn(submissions);
        when(persistence.reviewReadNative()).thenReturn(context);
        var manager =
                new AbstractReactiveTransactionManager() {
                    protected Object doGetTransaction(TransactionSynchronizationManager tx) {
                        return new Object();
                    }

                    protected Mono<Void> doBegin(
                            TransactionSynchronizationManager tx,
                            Object value,
                            TransactionDefinition definition) {
                        assertThat(definition.isReadOnly()).isTrue();
                        assertThat(definition.getIsolationLevel())
                                .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                        return Mono.fromRunnable(() -> order.add("begin"));
                    }

                    protected Mono<Void> doCommit(
                            TransactionSynchronizationManager tx,
                            GenericReactiveTransaction value) {
                        return Mono.fromRunnable(() -> order.add("commit"));
                    }

                    protected Mono<Void> doRollback(
                            TransactionSynchronizationManager tx,
                            GenericReactiveTransaction value) {
                        return Mono.fromRunnable(() -> order.add("rollback"));
                    }
                };
        var tx = new org.springframework.transaction.support.DefaultTransactionDefinition();
        tx.setReadOnly(true);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        when(persistence.readTransactions()).thenReturn(TransactionalOperator.create(manager, tx));
        var actor =
                SecurityPrincipal.builder()
                        .issuer("https://issuer.example.invalid")
                        .subject("independent-checker")
                        .tenantId(tenant.toString())
                        .build();
        when(auth.authorize(any()))
                .thenAnswer(
                        call ->
                                outside("authorize")
                                        .thenReturn(
                                                new ProductDefinitionReviewAuthorization.Admission(
                                                        actor,
                                                        call.getArgument(0),
                                                        stamp.plusSeconds(300).toEpochMilli(),
                                                        stamp.plusSeconds(30).toEpochMilli())));
        when(auth.reauthorize(any())).thenAnswer(call -> outside("reauthorize"));
        when(context.open(
                        eq(tenant),
                        eq(root),
                        eq(submission),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyLong(),
                        anyString()))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionReviewReadNativeRepository.ReadContext(
                                        UUID.randomUUID(), stamp)));
        when(context.isCurrent(any())).thenReturn(Mono.just(true));
        when(drafts.findByTenantIdAndRootId(tenant, root))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionDraftRoot(
                                        root,
                                        tenant,
                                        UUID.randomUUID(),
                                        DefinitionProfiles.decode(definition).common().code(),
                                        "BANKING",
                                        "ACCOUNT_TARIFF_V1",
                                        1,
                                        "IN_REVIEW",
                                        3,
                                        revision,
                                        null,
                                        stamp.minusSeconds(100))));
        when(submissions.findByTenantIdAndRootIdAndSubmissionId(tenant, root, submission))
                .thenReturn(
                        Mono.just(
                                new Submission(
                                        submission,
                                        tenant,
                                        root,
                                        revision,
                                        publicDigest,
                                        3,
                                        "https://issuer.example.invalid",
                                        "maker",
                                        stamp.minusSeconds(50))));
        when(revisions.findByTenantIdAndRootIdAndRevisionId(tenant, root, revision))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionDraftRevision(
                                        revision,
                                        tenant,
                                        root,
                                        definition,
                                        publicDigest,
                                        DefinitionCanonical.ENCODING,
                                        null,
                                        null,
                                        "https://issuer.example.invalid",
                                        "maker",
                                        stamp.minusSeconds(80))));
        when(controls.findByTenantIdAndRootId(tenant, root))
                .thenReturn(Mono.just(new ProductDefinitionCostControl(root, tenant, cost, 2)));
        when(costs.findByTenantIdAndRootIdAndCostRevisionId(tenant, root, cost))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionCostRevision(
                                        cost,
                                        tenant,
                                        root,
                                        costJson,
                                        costDigest,
                                        DefinitionCanonical.ENCODING,
                                        "https://issuer.example.invalid",
                                        "cost-maker",
                                        stamp.minusSeconds(40))));
    }

    Mono<Void> outside(String event) {
        return TransactionSynchronizationManager.forCurrentTransaction()
                .map(TransactionSynchronizationManager::isActualTransactionActive)
                .onErrorResume(NoTransactionException.class, error -> Mono.just(false))
                .doOnNext(
                        active -> {
                            assertThat(active).isFalse();
                            order.add(event);
                        })
                .then();
    }

    ProductDefinitionReviewReadService service() {
        try {
            return (ProductDefinitionReviewReadService)
                    Class.forName(
                                    "io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionReviewReadServiceImpl")
                            .getConstructor(
                                    ProductDefinitionReviewAuthorization.class,
                                    ProductDefinitionCommandPersistence.class)
                            .newInstance(auth, persistence);
        } catch (ReflectiveOperationException missing) {
            throw new AssertionError("Missing actual checker selection read service", missing);
        }
    }

    @Test
    void checkerReadsActualExactPublicAndPrivateSnapshotsAndImmutableSelectionBinding() {
        var result = service().readSelection(tenant, root, submission).block();
        assertThat(result).isNotNull();
        assertThat(result.selection().revisionId()).isEqualTo(revision);
        assertThat(result.selection().costRevisionId()).isEqualTo(cost);
        assertThat(result.selection().publicVersion()).isEqualTo(3);
        assertThat(result.selection().costVersion()).isEqualTo(2);
        assertThat(DefinitionCanonical.encode(DefinitionCanonical.tree(result.definition())))
                .isEqualTo(definition);
        assertThat(DefinitionCanonical.encode(DefinitionCanonical.tree(result.cost())))
                .isEqualTo(costJson);
        assertThat(result.selectionFingerprint())
                .isEqualTo(
                        DefinitionCanonical.hash(
                                "product-definition-selection-v1",
                                DefinitionCanonical.encode(
                                        DefinitionCanonical.tree(result.selection()))));
        assertThat(result.observedAt()).isEqualTo(stamp);
        assertThat(result.validationBoundary()).isEqualTo("TRUSTED_SERVICE");
        assertThat(order.indexOf("authorize")).isLessThan(order.indexOf("begin"));
        assertThat(order.indexOf("commit")).isLessThan(order.indexOf("reauthorize"));
        verify(persistence, never()).operations();
    }

    @Test
    void staleSubmissionDoesNotSilentlySelectNewerPublicOrPrivateTerms() {
        when(submissions.findByTenantIdAndRootIdAndSubmissionId(tenant, root, submission))
                .thenReturn(
                        Mono.just(
                                new Submission(
                                        submission,
                                        tenant,
                                        root,
                                        UUID.randomUUID(),
                                        publicDigest,
                                        2,
                                        "issuer",
                                        "maker",
                                        stamp)));
        StepVerifier.create(service().readSelection(tenant, root, submission))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(409))
                .verify();
        verifyNoInteractions(controls, costs);
        assertThat(order).contains("rollback");
    }

    @Test
    void everyReadReauthorizesAfterCompletedTransactionAndDoesNotCachePrivatePayload() {
        var service = service();
        service.readSelection(tenant, root, submission).block();
        service.readSelection(tenant, root, submission).block();
        verify(auth, times(2)).authorize(any());
        verify(auth, times(2)).reauthorize(any());
        verify(context, times(2))
                .open(
                        any(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyLong(),
                        anyString());
    }

    @Test
    void revokedPostReadVisibilityCannotExposePrivateSnapshot() {
        when(auth.reauthorize(any()))
                .thenReturn(
                        Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.FORBIDDEN, "current denial")));
        StepVerifier.create(service().readSelection(tenant, root, submission))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(403))
                .verify();
        assertThat(order).contains("commit");
    }

    @Test
    void expiredNativeContextReturnsNoPrivatePayload() {
        when(context.isCurrent(any())).thenReturn(Mono.just(false));
        StepVerifier.create(service().readSelection(tenant, root, submission))
                .expectError(ResponseStatusException.class)
                .verify();
        verifyNoInteractions(drafts, submissions, costs, controls);
    }

    @Test
    void ambientTransactionIsRejectedBeforeAuthorityOrPrivateDatabaseAccess() {
        var outer =
                TransactionalOperator.create(
                        new ProductDefinitionLifecycleServiceTest.RecordingManager(order));
        StepVerifier.create(outer.transactional(service().readSelection(tenant, root, submission)))
                .expectError(ResponseStatusException.class)
                .verify();
        verifyNoInteractions(auth, context);
    }
}

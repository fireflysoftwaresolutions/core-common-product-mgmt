package io.fireflysoftware.core.product.models.repositories.definition.lifecycle.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionDraftWrites;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

class ProductDefinitionDraftCountTest {
    private Mono<Void> update(Mono<Long> result) {
        var operations = mock(R2dbcEntityOperations.class);
        when(operations.update(any(Query.class), any(Update.class), any(Class.class)))
                .thenReturn(result);
        return new ProductDefinitionDraftWritesImpl(operations)
                .replaceCostHead(
                        new ProductDefinitionDraftWrites.CostHeadChange(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                1,
                                UUID.randomUUID()));
    }

    @Test
    void exactlyOneUpdatedRowCompletes() {
        StepVerifier.create(update(Mono.just(1L))).verifyComplete();
    }

    @Test
    void noUpdatedRowCannotBecomeSuccessfulCas() {
        StepVerifier.create(update(Mono.just(0L)))
                .expectError(org.springframework.dao.OptimisticLockingFailureException.class)
                .verify();
    }

    @Test
    void moreThanOneUpdatedRowIsAnIntegrityFailure() {
        StepVerifier.create(update(Mono.just(2L)))
                .expectError(org.springframework.dao.DataIntegrityViolationException.class)
                .verify();
    }

    @Test
    void absentCountCannotBecomeSuccessfulCas() {
        StepVerifier.create(update(Mono.empty()))
                .expectError(org.springframework.dao.DataIntegrityViolationException.class)
                .verify();
    }

    @Test
    void privateReviewPointerCasCannotSucceedWithoutAffectedRowEvidence() {
        var ops = mock(R2dbcEntityOperations.class);
        when(ops.update(any(Query.class), any(Update.class), any(Class.class)))
                .thenReturn(Mono.just(0L));
        var change =
                new ProductDefinitionDraftWrites.ActiveReviewChange(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        null,
                        UUID.randomUUID());
        StepVerifier.create(new ProductDefinitionDraftWritesImpl(ops).replaceActiveReview(change))
                .expectError(org.springframework.dao.OptimisticLockingFailureException.class)
                .verify();
    }

    @Test
    void publicHeadMutationClearsPriorPrivateApprovalWithoutChangingCostState() {
        var ops = mock(R2dbcEntityOperations.class);
        when(ops.update(any(Query.class), any(Update.class), any(Class.class)))
                .thenReturn(Mono.just(1L));
        var change =
                new ProductDefinitionDraftWrites.PublicHeadChange(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        "DRAFT",
                        UUID.randomUUID(),
                        "IN_REVIEW");
        StepVerifier.create(new ProductDefinitionDraftWritesImpl(ops).replacePublicHead(change))
                .verifyComplete();
        var query = org.mockito.ArgumentCaptor.forClass(Query.class);
        var update = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(ops)
                .update(
                        query.capture(),
                        update.capture(),
                        eq(
                                io.fireflysoftware.core.product.models.entities.definition.lifecycle
                                        .ProductDefinitionDraftRoot.class));
        var mapping = new org.springframework.data.r2dbc.mapping.R2dbcMappingContext();
        var conversions =
                org.springframework.data.r2dbc.convert.R2dbcCustomConversions.of(
                        org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE,
                        java.util.List.of());
        mapping.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        var strategy =
                new org.springframework.data.r2dbc.core.DefaultReactiveDataAccessStrategy(
                        org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE,
                        new org.springframework.data.r2dbc.convert.MappingR2dbcConverter(
                                mapping, conversions));
        var type =
                io.fireflysoftware.core.product.models.entities.definition.lifecycle
                        .ProductDefinitionDraftRoot.class;
        var mapper = strategy.getStatementMapper().forType(type);
        String sql =
                mapper.getMappedObject(
                                mapper.createUpdate(strategy.getTableName(type), update.getValue())
                                        .withCriteria(query.getValue().getCriteria().orElseThrow()))
                        .toQuery()
                        .replace("\"", "");
        assertThat(sql)
                .contains(
                        "active_review_id =",
                        "tenant_id =",
                        "root_id =",
                        "public_state_version =",
                        "public_head =",
                        "phase =")
                .doesNotContain("cost_head", "cost_state_version");
    }

    @Test
    void withdrawalCasRequiresOneAffectedRow() {
        var ops = mock(R2dbcEntityOperations.class);
        when(ops.update(any(Query.class), any(Update.class), any(Class.class)))
                .thenReturn(Mono.just(0L));
        var change =
                new ProductDefinitionDraftWrites.PublicationStateChange(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3);
        StepVerifier.create(new ProductDefinitionDraftWritesImpl(ops).withdrawPublication(change))
                .expectError(org.springframework.dao.OptimisticLockingFailureException.class)
                .verify();
    }
}

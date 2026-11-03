package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionLifecycleRows.Submission;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionReviewReadNativeRepository;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ProductDefinitionCurrentSelectionTest {
    final ProductDefinitionReviewReadServiceTest f;

    ProductDefinitionCurrentSelectionTest() throws Exception {
        f = new ProductDefinitionReviewReadServiceTest();
        when(f.context.openCurrent(
                        eq(f.tenant),
                        eq(f.root),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyLong(),
                        anyString()))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionReviewReadNativeRepository.ReadContext(
                                        java.util.UUID.randomUUID(), f.stamp)));
        when(f.context.isCurrentSelectionLive(any())).thenReturn(Mono.just(true));
        when(f.submissions.findTop2ByTenantIdAndRootIdAndRevisionIdAndPublicVersion(
                        f.tenant, f.root, f.revision, 3))
                .thenReturn(
                        Flux.just(
                                new Submission(
                                        f.submission,
                                        f.tenant,
                                        f.root,
                                        f.revision,
                                        f.publicDigest,
                                        3,
                                        "https://issuer.example.invalid",
                                        "maker",
                                        f.stamp.minusSeconds(10))));
    }

    @Test
    void differentCheckerDiscoversTheRealCurrentSubmissionWithoutAnyClientHeldSubmissionId() {
        var view = f.service().readCurrentSelection(f.tenant, f.root).block();
        assertThat(view).isNotNull();
        assertThat(view.selection().submissionId()).isEqualTo(f.submission);
        assertThat(view.selection().revisionId()).isEqualTo(f.revision);
        assertThat(view.selection().costRevisionId()).isEqualTo(f.cost);
        verify(f.auth)
                .authorize(ProductDefinitionReviewAuthorization.Scope.current(f.tenant, f.root));
        verify(f.context)
                .openCurrent(
                        any(), any(), anyString(), anyString(), anyLong(), anyLong(), anyString());
        verify(f.context, never())
                .open(
                        any(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyLong(),
                        anyString());
        assertThat(f.order.indexOf("commit")).isLessThan(f.order.indexOf("reauthorize"));
    }

    @Test
    void duplicateCurrentTupleIsUnavailableRatherThanChoosingOneSubmission() {
        var row =
                new Submission(
                        f.submission,
                        f.tenant,
                        f.root,
                        f.revision,
                        f.publicDigest,
                        3,
                        "issuer",
                        "maker",
                        f.stamp);
        when(f.submissions.findTop2ByTenantIdAndRootIdAndRevisionIdAndPublicVersion(
                        f.tenant, f.root, f.revision, 3))
                .thenReturn(Flux.just(row, row));
        StepVerifier.create(f.service().readCurrentSelection(f.tenant, f.root))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(503))
                .verify();
        verifyNoInteractions(f.costs, f.controls);
    }

    @Test
    void expiredCurrentSelectionContextDoesNotReachMappedReads() {
        when(f.context.isCurrentSelectionLive(any())).thenReturn(Mono.just(false));
        StepVerifier.create(f.service().readCurrentSelection(f.tenant, f.root))
                .expectError(ResponseStatusException.class)
                .verify();
        verifyNoInteractions(f.drafts, f.submissions, f.costs);
    }

    @Test
    void currentSelectionRequiresAnActualInReviewTupleBeforePrivateCostReads() {
        var previous = f.drafts.findByTenantIdAndRootId(f.tenant, f.root).block();
        when(f.drafts.findByTenantIdAndRootId(f.tenant, f.root))
                .thenReturn(
                        Mono.just(
                                new io.fireflysoftware.core.product.models.entities.definition
                                        .lifecycle.ProductDefinitionDraftRoot(
                                        previous.rootId(),
                                        previous.tenantId(),
                                        previous.productId(),
                                        previous.productCode(),
                                        previous.businessArea(),
                                        previous.profileCode(),
                                        previous.profileVersion(),
                                        "DRAFT",
                                        previous.publicStateVersion(),
                                        previous.publicHead(),
                                        previous.activeReviewId(),
                                        previous.createdAt())));
        StepVerifier.create(f.service().readCurrentSelection(f.tenant, f.root))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(409))
                .verify();
        verifyNoInteractions(f.controls, f.costs);
    }

    @Test
    void observedActiveReviewPointerSupportsResumptionWithoutPretendingItIsAdmission() {
        var previous = f.drafts.findByTenantIdAndRootId(f.tenant, f.root).block();
        var actualReview = java.util.UUID.randomUUID();
        when(f.drafts.findByTenantIdAndRootId(f.tenant, f.root))
                .thenReturn(
                        Mono.just(
                                new io.fireflysoftware.core.product.models.entities.definition
                                        .lifecycle.ProductDefinitionDraftRoot(
                                        previous.rootId(),
                                        previous.tenantId(),
                                        previous.productId(),
                                        previous.productCode(),
                                        previous.businessArea(),
                                        previous.profileCode(),
                                        previous.profileVersion(),
                                        previous.phase(),
                                        previous.publicStateVersion(),
                                        previous.publicHead(),
                                        actualReview,
                                        previous.createdAt())));
        var view = f.service().readCurrentSelection(f.tenant, f.root).block();
        assertThat(view).isNotNull();
        assertThat(view.activeReviewId()).isEqualTo(actualReview);
        assertThat(view.validationBoundary()).isEqualTo("TRUSTED_SERVICE");
    }
}

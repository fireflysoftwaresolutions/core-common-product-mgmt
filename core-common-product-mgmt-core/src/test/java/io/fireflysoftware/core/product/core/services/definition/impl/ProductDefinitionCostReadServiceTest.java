package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionDraftRoot;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.*;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ProductDefinitionCostReadServiceTest {
    final ProductDefinitionReviewReadServiceTest f;
    final ProductDefinitionCostReadAuthorization auth =
            mock(ProductDefinitionCostReadAuthorization.class);

    ProductDefinitionCostReadServiceTest() throws Exception {
        f = new ProductDefinitionReviewReadServiceTest();
        var previous = f.drafts.findByTenantIdAndRootId(f.tenant, f.root).block();
        when(f.drafts.findByTenantIdAndRootId(f.tenant, f.root))
                .thenReturn(
                        Mono.just(
                                new ProductDefinitionDraftRoot(
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
        clearInvocations(f.drafts);
        var actor =
                SecurityPrincipal.builder()
                        .issuer("https://issuer.example.invalid")
                        .subject("cost-editor")
                        .tenantId(f.tenant.toString())
                        .scopes(
                                java.util.Set.of(
                                        "product:definitions:cost:read",
                                        "product:definitions:cost:write"))
                        .build();
        when(auth.authorize(any()))
                .thenAnswer(
                        call ->
                                f.outside("authorize")
                                        .thenReturn(
                                                new ProductDefinitionCostReadAuthorization
                                                        .Admission(
                                                        actor,
                                                        call.getArgument(0),
                                                        f.stamp.plusSeconds(300).toEpochMilli(),
                                                        f.stamp.plusSeconds(30).toEpochMilli())));
        when(auth.reauthorize(any())).thenAnswer(call -> f.outside("reauthorize"));
        when(f.context.openCost(
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
        when(f.context.isCostCurrent(any())).thenReturn(Mono.just(true));
    }

    ProductDefinitionCostReadService service() {
        try {
            return (ProductDefinitionCostReadService)
                    Class.forName(
                                    "io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionCostReadServiceImpl")
                            .getConstructor(
                                    ProductDefinitionCostReadAuthorization.class,
                                    ProductDefinitionCommandPersistence.class)
                            .newInstance(auth, f.persistence);
        } catch (ReflectiveOperationException missing) {
            throw new AssertionError("Missing ordinary cost editor current read", missing);
        }
    }

    @Test
    void ordinaryEditorReopensActualCurrentCostAndPublicContextWithExactImmutableReferences() {
        var result = service().readCurrent(f.tenant, f.root).block();
        assertThat(result).isNotNull();
        assertThat(result.phase()).isEqualTo("DRAFT");
        assertThat(result.publicRevisionId()).isEqualTo(f.revision);
        assertThat(result.costRevisionId()).isEqualTo(f.cost);
        assertThat(result.costVersion()).isEqualTo(2);
        assertThat(DefinitionCanonical.encode(DefinitionCanonical.tree(result.publicDefinition())))
                .isEqualTo(f.definition);
        assertThat(DefinitionCanonical.encode(DefinitionCanonical.tree(result.cost())))
                .isEqualTo(f.costJson);
        assertThat(result.observedAt()).isEqualTo(f.stamp);
        verifyNoInteractions(f.submissions);
        verify(f.persistence, never()).operations();
        assertThat(f.order.indexOf("commit")).isLessThan(f.order.indexOf("reauthorize"));
    }

    @Test
    void everyCostReadRechecksCurrentVisibilityAndDoesNotRequireAReviewSubmission() {
        var service = service();
        service.readCurrent(f.tenant, f.root).block();
        service.readCurrent(f.tenant, f.root).block();
        verify(auth, times(2)).authorize(any());
        verify(auth, times(2)).reauthorize(any());
        verifyNoInteractions(f.submissions);
    }

    @Test
    void postReadDenialDoesNotExposePrivateCostPayload() {
        when(auth.reauthorize(any()))
                .thenReturn(
                        Mono.error(
                                new ResponseStatusException(HttpStatus.FORBIDDEN, "current deny")));
        StepVerifier.create(service().readCurrent(f.tenant, f.root))
                .expectErrorSatisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(403))
                .verify();
        assertThat(f.order).contains("commit");
    }

    @Test
    void expiredReadCostContextIsDeniedBeforeOrdinaryRowReads() {
        when(f.context.isCostCurrent(any())).thenReturn(Mono.just(false));
        StepVerifier.create(service().readCurrent(f.tenant, f.root))
                .expectError(ResponseStatusException.class)
                .verify();
        verifyNoInteractions(f.drafts, f.controls, f.costs, f.submissions);
    }

    @Test
    void ambientSqlTransactionIsRejectedBeforeAuthorityOrPrivateReads() {
        var outer =
                TransactionalOperator.create(
                        new ProductDefinitionLifecycleServiceTest.RecordingManager(f.order));
        StepVerifier.create(outer.transactional(service().readCurrent(f.tenant, f.root)))
                .expectError(ResponseStatusException.class)
                .verify();
        verifyNoInteractions(auth, f.context);
    }
}

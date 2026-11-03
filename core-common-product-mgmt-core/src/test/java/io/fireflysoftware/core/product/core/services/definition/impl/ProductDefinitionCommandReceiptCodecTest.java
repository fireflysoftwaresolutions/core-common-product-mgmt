package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.*;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class ProductDefinitionCommandReceiptCodecTest {
    final ProductDefinitionLifecycleContracts.Scope scope =
            new ProductDefinitionLifecycleContracts.Scope(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    final UUID action = UUID.randomUUID(),
            revision = UUID.randomUUID(),
            publication = UUID.randomUUID(),
            version = UUID.randomUUID();

    PublicReceipt publication(String state, int stateVersion, Long number) {
        return new PublicReceipt(
                scope,
                ProductDefinitionCommand.PUBLISH,
                action,
                "2026-10-24T12:00:00Z",
                "PUBLISHED",
                2,
                revision,
                publication,
                version,
                number,
                state,
                stateVersion,
                "NOT_ADMITTED");
    }

    @Test
    void immutablePublicationReceiptRetainsExactPositiveVersionAndCurrentAcceptanceState() {
        var value = publication("ACTIVE", 0, Long.MAX_VALUE);
        assertThat(
                        ProductDefinitionCommandReceiptCodec.decode(
                                ProductDefinitionCommandReceiptCodec.encode(value),
                                ProductDefinitionCommand.PUBLISH))
                .isEqualTo(value);
    }

    @Test
    void impossiblePublicationCrossFieldsCannotBeAcceptedOnReplay() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionCommandReceiptCodec.encode(
                                        publication("WITHDRAWN", 0, 17L)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionCommandReceiptCodec.encode(
                                        publication("ACTIVE", 1, 17L)));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionCommandReceiptCodec.encode(
                                        publication("ACTIVE", 0, 0L)));
    }

    @Test
    void publicDraftCannotCarryPublicationFieldsOrClaimAnotherPhase() {
        var value =
                new PublicReceipt(
                        scope,
                        ProductDefinitionCommand.CREATE_DRAFT,
                        action,
                        "2026-10-24T12:00:00Z",
                        "PUBLISHED",
                        0,
                        revision,
                        publication,
                        version,
                        1L,
                        "ACTIVE",
                        0,
                        "NOT_ADMITTED");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProductDefinitionCommandReceiptCodec.encode(value));
    }

    @Test
    void privateDecisionMustMatchCommandAndHaveImmutableReviewIdentity() {
        var value =
                new PrivateReceipt(
                        scope,
                        ProductDefinitionCommand.APPROVE_SELECTION,
                        action,
                        "2026-10-24T12:00:00Z",
                        revision,
                        0,
                        null,
                        "REJECTED");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProductDefinitionCommandReceiptCodec.encode(value));
    }

    @Test
    void zeroScopeIdentitiesAndTrailingReceiptValuesAreRejected() {
        var zero =
                new ProductDefinitionLifecycleContracts.Scope(
                        new UUID(0, 0), scope.rootId(), scope.operationId());
        var value =
                new PublicReceipt(
                        zero,
                        ProductDefinitionCommand.CREATE_DRAFT,
                        action,
                        "2026-10-24T12:00:00Z",
                        "DRAFT",
                        0,
                        revision,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "NOT_ADMITTED");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProductDefinitionCommandReceiptCodec.encode(value));
        var raw = ProductDefinitionCommandReceiptCodec.encode(publication("ACTIVE", 0, 17L));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionCommandReceiptCodec.decode(
                                        raw + "{}", ProductDefinitionCommand.PUBLISH));
    }
}

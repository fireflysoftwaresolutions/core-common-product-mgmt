package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationDiscoveryView;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationDiscoveryView.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationReadinessView;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionPublicationReadinessView.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

class ProductDefinitionPublicationViewsTest {
    private final UUID tenant = UUID.randomUUID();
    private final UUID root = UUID.randomUUID();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final ReadObservation observation =
            new ReadObservation(
                    Instant.parse("2026-10-25T00:00:00Z"), Instant.parse("2026-10-25T00:00:30Z"));

    @Test
    void notReadyDoesNotSerializeSelectionCoordinatesOrPrivateReason() {
        var tree =
                json.valueToTree(
                        new ProductDefinitionPublicationReadinessView(
                                tenant,
                                root,
                                PublicationReadinessStatus.NOT_READY,
                                null,
                                observation,
                                OperationalAdmission.NOT_ADMITTED));
        assertThat(tree.has("selection")).isFalse();
        assertThat(tree.toString())
                .doesNotContain(
                        "reviewId",
                        "costVersion",
                        "reason",
                        "costDigest",
                        "costRevisionId",
                        "selectionFingerprint",
                        "actorIssuer");
    }

    @Test
    void readyExposesOnlyExactPublishCoordinates() {
        var selected = new PublicationReadySelection(UUID.randomUUID(), 4, 2);
        var tree =
                json.valueToTree(
                        new ProductDefinitionPublicationReadinessView(
                                tenant,
                                root,
                                PublicationReadinessStatus.READY,
                                selected,
                                observation,
                                OperationalAdmission.NOT_ADMITTED));
        assertThat(tree.at("/selection/reviewId").asText())
                .isEqualTo(selected.reviewId().toString());
        assertThat(tree.at("/selection/publicVersion").asInt()).isEqualTo(4);
        assertThat(tree.at("/selection/costVersion").asInt()).isEqualTo(2);
        assertThat(tree.get("selection").size()).isEqualTo(3);
        assertThat(tree.toString())
                .doesNotContain(
                        "costDigest",
                        "costRevisionId",
                        "selectionFingerprint",
                        "reason",
                        "actorIssuer",
                        "amount",
                        "withdrawable");
    }

    @Test
    void readinessCannotContradictSelectionPresence() {
        var selection = new PublicationReadySelection(UUID.randomUUID(), 4, 2);
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new ProductDefinitionPublicationReadinessView(
                                        tenant,
                                        root,
                                        PublicationReadinessStatus.READY,
                                        null,
                                        observation,
                                        OperationalAdmission.NOT_ADMITTED));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new ProductDefinitionPublicationReadinessView(
                                        tenant,
                                        root,
                                        PublicationReadinessStatus.NOT_READY,
                                        selection,
                                        observation,
                                        OperationalAdmission.NOT_ADMITTED));
    }

    @Test
    void publishCoordinatesRequireSubmittedPublicVersionAndNonnegativeCostVersion() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PublicationReadySelection(UUID.randomUUID(), 0, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PublicationReadySelection(UUID.randomUUID(), 1, -1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PublicationReadySelection(new UUID(0, 0), 1, 0));
        assertThat(new PublicationReadySelection(UUID.randomUUID(), 1, 0).costVersion()).isZero();
    }

    @Test
    void noneHasNoPublicationAndActiveDoesNotGrantWithdrawability() {
        var none =
                json.valueToTree(
                        new ProductDefinitionPublicationDiscoveryView(
                                tenant,
                                root,
                                PublicationDiscoveryState.NONE,
                                null,
                                observation,
                                OperationalAdmission.NOT_ADMITTED));
        assertThat(none.has("publication")).isFalse();
        var active =
                json.valueToTree(
                        new ProductDefinitionPublicationDiscoveryView(
                                tenant,
                                root,
                                PublicationDiscoveryState.ACTIVE,
                                new DiscoveredPublication(UUID.randomUUID(), 0),
                                observation,
                                OperationalAdmission.NOT_ADMITTED));
        assertThat(active.toString())
                .doesNotContain("withdrawable", "permission", "cost", "reason");
        assertThat(active.at("/publication/stateVersion").asInt()).isZero();
    }

    @Test
    void retainedWithdrawalRequiresAnAdvancedControlVersion() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new ProductDefinitionPublicationDiscoveryView(
                                        tenant,
                                        root,
                                        PublicationDiscoveryState.RETAINED_WITHDRAWN,
                                        new DiscoveredPublication(UUID.randomUUID(), 0),
                                        observation,
                                        OperationalAdmission.NOT_ADMITTED));
        var view =
                new ProductDefinitionPublicationDiscoveryView(
                        tenant,
                        root,
                        PublicationDiscoveryState.RETAINED_WITHDRAWN,
                        new DiscoveredPublication(UUID.randomUUID(), 1),
                        observation,
                        OperationalAdmission.NOT_ADMITTED);
        assertThat(view.state()).isEqualTo(PublicationDiscoveryState.RETAINED_WITHDRAWN);
    }

    @Test
    void discoveryCannotContradictPublicationPresence() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new ProductDefinitionPublicationDiscoveryView(
                                        tenant,
                                        root,
                                        PublicationDiscoveryState.ACTIVE,
                                        null,
                                        observation,
                                        OperationalAdmission.NOT_ADMITTED));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new ProductDefinitionPublicationDiscoveryView(
                                        tenant,
                                        root,
                                        PublicationDiscoveryState.NONE,
                                        new DiscoveredPublication(UUID.randomUUID(), 1),
                                        observation,
                                        OperationalAdmission.NOT_ADMITTED));
    }

    @Test
    void observationAndOwningScopeAreRequired() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new ProductDefinitionPublicationReadinessView(
                                        tenant,
                                        root,
                                        PublicationReadinessStatus.NOT_READY,
                                        null,
                                        null,
                                        OperationalAdmission.NOT_ADMITTED));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new ProductDefinitionPublicationDiscoveryView(
                                        tenant,
                                        new UUID(0, 0),
                                        PublicationDiscoveryState.NONE,
                                        null,
                                        observation,
                                        OperationalAdmission.NOT_ADMITTED));
    }
}

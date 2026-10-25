/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.core.product.core.services.definition;

import static io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleContracts.*;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.interfaces.dtos.definition.CostTerms;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

class ProductDefinitionLifecycleRulesTest {
    private final Scope scope = new Scope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    private final UUID product = UUID.randomUUID();
    private final UUID category = UUID.randomUUID();

    private CreateDraft draft() {
        return new CreateDraft(
                scope,
                product,
                category,
                DefinitionProfiles.decode(ProfileFixtures.load("account").toString()),
                new CostTerms(1, "BANKING", "ACCOUNT_TARIFF_V1", "NONE", List.of()));
    }

    @Test
    void completeDraftRetainsActualCanonicalPublicPrivateAndScopedOriginalIntent() {
        var request = draft();
        var actual = ProductDefinitionLifecycleRules.prepare(request);
        assertThat(actual.canonicalDefinition())
                .isEqualTo(
                        DefinitionProfiles.canonical(
                                        DefinitionCanonical.tree(request.definition()).toString())
                                .json());
        assertThat(actual.canonicalCost()).contains("NONE");
        assertThat(actual.canonicalIntent())
                .contains(
                        scope.tenantId().toString(),
                        scope.rootId().toString(),
                        scope.operationId().toString(),
                        product.toString(),
                        category.toString(),
                        "CREATE_DRAFT");
        assertThat(actual.intentDigest())
                .isEqualTo(
                        DefinitionCanonical.intentDigest(
                                DefinitionCanonical.parse(actual.canonicalIntent())));
        assertThat(actual.publicDigest()).hasSize(64);
        assertThat(actual.costDigest()).hasSize(64);
    }

    @Test
    void operationIdentityCannotDisappearFromFingerprint() {
        var a = draft();
        var b =
                new CreateDraft(
                        new Scope(scope.tenantId(), scope.rootId(), UUID.randomUUID()),
                        product,
                        category,
                        a.definition(),
                        a.cost());
        assertThat(ProductDefinitionLifecycleRules.prepare(a).intentDigest())
                .isNotEqualTo(ProductDefinitionLifecycleRules.prepare(b).intentDigest());
    }

    @Test
    void incompleteDraftAndZeroTenantFailBeforePersistence() {
        var a = draft();
        for (var bad :
                List.of(
                        new CreateDraft(scope, product, category, null, a.cost()),
                        new CreateDraft(scope, product, category, a.definition(), null),
                        new CreateDraft(
                                new Scope(new UUID(0, 0), scope.rootId(), scope.operationId()),
                                product,
                                category,
                                a.definition(),
                                a.cost())))
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ProductDefinitionLifecycleRules.prepare(bad));
    }

    @Test
    void otherSupportedProfileCannotEnterAccountLifecycle() {
        var a = draft();
        var bad =
                new CreateDraft(
                        scope,
                        product,
                        category,
                        DefinitionProfiles.decode(ProfileFixtures.load("loan").toString()),
                        a.cost());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProductDefinitionLifecycleRules.prepare(bad));
    }

    @Test
    void mismatchedPrivateProfileUsesActualCostValidation() {
        var a = draft();
        var bad =
                new CreateDraft(
                        scope,
                        product,
                        category,
                        a.definition(),
                        new CostTerms(1, "LENDING", "ACCOUNT_TARIFF_V1", "NONE", List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProductDefinitionLifecycleRules.prepare(bad));
    }

    @Test
    void publicTransitionRequiresSubmissionAndPermitsNewDraftAfterPublication() {
        assertThat(
                        ProductDefinitionLifecycleRules.phaseAfter(
                                Phase.DRAFT, PublicCommand.SUBMIT_PUBLIC))
                .isEqualTo(Phase.IN_REVIEW);
        assertThat(
                        ProductDefinitionLifecycleRules.phaseAfter(
                                Phase.IN_REVIEW, PublicCommand.PUBLISH))
                .isEqualTo(Phase.PUBLISHED);
        assertThat(
                        ProductDefinitionLifecycleRules.phaseAfter(
                                Phase.PUBLISHED, PublicCommand.EDIT_PUBLIC))
                .isEqualTo(Phase.DRAFT);
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionLifecycleRules.phaseAfter(
                                        Phase.DRAFT, PublicCommand.PUBLISH));
    }

    @Test
    void versionCannotWrapOrAcceptNegativeHistory() {
        assertThat(ProductDefinitionLifecycleRules.nextVersion(0)).isEqualTo(1);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProductDefinitionLifecycleRules.nextVersion(-1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProductDefinitionLifecycleRules.nextVersion(Integer.MAX_VALUE));
    }

    private Selection selection() {
        return new Selection(
                scope.tenantId(),
                scope.rootId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "a".repeat(64),
                2,
                UUID.randomUUID(),
                "b".repeat(64),
                3);
    }

    @Test
    void costChangeStalesApprovalWithoutChangingPublicSelection() {
        var a = selection();
        var changed =
                new Selection(
                        a.tenantId(),
                        a.rootId(),
                        a.submissionId(),
                        a.revisionId(),
                        a.publicDigest(),
                        a.publicVersion(),
                        UUID.randomUUID(),
                        a.costDigest(),
                        a.costVersion() + 1);
        ProductDefinitionLifecycleRules.requireSelection(a, a);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProductDefinitionLifecycleRules.requireSelection(a, changed))
                .withMessage("PRODUCT_DEFINITION_SELECTION_STALE");
    }

    @Test
    void changedSubmissionOrDigestCannotReuseApproval() {
        var a = selection();
        var changed =
                new Selection(
                        a.tenantId(),
                        a.rootId(),
                        UUID.randomUUID(),
                        a.revisionId(),
                        "c".repeat(64),
                        a.publicVersion(),
                        a.costRevisionId(),
                        a.costDigest(),
                        a.costVersion());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProductDefinitionLifecycleRules.requireSelection(a, changed));
    }

    @Test
    void retainedContributorCannotCheckOwnRoot() {
        var maker = new Actor("issuer", "maker");
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionLifecycleRules.requireIndependent(
                                        maker,
                                        Set.of(maker, new Actor("issuer", "private-maker"))));
        ProductDefinitionLifecycleRules.requireIndependent(
                new Actor("issuer", "checker"), Set.of(maker));
    }

    @Test
    void issuerIsPartOfIdentityAndMissingIdentityCannotPass() {
        ProductDefinitionLifecycleRules.requireIndependent(
                new Actor("other-issuer", "maker"), Set.of(new Actor("issuer", "maker")));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionLifecycleRules.requireIndependent(
                                        new Actor("issuer", " "),
                                        Set.of(new Actor("issuer", "maker"))));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                ProductDefinitionLifecycleRules.requireIndependent(
                                        new Actor("issuer", "checker"), Set.of()));
    }

    @Test
    void publicationRequiresNoActiveControlAndWithdrawalCannotReactivate() {
        ProductDefinitionLifecycleRules.requireCanPublish(0);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProductDefinitionLifecycleRules.requireCanPublish(1));
        assertThat(ProductDefinitionLifecycleRules.withdraw(PublicationState.ACTIVE))
                .isEqualTo(PublicationState.WITHDRAWN);
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> ProductDefinitionLifecycleRules.withdraw(PublicationState.WITHDRAWN));
    }

    @Test
    void selectionRequiresExplicitTenantAndRootEvenWhenComparedToItself() {
        var a = selection();
        var bad =
                new Selection(
                        null,
                        a.rootId(),
                        a.submissionId(),
                        a.revisionId(),
                        a.publicDigest(),
                        a.publicVersion(),
                        a.costRevisionId(),
                        a.costDigest(),
                        a.costVersion());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProductDefinitionLifecycleRules.requireSelection(bad, bad));
    }
}

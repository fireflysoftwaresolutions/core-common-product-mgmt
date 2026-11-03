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

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DefinitionReviewRulesTest {
    @Test
    void makerCannotApproveOwnSubmittedContribution() {
        assertThatThrownBy(
                        () ->
                                DefinitionReviewRules.independent(
                                        "issuer", "maker", java.util.Set.of("issuer|maker"), true))
                .hasMessageContaining("INDEPENDENT_CHECKER_REQUIRED");
    }

    @Test
    void sameActorDifferentIssuerIsASeparateIdentity() {
        DefinitionReviewRules.independent(
                "other-issuer", "maker", java.util.Set.of("issuer|maker"), true);
    }

    @Test
    void serviceCannotBecomeHumanChecker() {
        assertThatThrownBy(
                        () ->
                                DefinitionReviewRules.independent(
                                        "issuer",
                                        "checker",
                                        java.util.Set.of("issuer|maker"),
                                        false))
                .hasMessageContaining("HUMAN_CHECKER_REQUIRED");
    }

    @Test
    void missingContributorCannotPassAnEmptyStream() {
        assertThatThrownBy(
                        () ->
                                DefinitionReviewRules.independent(
                                        "issuer", "checker", java.util.Set.of(), true))
                .hasMessageContaining("REVIEW_CONTRIBUTORS_REQUIRED");
    }

    @Test
    void currentCheckerHasStableIdentityNotDisplayName() {
        DefinitionReviewRules.independent(
                "issuer", "checker", java.util.Set.of("issuer|maker"), true);
        assertThatThrownBy(
                        () ->
                                DefinitionReviewRules.independent(
                                        "issuer", " ", java.util.Set.of("issuer|maker"), true))
                .hasMessageContaining("CHECKER_IDENTITY_REQUIRED");
    }
}

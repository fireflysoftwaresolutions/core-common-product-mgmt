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

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ProductDefinitionLifecycleRules {
    private ProductDefinitionLifecycleRules() {}

    public static PreparedDraft prepare(CreateDraft command) {
        if (command == null
                || command.scope() == null
                || command.definition() == null
                || command.cost() == null) throw invalid("PRODUCT_DEFINITION_DRAFT_REQUIRED");
        identity(command.scope().tenantId());
        identity(command.scope().rootId());
        identity(command.scope().operationId());
        identity(command.productId());
        identity(command.categoryId());
        var definition =
                DefinitionProfiles.canonical(
                        DefinitionCanonical.tree(command.definition()).toString());
        var common = DefinitionProfiles.decode(definition.json()).common();
        GovernedDefinitionProfile.from(common);
        var cost =
                CostRules.canonical(
                        DefinitionCanonical.tree(command.cost()).toString(), definition.json());
        var intent =
                DefinitionCanonical.tree(
                        Map.of(
                                "encoding",
                                "product-definition-lifecycle-intent-v1",
                                "command",
                                "CREATE_DRAFT",
                                "scope",
                                command.scope(),
                                "productId",
                                command.productId(),
                                "categoryId",
                                command.categoryId(),
                                "definition",
                                DefinitionCanonical.parse(definition.json()),
                                "cost",
                                DefinitionCanonical.parse(cost.json())));
        String encoded = DefinitionCanonical.encode(intent);
        DefinitionCanonical.parse(encoded);
        return new PreparedDraft(
                definition.json(),
                definition.digest(),
                cost.json(),
                cost.digest(),
                encoded,
                DefinitionCanonical.intentDigest(intent));
    }

    public static Phase phaseAfter(Phase phase, PublicCommand command) {
        if (phase == null || command == null) throw invalid("PRODUCT_DEFINITION_PHASE_CONFLICT");
        return switch (command) {
            case EDIT_PUBLIC -> Phase.DRAFT;
            case SUBMIT_PUBLIC -> {
                if (phase != Phase.DRAFT) throw invalid("PRODUCT_DEFINITION_PHASE_CONFLICT");
                yield Phase.IN_REVIEW;
            }
            case PUBLISH -> {
                if (phase != Phase.IN_REVIEW) throw invalid("PRODUCT_DEFINITION_PHASE_CONFLICT");
                yield Phase.PUBLISHED;
            }
        };
    }

    public static int nextVersion(int current) {
        if (current < 0 || current == Integer.MAX_VALUE)
            throw invalid("PRODUCT_DEFINITION_VERSION_EXHAUSTED");
        return current + 1;
    }

    public static void requireSelection(Selection selected, Selection current) {
        if (!validSelection(selected) || !validSelection(current) || !selected.equals(current))
            throw invalid("PRODUCT_DEFINITION_SELECTION_STALE");
    }

    private static boolean validSelection(Selection value) {
        return value != null
                && validId(value.tenantId())
                && validId(value.rootId())
                && validId(value.submissionId())
                && validId(value.revisionId())
                && validId(value.costRevisionId())
                && value.publicVersion() >= 0
                && value.costVersion() >= 0
                && value.publicDigest() != null
                && value.publicDigest().matches("[0-9a-f]{64}")
                && value.costDigest() != null
                && value.costDigest().matches("[0-9a-f]{64}");
    }

    public static void requireIndependent(Actor checker, Set<Actor> contributors) {
        if (!validActor(checker)
                || contributors == null
                || contributors.isEmpty()
                || contributors.stream().anyMatch(value -> !validActor(value)))
            throw invalid("PRODUCT_DEFINITION_REVIEW_IDENTITY_REQUIRED");
        if (contributors.contains(checker))
            throw invalid("PRODUCT_DEFINITION_INDEPENDENT_CHECKER_REQUIRED");
    }

    private static boolean validActor(Actor actor) {
        return actor != null && validText(actor.issuer(), 512) && validText(actor.subject(), 255);
    }

    private static boolean validText(String value, int max) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > max)
            return false;
        DefinitionRules.unicode(value, "/actor");
        return true;
    }

    public static void requireCanPublish(int activePublications) {
        if (activePublications != 0)
            throw invalid("PRODUCT_DEFINITION_ACTIVE_PUBLICATION_CONFLICT");
    }

    public static PublicationState withdraw(PublicationState state) {
        if (state != PublicationState.ACTIVE)
            throw invalid("PRODUCT_DEFINITION_PUBLICATION_STATE_CONFLICT");
        return PublicationState.WITHDRAWN;
    }

    private static boolean validId(UUID value) {
        return value != null && !value.equals(new UUID(0, 0));
    }

    private static void identity(UUID value) {
        if (!validId(value)) throw invalid("PRODUCT_DEFINITION_IDENTITY_REQUIRED");
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}

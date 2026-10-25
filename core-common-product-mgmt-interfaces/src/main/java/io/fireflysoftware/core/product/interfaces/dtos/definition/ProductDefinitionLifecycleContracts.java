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

package io.fireflysoftware.core.product.interfaces.dtos.definition;

import java.util.UUID;

public final class ProductDefinitionLifecycleContracts {
    private ProductDefinitionLifecycleContracts() {}

    public enum Phase {
        DRAFT,
        IN_REVIEW,
        PUBLISHED
    }

    public enum PublicCommand {
        EDIT_PUBLIC,
        SUBMIT_PUBLIC,
        PUBLISH
    }

    public enum PublicationState {
        ACTIVE,
        WITHDRAWN
    }

    public record Scope(UUID tenantId, UUID rootId, UUID operationId) {}

    public record CreateDraft(
            Scope scope,
            UUID productId,
            UUID categoryId,
            DefinitionContracts.ProductDefinition definition,
            CostTerms cost) {}

    public record PreparedDraft(
            String canonicalDefinition,
            String publicDigest,
            String canonicalCost,
            String costDigest,
            String canonicalIntent,
            String intentDigest) {}

    public record Selection(
            UUID tenantId,
            UUID rootId,
            UUID submissionId,
            UUID revisionId,
            String publicDigest,
            int publicVersion,
            UUID costRevisionId,
            String costDigest,
            int costVersion) {}

    public record Actor(String issuer, String subject) {}
}

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

package io.fireflysoftware.core.product.models.repositories.definition.lifecycle;

import io.fireflysoftware.core.product.models.entities.definition.lifecycle.*;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductDefinitionDraftWrites {
    Mono<ProductDefinitionDraftRoot> insertRoot(ProductDefinitionDraftRoot row);

    Mono<ProductDefinitionDraftRevision> insertPublicRevision(ProductDefinitionDraftRevision row);

    Mono<ProductDefinitionCostRevision> insertCostRevision(ProductDefinitionCostRevision row);

    Mono<ProductDefinitionCostControl> insertCostControl(ProductDefinitionCostControl row);

    Mono<Void> replacePublicHead(PublicHeadChange change);

    Mono<Void> replaceCostHead(CostHeadChange change);

    Mono<Void> replaceActiveReview(ActiveReviewChange change);

    Mono<Void> withdrawPublication(PublicationStateChange change);

    record PublicationStateChange(
            UUID tenantId, UUID rootId, UUID publicationId, int expectedVersion) {}

    record ActiveReviewChange(
            UUID tenantId,
            UUID rootId,
            UUID publicHead,
            int publicVersion,
            UUID expectedReviewId,
            UUID newReviewId) {}

    record PublicHeadChange(
            UUID tenantId,
            UUID rootId,
            UUID expectedHead,
            int expectedVersion,
            String expectedPhase,
            UUID newHead,
            String newPhase) {}

    record CostHeadChange(
            UUID tenantId, UUID rootId, UUID expectedHead, int expectedVersion, UUID newHead) {}
}

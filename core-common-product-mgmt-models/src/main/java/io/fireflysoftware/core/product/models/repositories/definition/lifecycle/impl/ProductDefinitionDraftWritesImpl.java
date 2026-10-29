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

package io.fireflysoftware.core.product.models.repositories.definition.lifecycle.impl;

import io.fireflysoftware.core.product.models.entities.definition.lifecycle.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionDraftWrites;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;

import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

public final class ProductDefinitionDraftWritesImpl implements ProductDefinitionDraftWrites {
    private final R2dbcEntityOperations operations;

    public ProductDefinitionDraftWritesImpl(R2dbcEntityOperations operations) {
        this.operations = Objects.requireNonNull(operations);
    }

    public Mono<ProductDefinitionDraftRoot> insertRoot(ProductDefinitionDraftRoot row) {
        return insert(row, row == null ? null : row.rootId());
    }

    public Mono<ProductDefinitionDraftRevision> insertPublicRevision(
            ProductDefinitionDraftRevision row) {
        return insert(row, row == null ? null : row.revisionId());
    }

    public Mono<ProductDefinitionCostRevision> insertCostRevision(
            ProductDefinitionCostRevision row) {
        return insert(row, row == null ? null : row.costRevisionId());
    }

    public Mono<ProductDefinitionCostControl> insertCostControl(ProductDefinitionCostControl row) {
        return insert(row, row == null ? null : row.rootId());
    }

    private <T> Mono<T> insert(T row, UUID identity) {
        if (row == null || identity == null || identity.equals(new UUID(0, 0)))
            return Mono.error(
                    new IllegalArgumentException("PRODUCT_DEFINITION_ASSIGNED_ID_REQUIRED"));
        return operations.insert(row);
    }

    public Mono<Void> replacePublicHead(PublicHeadChange change) {
        return Mono.defer(
                () -> {
                    int version = nextVersion(change.expectedVersion());
                    var where =
                            publicHeadCriteria(
                                    change.tenantId(),
                                    change.rootId(),
                                    change.expectedHead(),
                                    change.expectedVersion(),
                                    change.expectedPhase());
                    var update =
                            Update.update("publicHead", change.newHead())
                                    .set("phase", change.newPhase())
                                    .set("publicStateVersion", version)
                                    .set("activeReviewId", null);
                    return exactlyOne(
                            operations.update(
                                    Query.query(where), update, ProductDefinitionDraftRoot.class));
                });
    }

    public Mono<Void> replaceCostHead(CostHeadChange change) {
        return Mono.defer(
                () -> {
                    int version = nextVersion(change.expectedVersion());
                    var where =
                            costHeadCriteria(
                                    change.tenantId(),
                                    change.rootId(),
                                    change.expectedHead(),
                                    change.expectedVersion());
                    var update =
                            Update.update("costHead", change.newHead())
                                    .set("costStateVersion", version);
                    return exactlyOne(
                            operations.update(
                                    Query.query(where),
                                    update,
                                    ProductDefinitionCostControl.class));
                });
    }

    public Mono<Void> replaceActiveReview(ActiveReviewChange change) {
        return Mono.defer(
                () -> {
                    var where =
                            Criteria.where("tenantId")
                                    .is(change.tenantId())
                                    .and("rootId")
                                    .is(change.rootId())
                                    .and("publicHead")
                                    .is(change.publicHead())
                                    .and("publicStateVersion")
                                    .is(change.publicVersion());
                    where =
                            change.expectedReviewId() == null
                                    ? where.and("activeReviewId").isNull()
                                    : where.and("activeReviewId").is(change.expectedReviewId());
                    return exactlyOne(
                            operations.update(
                                    Query.query(where),
                                    Update.update("activeReviewId", change.newReviewId()),
                                    ProductDefinitionDraftRoot.class));
                });
    }

    public Mono<Void> withdrawPublication(PublicationStateChange change) {
        return Mono.defer(
                () -> {
                    int next = nextVersion(change.expectedVersion());
                    var where =
                            Criteria.where("tenantId")
                                    .is(change.tenantId())
                                    .and("rootId")
                                    .is(change.rootId())
                                    .and("publicationId")
                                    .is(change.publicationId())
                                    .and("state")
                                    .is("ACTIVE")
                                    .and("stateVersion")
                                    .is(change.expectedVersion());
                    return exactlyOne(
                            operations.update(
                                    Query.query(where),
                                    Update.update("state", "WITHDRAWN").set("stateVersion", next),
                                    io.fireflysoftware.core.product.models.entities.definition
                                            .ProductDefinitionPublicationControl.class));
                });
    }

    static Criteria publicHeadCriteria(
            UUID tenant, UUID root, UUID head, int version, String phase) {
        return Criteria.where("tenantId")
                .is(tenant)
                .and("rootId")
                .is(root)
                .and("publicHead")
                .is(head)
                .and("publicStateVersion")
                .is(version)
                .and("phase")
                .is(phase);
    }

    static Criteria costHeadCriteria(UUID tenant, UUID root, UUID head, int version) {
        return Criteria.where("tenantId")
                .is(tenant)
                .and("rootId")
                .is(root)
                .and("costHead")
                .is(head)
                .and("costStateVersion")
                .is(version);
    }

    private static int nextVersion(int current) {
        if (current < 0 || current == Integer.MAX_VALUE)
            throw new IllegalArgumentException("PRODUCT_DEFINITION_VERSION_EXHAUSTED");
        return current + 1;
    }

    private static Mono<Void> exactlyOne(Mono<Long> count) {
        return count.switchIfEmpty(
                        Mono.error(
                                new DataIntegrityViolationException(
                                        "PRODUCT_DEFINITION_ROW_COUNT_REQUIRED")))
                .flatMap(
                        rows -> {
                            if (rows == 1) return Mono.empty();
                            if (rows == 0)
                                return Mono.error(
                                        new OptimisticLockingFailureException(
                                                "PRODUCT_DEFINITION_STATE_CONFLICT"));
                            return Mono.error(
                                    new DataIntegrityViolationException(
                                            "PRODUCT_DEFINITION_ROW_COUNT_INVALID"));
                        });
    }
}

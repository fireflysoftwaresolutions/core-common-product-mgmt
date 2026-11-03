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

package io.fireflysoftware.core.product.models.entities.definition.lifecycle;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table(value = "product_definition_cost_revision", schema = "public")
public record ProductDefinitionCostRevision(
        @Id @Column("cost_revision_id") UUID costRevisionId,
        @Column("tenant_id") UUID tenantId,
        @Column("root_id") UUID rootId,
        @Column("canonical_cost") String canonicalCost,
        @Column("cost_digest") String costDigest,
        @Column("encoding_version") String encodingVersion,
        @Column("actor_issuer") String actorIssuer,
        @Column("actor_id") String actorId,
        @Column("created_at") Instant createdAt) {}

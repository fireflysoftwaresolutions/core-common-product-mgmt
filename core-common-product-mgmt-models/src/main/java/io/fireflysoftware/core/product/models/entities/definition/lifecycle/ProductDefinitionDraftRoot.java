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

@Table(value = "product_definition_root", schema = "public")
public record ProductDefinitionDraftRoot(
        @Id @Column("root_id") UUID rootId,
        @Column("tenant_id") UUID tenantId,
        @Column("product_id") UUID productId,
        @Column("product_code") String productCode,
        @Column("business_area") String businessArea,
        @Column("profile_code") String profileCode,
        @Column("profile_version") Integer profileVersion,
        @Column("phase") String phase,
        @Column("public_state_version") Integer publicStateVersion,
        @Column("public_head") UUID publicHead,
        @Column("active_review_id") UUID activeReviewId,
        @Column("created_at") Instant createdAt) {}

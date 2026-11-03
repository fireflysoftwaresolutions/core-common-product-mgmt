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

import io.fireflysoftware.core.product.interfaces.enums.ProductStatusEnum;
import io.fireflysoftware.core.product.interfaces.enums.ProductTypeEnum;
import io.r2dbc.postgresql.codec.Json;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public final class ProductDefinitionLifecycleRows {
    private ProductDefinitionLifecycleRows() {}

    @Table(value = "product_definition_action", schema = "public")
    public record Action(
            @Id @Column("action_id") UUID actionId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("command") String command,
            @Column("visibility") String visibility,
            @Column("canonical_receipt") String canonicalReceipt,
            @Column("actor_issuer") String actorIssuer,
            @Column("actor_id") String actorId,
            @Column("created_at") Instant createdAt) {}

    @Table(value = "product_definition_action_private", schema = "public")
    public record ActionPrivate(
            @Id @Column("action_id") UUID actionId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("request_fingerprint") String requestFingerprint,
            @Column("selection_fingerprint") String selectionFingerprint,
            @Column("canonical_instruction") String canonicalInstruction) {}

    @Table(value = "product_definition_submission", schema = "public")
    public record Submission(
            @Id @Column("submission_id") UUID submissionId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("revision_id") UUID revisionId,
            @Column("public_digest") String publicDigest,
            @Column("public_version") Integer publicVersion,
            @Column("actor_issuer") String actorIssuer,
            @Column("actor_id") String actorId,
            @Column("created_at") Instant createdAt) {}

    @Table(value = "product_definition_review", schema = "public")
    public record Review(
            @Id @Column("review_id") UUID reviewId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("revision_id") UUID revisionId,
            @Column("public_digest") String publicDigest,
            @Column("actor_issuer") String actorIssuer,
            @Column("actor_id") String actorId,
            @Column("created_at") Instant createdAt) {}

    @Table(value = "product_definition_review_private", schema = "public")
    public record ReviewPrivate(
            @Id @Column("review_id") UUID reviewId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("cost_revision_id") UUID costRevisionId,
            @Column("selection_fingerprint") String selectionFingerprint) {}

    @Table(value = "product_definition_review_decision", schema = "public")
    public record Decision(
            @Id @Column("review_id") UUID reviewId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("submission_id") UUID submissionId,
            @Column("public_version") Integer publicVersion,
            @Column("cost_version") Integer costVersion,
            @Column("state") String state,
            @Column("reason") String reason,
            @Column("actor_issuer") String actorIssuer,
            @Column("actor_id") String actorId,
            @Column("created_at") Instant createdAt) {}

    @Table(value = "product_definition_history", schema = "public")
    public record History(
            @Id @Column("history_id") UUID historyId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("action_id") UUID actionId,
            @Column("public_state_version") Integer publicStateVersion,
            @Column("event_type") String eventType) {}

    @Table(value = "product_definition_audit", schema = "public")
    public record Audit(
            @Id @Column("audit_id") UUID auditId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("action_id") UUID actionId,
            @Column("event_type") String eventType,
            @Column("actor_issuer") String actorIssuer,
            @Column("actor_id") String actorId,
            @Column("created_at") Instant createdAt) {}

    @Table(value = "product_definition_outbox", schema = "public")
    public record Outbox(
            @Id @Column("event_id") UUID eventId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("action_id") UUID actionId,
            @Column("event_type") String eventType,
            @Column("created_at") Instant createdAt) {}

    @Table(value = "product_definition_cost_outbox", schema = "public")
    public record CostOutbox(
            @Id @Column("event_id") UUID eventId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("action_id") UUID actionId,
            @Column("cost_revision_id") UUID costRevisionId,
            @Column("created_at") Instant createdAt) {}

    @Table(value = "product", schema = "public")
    public record ProductBirth(
            @Id @Column("product_id") UUID productId,
            @Column("tenant_id") UUID tenantId,
            @Column("product_category_id") UUID productCategoryId,
            @Column("product_type") ProductTypeEnum productType,
            @Column("product_name") String productName,
            @Column("product_code") String productCode,
            @Column("product_description") String productDescription,
            @Column("product_status") ProductStatusEnum productStatus,
            @Column("launch_date") LocalDateTime launchDate,
            @Column("end_date") LocalDateTime endDate,
            @Column("date_created") LocalDateTime dateCreated,
            @Column("date_updated") LocalDateTime dateUpdated,
            @Column("marketing_features") Json marketingFeatures) {}

    @Table(value = "product_version", schema = "public")
    public record Version(
            @Id @Column("product_version_id") UUID productVersionId,
            @Column("product_id") UUID productId,
            @Column("version_number") Long versionNumber,
            @Column("version_description") String versionDescription,
            @Column("effective_date") LocalDateTime effectiveDate,
            @Column("date_created") LocalDateTime dateCreated,
            @Column("date_updated") LocalDateTime dateUpdated) {}

    @Table(value = "product_definition_version_identity", schema = "public")
    public record VersionIdentity(
            @Id @Column("product_version_id") UUID productVersionId,
            @Column("tenant_id") UUID tenantId,
            @Column("root_id") UUID rootId,
            @Column("product_id") UUID productId,
            @Column("publication_id") UUID publicationId,
            @Column("version_number") Long versionNumber,
            @Column("recorded_at") Instant recordedAt) {}
}

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

package io.fireflysoftware.core.product.models.repositories.definition.purpose;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Narrow public metadata projections; private cost, contributors and review reasons are absent. */
public final class InsuranceQuotePurposeRows {
    private InsuranceQuotePurposeRows() {}
    @Table(value="product_definition_root",schema="public")
    public record Root(@Id UUID rootId,UUID tenantId,UUID productId,String productCode,String businessArea,String profileCode,Integer profileVersion){@Override public String toString(){return "InsuranceQuoteRoot";}}
    @Table(value="product_definition_revision",schema="public")
    public record Revision(@Id UUID revisionId,UUID tenantId,UUID rootId,String canonicalDefinition,String publicDigest,String encodingVersion){@Override public String toString(){return "InsuranceQuoteRevision";}}
    @Table(value="product_definition_publication",schema="public")
    public record Publication(@Id UUID publicationId,UUID tenantId,UUID rootId,UUID revisionId,UUID productVersionId,String canonicalDefinition,String publicDigest,String encodingVersion,String operationalAdmission,OffsetDateTime createdAt){@Override public String toString(){return "InsuranceQuotePublication";}}
    @Table(value="product_definition_publication_control",schema="public")
    public record Control(@Id UUID publicationId,UUID tenantId,UUID rootId,String state,Integer stateVersion){@Override public String toString(){return "InsuranceQuotePublicationControl";}}
    @Table(value="product_definition_version_identity",schema="public")
    public record Version(@Id UUID productVersionId,UUID tenantId,UUID rootId,UUID productId,UUID publicationId,Long versionNumber,OffsetDateTime recordedAt){@Override public String toString(){return "InsuranceQuoteVersionIdentity";}}
    public record ReadStamp(@Column("context_id") UUID contextId,@Column("recorded_at") OffsetDateTime recordedAt,@Column("db_now_ms") Long dbNowMillis,@Column("admission_until_ms") Long validUntilMillis){@Override public String toString(){return "InsuranceQuoteReadStamp";}}
}

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

import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionLifecycleRows.VersionIdentity;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

import java.util.UUID;

@NoRepositoryBean
public interface LifecycleVersionIdentityRepository extends Repository<VersionIdentity, UUID> {
    Mono<VersionIdentity> findByTenantIdAndRootIdAndPublicationId(
            UUID tenantId, UUID rootId, UUID publicationId);

    Mono<Boolean> existsByTenantIdAndRootIdAndProductIdAndVersionNumber(
            UUID tenantId, UUID rootId, UUID productId, Long versionNumber);
}

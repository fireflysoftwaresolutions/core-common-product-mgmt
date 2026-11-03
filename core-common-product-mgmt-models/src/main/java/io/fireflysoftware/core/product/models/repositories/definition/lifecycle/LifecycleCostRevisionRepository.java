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

import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionCostRevision;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import reactor.core.publisher.Mono;

import java.util.UUID;

@NoRepositoryBean
public interface LifecycleCostRevisionRepository
        extends Repository<ProductDefinitionCostRevision, UUID> {
    Mono<ProductDefinitionCostRevision> findByTenantIdAndRootIdAndCostRevisionId(
            UUID tenantId, UUID rootId, UUID costRevisionId);

    interface Contributor {
        String getActorIssuer();

        String getActorId();
    }

    reactor.core.publisher.Flux<Contributor> findDistinctTop513ByTenantIdAndRootId(
            UUID tenantId, UUID rootId);
}

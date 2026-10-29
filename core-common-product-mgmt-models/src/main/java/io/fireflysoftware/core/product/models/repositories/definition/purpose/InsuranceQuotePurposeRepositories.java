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

import io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeRows.*;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;

/** Five bounded derived reads and native read context only; no generic CRUD inheritance. */
public final class InsuranceQuotePurposeRepositories {
    private InsuranceQuotePurposeRepositories() {}
    public interface Roots extends Repository<Root,UUID>{Mono<Root> findByTenantIdAndRootId(UUID tenantId,UUID rootId);}
    public interface Revisions extends Repository<Revision,UUID>{Mono<Revision> findByTenantIdAndRootIdAndRevisionId(UUID tenantId,UUID rootId,UUID revisionId);}
    public interface Publications extends Repository<Publication,UUID>{Mono<Publication> findByTenantIdAndRootIdAndPublicationId(UUID tenantId,UUID rootId,UUID publicationId);}
    public interface Controls extends Repository<Control,UUID>{Mono<Control> findByTenantIdAndRootIdAndPublicationId(UUID tenantId,UUID rootId,UUID publicationId);}
    public interface Versions extends Repository<Version,UUID>{Mono<Version> findFirstByTenantIdAndRootIdOrderByVersionNumberDesc(UUID tenantId,UUID rootId);}
    public interface Native extends Repository<Root,UUID>{
        @Query("SELECT context_id, recorded_at, db_now_ms, admission_until_ms FROM public.open_product_definition_insurance_quote_read(:tenant,:root,:product,:revision,:publication,:issuer,:actor,:actorUntil,:permitUntil)")
        Mono<ReadStamp> open(@Param("tenant") UUID tenant,@Param("root") UUID root,@Param("product") UUID product,@Param("revision") UUID revision,@Param("publication") UUID publication,@Param("issuer") String issuer,@Param("actor") String actor,@Param("actorUntil") long actorUntil,@Param("permitUntil") long permitUntil);
        @Query("SELECT public.assert_product_definition_insurance_quote_read_live(:context)")
        Mono<Boolean> isCurrent(@Param("context") UUID context);
    }
}

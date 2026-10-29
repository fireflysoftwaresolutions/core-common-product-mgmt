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

package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.*;
import io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeRows.*;
import java.util.Objects;

/** Exact immutable public graph projection. Supplied rows do not establish owner/authentication evidence. */
public final class InsuranceQuotePurposeProjection {
    private InsuranceQuotePurposeProjection() {}
    public static Configuration project(Scope scope,Root root,Revision revision,Publication publication,Control control,Version latest) {
        try {
            require(scope!=null && root!=null && revision!=null && publication!=null && control!=null && latest!=null);
            require(owner(scope,root.tenantId(),root.rootId()) && scope.productId().equals(root.productId())
                    && GovernedDefinitionProfile.TERM_PC_INSURANCE.matches(root.businessArea(),root.profileCode(),root.profileVersion()));
            require(owner(scope,revision.tenantId(),revision.rootId()) && scope.revisionId().equals(revision.revisionId()));
            require(owner(scope,publication.tenantId(),publication.rootId()) && scope.publicationId().equals(publication.publicationId())
                    && scope.revisionId().equals(publication.revisionId()) && publication.productVersionId()!=null
                    && "NOT_ADMITTED".equals(publication.operationalAdmission()) && publication.createdAt()!=null);
            require(owner(scope,control.tenantId(),control.rootId()) && scope.publicationId().equals(control.publicationId())
                    && "ACTIVE".equals(control.state()) && control.stateVersion()!=null && control.stateVersion()>=0);
            require(owner(scope,latest.tenantId(),latest.rootId()) && scope.productId().equals(latest.productId())
                    && scope.publicationId().equals(latest.publicationId()) && publication.productVersionId().equals(latest.productVersionId())
                    && latest.versionNumber()!=null && latest.versionNumber()>0 && latest.recordedAt()!=null
                    && publication.createdAt().toInstant().equals(latest.recordedAt().toInstant()));
            require(DefinitionCanonical.ENCODING.equals(revision.encodingVersion()) && DefinitionCanonical.ENCODING.equals(publication.encodingVersion())
                    && Objects.equals(revision.publicDigest(),publication.publicDigest())
                    && revision.canonicalDefinition()!=null && revision.canonicalDefinition().equals(publication.canonicalDefinition()));
            var canonical=DefinitionProfiles.canonical(revision.canonicalDefinition());
            require(revision.canonicalDefinition().equals(canonical.json()) && revision.publicDigest().equals(canonical.digest()));
            var definition=DefinitionProfiles.decode(canonical.json());GovernedDefinitionProfile.TERM_PC_INSURANCE.require(definition.common());
            require(Objects.equals(root.productCode(),definition.common().code()));
            var result=new Configuration(scope,canonical.digest(),definition);InsuranceQuotePurposeCodec.encode(result);return result;
        }catch(RuntimeException error){throw unavailable();}
    }
    private static boolean owner(Scope s,java.util.UUID tenant,java.util.UUID root){return s.tenantId().equals(tenant) && s.rootId().equals(root);}
    private static void require(boolean value){if(!value)throw unavailable();}
    private static IllegalStateException unavailable(){return new IllegalStateException("INSURANCE_QUOTE_PURPOSE_GRAPH_UNAVAILABLE");}
}

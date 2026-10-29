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

package io.fireflysoftware.core.product.interfaces.dtos.definition;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Public owner configuration data; neither applicant eligibility nor a financial admission. */
public final class InsuranceQuotePurposeContracts {
    private InsuranceQuotePurposeContracts() {}

    @Schema(name="InsuranceQuotePurposeScope")
    public record Scope(UUID tenantId,UUID rootId,UUID productId,UUID revisionId,UUID publicationId) {
        public Scope {
            for(UUID id:new UUID[]{tenantId,rootId,productId,revisionId,publicationId})
                if(id==null || id.equals(new UUID(0,0)))throw invalid();
        }
        @Override public String toString(){return "InsuranceQuotePurposeScope";}
    }

    /** Canonical public definition and its immutable publication graph, without ephemeral authority. */
    public record TermPcDefinition(DefinitionContracts.Common common,TermPcInsuranceTerms terms) {
        public TermPcDefinition {Objects.requireNonNull(common);Objects.requireNonNull(terms);}
        @Override public String toString(){return "InsuranceQuoteTermPcDefinition";}
    }
    public record Configuration(Scope scope,String publicDigest,TermPcDefinition definition) {
        public Configuration(Scope scope,String publicDigest,DefinitionContracts.ProductDefinition definition) {
            this(scope,publicDigest,typed(definition));
        }
        public Configuration {
            Objects.requireNonNull(scope);Objects.requireNonNull(definition);
            if(publicDigest==null || !publicDigest.matches("[0-9a-f]{64}"))throw invalid();
        }
        @Override public String toString(){return "InsuranceQuotePurposeConfiguration";}
    }

    /** Supplied response data. Authentic current owner/PDP/native evidence is established by the service. */
    public record Observation(Configuration configuration,
            @Schema(ref="#/components/schemas/FireflyInstant") Instant observedAt,
            @Schema(ref="#/components/schemas/FireflyInstant") Instant effectiveUntil) {
        public Observation {
            Objects.requireNonNull(configuration);Objects.requireNonNull(observedAt);Objects.requireNonNull(effectiveUntil);
            if(observedAt.getNano()%1_000!=0 || effectiveUntil.getNano()%1_000!=0
                    || !effectiveUntil.isAfter(observedAt)
                    || Duration.between(observedAt,effectiveUntil).compareTo(Duration.ofSeconds(30))>0)throw invalid();
        }
        @Override public String toString(){return "InsuranceQuotePurposeObservation";}
    }
    private static TermPcDefinition typed(DefinitionContracts.ProductDefinition definition) {
        if(definition==null || !(definition.terms() instanceof TermPcInsuranceTerms terms))throw invalid();
        return new TermPcDefinition(definition.common(),terms);
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid insurance quote-purpose data");}
}

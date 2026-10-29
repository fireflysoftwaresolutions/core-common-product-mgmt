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

package io.fireflysoftware.core.product.core.services.definition;

import io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

/** Mandatory current authenticated owner-record verification. No generic CRUD/digest-as-authority fallback. */
public interface InsuranceQuotePurposeReferenceResolver {
    Mono<VerifiedReferences> verify(Configuration configuration);

    /** Internal transport result; construction alone does not prove authenticity or applicant approval. */
    record VerifiedReferences(Scope scope,String configurationDigest,CurrencyPolicyReference currencyPolicy,
            Set<ExactReference> references,Instant observedAt,Instant effectiveUntil) {
        public VerifiedReferences {
            Objects.requireNonNull(scope);Objects.requireNonNull(currencyPolicy);references=Set.copyOf(references);
            Objects.requireNonNull(observedAt);Objects.requireNonNull(effectiveUntil);
            if(configurationDigest==null || !configurationDigest.matches("[0-9a-f]{64}") || references.size()>1024
                    || !effectiveUntil.isAfter(observedAt) || Duration.between(observedAt,effectiveUntil).compareTo(Duration.ofSeconds(30))>0)throw new IllegalArgumentException("Invalid supplied owner reference observation");
        }
        @Override public String toString(){return "InsuranceQuotePurposeVerifiedReferences";}
    }
}

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

import java.util.List;

public final class DefinitionContracts {
    private DefinitionContracts() {}

    public record CurrencyPolicyReference(
            String policyId,
            String currencyId,
            String isoCode,
            Long version,
            String digest,
            String encodingVersion) {}

    public record Money(String minorUnits, CurrencyPolicyReference currencyPolicy) {}

    public record ExactReference(
            String owner, String kind, String id, Long version, String digest) {}

    public record MarketScope(
            String jurisdiction,
            String channel,
            String customerSegment,
            CurrencyPolicyReference currencyPolicy) {}

    public record FactRequirement(
            String code,
            String sourceOwner,
            String factContract,
            Long version,
            String contractDigest,
            String kind,
            String operator,
            List<String> values,
            Integer maxAgeSeconds,
            Boolean evidenceRequired) {
        public FactRequirement {
            values = copy(values);
        }
    }

    public record DocumentRequirement(
            String code,
            String documentType,
            Boolean mandatory,
            ExactReference template,
            ExactReference issuer,
            ExactReference layout,
            String locale) {}

    public record DependencyRequirement(
            String code, String capability, String owner, Boolean requiredForLaunch) {}

    public record Requirements(
            List<FactRequirement> facts,
            List<DocumentRequirement> documents,
            List<DependencyRequirement> dependencies) {
        public Requirements {
            facts = copy(facts);
            documents = copy(documents);
            dependencies = copy(dependencies);
        }
    }

    public record Common(
            Integer schemaVersion,
            String businessArea,
            String profileCode,
            Integer profileVersion,
            String code,
            String name,
            String description,
            String portfolioCode,
            String legalEntityId,
            MarketScope market,
            String effectiveFrom,
            String effectiveTo,
            Requirements requirements) {}

    @io.swagger.v3.oas.annotations.media.Schema(
            oneOf = {
                AccountTariffTerms.class,
                FixedRateInstallmentLoanTerms.class,
                TermPcInsuranceTerms.class
            })
    public sealed interface ProfileTerms
            permits AccountTariffTerms, FixedRateInstallmentLoanTerms, TermPcInsuranceTerms {}

    public record ProductDefinition(Common common, ProfileTerms terms) {}

    public record FeeRule(
            String code,
            String event,
            String kind,
            Money amount,
            String payer,
            String occurrence,
            String refundPolicy,
            String taxTreatment,
            ExactReference taxPolicy) {}

    static <T> List<T> copy(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }
}

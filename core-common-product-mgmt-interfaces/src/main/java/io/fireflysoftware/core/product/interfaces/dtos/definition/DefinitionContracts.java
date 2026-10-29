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

    public sealed interface OwnerVersion permits NumericOwnerVersion, OpaqueOwnerVersion {}

    public record NumericOwnerVersion(long value) implements OwnerVersion {
        public NumericOwnerVersion {if(value<=0)throw invalidVersion();}
    }

    public record OpaqueOwnerVersion(String value) implements OwnerVersion {
        public OpaqueOwnerVersion {
            if(value==null || value.isBlank() || value.codePoints().anyMatch(Character::isISOControl))throw invalidVersion();
            byte[] encoded=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if(encoded.length>255 || !value.equals(new String(encoded,java.nio.charset.StandardCharsets.UTF_8)))throw invalidVersion();
        }
        @Override public String toString(){return "OpaqueOwnerVersion";}
    }

    /** Numeric references retain their original wire shape; opaque owner versions are explicit. */
    public record ExactReference(
            String owner, String kind, String id,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            Long version, String digest,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            @io.swagger.v3.oas.annotations.media.Schema(nullable=true,allowableValues={"OPAQUE"})
            String versionFormat,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            @io.swagger.v3.oas.annotations.media.Schema(nullable=true,minLength=1,maxLength=255,description="Exact case-sensitive opaque owner version; no normalization or numeric conversion; at most 255 UTF-8 bytes. Owner-specific constraints also apply.")
            String versionText) {
        public ExactReference(String owner,String kind,String id,Long version,String digest){this(owner,kind,id,version,digest,null,null);}
        @com.fasterxml.jackson.annotation.JsonIgnore
        public OwnerVersion ownerVersion() {
            if(version!=null && versionFormat==null && versionText==null)return new NumericOwnerVersion(version);
            if(version==null && "OPAQUE".equals(versionFormat) && versionText!=null)return new OpaqueOwnerVersion(versionText);
            throw invalidVersion();
        }
    }

    private static IllegalArgumentException invalidVersion(){return new IllegalArgumentException("Invalid exact owner version");}

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
                DepositAccountTerms.class,
                FixedRateInstallmentLoanTerms.class,
                TermPcInsuranceTerms.class
            })
    public sealed interface ProfileTerms
            permits AccountTariffTerms, DepositAccountTerms, FixedRateInstallmentLoanTerms, TermPcInsuranceTerms {}

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

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

import static io.fireflysoftware.core.product.core.services.definition.DefinitionProfileSupport.*;
import static io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;

import io.fireflysoftware.core.product.interfaces.dtos.definition.*;

import java.util.Set;

public final class DefinitionProfiles {
    private DefinitionProfiles() {}

    public record Canonical(String json, String digest) {}

    public static ProductDefinition decode(String raw) {
        var node = DefinitionCanonical.parse(raw);
        DefinitionCanonical.closed(node, Set.of("common", "terms"), "/");
        var common = DefinitionCanonical.read(node.get("common"), Common.class);
        DefinitionEnvelopeRules.validate(common);
        common = DefinitionEnvelopeRules.normalized(common);
        ProfileTerms terms;
        require(common.profileCode() != null, "/common/profileCode");
        switch (common.profileCode()) {
            case "ACCOUNT_TARIFF_V1" -> {
                require("BANKING".equals(common.businessArea()), "/common/businessArea");
                var t = DefinitionCanonical.read(node.get("terms"), AccountTariffTerms.class);
                AccountTariffRules.validate(t, common);
                terms = t;
            }
            case "FIXED_RATE_INSTALLMENT_LOAN_V1" -> {
                require("LENDING".equals(common.businessArea()), "/common/businessArea");
                var t =
                        DefinitionCanonical.read(
                                node.get("terms"), FixedRateInstallmentLoanTerms.class);
                terms = FixedRateInstallmentLoanRules.validate(t, common);
            }
            case "TERM_PC_INSURANCE_V1" -> {
                require("INSURANCE".equals(common.businessArea()), "/common/businessArea");
                var t = DefinitionCanonical.read(node.get("terms"), TermPcInsuranceTerms.class);
                TermPcInsuranceRules.validate(t, common);
                terms = t;
            }
            default -> throw DefinitionRules.invalid("/common/profileCode");
        }
        return new ProductDefinition(common, terms);
    }

    public static Canonical canonical(String raw) {
        var definition = decode(raw);
        String json = DefinitionCanonical.encode(DefinitionCanonical.tree(definition));
        return new Canonical(json, DefinitionCanonical.hash("product-definition-public-v1", json));
    }
}

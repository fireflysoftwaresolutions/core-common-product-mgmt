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

import io.fireflysoftware.core.product.interfaces.dtos.definition.TermPcInsuranceTerms;

import java.util.*;

public final class TermPcInsuranceRules {
    private TermPcInsuranceRules() {}

    public static void validate(TermPcInsuranceTerms t, Common c) {
        require(t != null, "/terms");
        require("PROPERTY".equals(t.riskKind()), "/terms/riskKind");
        require(
                t.minimumCoverageDays() != null
                        && t.maximumCoverageDays() != null
                        && t.minimumCoverageDays() >= 1
                        && t.minimumCoverageDays() <= t.maximumCoverageDays()
                        && t.maximumCoverageDays() <= 366,
                "/terms/maximumCoverageDays");
        require("FIXED_PER_POLICY".equals(t.premiumBasis()), "/terms/premiumBasis");
        require("SINGLE_PREMIUM".equals(t.paymentSchedule()), "/terms/paymentSchedule");
        var total = money(t.basePremium(), c, "/terms/basePremium");
        require(total.signum() > 0, "/terms/basePremium");
        unique(
                t.taxComponents(),
                1,
                20,
                TermPcInsuranceTerms.TaxComponent::code,
                "/terms/taxComponents");
        for (var tax : t.taxComponents()) {
            total = total.add(money(tax.amount(), c, "/terms/taxComponents/amount"));
            reference(tax.policy(), "PREMIUM_TAX", "/terms/taxComponents/policy");
        }
        require(
                total.equals(money(t.totalPremium(), c, "/terms/totalPremium")),
                "/terms/totalPremium");
        unique(t.coverages(), 1, 100, TermPcInsuranceTerms.Coverage::code, "/terms/coverages");
        unique(
                t.deductibleGroups(),
                1,
                100,
                TermPcInsuranceTerms.DeductibleGroup::code,
                "/terms/deductibleGroups");
        var groups = new HashSet<String>();
        for (var group : t.deductibleGroups()) {
            require(
                    Set.of("PER_COVERAGE", "SHARED_MAX_ONCE_PER_OCCURRENCE")
                            .contains(group.mode() == null ? "" : group.mode()),
                    "/terms/deductibleGroups/mode");
            groups.add(group.code());
        }
        var used = new HashSet<String>();
        for (var coverage : t.coverages()) {
            reference(coverage.wording(), "COVERAGE_WORDING", "/terms/coverages/wording");
            var limit =
                    money(coverage.perOccurrenceLimit(), c, "/terms/coverages/perOccurrenceLimit");
            var aggregate = money(coverage.aggregateLimit(), c, "/terms/coverages/aggregateLimit");
            var deductible = money(coverage.deductible(), c, "/terms/coverages/deductible");
            require(
                    limit.signum() > 0 && aggregate.compareTo(limit) >= 0,
                    "/terms/coverages/aggregateLimit");
            require(deductible.compareTo(limit) <= 0, "/terms/coverages/deductible");
            require(groups.contains(coverage.groupCode()), "/terms/coverages/groupCode");
            used.add(coverage.groupCode());
        }
        require(used.equals(groups), "/terms/deductibleGroups");
        require(
                t.exclusions() != null && !t.exclusions().isEmpty() && t.exclusions().size() <= 50,
                "/terms/exclusions");
        var exclusions = new HashSet<ExactReference>();
        for (var ref : t.exclusions()) {
            reference(ref, "EXCLUSION_WORDING", "/terms/exclusions");
            require(exclusions.add(ref), "/terms/exclusions");
        }
        require(c.market().jurisdiction().equals(t.territory()), "/terms/territory");
        require(
                "PRO_RATA_UNEARNED".equals(t.cancellationRefundPolicy()),
                "/terms/cancellationRefundPolicy");
        namedFacts(c, "INSURED_OBJECT", "UNDERWRITING_APPROVAL", "REFERRAL_AUTHORITY");
    }
}

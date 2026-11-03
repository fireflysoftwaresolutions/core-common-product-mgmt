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

import io.fireflysoftware.core.product.interfaces.dtos.definition.FixedRateInstallmentLoanTerms;

public final class FixedRateInstallmentLoanRules {
    private FixedRateInstallmentLoanRules() {}

    public static FixedRateInstallmentLoanTerms validate(
            FixedRateInstallmentLoanTerms t, Common c) {
        require(t != null, "/terms");
        var min = money(t.principalMinimum(), c, "/terms/principalMinimum");
        var max = money(t.principalMaximum(), c, "/terms/principalMaximum");
        require(min.signum() > 0 && max.compareTo(min) >= 0, "/terms/principalMaximum");
        require(
                t.minimumMonths() != null
                        && t.maximumMonths() != null
                        && t.minimumMonths() >= 1
                        && t.minimumMonths() <= t.maximumMonths()
                        && t.maximumMonths() <= 600,
                "/terms/maximumMonths");
        var ratio =
                DefinitionRules.ratio(t.annualNominalRate(), "/terms/annualNominalRate")
                        .stripTrailingZeros()
                        .toPlainString();
        require(
                "NOMINAL_ANNUAL_DIVIDED_BY_12".equals(t.interestConvention()),
                "/terms/interestConvention");
        require(
                "MONTHLY_EQUAL_INSTALLMENT_NO_GRACE_NO_BALLOON".equals(t.repaymentConvention()),
                "/terms/repaymentConvention");
        require("HALF_EVEN".equals(t.installmentRounding()), "/terms/installmentRounding");
        require(
                "RECONCILE_EXACT_REMAINING_PRINCIPAL".equals(t.finalInstallmentAdjustment()),
                "/terms/finalInstallmentAdjustment");
        fee(t.originationFee(), c, "ORIGINATION", "/terms/originationFee");
        require("ALLOWED_NO_FEE".equals(t.earlyRepaymentPolicy()), "/terms/earlyRepaymentPolicy");
        reference(t.repaymentAllocation(), "REPAYMENT_ALLOCATION", "/terms/repaymentAllocation");
        reference(t.calendar(), "CALENDAR", "/terms/calendar");
        namedFacts(c, "KYC", "CREDIT_APPROVAL", "AFFORDABILITY");
        return new FixedRateInstallmentLoanTerms(
                t.principalMinimum(),
                t.principalMaximum(),
                t.minimumMonths(),
                t.maximumMonths(),
                ratio,
                t.interestConvention(),
                t.repaymentConvention(),
                t.installmentRounding(),
                t.finalInstallmentAdjustment(),
                t.originationFee(),
                t.earlyRepaymentPolicy(),
                t.repaymentAllocation(),
                t.calendar());
    }
}

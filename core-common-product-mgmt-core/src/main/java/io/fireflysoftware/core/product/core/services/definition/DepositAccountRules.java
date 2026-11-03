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
import io.fireflysoftware.core.product.interfaces.dtos.definition.DepositAccountTerms;
import java.util.List;

public final class DepositAccountRules {
    private DepositAccountRules() {}

    public static void validate(DepositAccountTerms terms, Common common) {
        require(terms != null, "/terms");
        require("CHECKING".equals(terms.accountKind()), "/terms/accountKind");
        require(List.of("SINGLE").equals(terms.ownershipKinds()), "/terms/ownershipKinds");
        require("USD".equals(common.market().currencyPolicy().isoCode()), "/common/market/currencyPolicy/isoCode");
        var opening = money(terms.openingMinimum(), common, "/terms/openingMinimum");
        var retained = money(terms.retainedMinimum(), common, "/terms/retainedMinimum");
        require(opening.compareTo(retained) >= 0, "/terms/openingMinimum");
        var transfer = money(terms.perTransferLimit(), common, "/terms/perTransferLimit");
        var daily = money(terms.dailyTransferLimit(), common, "/terms/dailyTransferLimit");
        require(transfer.signum() > 0 && daily.compareTo(transfer) >= 0, "/terms/dailyTransferLimit");
        require("NONE".equals(terms.interest()), "/terms/interest");
        require("NOT_OFFERED".equals(terms.overdraft()), "/terms/overdraft");
        require("NONE".equals(terms.transferFee()), "/terms/transferFee");
        require("DEPOSIT_ACCOUNT_ENROLLMENT".equals(terms.enrollmentPurpose()), "/terms/enrollmentPurpose");
    }
}

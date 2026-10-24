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

import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffTerms;

import java.util.*;

public final class AccountTariffRules {
    private AccountTariffRules() {}

    public static void validate(AccountTariffTerms t, Common c) {
        require(t != null, "/terms");
        require("CURRENT".equals(t.accountKind()), "/terms/accountKind");
        require(
                t.ownershipKinds() != null
                        && !t.ownershipKinds().isEmpty()
                        && t.ownershipKinds().size() <= 2
                        && new HashSet<>(t.ownershipKinds()).size() == t.ownershipKinds().size()
                        && Set.of("SINGLE", "JOINT").containsAll(t.ownershipKinds()),
                "/terms/ownershipKinds");
        var opening = money(t.openingMinimum(), c, "/terms/openingMinimum");
        var retained = money(t.retainedMinimum(), c, "/terms/retainedMinimum");
        require(opening.compareTo(retained) >= 0, "/terms/openingMinimum");
        var transfer = money(t.perTransferLimit(), c, "/terms/perTransferLimit");
        var daily = money(t.dailyTransferLimit(), c, "/terms/dailyTransferLimit");
        require(
                transfer.signum() > 0 && daily.compareTo(transfer) >= 0,
                "/terms/dailyTransferLimit");
        require("NONE".equals(t.interest()), "/terms/interest");
        require("NOT_OFFERED".equals(t.overdraft()), "/terms/overdraft");
        unique(t.tariffs(), 4, 4, FeeRule::code, "/terms/tariffs");
        var events = new HashSet<String>();
        var required = Set.of("MAINTENANCE", "INTERNAL_TRANSFER", "STATEMENT", "CLOSURE");
        for (var f : t.tariffs()) {
            require(
                    required.contains(f.event() == null ? "" : f.event()) && events.add(f.event()),
                    "/terms/tariffs/event");
            fee(f, c, f.event(), "/terms/tariffs");
        }
        require(events.equals(required), "/terms/tariffs");
        namedFacts(c, "KYC", "OWNERSHIP_MANDATE");
    }
}

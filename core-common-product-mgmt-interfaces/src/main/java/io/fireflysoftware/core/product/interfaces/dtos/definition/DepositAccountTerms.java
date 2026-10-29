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

import static io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;
import java.util.List;

public record DepositAccountTerms(
        String accountKind,
        List<String> ownershipKinds,
        Money openingMinimum,
        Money retainedMinimum,
        Money perTransferLimit,
        Money dailyTransferLimit,
        String interest,
        String overdraft,
        String transferFee,
        String enrollmentPurpose) implements ProfileTerms {
    public DepositAccountTerms { ownershipKinds = DefinitionContracts.copy(ownershipKinds); }
}

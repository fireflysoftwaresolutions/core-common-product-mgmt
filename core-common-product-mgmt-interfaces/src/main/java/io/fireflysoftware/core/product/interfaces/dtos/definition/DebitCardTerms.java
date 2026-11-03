/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.product.interfaces.dtos.definition;

import static io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;
import java.util.List;

/** Configured single-currency debit offering; metadata alone is never issuance or activation authority. */
public record DebitCardTerms(String cardKind,List<String> formFactors,String settlementMode,
        String issuerAdapterId,String processorAdapterId,Money perPurchaseLimit,Money dailyPurchaseLimit,
        Boolean cashWithdrawalEnabled,Money perWithdrawalLimit,Money dailyWithdrawalLimit,
        String foreignExchange,List<FeeRule> fees,String enrollmentPurpose) implements ProfileTerms {
    public DebitCardTerms {formFactors=DefinitionContracts.copy(formFactors);fees=DefinitionContracts.copy(fees);}
}

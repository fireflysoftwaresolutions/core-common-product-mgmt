/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.product.core.services.definition;

import static io.fireflysoftware.core.product.core.services.definition.DefinitionProfileSupport.*;
import static io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DebitCardTerms;
import java.util.*;

public final class DebitCardRules {
    private DebitCardRules() {}
    public static void validate(DebitCardTerms terms,Common common) {
        require(terms!=null,"/terms");
        require("DEBIT".equals(terms.cardKind()),"/terms/cardKind");
        require("LINKED_DEPOSIT_ACCOUNT".equals(terms.settlementMode()),"/terms/settlementMode");
        var factors=terms.formFactors();require(factors!=null&&!factors.isEmpty()&&factors.size()<=2,"/terms/formFactors");
        require(new HashSet<>(factors).size()==factors.size()&&Set.of("PHYSICAL","VIRTUAL").containsAll(factors),"/terms/formFactors");
        adapter(terms.issuerAdapterId(),"/terms/issuerAdapterId");adapter(terms.processorAdapterId(),"/terms/processorAdapterId");
        var purchase=money(terms.perPurchaseLimit(),common,"/terms/perPurchaseLimit");
        var daily=money(terms.dailyPurchaseLimit(),common,"/terms/dailyPurchaseLimit");
        require(purchase.signum()>0&&daily.compareTo(purchase)>=0,"/terms/dailyPurchaseLimit");
        require(terms.cashWithdrawalEnabled()!=null,"/terms/cashWithdrawalEnabled");
        if(terms.cashWithdrawalEnabled()) {
            var withdrawal=money(terms.perWithdrawalLimit(),common,"/terms/perWithdrawalLimit");
            var dailyWithdrawal=money(terms.dailyWithdrawalLimit(),common,"/terms/dailyWithdrawalLimit");
            require(withdrawal.signum()>0&&dailyWithdrawal.compareTo(withdrawal)>=0,"/terms/dailyWithdrawalLimit");
        } else require(terms.perWithdrawalLimit()==null&&terms.dailyWithdrawalLimit()==null,"/terms/perWithdrawalLimit");
        require("NOT_OFFERED".equals(terms.foreignExchange()),"/terms/foreignExchange");
        require("CARD_ENROLLMENT".equals(terms.enrollmentPurpose()),"/terms/enrollmentPurpose");
        unique(terms.fees(),3,3,FeeRule::code,"/terms/fees");unique(terms.fees(),3,3,FeeRule::event,"/terms/fees");
        Set<String> expected=Set.of("ISSUANCE","MAINTENANCE","REPLACEMENT");
        for(FeeRule rule:terms.fees()) {require(expected.contains(rule.event()),"/terms/fees/event");fee(rule,common,rule.event(),"/terms/fees/"+rule.code());}
        namedFacts(common,"KYC","OWNERSHIP_MANDATE");
    }
    private static void adapter(String value,String path) {require(value!=null&&value.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}"),path);}
}

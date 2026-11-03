/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.product.core.services.definition;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

/** Configuration/canonical controls only; metadata is never actual issued-card or owner-purpose evidence. */
class DebitCardProfileTest {
    static ObjectNode debit() { return ProfileFixtures.load("debit-card"); }
    static void invalid(java.util.function.Consumer<ObjectNode> change) {var root=debit();change.accept((ObjectNode)root.get("terms"));assertThrows(IllegalArgumentException.class,()->DefinitionProfiles.decode(root.toString()));}
    @Test void debitDefinitionHasLosslessClosedCanonicalRoundtrip() {var raw=debit().toString();assertEquals("DebitCardTerms",DefinitionProfiles.decode(raw).terms().getClass().getSimpleName());var canonical=DefinitionProfiles.canonical(raw);assertEquals(canonical,DefinitionProfiles.canonical(canonical.json()));}
    @Test void distinctNativeLifecycleProfileCannotBeAdmittedAsDepositOrInsurance() {var common=DefinitionProfiles.decode(debit().toString()).common();assertEquals("product-definition-debit-card-staff-v1",GovernedDefinitionProfile.from(common).policyProfile());assertThrows(IllegalArgumentException.class,()->GovernedDefinitionProfile.DEPOSIT_ACCOUNT.require(common));assertThrows(IllegalArgumentException.class,()->GovernedDefinitionProfile.TERM_PC_INSURANCE.require(common));}
    @Test void creditAndPrepaidAreNeverInterpretedAsDebit() {for(String kind:java.util.List.of("CREDIT","PREPAID","SANDBOX"))invalid(t->t.put("cardKind",kind));}
    @Test void debitNeverOffersUnbackedSettlement() {invalid(t->t.put("settlementMode","CREDIT_LINE"));}
    @Test void allLimitsBindTheExactMarketCurrencyPolicy() {invalid(t->((ObjectNode)t.at("/perPurchaseLimit/currencyPolicy")).put("digest","b".repeat(64)));}
    @Test void dailyLimitsCannotBeLessThanPerOperation() {invalid(t->((ObjectNode)t.get("dailyPurchaseLimit")).put("minorUnits","1"));invalid(t->((ObjectNode)t.get("dailyWithdrawalLimit")).put("minorUnits","1"));}
    @Test void disabledCashRequiresAbsentWithdrawalAmounts() {invalid(t->{t.put("cashWithdrawalEnabled",false);});var root=debit();var terms=(ObjectNode)root.get("terms");terms.put("cashWithdrawalEnabled",false);terms.putNull("perWithdrawalLimit");terms.putNull("dailyWithdrawalLimit");assertNotNull(DefinitionProfiles.decode(root.toString()));}
    @Test void everyDeclaredFeeIsUniqueAndNoOperationChargeIsImplicit() {invalid(t->((com.fasterxml.jackson.databind.node.ArrayNode)t.get("fees")).remove(0));invalid(t->((com.fasterxml.jackson.databind.node.ArrayNode)t.get("fees")).add(t.get("fees").get(0).deepCopy()));}
    @Test void fixedFeesNeedPositiveExactPolicyAmounts() {invalid(t->{var fee=(ObjectNode)t.get("fees").get(0);fee.put("kind","FIXED");fee.set("amount",t.get("perPurchaseLimit").deepCopy());((ObjectNode)fee.get("amount")).put("minorUnits","0");});}
    @Test void unknownFormFactorOrProviderCannotLaunch() {invalid(t->t.putArray("formFactors").add("MAGIC"));invalid(t->t.put("issuerAdapterId"," "));invalid(t->t.putArray("formFactors").add("VIRTUAL").add("VIRTUAL"));}
    @Test void unsupportedFxAndMissingPurposeAreExplicitRefusals() {invalid(t->t.put("foreignExchange","AUTO"));invalid(t->t.put("enrollmentPurpose","DEPOSIT_ACCOUNT_ENROLLMENT"));}
    @Test void callerCannotInsertFinancialOrIssuerApprovalIntoDefinition() {invalid(t->t.put("issuerApproved",true));invalid(t->t.put("activationProof","untrusted"));}
    @Test void realDocumentOpaqueVersionsRemainExactInCardContractSlots() {
        var root=debit();var document=(ObjectNode)root.at("/common/requirements/documents/0");
        for(String slot:java.util.List.of("template","issuer","layout")) {var reference=(ObjectNode)document.get(slot);reference.remove("version");reference.put("versionFormat","OPAQUE");reference.put("versionText","release.Case/"+slot);}
        var canonical=DefinitionProfiles.canonical(root.toString());var actual=DefinitionProfiles.decode(canonical.json()).common().requirements().documents().getFirst();
        assertEquals("release.Case/issuer",((io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.OpaqueOwnerVersion)actual.issuer().ownerVersion()).value());assertEquals(canonical,DefinitionProfiles.canonical(canonical.json()));
    }
    @Test void existingTariffAndInsuranceProfilesRemainDistinct() {assertEquals("AccountTariffTerms",DefinitionProfiles.decode(ProfileFixtures.load("account").toString()).terms().getClass().getSimpleName());assertEquals("TermPcInsuranceTerms",DefinitionProfiles.decode(ProfileFixtures.load("insurance").toString()).terms().getClass().getSimpleName());}
}

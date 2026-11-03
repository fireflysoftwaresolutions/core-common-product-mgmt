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

package io.fireflysoftware.core.product.core.services.cardeligibility;

import static io.fireflysoftware.core.product.interfaces.dtos.cardeligibility.CardEnrollmentCapabilityContracts.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CardEnrollmentCapabilityRulesTest {
    private static final long NOW = 1000;
    private static UUID id(int value) { return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", value)); }
    private static Selection selected() { return new Selection(id(1), id(2), id(3), id(4), id(5), id(6), 7L, 8L); }
    private static Request request() { return new Request(id(9), 7L, "CARD_ENROLLMENT", 1500L); }
    private static SuppliedEvidence evidence(Selection scope, String owner, String profile, long observed, long until) {
        return new SuppliedEvidence(scope, Purpose.CARD_ENROLLMENT, owner, id(10), profile, observed, until);
    }
    private static SuppliedEvidence evidence() { return evidence(selected(), "core-common-product-mgmt", "CARD_INSTRUMENT_V1", 900, 1400); }
    private static Assessment evaluate(Policy policy, SuppliedEvidence evidence) {
        return CardEnrollmentCapabilityRules.evaluateSupplied(request(), selected(), policy, evidence, NOW);
    }

    @Test void absentPolicyDefaultsToNotOfferedEvenWithCompleteSuppliedFacts() {
        var result = evaluate(null, evidence());
        assertEquals(Capability.NOT_OFFERED, result.capability());
        assertEquals(Reason.POLICY_NOT_OFFERED, result.reason());
    }
    @Test void explicitNotOfferedCannotBeOverriddenBySuppliedEvidence() {
        assertEquals(Capability.NOT_OFFERED, evaluate(Policy.NOT_OFFERED, evidence()).capability());
    }
    @Test void evidenceRequiredDoesNotOfferWithoutEvidence() {
        var result = evaluate(Policy.EVIDENCE_REQUIRED, null);
        assertEquals(Capability.UNKNOWN, result.capability());
        assertEquals(Reason.EVIDENCE_MISSING, result.reason());
    }
    @Test void completeClaimedCardEvidenceRemainsUnknownWhileGovernedCardIsUnsupported() {
        var result = evaluate(Policy.EVIDENCE_REQUIRED, evidence());
        assertEquals(Capability.UNKNOWN, result.capability());
        assertEquals(Reason.GOVERNED_CARD_UNSUPPORTED, result.reason());
    }
    @Test void activeManagedAndExistingProfilesCannotImplyCardOffering() {
        for (String profile : new String[]{"ACTIVE", "MANAGED", "FINANCIAL", "ACCOUNT_TARIFF_V1", "FIXED_RATE_INSTALLMENT_LOAN_V1", "TERM_PC_INSURANCE_V1", ""}) {
            var result = evaluate(Policy.EVIDENCE_REQUIRED, evidence(selected(), "core-common-product-mgmt", profile, 900, 1400));
            assertEquals(Capability.UNKNOWN, result.capability(), profile);
            assertEquals(Reason.GOVERNED_CARD_UNSUPPORTED, result.reason(), profile);
        }
    }
    @Test void everyScopeOrRevisionMismatchIsUnknown() {
        var s = selected();
        for (var wrong : new Selection[]{
                new Selection(id(20),s.institutionId(),s.bankId(),s.productId(),s.definitionId(),s.publicationId(),7L,8L),
                new Selection(s.tenantId(),id(20),s.bankId(),s.productId(),s.definitionId(),s.publicationId(),7L,8L),
                new Selection(s.tenantId(),s.institutionId(),id(20),s.productId(),s.definitionId(),s.publicationId(),7L,8L),
                new Selection(s.tenantId(),s.institutionId(),s.bankId(),id(20),s.definitionId(),s.publicationId(),7L,8L),
                new Selection(s.tenantId(),s.institutionId(),s.bankId(),s.productId(),id(20),s.publicationId(),7L,8L),
                new Selection(s.tenantId(),s.institutionId(),s.bankId(),s.productId(),s.definitionId(),id(20),7L,8L),
                new Selection(s.tenantId(),s.institutionId(),s.bankId(),s.productId(),s.definitionId(),s.publicationId(),9L,8L),
                new Selection(s.tenantId(),s.institutionId(),s.bankId(),s.productId(),s.definitionId(),s.publicationId(),7L,9L)}) {
            var result = evaluate(Policy.EVIDENCE_REQUIRED, evidence(wrong, "core-common-product-mgmt", "CARD_INSTRUMENT_V1", 900, 1400));
            assertEquals(Capability.UNKNOWN, result.capability());
            assertEquals(Reason.EVIDENCE_SCOPE_MISMATCH, result.reason());
        }
    }
    @Test void unownedAndIncompleteEvidenceIsUnknown() {
        for (String owner : new String[]{null, "", "core-banking-cards", "ProductDTO"}) {
            var result = evaluate(Policy.EVIDENCE_REQUIRED, evidence(selected(), owner, "CARD_INSTRUMENT_V1", 900, 1400));
            assertEquals(Capability.UNKNOWN, result.capability());
            assertEquals(Reason.EVIDENCE_OWNER_MISMATCH, result.reason());
        }
        for (var e : new SuppliedEvidence[]{
                new SuppliedEvidence(selected(), Purpose.CARD_ENROLLMENT,"core-common-product-mgmt",null,"CARD_INSTRUMENT_V1",900L,1400L),
                new SuppliedEvidence(selected(),null,"core-common-product-mgmt",id(10),"CARD_INSTRUMENT_V1",900L,1400L)}) {
            assertEquals(Reason.EVIDENCE_INCOMPLETE,evaluate(Policy.EVIDENCE_REQUIRED,e).reason());
        }
    }
    @Test void expiredFutureAndInvalidEvidenceIntervalsRemainUnknown() {
        for (long[] interval : new long[][]{{900,1000},{1001,1400},{0,1400},{900,899}}) {
            var result = evaluate(Policy.EVIDENCE_REQUIRED,evidence(selected(),"core-common-product-mgmt","CARD_INSTRUMENT_V1",interval[0],interval[1]));
            assertEquals(Capability.UNKNOWN,result.capability()); assertEquals(Reason.EVIDENCE_NOT_CURRENT,result.reason());
        }
    }
    @Test void originalDeadlineAndExpectedProductRevisionAreNotRenewed() {
        for (Request changed : new Request[]{new Request(id(9),7L,"CARD_ENROLLMENT",1000L),new Request(id(9),6L,"CARD_ENROLLMENT",1500L)}) {
            var result = CardEnrollmentCapabilityRules.evaluateSupplied(changed,selected(),Policy.EVIDENCE_REQUIRED,evidence(),NOW);
            assertEquals(Capability.UNKNOWN,result.capability()); assertEquals(Reason.REQUEST_NOT_CURRENT,result.reason());
        }
    }
    @Test void assessmentRetainsSelectionIntentAndPurposeWithoutReceiptClaim() {
        var result = evaluate(Policy.EVIDENCE_REQUIRED,evidence());
        assertEquals(selected(),result.selection()); assertEquals(request(),result.request());
        assertEquals(Purpose.CARD_ENROLLMENT,result.purpose()); assertEquals(NOW,result.assessedAtEpochMillis());
    }
    @Test void malformedRequestsAreRejectedBeforeEvaluation() {
        for (Request bad : new Request[]{null,new Request(null,7L,"REASON",1500L),new Request(new UUID(0,0),7L,"REASON",1500L),
                new Request(id(9),null,"REASON",1500L),new Request(id(9),-1L,"REASON",1500L),new Request(id(9),7L,null,1500L),
                new Request(id(9),7L,"lower",1500L),new Request(id(9),7L,"REASON\n",1500L),new Request(id(9),7L,"A".repeat(65),1500L),
                new Request(id(9),7L,"REASON",null),new Request(id(9),7L,"REASON",0L)}) {
            assertThrows(IllegalArgumentException.class,() -> CardEnrollmentCapabilityRules.evaluateSupplied(bad,selected(),null,null,NOW));
        }
    }
    @Test void malformedServerSelectionAndClockAreRejected() {
        assertThrows(IllegalArgumentException.class,() -> CardEnrollmentCapabilityRules.evaluateSupplied(request(),null,null,null,NOW));
        assertThrows(IllegalArgumentException.class,() -> CardEnrollmentCapabilityRules.evaluateSupplied(request(),new Selection(null,id(2),id(3),id(4),id(5),id(6),7L,8L),null,null,NOW));
        assertThrows(IllegalArgumentException.class,() -> CardEnrollmentCapabilityRules.evaluateSupplied(request(),new Selection(id(1),id(2),id(3),id(4),id(5),id(6),7L,-1L),null,null,NOW));
        assertThrows(IllegalArgumentException.class,() -> CardEnrollmentCapabilityRules.evaluateSupplied(request(),selected(),null,null,-1));
    }
}

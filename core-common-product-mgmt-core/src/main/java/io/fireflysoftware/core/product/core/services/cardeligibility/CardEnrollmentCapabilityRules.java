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

import java.util.UUID;

public final class CardEnrollmentCapabilityRules {
    private CardEnrollmentCapabilityRules() {}

    public static void validate(Request request) {
        if (request == null || !uuid(request.actionId()) || request.expectedProductRevision() == null
                || request.expectedProductRevision() < 0 || request.reasonCode() == null
                || !request.reasonCode().matches("[A-Z][A-Z0-9_]{0,63}")
                || request.acceptNotAfterEpochMillis() == null || request.acceptNotAfterEpochMillis() <= 0) throw invalid();
    }

    /** Compares supplied facts only; a matching sourceOwner string cannot authenticate an owner producer. */
    public static Assessment evaluateSupplied(Request request, Selection selected, Policy policy,
            SuppliedEvidence evidence, long now) {
        validate(request);
        selection(selected);
        if (now < 0) throw invalid();
        if (policy == null || policy == Policy.NOT_OFFERED) {
            return assessment(request, selected, Capability.NOT_OFFERED, Reason.POLICY_NOT_OFFERED, now);
        }
        Reason reason;
        if (request.acceptNotAfterEpochMillis() <= now || !request.expectedProductRevision().equals(selected.productRevision())) {
            reason = Reason.REQUEST_NOT_CURRENT;
        } else if (evidence == null) {
            reason = Reason.EVIDENCE_MISSING;
        } else if (evidence.purpose() != Purpose.CARD_ENROLLMENT || !uuid(evidence.receiptId())
                || evidence.profileCode() == null) {
            reason = Reason.EVIDENCE_INCOMPLETE;
        } else if (!selected.equals(evidence.selection())) {
            reason = Reason.EVIDENCE_SCOPE_MISMATCH;
        } else if (!"core-common-product-mgmt".equals(evidence.sourceOwner())) {
            reason = Reason.EVIDENCE_OWNER_MISMATCH;
        } else if (evidence.observedAtEpochMillis() == null || evidence.observedAtEpochMillis() <= 0
                || evidence.observedAtEpochMillis() > now || evidence.validUntilEpochMillis() == null
                || evidence.validUntilEpochMillis() <= now) {
            reason = Reason.EVIDENCE_NOT_CURRENT;
        } else {
            // CARD is absent from the governed profile decoder and native admission contract.
            reason = Reason.GOVERNED_CARD_UNSUPPORTED;
        }
        return assessment(request, selected, Capability.UNKNOWN, reason, now);
    }

    private static Assessment assessment(Request request, Selection selected, Capability capability, Reason reason, long now) {
        return new Assessment(selected, request, Purpose.CARD_ENROLLMENT, capability, reason, now);
    }

    private static void selection(Selection selected) {
        if (selected == null || !uuid(selected.tenantId()) || !uuid(selected.institutionId())
                || !uuid(selected.bankId()) || !uuid(selected.productId()) || !uuid(selected.definitionId())
                || !uuid(selected.publicationId()) || selected.productRevision() == null || selected.productRevision() < 0
                || selected.publicationControlRevision() == null || selected.publicationControlRevision() < 0) throw invalid();
    }

    static boolean uuid(UUID value) { return value != null && !value.equals(new UUID(0, 0)); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("EXACT_CARD_ENROLLMENT_CAPABILITY_REQUIRED"); }
}

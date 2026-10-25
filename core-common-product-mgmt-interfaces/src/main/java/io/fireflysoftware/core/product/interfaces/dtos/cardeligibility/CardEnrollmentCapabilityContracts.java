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

package io.fireflysoftware.core.product.interfaces.dtos.cardeligibility;

import java.util.UUID;

/** Pure comparison inputs and results; none establishes an authenticated owner observation. */
public final class CardEnrollmentCapabilityContracts {
    private CardEnrollmentCapabilityContracts() {}

    public enum Purpose { CARD_ENROLLMENT }
    public enum Policy { NOT_OFFERED, EVIDENCE_REQUIRED }
    public enum Capability { NOT_OFFERED, UNKNOWN }
    public enum Reason {
        POLICY_NOT_OFFERED, REQUEST_NOT_CURRENT, EVIDENCE_MISSING, EVIDENCE_INCOMPLETE,
        EVIDENCE_SCOPE_MISMATCH, EVIDENCE_OWNER_MISMATCH, EVIDENCE_NOT_CURRENT,
        GOVERNED_CARD_UNSUPPORTED
    }

    public record Request(UUID actionId, Long expectedProductRevision, String reasonCode,
            Long acceptNotAfterEpochMillis) {}

    public record Selection(UUID tenantId, UUID institutionId, UUID bankId, UUID productId,
            UUID definitionId, UUID publicationId, Long productRevision, Long publicationControlRevision) {}

    /** sourceOwner and receiptId are supplied correlation facts, not proof of ownership or authenticity. */
    public record SuppliedEvidence(Selection selection, Purpose purpose, String sourceOwner,
            UUID receiptId, String profileCode, Long observedAtEpochMillis, Long validUntilEpochMillis) {}

    /** An assessment is neither a durable receipt nor a capability-admission decision. */
    public record Assessment(Selection selection, Request request, Purpose purpose,
            Capability capability, Reason reason, long assessedAtEpochMillis) {}
}

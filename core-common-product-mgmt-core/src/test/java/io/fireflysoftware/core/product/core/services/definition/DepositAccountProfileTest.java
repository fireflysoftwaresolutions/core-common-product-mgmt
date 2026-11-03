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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DepositAccountProfileTest {
    static ObjectNode deposit() {
        var root = ProfileFixtures.load("account");
        ((ObjectNode) root.get("common")).put("profileCode", "DEPOSIT_ACCOUNT_V1");
        var terms = (ObjectNode) root.get("terms");
        terms.put("accountKind", "CHECKING");
        terms.putArray("ownershipKinds").add("SINGLE");
        terms.remove("tariffs");
        terms.put("transferFee", "NONE");
        terms.put("enrollmentPurpose", "DEPOSIT_ACCOUNT_ENROLLMENT");
        return root;
    }
    @Test void distinctDepositDefinitionHasCanonicalRoundtrip() {
        var raw = deposit().toString();
        var decoded = DefinitionProfiles.decode(raw);
        assertEquals("DEPOSIT_ACCOUNT_V1", decoded.common().profileCode());
        assertEquals("DepositAccountTerms", decoded.terms().getClass().getSimpleName());
        var canonical = DefinitionProfiles.canonical(raw);
        assertEquals(canonical, DefinitionProfiles.canonical(canonical.json()));
    }
    @Test void lifecycleUsesDistinctBackendPolicyProfile() {
        var common = DefinitionProfiles.decode(deposit().toString()).common();
        var profile = GovernedDefinitionProfile.from(common);
        assertEquals("product-definition-deposit-staff-v1", profile.policyProfile());
        assertEquals("DEPOSIT_ACCOUNT_V1", profile.profileCode());
        assertThrows(IllegalArgumentException.class, () -> GovernedDefinitionProfile.ACCOUNT_TARIFF.require(common));
    }
    @Test void tariffStaysDistinct() {
        var decoded = DefinitionProfiles.decode(ProfileFixtures.load("account").toString());
        assertEquals("AccountTariffTerms", decoded.terms().getClass().getSimpleName());
        assertEquals("ACCOUNT_TARIFF_V1", decoded.common().profileCode());
    }
    @Test void sandboxIsNotACustomerProductType() {
        var root = deposit();
        ((ObjectNode) root.get("terms")).put("accountKind", "SANDBOX");
        assertThrows(IllegalArgumentException.class, () -> DefinitionProfiles.decode(root.toString()));
    }
    @Test void depositDoesNotOfferOverdraft() {
        var root = deposit();
        ((ObjectNode) root.get("terms")).put("overdraft", "OFFERED");
        assertThrows(IllegalArgumentException.class, () -> DefinitionProfiles.decode(root.toString()));
    }
    @Test void noUnknownFeeOrActivationField() {
        var root = deposit();
        ((ObjectNode) root.get("terms")).put("financialAdmission", "APPROVED");
        assertThrows(IllegalArgumentException.class, () -> DefinitionProfiles.decode(root.toString()));
    }
    @Test void realOpaqueDocumentVersionsRemainLosslessInDepositSlots() {
        var root=deposit();var document=(ObjectNode)root.get("common").get("requirements").get("documents").get(0);
        for(String slot:java.util.List.of("template","issuer","layout")){
            var reference=(ObjectNode)document.get(slot);reference.remove("version");reference.put("versionFormat","OPAQUE");reference.put("versionText","release.Case/"+slot);
        }
        var canonical=DefinitionProfiles.canonical(root.toString());
        var decoded=DefinitionProfiles.decode(canonical.json());var actual=decoded.common().requirements().documents().getFirst();
        assertEquals("release.Case/template",((io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.OpaqueOwnerVersion)actual.template().ownerVersion()).value());
        assertEquals("release.Case/issuer",((io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.OpaqueOwnerVersion)actual.issuer().ownerVersion()).value());
        assertEquals("release.Case/layout",((io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.OpaqueOwnerVersion)actual.layout().ownerVersion()).value());
        assertEquals(canonical,DefinitionProfiles.canonical(canonical.json()));
    }
    @Test void existingTariffDocumentPolicyIsNotBroadened() {
        var root=ProfileFixtures.load("account");var ref=(ObjectNode)root.get("common").get("requirements").get("documents").get(0).get("template");
        ref.remove("version");ref.put("versionFormat","OPAQUE");ref.put("versionText","release.Case/template");
        assertThrows(IllegalArgumentException.class,()->DefinitionProfiles.decode(root.toString()));
    }
}

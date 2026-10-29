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

import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.Common;
import java.util.Objects;

/** Backend-selected lifecycle binding; it is never a request permission or claimant-provided role. */
public enum GovernedDefinitionProfile {
    ACCOUNT_TARIFF("BANKING","ACCOUNT_TARIFF_V1","product-definition-human-staff-v1"),
    DEPOSIT_ACCOUNT("BANKING","DEPOSIT_ACCOUNT_V1","product-definition-deposit-staff-v1"),
    TERM_PC_INSURANCE("INSURANCE","TERM_PC_INSURANCE_V1","product-definition-term-pc-staff-v1");
    private final String businessArea,profileCode,policyProfile;
    GovernedDefinitionProfile(String businessArea,String profileCode,String policyProfile){this.businessArea=businessArea;this.profileCode=profileCode;this.policyProfile=policyProfile;}
    public String businessArea(){return businessArea;}
    public String profileCode(){return profileCode;}
    public String policyProfile(){return policyProfile;}
    public boolean matches(String businessArea,String profileCode,Integer version){return this.businessArea.equals(businessArea) && this.profileCode.equals(profileCode) && Integer.valueOf(1).equals(version);}
    public void require(Common common){if(common==null || !matches(common.businessArea(),common.profileCode(),common.profileVersion()))throw invalid();}
    public static GovernedDefinitionProfile from(Common common){Objects.requireNonNull(common);for(var profile:values())if(profile.matches(common.businessArea(),common.profileCode(),common.profileVersion()))return profile;throw invalid();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("PRODUCT_DEFINITION_ACCOUNT_PROFILE_REQUIRED");}
}

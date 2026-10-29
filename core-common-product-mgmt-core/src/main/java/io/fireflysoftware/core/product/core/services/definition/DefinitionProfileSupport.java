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

import static io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;

final class DefinitionProfileSupport {
    private DefinitionProfileSupport() {}

    static void require(boolean value, String path) {
        if (!value) throw DefinitionRules.invalid(path);
    }

    static void code(String value, String path) {
        require(value != null && value.matches("[A-Z][A-Z0-9_]{0,63}"), path);
    }

    static void owner(String value, String path) {
        require(value != null && value.matches("[a-z][a-z0-9-]{0,63}"), path);
    }

    static void digest(String value, String path) {
        require(value != null && value.matches("[0-9a-f]{64}"), path);
    }

    static void reference(ExactReference ref, String kind, String path) {
        require(ref != null, path);
        owner(ref.owner(), path + "/owner");
        require(kind.equals(ref.kind()), path + "/kind");
        DefinitionRules.uuid(ref.id(), path + "/id");
        require(ref.version() != null && ref.version() > 0 && ref.versionFormat()==null && ref.versionText()==null, path + "/version");
        digest(ref.digest(), path + "/digest");
    }

    static void documentReference(ExactReference ref,String kind,String path,boolean opaqueAllowed) {
        if(opaqueAllowed && ref!=null && ref.version()==null && "OPAQUE".equals(ref.versionFormat())) {
            owner(ref.owner(),path+"/owner");require(kind.equals(ref.kind()),path+"/kind");
            DefinitionRules.uuid(ref.id(),path+"/id");digest(ref.digest(),path+"/digest");
            try {require(ref.ownerVersion() instanceof OpaqueOwnerVersion,path+"/versionText");}
            catch(IllegalArgumentException invalid){throw DefinitionRules.invalid(path+"/versionText");}
        } else reference(ref,kind,path);
    }

    static void currency(CurrencyPolicyReference ref, String path) {
        require(ref != null, path);
        DefinitionRules.uuid(ref.policyId(), path + "/policyId");
        DefinitionRules.uuid(ref.currencyId(), path + "/currencyId");
        require(ref.isoCode() != null && ref.isoCode().matches("[A-Z]{3}"), path + "/isoCode");
        require(ref.version() != null && ref.version() > 0, path + "/version");
        digest(ref.digest(), path + "/digest");
        require(
                "MASTERS_CURRENCY_POLICY_V1".equals(ref.encodingVersion()),
                path + "/encodingVersion");
    }

    static BigInteger money(Money value, Common common, String path) {
        require(value != null, path);
        currency(value.currencyPolicy(), path + "/currencyPolicy");
        require(
                value.currencyPolicy().equals(common.market().currencyPolicy()),
                path + "/currencyPolicy");
        return DefinitionRules.minorUnits(value.minorUnits(), path + "/minorUnits");
    }

    static <T> void unique(
            List<T> values, int minimum, int maximum, Function<T, String> key, String path) {
        require(values != null && values.size() >= minimum && values.size() <= maximum, path);
        var seen = new HashSet<String>();
        for (T value : values) {
            require(value != null, path);
            String code = key.apply(value);
            code(code, path);
            require(seen.add(code), path);
        }
    }

    static void namedFacts(Common common, String... codes) {
        var actual = new HashSet<String>();
        for (var f : common.requirements().facts()) actual.add(f.code());
        require(actual.containsAll(List.of(codes)), "/common/requirements/facts");
    }

    static void fee(FeeRule rule, Common common, String expectedEvent, String path) {
        require(rule != null, path);
        code(rule.code(), path + "/code");
        require(expectedEvent.equals(rule.event()), path + "/event");
        require("CUSTOMER".equals(rule.payer()), path + "/payer");
        require(
                ("MAINTENANCE".equals(expectedEvent) ? "MONTHLY" : "PER_EVENT")
                        .equals(rule.occurrence()),
                path + "/occurrence");
        if ("FREE".equals(rule.kind())) require(rule.amount() == null, path + "/amount");
        else {
            require("FIXED".equals(rule.kind()), path + "/kind");
            require(money(rule.amount(), common, path + "/amount").signum() > 0, path + "/amount");
        }
        require(
                Set.of("NOT_REFUNDABLE", "PRO_RATA_UNEARNED", "FULL_ON_CANCELLATION")
                        .contains(rule.refundPolicy() == null ? "" : rule.refundPolicy()),
                path + "/refundPolicy");
        if ("EXEMPT".equals(rule.taxTreatment()))
            require(rule.taxPolicy() == null, path + "/taxPolicy");
        else {
            require("EXTERNAL_POLICY_REQUIRED".equals(rule.taxTreatment()), path + "/taxTreatment");
            reference(rule.taxPolicy(), "TAX_POLICY", path + "/taxPolicy");
        }
    }
}

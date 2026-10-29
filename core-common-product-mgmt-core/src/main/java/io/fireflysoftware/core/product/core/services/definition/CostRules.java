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

import static io.fireflysoftware.core.product.core.services.definition.DefinitionProfileSupport.*;

import io.fireflysoftware.core.product.interfaces.dtos.definition.CostTerms;

import java.util.*;

public final class CostRules {
    private CostRules() {}

    public record Canonical(String json, String digest) {}

    public static Canonical canonical(String raw, String publicRaw) {
        try {
            var common = DefinitionProfiles.decode(publicRaw).common();
            var cost = DefinitionCanonical.read(DefinitionCanonical.parse(raw), CostTerms.class);
            require(
                    Integer.valueOf(1).equals(cost.schemaVersion())
                            && common.businessArea().equals(cost.businessArea())
                            && common.profileCode().equals(cost.profileCode()),
                    "/");
            if ("NONE".equals(cost.mode()))
                require(cost.lines() != null && cost.lines().isEmpty(), "/");
            else {
                require("DEFINED".equals(cost.mode()), "/");
                unique(cost.lines(), 1, 100, CostTerms.CostLine::code, "/");
                for (var line : cost.lines()) {
                    if ("FIXED".equals(line.kind())) {
                        require(
                                line.ratio() == null
                                        && money(line.amount(), common, "/").signum() > 0,
                                "/");
                        String occurrence =
                                switch (line.basis() == null ? "" : line.basis()) {
                                    case "PER_CONTRACT" -> "ON_CREATION";
                                    case "PER_CYCLE" -> "MONTHLY";
                                    case "PER_EVENT" -> "PER_EVENT";
                                    default -> throw DefinitionRules.invalid("/");
                                };
                        require(occurrence.equals(line.occurrence()), "/");
                    } else {
                        require("RATIO".equals(line.kind()) && line.amount() == null, "/");
                        require(DefinitionRules.ratio(line.ratio(), "/").signum() > 0, "/");
                        String basis =
                                switch (common.businessArea()) {
                                    case "BANKING" -> "TARIFF_REVENUE";
                                    case "LENDING" -> "PRINCIPAL";
                                    case "INSURANCE" -> "PREMIUM";
                                    default -> throw DefinitionRules.invalid("/");
                                };
                        require(
                                basis.equals(line.basis())
                                        && "ON_CREATION".equals(line.occurrence()),
                                "/");
                    }
                    reference(line.payee(), "PARTY", "/");
                    reference(line.allocation(), "GL_ALLOCATION", "/");
                }
            }
            if (cost.lines() != null)
                cost =
                        new CostTerms(
                                cost.schemaVersion(),
                                cost.businessArea(),
                                cost.profileCode(),
                                cost.mode(),
                                cost.lines().stream()
                                        .map(
                                                line ->
                                                        "RATIO".equals(line.kind())
                                                                ? new CostTerms.CostLine(
                                                                        line.code(),
                                                                        line.kind(),
                                                                        line.amount(),
                                                                        DefinitionRules.ratio(
                                                                                        line
                                                                                                .ratio(),
                                                                                        "/")
                                                                                .stripTrailingZeros()
                                                                                .toPlainString(),
                                                                        line.basis(),
                                                                        line.occurrence(),
                                                                        line.payee(),
                                                                        line.allocation())
                                                                : line)
                                        .toList());
            String json = DefinitionCanonical.encode(DefinitionCanonical.tree(cost));
            return new Canonical(
                    json, DefinitionCanonical.hash("product-definition-private-cost-v1", json));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("COST_DEFINITION_INVALID");
        }
    }
}

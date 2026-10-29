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
import static io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

final class DefinitionEnvelopeRules {
    private DefinitionEnvelopeRules() {}

    static void validate(Common c) {
        require(c != null, "/common");
        require(Integer.valueOf(1).equals(c.schemaVersion()), "/common/schemaVersion");
        require(Integer.valueOf(1).equals(c.profileVersion()), "/common/profileVersion");
        code(c.code(), "/common/code");
        code(c.portfolioCode(), "/common/portfolioCode");
        DefinitionRules.text(c.name(), 256, "/common/name");
        DefinitionRules.text(c.description(), 2048, "/common/description");
        DefinitionRules.uuid(c.legalEntityId(), "/common/legalEntityId");
        require(c.market() != null, "/common/market");
        require(
                c.market().jurisdiction() != null && c.market().jurisdiction().matches("[A-Z]{2}"),
                "/common/market/jurisdiction");
        require(
                Set.of("BRANCH", "DIGITAL", "AGENCY")
                        .contains(c.market().channel() == null ? "" : c.market().channel()),
                "/common/market/channel");
        code(c.market().customerSegment(), "/common/market/customerSegment");
        currency(c.market().currencyPolicy(), "/common/market/currencyPolicy");
        DefinitionRules.window(c.effectiveFrom(), c.effectiveTo());
        var r = c.requirements();
        require(r != null, "/common/requirements");
        unique(r.facts(), 1, 100, FactRequirement::code, "/common/requirements/facts");
        for (var fact : r.facts()) {
            String path = "/common/requirements/facts";
            owner(fact.sourceOwner(), path + "/sourceOwner");
            code(fact.factContract(), path + "/factContract");
            require(fact.version() != null && fact.version() > 0, path + "/version");
            digest(fact.contractDigest(), path + "/contractDigest");
            require(
                    fact.maxAgeSeconds() != null
                            && fact.maxAgeSeconds() > 0
                            && fact.maxAgeSeconds() <= 31536000,
                    path + "/maxAgeSeconds");
            require(fact.evidenceRequired() != null, path + "/evidenceRequired");
            require(
                    Set.of("STRING", "INTEGER", "DECIMAL_RATIO", "DATE", "BOOLEAN")
                            .contains(fact.kind() == null ? "" : fact.kind()),
                    path + "/kind");
            var values = fact.values();
            require(
                    values != null
                            && values.size() <= 50
                            && new HashSet<>(values).size() == values.size(),
                    path + "/values");
            switch (fact.operator() == null ? "" : fact.operator()) {
                case "PRESENT" -> require(values.isEmpty(), path + "/values");
                case "EQUALS" -> require(values.size() == 1, path + "/values");
                case "ONE_OF" -> require(!values.isEmpty(), path + "/values");
                case "BETWEEN" -> {
                    require(
                            values.size() == 2
                                    && !Set.of("STRING", "BOOLEAN").contains(fact.kind()),
                            path + "/values");
                }
                default -> throw DefinitionRules.invalid(path + "/operator");
            }
            for (String value : values) {
                DefinitionRules.text(value, 128, path + "/values");
                switch (fact.kind()) {
                    case "INTEGER" ->
                            require(value.matches("0|[1-9][0-9]{0,14}"), path + "/values");
                    case "DECIMAL_RATIO" -> DefinitionRules.ratio(value, path + "/values");
                    case "BOOLEAN" ->
                            require(Set.of("true", "false").contains(value), path + "/values");
                    case "DATE" -> {
                        try {
                            require(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"), path + "/values");
                            LocalDate.parse(value);
                        } catch (java.time.DateTimeException e) {
                            throw DefinitionRules.invalid(path + "/values");
                        }
                    }
                    default -> {}
                }
            }
            if ("BETWEEN".equals(fact.operator())) {
                int order =
                        "DATE".equals(fact.kind())
                                ? values.get(0).compareTo(values.get(1))
                                : new BigDecimal(values.get(0))
                                        .compareTo(new BigDecimal(values.get(1)));
                require(order <= 0, path + "/values");
            }
        }
        unique(r.documents(), 1, 50, DocumentRequirement::code, "/common/requirements/documents");
        boolean contract = false;
        boolean opaqueDocuments=("INSURANCE".equals(c.businessArea()) && "TERM_PC_INSURANCE_V1".equals(c.profileCode()))
                || ("BANKING".equals(c.businessArea()) && "DEPOSIT_ACCOUNT_V1".equals(c.profileCode()));
        for (var doc : r.documents()) {
            String path = "/common/requirements/documents";
            code(doc.documentType(), path + "/documentType");
            require(doc.mandatory() != null, path + "/mandatory");
            documentReference(doc.template(), "DOCUMENT_TEMPLATE", path + "/template", opaqueDocuments);
            documentReference(doc.issuer(), "DOCUMENT_ISSUER", path + "/issuer", opaqueDocuments);
            documentReference(doc.layout(), "DOCUMENT_LAYOUT", path + "/layout", opaqueDocuments);
            DefinitionRules.text(doc.locale(), 50, path + "/locale");
            try {
                new Locale.Builder().setLanguageTag(doc.locale()).build();
            } catch (IllformedLocaleException e) {
                throw DefinitionRules.invalid(path + "/locale");
            }
            contract |= "CONTRACT".equals(doc.code()) && Boolean.TRUE.equals(doc.mandatory());
        }
        require(contract, "/common/requirements/documents");
        unique(
                r.dependencies(),
                1,
                100,
                DependencyRequirement::code,
                "/common/requirements/dependencies");
        boolean execution = false;
        for (var d : r.dependencies()) {
            code(d.capability(), "/common/requirements/dependencies/capability");
            owner(d.owner(), "/common/requirements/dependencies/owner");
            require(
                    d.requiredForLaunch() != null,
                    "/common/requirements/dependencies/requiredForLaunch");
            execution |= Boolean.TRUE.equals(d.requiredForLaunch());
        }
        require(execution, "/common/requirements/dependencies");
    }

    static Common normalized(Common c) {
        var facts =
                c.requirements().facts().stream()
                        .map(
                                f -> {
                                    var values =
                                            "DECIMAL_RATIO".equals(f.kind())
                                                    ? f.values().stream()
                                                            .map(
                                                                    value ->
                                                                            DefinitionRules.ratio(
                                                                                            value,
                                                                                            "/common/requirements/facts/values")
                                                                                    .stripTrailingZeros()
                                                                                    .toPlainString())
                                                            .toList()
                                                    : f.values();
                                    return new FactRequirement(
                                            f.code(),
                                            f.sourceOwner(),
                                            f.factContract(),
                                            f.version(),
                                            f.contractDigest(),
                                            f.kind(),
                                            f.operator(),
                                            values,
                                            f.maxAgeSeconds(),
                                            f.evidenceRequired());
                                })
                        .toList();
        return new Common(
                c.schemaVersion(),
                c.businessArea(),
                c.profileCode(),
                c.profileVersion(),
                c.code(),
                c.name(),
                c.description(),
                c.portfolioCode(),
                c.legalEntityId(),
                c.market(),
                c.effectiveFrom(),
                c.effectiveTo(),
                new Requirements(
                        facts, c.requirements().documents(), c.requirements().dependencies()));
    }
}

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

package io.fireflysoftware.core.product.interfaces.dtos.definition;

import java.util.Set;

public enum ProductDefinitionCommand {
    CREATE_DRAFT(
            "draft.create",
            null,
            Set.of("product:definitions:write", "product:definitions:cost:write")),
    EDIT_PUBLIC("public.edit", null, Set.of("product:definitions:write")),
    EDIT_COST("cost.edit", null, Set.of("product:definitions:cost:write")),
    SUBMIT_PUBLIC("public.submit", null, Set.of("product:definitions:write")),
    APPROVE_SELECTION(
            "selection.approve",
            "submission",
            Set.of("product:definitions:review", "product:definitions:cost:read")),
    REJECT_SELECTION(
            "selection.reject",
            "submission",
            Set.of("product:definitions:review", "product:definitions:cost:read")),
    PUBLISH("publication.publish", "review", Set.of("product:definitions:publish")),
    WITHDRAW_PUBLICATION(
            "publication.withdraw", "publication", Set.of("product:definitions:withdraw"));
    private final String action;
    private final String target;
    private final Set<String> scopes;

    ProductDefinitionCommand(String suffix, String target, Set<String> scopes) {
        action = "product.definition." + suffix;
        this.target = target;
        this.scopes = scopes;
    }

    public String action() {
        return action;
    }

    public String target() {
        return target;
    }

    public Set<String> requiredScopes() {
        return scopes;
    }
}

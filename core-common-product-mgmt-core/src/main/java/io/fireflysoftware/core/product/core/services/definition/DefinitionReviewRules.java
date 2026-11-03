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

import java.util.Set;

public final class DefinitionReviewRules {
    private DefinitionReviewRules() {}

    public static void independent(
            String issuer, String actor, Set<String> contributors, boolean human) {
        if (issuer == null
                || issuer.isBlank()
                || actor == null
                || actor.isBlank()
                || issuer.contains("|")
                || actor.contains("|"))
            throw new IllegalArgumentException("CHECKER_IDENTITY_REQUIRED");
        if (!human) throw new IllegalArgumentException("HUMAN_CHECKER_REQUIRED");
        if (contributors == null || contributors.isEmpty())
            throw new IllegalArgumentException("REVIEW_CONTRIBUTORS_REQUIRED");
        if (contributors.contains(issuer + "|" + actor))
            throw new IllegalArgumentException("INDEPENDENT_CHECKER_REQUIRED");
    }
}

package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

final class ProductDefinitionCommandPlan {
    private static final int MAX_PLAN_BYTES = 524288;
    private static final String ENCODING = "product-definition-native-plan-v1";

    private ProductDefinitionCommandPlan() {}

    private record Snapshot(String encoding, String canonical, String digest) {}

    private record Plan(
            String encoding,
            String validationBoundary,
            ProductDefinitionLifecycleContracts.Scope scope,
            ProductDefinitionCommand command,
            String originalIntentDigest,
            ProductDefinitionCommandResources resources,
            Body preparedBody,
            Snapshot publicSnapshot,
            Snapshot privateSnapshot) {}

    static String encode(Parsed parsed, ProductDefinitionCommandResources resources) {
        try {
            if (parsed == null
                    || parsed.instruction() == null
                    || parsed.canonicalIntent() == null
                    || parsed.canonicalIntent().length() > 131072) throw invalid();
            var instruction = parsed.instruction();
            var scope = instruction.scope();
            var original = DefinitionCanonical.parse(parsed.canonicalIntent());
            var reparsed =
                    ProductDefinitionInstructionCodec.parse(
                            scope.tenantId(),
                            scope.rootId(),
                            instruction.command(),
                            DefinitionCanonical.encode(original.get("request")));
            if (!reparsed.equals(parsed)) throw invalid();
            resources(instruction.command(), resources);
            Snapshot publicSnapshot = null, privateSnapshot = null;
            if (instruction.body() instanceof CreateDraftBody body) {
                publicSnapshot = publicSnapshot(body.definition());
                privateSnapshot = privateSnapshot(body.cost(), publicSnapshot);
            } else if (instruction.body() instanceof EditPublicBody body)
                publicSnapshot = publicSnapshot(body.definition());
            else if (instruction.body() instanceof EditCostBody body) {
                publicSnapshot = publicSnapshot(body.publicDefinition());
                privateSnapshot = privateSnapshot(body.cost(), publicSnapshot);
            }
            String encoded =
                    DefinitionCanonical.encode(
                            DefinitionCanonical.tree(
                                    new Plan(
                                            ENCODING,
                                            "TRUSTED_SERVICE",
                                            scope,
                                            instruction.command(),
                                            parsed.intentDigest(),
                                            resources,
                                            instruction.body(),
                                            publicSnapshot,
                                            privateSnapshot)));
            if (encoded.getBytes(StandardCharsets.UTF_8).length > MAX_PLAN_BYTES) throw invalid();
            return encoded;
        } catch (RuntimeException invalid) {
            throw invalid();
        }
    }

    private static Snapshot publicSnapshot(DefinitionContracts.ProductDefinition definition) {
        var value =
                DefinitionProfiles.canonical(
                        DefinitionCanonical.encode(DefinitionCanonical.tree(definition)));
        return new Snapshot(DefinitionCanonical.ENCODING, value.json(), value.digest());
    }

    private static Snapshot privateSnapshot(CostTerms cost, Snapshot publicSnapshot) {
        var value =
                CostRules.canonical(
                        DefinitionCanonical.encode(DefinitionCanonical.tree(cost)),
                        publicSnapshot.canonical());
        return new Snapshot(DefinitionCanonical.ENCODING, value.json(), value.digest());
    }

    private static void resources(
            ProductDefinitionCommand command, ProductDefinitionCommandResources resources) {
        if (resources == null) throw invalid();
        var required = new HashSet<>(Set.of("actionId", "auditId"));
        boolean publicAction =
                switch (command) {
                    case EDIT_COST, APPROVE_SELECTION, REJECT_SELECTION -> false;
                    default -> true;
                };
        if (publicAction) required.addAll(Set.of("historyId", "publicEventId"));
        switch (command) {
            case CREATE_DRAFT ->
                    required.addAll(Set.of("revisionId", "costRevisionId", "costEventId"));
            case EDIT_PUBLIC -> required.add("revisionId");
            case EDIT_COST -> required.addAll(Set.of("costRevisionId", "costEventId"));
            case SUBMIT_PUBLIC -> required.add("submissionId");
            case APPROVE_SELECTION, REJECT_SELECTION ->
                    required.addAll(Set.of("reviewId", "costEventId"));
            case PUBLISH -> required.addAll(Set.of("publicationId", "productVersionId"));
            case WITHDRAW_PUBLICATION -> {}
        }
        var ids = new HashSet<UUID>();
        var value = DefinitionCanonical.tree(resources);
        value.properties()
                .forEach(
                        entry -> {
                            if (entry.getValue().isNull()) {
                                if (required.contains(entry.getKey())) throw invalid();
                            } else if (!required.contains(entry.getKey())
                                    || !ids.add(
                                            DefinitionRules.uuid(
                                                    entry.getValue().asText(),
                                                    "/native/resources"))) throw invalid();
                        });
        if (ids.size() != required.size()) throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("PRODUCT_DEFINITION_NATIVE_PLAN_INVALID");
    }
}

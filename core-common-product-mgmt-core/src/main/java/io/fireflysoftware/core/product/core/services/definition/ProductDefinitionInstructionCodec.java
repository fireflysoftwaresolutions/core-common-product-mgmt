package io.fireflysoftware.core.product.core.services.definition;

import com.fasterxml.jackson.databind.JsonNode;

import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.ProductDefinition;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleContracts.Scope;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.*;

import java.util.Map;
import java.util.UUID;

public final class ProductDefinitionInstructionCodec {
    private ProductDefinitionInstructionCodec() {}

    public static Parsed parse(
            UUID tenantId, UUID rootId, ProductDefinitionCommand command, String body) {
        try {
            if (command == null || body == null || body.length() > 131072) throw invalid();
            var raw = DefinitionCanonical.parse(body);
            Body request =
                    switch (command) {
                        case CREATE_DRAFT -> {
                            var value = DefinitionCanonical.read(raw, RawCreate.class);
                            var definition = profile(value.definition());
                            DefinitionRules.text(
                                    definition.common().name(), 255, "/definition/common/name");
                            yield new CreateDraftBody(
                                    uuid(value.operationId()),
                                    uuid(value.productId()),
                                    uuid(value.categoryId()),
                                    definition,
                                    cost(value.cost(), definition));
                        }
                        case EDIT_PUBLIC -> {
                            var value = DefinitionCanonical.read(raw, RawEditPublic.class);
                            if ((value.sourcePublicationId() == null)
                                    != (value.sourcePublicationDigest() == null)) throw invalid();
                            yield new EditPublicBody(
                                    uuid(value.operationId()),
                                    version(value.expectedPublicVersion()),
                                    uuid(value.expectedPublicHead()),
                                    profile(value.definition()),
                                    value.sourcePublicationId() == null
                                            ? null
                                            : uuid(value.sourcePublicationId()),
                                    value.sourcePublicationDigest() == null
                                            ? null
                                            : digest(value.sourcePublicationDigest()));
                        }
                        case EDIT_COST -> {
                            var value = DefinitionCanonical.read(raw, RawEditCost.class);
                            var definition = profile(value.publicDefinition());
                            String actual =
                                    DefinitionProfiles.canonical(
                                                    DefinitionCanonical.tree(definition).toString())
                                            .digest();
                            if (!actual.equals(digest(value.publicDigest()))) throw invalid();
                            yield new EditCostBody(
                                    uuid(value.operationId()),
                                    version(value.expectedCostVersion()),
                                    uuid(value.expectedCostHead()),
                                    uuid(value.publicRevisionId()),
                                    actual,
                                    definition,
                                    cost(value.cost(), definition));
                        }
                        case SUBMIT_PUBLIC -> {
                            var value = DefinitionCanonical.read(raw, RawSubmit.class);
                            yield new SubmitPublicBody(
                                    uuid(value.operationId()),
                                    version(value.expectedPublicVersion()),
                                    uuid(value.expectedPublicHead()));
                        }
                        case APPROVE_SELECTION, REJECT_SELECTION -> {
                            var value = DefinitionCanonical.read(raw, RawReview.class);
                            String reason = value.reason();
                            if (command == ProductDefinitionCommand.REJECT_SELECTION
                                    || reason != null)
                                reason = DefinitionRules.text(reason, 1000, "/reason");
                            yield new ReviewSelectionBody(
                                    uuid(value.operationId()),
                                    uuid(value.submissionId()),
                                    uuid(value.revisionId()),
                                    digest(value.publicDigest()),
                                    version(value.publicVersion()),
                                    uuid(value.costRevisionId()),
                                    digest(value.costDigest()),
                                    version(value.costVersion()),
                                    digest(value.selectionFingerprint()),
                                    reason);
                        }
                        case PUBLISH -> {
                            var value = DefinitionCanonical.read(raw, RawPublish.class);
                            if (value.versionNumber() == null || value.versionNumber() <= 0)
                                throw invalid();
                            yield new PublishBody(
                                    uuid(value.operationId()),
                                    uuid(value.reviewId()),
                                    version(value.expectedPublicVersion()),
                                    version(value.expectedCostVersion()),
                                    value.versionNumber());
                        }
                        case WITHDRAW_PUBLICATION -> {
                            var value = DefinitionCanonical.read(raw, RawWithdraw.class);
                            yield new WithdrawBody(
                                    uuid(value.operationId()),
                                    uuid(value.publicationId()),
                                    version(value.expectedStateVersion()));
                        }
                    };
            var scope =
                    new Scope(
                            uuid(tenantId == null ? null : tenantId.toString()),
                            uuid(rootId == null ? null : rootId.toString()),
                            request.operationId());
            var instruction = new Instruction(scope, command, request);
            var original =
                    DefinitionCanonical.tree(
                            Map.of(
                                    "encoding",
                                    "product-definition-lifecycle-instruction-v1",
                                    "command",
                                    command.name(),
                                    "scope",
                                    scope,
                                    "request",
                                    raw));
            String canonical = DefinitionCanonical.encode(original);
            DefinitionCanonical.parse(canonical);
            return new Parsed(instruction, canonical, DefinitionCanonical.intentDigest(original));
        } catch (RuntimeException invalid) {
            throw invalid();
        }
    }

    private static ProductDefinition profile(JsonNode value) {
        if (value == null) throw invalid();
        var definition = DefinitionProfiles.decode(value.toString());
        if (!"ACCOUNT_TARIFF_V1".equals(definition.common().profileCode())
                || !"BANKING".equals(definition.common().businessArea())) throw invalid();
        return definition;
    }

    private static CostTerms cost(JsonNode value, ProductDefinition definition) {
        if (value == null) throw invalid();
        var result =
                CostRules.canonical(
                        value.toString(), DefinitionCanonical.tree(definition).toString());
        return DefinitionCanonical.read(DefinitionCanonical.parse(result.json()), CostTerms.class);
    }

    private static UUID uuid(String value) {
        return DefinitionRules.uuid(value, "/identity");
    }

    private static Integer version(Integer value) {
        if (value == null || value < 0) throw invalid();
        return value;
    }

    private static String digest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw invalid();
        return value;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("PRODUCT_DEFINITION_COMMAND_INVALID");
    }

    private record RawCreate(
            String operationId,
            String productId,
            String categoryId,
            JsonNode definition,
            JsonNode cost) {}

    private record RawEditPublic(
            String operationId,
            Integer expectedPublicVersion,
            String expectedPublicHead,
            JsonNode definition,
            String sourcePublicationId,
            String sourcePublicationDigest) {}

    private record RawEditCost(
            String operationId,
            Integer expectedCostVersion,
            String expectedCostHead,
            String publicRevisionId,
            String publicDigest,
            JsonNode publicDefinition,
            JsonNode cost) {}

    private record RawSubmit(
            String operationId, Integer expectedPublicVersion, String expectedPublicHead) {}

    private record RawReview(
            String operationId,
            String submissionId,
            String revisionId,
            String publicDigest,
            Integer publicVersion,
            String costRevisionId,
            String costDigest,
            Integer costVersion,
            String selectionFingerprint,
            String reason) {}

    private record RawPublish(
            String operationId,
            String reviewId,
            Integer expectedPublicVersion,
            Integer expectedCostVersion,
            Long versionNumber) {}

    private record RawWithdraw(
            String operationId, String publicationId, Integer expectedStateVersion) {}
}

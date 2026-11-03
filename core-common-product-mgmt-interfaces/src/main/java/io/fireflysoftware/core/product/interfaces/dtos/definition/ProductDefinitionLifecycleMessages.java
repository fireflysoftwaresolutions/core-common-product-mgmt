package io.fireflysoftware.core.product.interfaces.dtos.definition;

import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.ProductDefinition;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleContracts.Scope;

import java.util.UUID;

public final class ProductDefinitionLifecycleMessages {
    private ProductDefinitionLifecycleMessages() {}

    public sealed interface Body
            permits CreateDraftBody,
                    EditPublicBody,
                    EditCostBody,
                    SubmitPublicBody,
                    ReviewSelectionBody,
                    PublishBody,
                    WithdrawBody {
        UUID operationId();
    }

    public record CreateDraftBody(
            UUID operationId,
            UUID productId,
            UUID categoryId,
            ProductDefinition definition,
            CostTerms cost)
            implements Body {}

    public record EditPublicBody(
            UUID operationId,
            Integer expectedPublicVersion,
            UUID expectedPublicHead,
            ProductDefinition definition,
            UUID sourcePublicationId,
            String sourcePublicationDigest)
            implements Body {}

    public record EditCostBody(
            UUID operationId,
            Integer expectedCostVersion,
            UUID expectedCostHead,
            UUID publicRevisionId,
            String publicDigest,
            ProductDefinition publicDefinition,
            CostTerms cost)
            implements Body {}

    public record SubmitPublicBody(
            UUID operationId, Integer expectedPublicVersion, UUID expectedPublicHead)
            implements Body {}

    public record ReviewSelectionBody(
            UUID operationId,
            UUID submissionId,
            UUID revisionId,
            String publicDigest,
            Integer publicVersion,
            UUID costRevisionId,
            String costDigest,
            Integer costVersion,
            String selectionFingerprint,
            String reason)
            implements Body {}

    public record PublishBody(
            UUID operationId,
            UUID reviewId,
            Integer expectedPublicVersion,
            Integer expectedCostVersion,
            Long versionNumber)
            implements Body {}

    public record WithdrawBody(UUID operationId, UUID publicationId, Integer expectedStateVersion)
            implements Body {}

    public record Instruction(Scope scope, ProductDefinitionCommand command, Body body) {
        public Instruction {
            if (scope == null
                    || command == null
                    || body == null
                    || !java.util.Objects.equals(scope.operationId(), body.operationId()))
                throw new IllegalArgumentException("PRODUCT_DEFINITION_COMMAND_SCOPE_REQUIRED");
            boolean valid =
                    switch (command) {
                        case CREATE_DRAFT -> body instanceof CreateDraftBody;
                        case EDIT_PUBLIC -> body instanceof EditPublicBody;
                        case EDIT_COST -> body instanceof EditCostBody;
                        case SUBMIT_PUBLIC -> body instanceof SubmitPublicBody;
                        case APPROVE_SELECTION, REJECT_SELECTION ->
                                body instanceof ReviewSelectionBody;
                        case PUBLISH -> body instanceof PublishBody;
                        case WITHDRAW_PUBLICATION -> body instanceof WithdrawBody;
                    };
            if (!valid)
                throw new IllegalArgumentException("PRODUCT_DEFINITION_COMMAND_BODY_REQUIRED");
        }

        public UUID targetId() {
            return switch (body) {
                case ReviewSelectionBody value -> value.submissionId();
                case PublishBody value -> value.reviewId();
                case WithdrawBody value -> value.publicationId();
                default -> null;
            };
        }
    }

    public record Parsed(Instruction instruction, String canonicalIntent, String intentDigest) {}

    public sealed interface Receipt permits PublicReceipt, PrivateReceipt {
        Scope scope();

        ProductDefinitionCommand command();

        UUID actionId();

        String recordedAt();
    }

    public record PublicReceipt(
            Scope scope,
            ProductDefinitionCommand command,
            UUID actionId,
            String recordedAt,
            String phase,
            Integer publicVersion,
            UUID revisionId,
            UUID publicationId,
            UUID productVersionId,
            Long versionNumber,
            String publicationState,
            Integer publicationStateVersion,
            String operationalAdmission)
            implements Receipt {}

    public record PrivateReceipt(
            Scope scope,
            ProductDefinitionCommand command,
            UUID actionId,
            String recordedAt,
            UUID costRevisionId,
            Integer costVersion,
            UUID reviewId,
            String decision)
            implements Receipt {}
}

package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCommand;

import java.util.UUID;

record ProductDefinitionCommandResources(
        UUID actionId,
        UUID revisionId,
        UUID costRevisionId,
        UUID submissionId,
        UUID reviewId,
        UUID publicationId,
        UUID productVersionId,
        UUID historyId,
        UUID auditId,
        UUID publicEventId,
        UUID costEventId) {
    static ProductDefinitionCommandResources fresh(ProductDefinitionCommand command) {
        boolean create = command == ProductDefinitionCommand.CREATE_DRAFT;
        boolean publicEdit = command == ProductDefinitionCommand.EDIT_PUBLIC;
        boolean costEdit = command == ProductDefinitionCommand.EDIT_COST;
        boolean submit = command == ProductDefinitionCommand.SUBMIT_PUBLIC;
        boolean review =
                command == ProductDefinitionCommand.APPROVE_SELECTION
                        || command == ProductDefinitionCommand.REJECT_SELECTION;
        boolean publish = command == ProductDefinitionCommand.PUBLISH;
        boolean publicAction = !(costEdit || review);
        return new ProductDefinitionCommandResources(
                UUID.randomUUID(),
                create || publicEdit ? UUID.randomUUID() : null,
                create || costEdit ? UUID.randomUUID() : null,
                submit ? UUID.randomUUID() : null,
                review ? UUID.randomUUID() : null,
                publish ? UUID.randomUUID() : null,
                publish ? UUID.randomUUID() : null,
                publicAction ? UUID.randomUUID() : null,
                UUID.randomUUID(),
                publicAction ? UUID.randomUUID() : null,
                create || costEdit || review ? UUID.randomUUID() : null);
    }
}

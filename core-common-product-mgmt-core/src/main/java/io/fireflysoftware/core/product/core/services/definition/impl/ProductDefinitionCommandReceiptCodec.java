package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.core.services.definition.DefinitionCanonical;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.*;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

final class ProductDefinitionCommandReceiptCodec {
    private static final String ENCODING = "product-definition-lifecycle-receipt-v1";

    private ProductDefinitionCommandReceiptCodec() {}

    static boolean privateCommand(ProductDefinitionCommand command) {
        return command == ProductDefinitionCommand.EDIT_COST
                || command == ProductDefinitionCommand.APPROVE_SELECTION
                || command == ProductDefinitionCommand.REJECT_SELECTION;
    }

    static String encode(Receipt receipt) {
        validate(receipt);
        return DefinitionCanonical.encode(
                DefinitionCanonical.tree(
                        Map.of(
                                "encoding",
                                ENCODING,
                                "visibility",
                                privateCommand(receipt.command()) ? "PRIVATE" : "PUBLIC",
                                "receipt",
                                receipt)));
    }

    static Receipt decode(String raw, ProductDefinitionCommand command) {
        var value = DefinitionCanonical.parse(raw);
        DefinitionCanonical.closed(value, Set.of("encoding", "visibility", "receipt"), "/receipt");
        if (!ENCODING.equals(value.path("encoding").asText())) throw invalid();
        Receipt receipt =
                privateCommand(command)
                        ? DefinitionCanonical.read(value.get("receipt"), PrivateReceipt.class)
                        : DefinitionCanonical.read(value.get("receipt"), PublicReceipt.class);
        if (receipt.command() != command || !encode(receipt).equals(raw)) throw invalid();
        return receipt;
    }

    private static void validate(Receipt value) {
        if (value == null
                || value.scope() == null
                || value.command() == null
                || !id(value.actionId())
                || !id(value.scope().tenantId())
                || !id(value.scope().rootId())
                || !id(value.scope().operationId())
                || value.recordedAt() == null
                || !Instant.parse(value.recordedAt()).toString().equals(value.recordedAt()))
            throw invalid();
        if (value instanceof PublicReceipt receipt) {
            if (privateCommand(receipt.command())
                    || !"NOT_ADMITTED".equals(receipt.operationalAdmission())
                    || !id(receipt.revisionId())
                    || receipt.publicVersion() == null
                    || receipt.publicVersion() < 0
                    || !Set.of("DRAFT", "IN_REVIEW", "PUBLISHED").contains(receipt.phase()))
                throw invalid();
            boolean publication =
                    receipt.command() == ProductDefinitionCommand.PUBLISH
                            || receipt.command() == ProductDefinitionCommand.WITHDRAW_PUBLICATION;
            if (publication) {
                if (!id(receipt.publicationId())
                        || !id(receipt.productVersionId())
                        || receipt.versionNumber() == null
                        || receipt.versionNumber() <= 0
                        || receipt.publicationStateVersion() == null
                        || receipt.publicationStateVersion() < 0) throw invalid();
                if (receipt.command() == ProductDefinitionCommand.PUBLISH) {
                    if (!"PUBLISHED".equals(receipt.phase())
                            || receipt.publicVersion() == 0
                            || !"ACTIVE".equals(receipt.publicationState())
                            || receipt.publicationStateVersion() != 0) throw invalid();
                } else if (!"WITHDRAWN".equals(receipt.publicationState())
                        || receipt.publicationStateVersion() == 0) throw invalid();
            } else {
                if (receipt.publicationId() != null
                        || receipt.productVersionId() != null
                        || receipt.versionNumber() != null
                        || receipt.publicationState() != null
                        || receipt.publicationStateVersion() != null) throw invalid();
                switch (receipt.command()) {
                    case CREATE_DRAFT -> {
                        if (!"DRAFT".equals(receipt.phase()) || receipt.publicVersion() != 0)
                            throw invalid();
                    }
                    case EDIT_PUBLIC -> {
                        if (!"DRAFT".equals(receipt.phase()) || receipt.publicVersion() == 0)
                            throw invalid();
                    }
                    case SUBMIT_PUBLIC -> {
                        if (!"IN_REVIEW".equals(receipt.phase()) || receipt.publicVersion() == 0)
                            throw invalid();
                    }
                    default -> throw invalid();
                }
            }
        } else if (value instanceof PrivateReceipt receipt) {
            if (!privateCommand(receipt.command())
                    || !id(receipt.costRevisionId())
                    || receipt.costVersion() == null
                    || receipt.costVersion() < 0) throw invalid();
            switch (receipt.command()) {
                case EDIT_COST -> {
                    if (receipt.costVersion() == 0
                            || receipt.reviewId() != null
                            || !"EDITED".equals(receipt.decision())) throw invalid();
                }
                case APPROVE_SELECTION -> {
                    if (!id(receipt.reviewId()) || !"APPROVED".equals(receipt.decision()))
                        throw invalid();
                }
                case REJECT_SELECTION -> {
                    if (!id(receipt.reviewId()) || !"REJECTED".equals(receipt.decision()))
                        throw invalid();
                }
                default -> throw invalid();
            }
        }
    }

    private static boolean id(java.util.UUID value) {
        return value != null && !value.equals(new java.util.UUID(0, 0));
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("PRODUCT_DEFINITION_RECEIPT_INVALID");
    }
}

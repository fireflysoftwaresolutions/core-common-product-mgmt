package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandPersistence;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.util.*;

final class ProductDefinitionPrivateReadSnapshots {
    private ProductDefinitionPrivateReadSnapshots() {}

    record Head(
            ProductDefinitionDraftRoot root,
            ProductDefinitionDraftRevision revision,
            DefinitionContracts.ProductDefinition definition) {}

    record Cost(
            ProductDefinitionCostControl control,
            ProductDefinitionCostRevision revision,
            CostTerms terms,
            String compatibility) {}

    static Mono<Head> head(
            ProductDefinitionCommandPersistence persistence, UUID tenant, UUID root) {
        return persistence
                .drafts()
                .findByTenantIdAndRootId(tenant, root)
                .switchIfEmpty(Mono.error(notFound()))
                .flatMap(
                        header -> {
                            require(
                                    header.tenantId().equals(tenant)
                                            && header.rootId().equals(root)
                                            && "BANKING".equals(header.businessArea())
                                            && "ACCOUNT_TARIFF_V1".equals(header.profileCode())
                                            && Integer.valueOf(1).equals(header.profileVersion())
                                            && header.publicHead() != null
                                            && header.publicStateVersion() != null
                                            && header.publicStateVersion() >= 0
                                            && Set.of("DRAFT", "IN_REVIEW", "PUBLISHED")
                                                    .contains(header.phase()));
                            return persistence
                                    .lifecycleRevisionRepository()
                                    .findByTenantIdAndRootIdAndRevisionId(
                                            tenant, root, header.publicHead())
                                    .switchIfEmpty(Mono.error(unavailable()))
                                    .map(
                                            revision -> {
                                                require(
                                                        revision.tenantId().equals(tenant)
                                                                && revision.rootId().equals(root)
                                                                && revision.revisionId()
                                                                        .equals(header.publicHead())
                                                                && DefinitionCanonical.ENCODING
                                                                        .equals(
                                                                                revision
                                                                                        .encodingVersion()));
                                                var canonical =
                                                        DefinitionProfiles.canonical(
                                                                revision.canonicalDefinition());
                                                var definition =
                                                        DefinitionProfiles.decode(canonical.json());
                                                require(
                                                        canonical
                                                                        .json()
                                                                        .equals(
                                                                                revision
                                                                                        .canonicalDefinition())
                                                                && canonical
                                                                        .digest()
                                                                        .equals(
                                                                                revision
                                                                                        .publicDigest())
                                                                && definition
                                                                        .common()
                                                                        .code()
                                                                        .equals(
                                                                                header
                                                                                        .productCode())
                                                                && "BANKING"
                                                                        .equals(
                                                                                definition
                                                                                        .common()
                                                                                        .businessArea())
                                                                && "ACCOUNT_TARIFF_V1"
                                                                        .equals(
                                                                                definition
                                                                                        .common()
                                                                                        .profileCode()));
                                                return new Head(header, revision, definition);
                                            });
                        });
    }

    static Mono<Cost> cost(ProductDefinitionCommandPersistence persistence, Head head) {
        var root = head.root();
        return persistence
                .costs()
                .findByTenantIdAndRootId(root.tenantId(), root.rootId())
                .switchIfEmpty(Mono.error(unavailable()))
                .flatMap(
                        control -> {
                            require(
                                    control.tenantId().equals(root.tenantId())
                                            && control.rootId().equals(root.rootId())
                                            && control.costHead() != null
                                            && control.costStateVersion() != null
                                            && control.costStateVersion() >= 0);
                            return persistence
                                    .lifecycleCostRevisionRepository()
                                    .findByTenantIdAndRootIdAndCostRevisionId(
                                            root.tenantId(), root.rootId(), control.costHead())
                                    .switchIfEmpty(Mono.error(unavailable()))
                                    .map(
                                            revision -> {
                                                require(
                                                        revision.tenantId().equals(root.tenantId())
                                                                && revision.rootId()
                                                                        .equals(root.rootId())
                                                                && revision.costRevisionId()
                                                                        .equals(control.costHead())
                                                                && DefinitionCanonical.ENCODING
                                                                        .equals(
                                                                                revision
                                                                                        .encodingVersion()));
                                                var terms =
                                                        DefinitionCanonical.read(
                                                                DefinitionCanonical.parse(
                                                                        revision.canonicalCost()),
                                                                CostTerms.class);
                                                require(
                                                        DefinitionCanonical.encode(
                                                                                DefinitionCanonical
                                                                                        .tree(
                                                                                                terms))
                                                                        .equals(
                                                                                revision
                                                                                        .canonicalCost())
                                                                && DefinitionCanonical.hash(
                                                                                "product-definition-private-cost-v1",
                                                                                revision
                                                                                        .canonicalCost())
                                                                        .equals(
                                                                                revision
                                                                                        .costDigest()));
                                                String compatibility;
                                                try {
                                                    var checked =
                                                            CostRules.canonical(
                                                                    revision.canonicalCost(),
                                                                    head.revision()
                                                                            .canonicalDefinition());
                                                    require(
                                                            checked.json()
                                                                            .equals(
                                                                                    revision
                                                                                            .canonicalCost())
                                                                    && checked.digest()
                                                                            .equals(
                                                                                    revision
                                                                                            .costDigest()));
                                                    compatibility = "COMPATIBLE";
                                                } catch (IllegalArgumentException incompatible) {
                                                    compatibility = "INCOMPATIBLE";
                                                }
                                                return new Cost(
                                                        control, revision, terms, compatibility);
                                            });
                        });
    }

    private static void require(boolean value) {
        if (!value) throw unavailable();
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Product private snapshot unavailable");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Product definition not found");
    }
}

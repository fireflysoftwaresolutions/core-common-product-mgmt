package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.ArrayList;
import java.util.List;

class ProductDefinitionReadMappingTest {
    @Test
    void nativeReadProjectionsMapOnlyPublicColumnsWithoutInheritedAuditOrCostFields()
            throws Exception {
        check(
                "ProductDefinitionVersionIdentity",
                "product_version",
                List.of("product_version_id", "product_id"));
        check(
                "ProductDefinitionRoot",
                "product_definition_root",
                List.of(
                        "root_id",
                        "tenant_id",
                        "product_id",
                        "product_code",
                        "business_area",
                        "profile_code",
                        "profile_version",
                        "phase",
                        "public_state_version",
                        "public_head",
                        "created_at"));
        check(
                "ProductDefinitionRevision",
                "product_definition_revision",
                List.of(
                        "revision_id",
                        "tenant_id",
                        "root_id",
                        "canonical_definition",
                        "public_digest",
                        "encoding_version",
                        "source_publication_id",
                        "source_publication_digest",
                        "created_at"));
        check(
                "ProductDefinitionPublication",
                "product_definition_publication",
                List.of(
                        "publication_id",
                        "tenant_id",
                        "root_id",
                        "review_id",
                        "revision_id",
                        "product_version_id",
                        "canonical_definition",
                        "public_digest",
                        "encoding_version",
                        "operational_admission",
                        "created_at"));
        check(
                "ProductDefinitionPublicationControl",
                "product_definition_publication_control",
                List.of("publication_id", "tenant_id", "root_id", "state", "state_version"));
    }

    private void check(String name, String table, List<String> expected) throws Exception {
        var type =
                Class.forName("io.fireflysoftware.core.product.models.entities.definition." + name);
        var mapping = new R2dbcMappingContext();
        var entity = mapping.getRequiredPersistentEntity(type);
        assertThat(entity.getTableName().getReference()).isEqualTo(table);
        assertThat(
                        entity.getQualifiedTableName()
                                .toSql(
                                        org.springframework.data.relational.core.sql
                                                .IdentifierProcessing.NONE))
                .isEqualTo("public." + table);
        var converter = new org.springframework.data.r2dbc.convert.MappingR2dbcConverter(mapping);
        var strategy =
                new org.springframework.data.r2dbc.core.DefaultReactiveDataAccessStrategy(
                        org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE, converter);
        var mapper = strategy.getStatementMapper();
        var select =
                mapper.createSelect(strategy.getTableName(type))
                        .withProjection(entity.getIdColumn());
        assertThat(mapper.getMappedObject(select).toQuery().replace("\"", ""))
                .contains("FROM public." + table);
        var columns = new ArrayList<String>();
        entity.forEach(property -> columns.add(property.getColumnName().getReference()));
        assertThat(columns).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(entity.getRequiredIdProperty().getColumnName().getReference())
                .isEqualTo(expected.getFirst());
        var repository =
                Class.forName(
                        "io.fireflysoftware.core.product.models.repositories.definition."
                                + name
                                + "Repository");
        assertThat(Repository.class.isAssignableFrom(repository)).isTrue();
        assertThat(ReactiveCrudRepository.class.isAssignableFrom(repository)).isFalse();
        assertThat(List.of(repository.getMethods()))
                .noneMatch(
                        method ->
                                method.getName().startsWith("save")
                                        || method.getName().startsWith("delete"));
    }
}

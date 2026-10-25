package io.fireflysoftware.core.product.models.repositories.definition.lifecycle.impl;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.List;

class ProductDefinitionDraftMappingTest {
    private Class<?> required(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Missing mapped lifecycle contract: " + name, missing);
        }
    }

    @Test
    void draftRowsUseExactNativeTablesAndAssignedIdentityWithoutInheritedAuditFields() {
        var mapping = new R2dbcMappingContext();
        for (var entry :
                List.of(
                        new String[] {
                            "ProductDefinitionDraftRoot", "product_definition_root", "root_id"
                        },
                        new String[] {
                            "ProductDefinitionDraftRevision",
                            "product_definition_revision",
                            "revision_id"
                        },
                        new String[] {
                            "ProductDefinitionCostRevision",
                            "product_definition_cost_revision",
                            "cost_revision_id"
                        },
                        new String[] {
                            "ProductDefinitionCostControl", "product_definition_control", "root_id"
                        })) {
            var type =
                    required(
                            "io.fireflysoftware.core.product.models.entities.definition.lifecycle."
                                    + entry[0]);
            var entity = mapping.getRequiredPersistentEntity(type);
            assertThat(
                            entity.getQualifiedTableName()
                                    .toSql(
                                            org.springframework.data.relational.core.sql
                                                    .IdentifierProcessing.NONE))
                    .isEqualTo("public." + entry[1]);
            assertThat(entity.getRequiredIdProperty().getColumnName().getReference())
                    .isEqualTo(entry[2]);
            assertThat(type.isRecord()).isTrue();
        }
    }

    @Test
    void commandRepositoriesCannotBeDefaultScannedOrExposeGenericSaveDelete() {
        for (var name :
                List.of(
                        "ProductDefinitionDraftRepository",
                        "ProductDefinitionCostControlRepository")) {
            var type =
                    required(
                            "io.fireflysoftware.core.product.models.repositories.definition.lifecycle."
                                    + name);
            assertThat(Repository.class.isAssignableFrom(type)).isTrue();
            assertThat(type.isAnnotationPresent(NoRepositoryBean.class)).isTrue();
            assertThat(List.of(type.getMethods()))
                    .noneMatch(
                            m ->
                                    m.getName().startsWith("save")
                                            || m.getName().startsWith("delete"));
        }
    }

    @Test
    void publicAndPrivateCasUseRealMapperWithTenantOldHeadVersionAndPublicPhasePredicates()
            throws Exception {
        var implementation =
                required(
                        "io.fireflysoftware.core.product.models.repositories.definition.lifecycle.impl.ProductDefinitionDraftWritesImpl");
        var criteria =
                implementation.getDeclaredMethod(
                        "publicHeadCriteria",
                        java.util.UUID.class,
                        java.util.UUID.class,
                        java.util.UUID.class,
                        int.class,
                        String.class);
        var tenant = java.util.UUID.randomUUID();
        var root = java.util.UUID.randomUUID();
        var head = java.util.UUID.randomUUID();
        var where =
                (org.springframework.data.relational.core.query.Criteria)
                        criteria.invoke(null, tenant, root, head, 3, "DRAFT");
        var type =
                required(
                        "io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionDraftRoot");
        var context = new R2dbcMappingContext();
        var conversions =
                org.springframework.data.r2dbc.convert.R2dbcCustomConversions.of(
                        org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE, List.of());
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        var converter =
                new org.springframework.data.r2dbc.convert.MappingR2dbcConverter(
                        context, conversions);
        var strategy =
                new org.springframework.data.r2dbc.core.DefaultReactiveDataAccessStrategy(
                        org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE, converter);
        var mapper = strategy.getStatementMapper().forType(type);
        var prepared =
                mapper.getMappedObject(
                        mapper.createUpdate(
                                        strategy.getTableName(type),
                                        org.springframework.data.relational.core.query.Update
                                                .update("publicStateVersion", 4))
                                .withCriteria(where));
        var bound = new java.util.ArrayList<Object>();
        prepared.bindTo(
                new org.springframework.r2dbc.core.binding.BindTarget() {
                    public void bind(String name, Object value) {
                        bound.add(value);
                    }

                    public void bind(int index, Object value) {
                        bound.add(value);
                    }

                    public void bindNull(String name, Class<?> type) {
                        throw new AssertionError("Unexpected null CAS binding");
                    }

                    public void bindNull(int index, Class<?> type) {
                        throw new AssertionError("Unexpected null CAS binding");
                    }
                });
        assertThat(bound).contains(tenant, root, head, 3, "DRAFT", 4);
        var sql = prepared.toQuery().replace("\"", "");
        assertThat(sql)
                .contains(
                        "UPDATE public.product_definition_root",
                        "tenant_id =",
                        "root_id =",
                        "public_head =",
                        "public_state_version =",
                        "phase =");
        var privateCriteria =
                implementation.getDeclaredMethod(
                        "costHeadCriteria",
                        java.util.UUID.class,
                        java.util.UUID.class,
                        java.util.UUID.class,
                        int.class);
        var privateWhere =
                (org.springframework.data.relational.core.query.Criteria)
                        privateCriteria.invoke(null, tenant, root, head, 2);
        var costType =
                required(
                        "io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionCostControl");
        var costMapper = strategy.getStatementMapper().forType(costType);
        var privateSql =
                costMapper
                        .getMappedObject(
                                costMapper
                                        .createUpdate(
                                                strategy.getTableName(costType),
                                                org.springframework.data.relational.core.query
                                                        .Update.update("costStateVersion", 3))
                                        .withCriteria(privateWhere))
                        .toQuery()
                        .replace("\"", "");
        assertThat(privateSql)
                .contains(
                        "UPDATE public.product_definition_control",
                        "tenant_id =",
                        "root_id =",
                        "cost_head =",
                        "cost_state_version =")
                .doesNotContain("public_head", "public_state_version", "phase");
    }
}

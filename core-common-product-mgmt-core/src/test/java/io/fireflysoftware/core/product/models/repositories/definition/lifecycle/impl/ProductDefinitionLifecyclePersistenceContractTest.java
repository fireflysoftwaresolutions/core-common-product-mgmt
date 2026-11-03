package io.fireflysoftware.core.product.models.repositories.definition.lifecycle.impl;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

class ProductDefinitionLifecyclePersistenceContractTest {
    private Class<?> required(String value) {
        try {
            return Class.forName(value);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Missing lifecycle persistence contract: " + value, missing);
        }
    }

    @Test
    void lifecycleWritesHaveExactSchemaQualifiedNativeAndLegacyMappings() {
        var mapping = new R2dbcMappingContext();
        for (var pair :
                List.of(
                        new String[] {"Action", "product_definition_action"},
                        new String[] {"ActionPrivate", "product_definition_action_private"},
                        new String[] {"Submission", "product_definition_submission"},
                        new String[] {"Review", "product_definition_review"},
                        new String[] {"ReviewPrivate", "product_definition_review_private"},
                        new String[] {"Decision", "product_definition_review_decision"},
                        new String[] {"History", "product_definition_history"},
                        new String[] {"Audit", "product_definition_audit"},
                        new String[] {"Outbox", "product_definition_outbox"},
                        new String[] {"CostOutbox", "product_definition_cost_outbox"},
                        new String[] {"ProductBirth", "product"},
                        new String[] {"Version", "product_version"},
                        new String[] {"VersionIdentity", "product_definition_version_identity"})) {
            var type =
                    required(
                            "io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionLifecycleRows$"
                                    + pair[0]);
            var entity = mapping.getRequiredPersistentEntity(type);
            assertThat(
                            entity.getQualifiedTableName()
                                    .toSql(
                                            org.springframework.data.relational.core.sql
                                                    .IdentifierProcessing.NONE))
                    .isEqualTo("public." + pair[1]);
            assertThat(entity.getRequiredIdProperty()).isNotNull();
            assertThat(type.isRecord()).isTrue();
        }
    }

    @Test
    void nativeAdmissionContractIsNarrowTypedAndCannotDefaultScan() {
        var type =
                required(
                        "io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandNativeRepository");
        assertThat(type.isAnnotationPresent(NoRepositoryBean.class)).isTrue();
        for (var method : List.of(type.getDeclaredMethods())) {
            var query = method.getAnnotation(Query.class);
            assertThat(query).isNotNull();
            assertThat(query.value())
                    .startsWith("SELECT ")
                    .doesNotContain("UPDATE ", "INSERT ", "DELETE ");
        }
    }

    @Test
    void immutableAndContributorReadsExposeNoGenericDmlOrUnboundedActorStream() {
        for (var name :
                List.of(
                        "LifecycleActionRepository",
                        "LifecycleActionPrivateRepository",
                        "LifecycleSubmissionRepository",
                        "LifecycleReviewRepository",
                        "LifecycleReviewPrivateRepository",
                        "LifecycleDecisionRepository",
                        "LifecycleRevisionRepository",
                        "LifecycleCostRevisionRepository",
                        "LifecyclePublicationRepository",
                        "LifecyclePublicationControlRepository",
                        "LifecycleVersionIdentityRepository")) {
            var type =
                    required(
                            "io.fireflysoftware.core.product.models.repositories.definition.lifecycle."
                                    + name);
            assertThat(type.isAnnotationPresent(NoRepositoryBean.class)).isTrue();
            assertThat(List.of(type.getMethods()))
                    .noneMatch(
                            m ->
                                    m.getName().startsWith("save")
                                            || m.getName().startsWith("delete"));
            if (name.equals("LifecycleRevisionRepository")
                    || name.equals("LifecycleCostRevisionRepository")
                    || name.equals("LifecycleSubmissionRepository"))
                assertThat(List.of(type.getMethods()))
                        .anyMatch(m -> m.getName().equals("findDistinctTop513ByTenantIdAndRootId"));
        }
    }

    @Test
    void nativePlanRemainsBoundTextUntilTheOwnerFunctionCanCheckItsByteLimit() {
        var type =
                required(
                        "io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandNativeRepository");
        var begin =
                java.util.Arrays.stream(type.getDeclaredMethods())
                        .filter(method -> method.getName().equals("begin"))
                        .findFirst()
                        .orElseThrow();
        assertThat(begin.getAnnotation(Query.class).value())
                .doesNotContain("CAST(:resourcePlan AS JSONB)")
                .contains(":resourcePlan)");
    }
}

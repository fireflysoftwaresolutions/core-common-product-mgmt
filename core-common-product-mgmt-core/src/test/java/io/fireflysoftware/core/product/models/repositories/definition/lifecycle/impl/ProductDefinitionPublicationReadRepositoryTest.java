package io.fireflysoftware.core.product.models.repositories.definition.lifecycle.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionPublicationReadRepositories;
import io.r2dbc.spi.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

class ProductDefinitionPublicationReadRepositoryTest {
    final UUID tenant = UUID.randomUUID(), root = UUID.randomUUID();
    final List<String> sql = new ArrayList<>();
    final List<Object> bound = new ArrayList<>();

    R2dbcRepositoryFactory factory() {
        var connection = mock(Connection.class);
        when(connection.close()).thenReturn(Mono.empty());
        when(connection.createStatement(anyString()))
                .thenAnswer(
                        call -> {
                            sql.add(call.getArgument(0));
                            var statement = mock(Statement.class);
                            when(statement.bind(anyInt(), any()))
                                    .thenAnswer(
                                            binding -> {
                                                Object value = binding.getArgument(1);
                                                bound.add(
                                                        value
                                                                        instanceof
                                                                        io.r2dbc.spi.Parameter
                                                                                parameter
                                                                ? parameter.getValue()
                                                                : value);
                                                return statement;
                                            });
                            when(statement.execute()).thenReturn(Flux.empty());
                            return statement;
                        });
        var source =
                new ConnectionFactory() {
                    public org.reactivestreams.Publisher<? extends Connection> create() {
                        return Mono.just(connection);
                    }

                    public ConnectionFactoryMetadata getMetadata() {
                        return () -> "PostgreSQL";
                    }
                };
        return new R2dbcRepositoryFactory(new R2dbcEntityTemplate(source));
    }

    String normalized() {
        assertThat(sql).hasSize(1);
        return sql.getFirst().replace("\"", "").toLowerCase(Locale.ROOT);
    }

    @Test
    void genuineHistoryProxyUsesSchemaTenantRootCommandAndDatabaseLimitTwo() {
        var repositories = new ProductDefinitionPublicationReadRepositories(factory());
        repositories
                .publishHistory()
                .findTop2ByTenantIdAndRootIdAndEventTypeOrderByPublicStateVersionDescHistoryIdDesc(
                        tenant, root, "PUBLISH")
                .collectList()
                .block();
        assertThat(normalized())
                .contains(
                        "public.product_definition_history",
                        "tenant_id",
                        "root_id",
                        "event_type",
                        "public_state_version desc",
                        "history_id desc",
                        "limit 2");
        assertThat(bound).containsExactly(tenant, root, "PUBLISH");
    }

    @Test
    void genuineActiveControlAndExistenceQueriesRemainBoundedAndScoped() {
        var factory = factory();
        var controls = factory.getRepository(LifecyclePublicationControlRepository.class);
        controls.findTop2ByTenantIdAndRootIdAndState(tenant, root, "ACTIVE").collectList().block();
        assertThat(normalized())
                .contains(
                        "public.product_definition_publication_control",
                        "tenant_id",
                        "root_id",
                        "state",
                        "limit 2");
        assertThat(bound).containsExactly(tenant, root, "ACTIVE");
        sql.clear();
        bound.clear();
        assertThat(
                        new ProductDefinitionPublicationReadRepositories(factory)
                                .publicationMetadata()
                                .existsByTenantIdAndRootId(tenant, root)
                                .block())
                .isFalse();
        assertThat(normalized())
                .contains(
                        "public.product_definition_publication", "tenant_id", "root_id", "limit 1");
        assertThat(bound).containsExactly(tenant, root);
    }

    @Test
    void approvalProjectionSqlOmitsPrivateCostPayloadReasonActorAndFingerprint() {
        var repositories = new ProductDefinitionPublicationReadRepositories(factory());
        UUID review = UUID.randomUUID();
        repositories
                .decisionMetadata()
                .findByTenantIdAndRootIdAndReviewId(tenant, root, review)
                .block();
        assertThat(normalized())
                .contains(
                        "public.product_definition_review_decision",
                        "public_version",
                        "cost_version",
                        "review_id")
                .doesNotContain(
                        "reason",
                        "actor_id",
                        "actor_issuer",
                        "selection_fingerprint",
                        "canonical_cost");
        assertThat(bound).containsExactly(tenant, root, review);
        for (var type : ProductDefinitionPublicationReadRepositories.class.getDeclaredClasses()) {
            if (!type.isInterface()) continue;
            assertThat(Arrays.stream(type.getMethods()).map(java.lang.reflect.Method::getName))
                    .noneMatch(name -> name.startsWith("save") || name.startsWith("delete"));
        }
    }
}

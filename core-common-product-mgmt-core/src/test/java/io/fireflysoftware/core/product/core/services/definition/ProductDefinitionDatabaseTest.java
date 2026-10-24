/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.core.product.core.services.definition;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Testcontainers
class ProductDefinitionDatabaseTest {
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String RUNTIME_PASSWORD = UUID.randomUUID().toString();

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("synthetic_product_definitions")
                    .withUsername("definition_bootstrap")
                    .withPassword(PASSWORD)
                    .withCreateContainerCmdModifier(
                            c ->
                                    c.getHostConfig()
                                            .withPortBindings(
                                                    new com.github.dockerjava.api.model.PortBinding(
                                                            com.github.dockerjava.api.model.Ports
                                                                    .Binding.bindIp("127.0.0.1"),
                                                            new com.github.dockerjava.api.model
                                                                    .ExposedPort(5432))));

    static final List<String> NATIVE_TABLES =
            List.of(
                    "product_definition_root",
                    "product_definition_revision",
                    "product_definition_cost_revision",
                    "product_definition_review",
                    "product_definition_review_private",
                    "product_definition_action",
                    "product_definition_action_private",
                    "product_definition_publication",
                    "product_definition_publication_control",
                    "product_definition_control",
                    "product_definition_history",
                    "product_definition_audit",
                    "product_definition_outbox",
                    "product_definition_cost_outbox");

    static Connection owner() throws Exception {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @BeforeAll
    static void migrate() throws Exception {
        try (var c = owner();
                var s = c.createStatement()) {
            s.execute(
                    "CREATE ROLE firefly_product_definition_guard NOLOGIN NOSUPERUSER NOBYPASSRLS;"
                            + " CREATE ROLE firefly_product_definition_serving NOLOGIN NOSUPERUSER NOBYPASSRLS;"
                            + " CREATE ROLE definition_runtime LOGIN PASSWORD '"
                            + RUNTIME_PASSWORD
                            + "' NOSUPERUSER NOBYPASSRLS");
        }
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        try (var c = owner();
                var s = c.createStatement()) {
            s.execute(
                    "REVOKE CREATE ON SCHEMA public FROM PUBLIC; GRANT USAGE ON SCHEMA public TO"
                        + " definition_runtime; GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN"
                        + " SCHEMA public TO definition_runtime");
        }
    }

    @Test
    void nativeAggregateHasRealDurableTablesAndDefaultDenyRls() throws Exception {
        var observed = new TreeSet<String>();
        try (var c = owner();
                var s = c.createStatement();
                var rs =
                        s.executeQuery(
                                "SELECT c.relname FROM pg_catalog.pg_class c JOIN"
                                    + " pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE"
                                    + " n.nspname='public' AND c.relkind='r' AND c.relrowsecurity"
                                    + " AND c.relforcerowsecurity")) {
            while (rs.next()) observed.add(rs.getString(1));
        }
        assertThat(observed).containsAll(NATIVE_TABLES);
    }

    @Test
    void confidentialCostBytesAreNotStoredInPublicDefinitionRelations() throws Exception {
        try (var c = owner();
                var s = c.createStatement();
                var rs =
                        s.executeQuery(
                                "SELECT count(*) FROM information_schema.columns WHERE"
                                        + " table_schema='public' AND"
                                        + " table_name='product_definition_cost_revision' AND"
                                        + " column_name='canonical_cost'")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
        try (var c = owner();
                var s = c.createStatement();
                var rs =
                        s.executeQuery(
                                "SELECT table_name||'.'||column_name FROM"
                                    + " information_schema.columns WHERE table_schema='public' AND"
                                    + " table_name"
                                    + " IN('product_definition_root','product_definition_revision',"
                                    + "'product_definition_review','product_definition_publication',"
                                    + "'product_definition_publication_control','product_definition_history','product_definition_audit','product_definition_outbox')"
                                    + " AND column_name"
                                    + " IN('canonical_cost','cost_digest','combined_digest',"
                                    + "'cost_head','cost_state_version','selection_fingerprint')")) {
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    void nativeScopeRuntimeCannotOwnBypassOrCreateSchemaObjects() throws Exception {
        try (var c =
                        DriverManager.getConnection(
                                postgres.getJdbcUrl(), "definition_runtime", RUNTIME_PASSWORD);
                var s = c.createStatement();
                var rs =
                        s.executeQuery(
                                "SELECT NOT rolsuper AND NOT rolbypassrls AND NOT"
                                    + " pg_catalog.has_schema_privilege(current_user,'public','CREATE')"
                                    + " FROM pg_catalog.pg_roles WHERE rolname=current_user")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1)).isTrue();
        }
    }

    @Test
    void freshProductAndBothSectionHeadsCanBeCreatedInOneTransaction() throws Exception {
        try (var c = owner()) {
            c.setAutoCommit(false);
            try {
                var seed = definition(c, product(c, true));
                assertThat(count(c, "product_definition_root", seed.root())).isEqualTo(1);
                assertThat(count(c, "product_definition_revision", seed.root())).isEqualTo(1);
                assertThat(count(c, "product_definition_cost_revision", seed.root())).isEqualTo(1);
                c.commit();
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void unscopedRuntimeCannotReadRealPublicOrPrivateRows() throws Exception {
        Seed seed;
        try (var c = owner()) {
            c.setAutoCommit(false);
            seed = definition(c, product(c, true));
            c.commit();
        }
        try (var c =
                DriverManager.getConnection(
                        postgres.getJdbcUrl(), "definition_runtime", RUNTIME_PASSWORD)) {
            assertThat(count(c, "product_definition_root", seed.root())).isZero();
            assertThat(count(c, "product_definition_revision", seed.root())).isZero();
            assertThat(count(c, "product_definition_cost_revision", seed.root())).isZero();
            assertThat(count(c, "product_definition_control", seed.root())).isZero();
        }
    }

    @Test
    void committedLegacyProductCannotBeAdoptedAsANewGovernedProduct() throws Exception {
        Product legacy;
        try (var c = owner()) {
            legacy = product(c);
        }
        try (var c = owner()) {
            c.setAutoCommit(false);
            try {
                assertThatThrownBy(() -> definition(c, legacy))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void noOpUpdateDoesNotTurnACommittedLegacyProductIntoANewInsert() throws Exception {
        Product legacy;
        try (var c = owner()) {
            legacy = product(c);
        }
        try (var c = owner()) {
            c.setAutoCommit(false);
            try {
                execute(
                        c,
                        "UPDATE public.product SET product_name=product_name WHERE product_id=?",
                        legacy.id());
                assertThatThrownBy(() -> definition(c, legacy))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void retainedPublicRevisionCannotBeRewritten() throws Exception {
        try (var c = owner()) {
            c.setAutoCommit(false);
            try {
                var seed = definition(c, product(c, true));
                var replacement =
                        DefinitionProfiles.canonical(
                                ProfileFixtures.changed(
                                        "account", "/common/name", "\"A different valid name\""));
                assertThatThrownBy(
                                () ->
                                        execute(
                                                c,
                                                "UPDATE public.product_definition_revision SET"
                                                        + " canonical_definition=?,public_digest=?"
                                                        + " WHERE root_id=?",
                                                replacement.json(),
                                                replacement.digest(),
                                                seed.root()))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void legacyProductMutationCannotRewriteManagedIdentityOrTerms() throws Exception {
        try (var c = owner()) {
            c.setAutoCommit(false);
            try {
                var product = product(c, true);
                definition(c, product);
                assertThatThrownBy(
                                () ->
                                        execute(
                                                c,
                                                "UPDATE public.product SET product_name=? WHERE"
                                                        + " product_id=?",
                                                "Rewritten through legacy DTO",
                                                product.id()))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void unmanagedProductsRetainIdentityUpdateDeleteAndIdReuseBehavior() throws Exception {
        try (var c = owner()) {
            c.setAutoCommit(false);
            try {
                var product = product(c);
                var movedId = UUID.randomUUID();
                execute(
                        c,
                        "UPDATE public.product SET product_id=?,tenant_id=? WHERE product_id=?",
                        movedId,
                        UUID.randomUUID(),
                        product.id());
                execute(
                        c,
                        "INSERT INTO"
                            + " public.product(product_id,tenant_id,product_category_id,product_type,product_name,product_code,product_status)"
                            + " SELECT"
                            + " ?,tenant_id,product_category_id,product_type,product_name,product_code,product_status"
                            + " FROM public.product WHERE product_id=?",
                        product.id(),
                        movedId);
                execute(c, "DELETE FROM public.product WHERE product_id=?", product.id());
                execute(
                        c,
                        "INSERT INTO"
                            + " public.product(product_id,tenant_id,product_category_id,product_type,product_name,product_code,product_status)"
                            + " SELECT"
                            + " ?,tenant_id,product_category_id,product_type,product_name,product_code,product_status"
                            + " FROM public.product WHERE product_id=?",
                        product.id(),
                        movedId);
                try (var s =
                        c.prepareStatement(
                                "SELECT count(*) FROM public.product WHERE product_id IN(?,?)")) {
                    s.setObject(1, product.id());
                    s.setObject(2, movedId);
                    try (var rs = s.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isEqualTo(2);
                    }
                }
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void nonownerRuntimeRetainsOrdinaryLegacyCreateUpdateDeleteWithoutPrivateSchemaAccess()
            throws Exception {
        try (var c =
                DriverManager.getConnection(
                        postgres.getJdbcUrl(), "definition_runtime", RUNTIME_PASSWORD)) {
            var ordinary = product(c);
            execute(
                    c,
                    "UPDATE public.product SET product_name=? WHERE product_id=?",
                    "Ordinary renamed product",
                    ordinary.id());
            try (var s =
                    c.prepareStatement(
                            "SELECT product_name FROM public.product WHERE product_id=?")) {
                s.setObject(1, ordinary.id());
                try (var rs = s.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("Ordinary renamed product");
                }
            }
            execute(c, "DELETE FROM public.product WHERE product_id=?", ordinary.id());
            try (var s =
                    c.prepareStatement("SELECT count(*) FROM public.product WHERE product_id=?")) {
                s.setObject(1, ordinary.id());
                try (var rs = s.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isZero();
                }
            }
            try (var s = c.createStatement();
                    var rs =
                            s.executeQuery(
                                    "SELECT pg_catalog.has_schema_privilege(current_user,"
                                            + "'firefly_product_definition_private','USAGE')")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean(1)).isFalse();
            }
        }
    }

    @Test
    void retainedPrivateCostRevisionCannotBeRewrittenWithDifferentValidCosts() throws Exception {
        try (var c = owner()) {
            c.setAutoCommit(false);
            try {
                var seed = definition(c, product(c, true));
                var replacement =
                        CostRules.canonical(
                                CostRulesTest.costs("account").replace("12345", "54321"),
                                ProfileFixtures.load("account").toString());
                assertThatThrownBy(
                                () ->
                                        execute(
                                                c,
                                                "UPDATE public.product_definition_cost_revision SET"
                                                        + " canonical_cost=?,cost_digest=? WHERE"
                                                        + " root_id=?",
                                                replacement.json(),
                                                replacement.digest(),
                                                seed.root()))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void governedRootCodeCannotBeChanged() throws Exception {
        try (var c = owner()) {
            c.setAutoCommit(false);
            try {
                var seed = definition(c, product(c, true));
                assertThatThrownBy(
                                () ->
                                        execute(
                                                c,
                                                "UPDATE public.product_definition_root SET"
                                                        + " product_code='CHANGED_CODE' WHERE"
                                                        + " root_id=?",
                                                seed.root()))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            } finally {
                c.rollback();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "version",
                "configuration",
                "documentation",
                "requirement",
                "localization",
                "relationship-primary",
                "relationship-related"
            })
    void legacyChildrenCannotAddTermsToEitherManagedEndpoint(String child) throws Exception {
        try (var c = owner()) {
            c.setAutoCommit(false);
            try {
                var managed = product(c, true);
                definition(c, managed);
                if (child.startsWith("relationship")) {
                    var other = product(c);
                    execute(
                            c,
                            "UPDATE public.product SET tenant_id=? WHERE product_id=?",
                            managed.tenant(),
                            other.id());
                    var first = child.endsWith("primary") ? managed.id() : other.id();
                    var second = child.endsWith("primary") ? other.id() : managed.id();
                    assertThatThrownBy(
                                    () ->
                                            execute(
                                                    c,
                                                    "INSERT INTO public.product_relationship("
                                                        + "product_id,related_product_id,relationship_type)"
                                                        + " VALUES(?,?,'CROSS_SELL')",
                                                    first,
                                                    second))
                            .isInstanceOfSatisfying(
                                    SQLException.class,
                                    e -> assertThat(e.getSQLState()).isEqualTo("23514"));
                } else {
                    var sql =
                            switch (child) {
                                case "version" ->
                                        "INSERT INTO"
                                            + " public.product_version(product_id,version_number,effective_date)"
                                            + " VALUES(?,1,CURRENT_TIMESTAMP)";
                                case "configuration" ->
                                        "INSERT INTO"
                                            + " public.product_configuration(product_id,config_type,config_key,config_value)"
                                            + " VALUES(?,'PRICING','fee','1')";
                                case "documentation" ->
                                        "INSERT INTO"
                                            + " public.product_documentation(product_id,doc_type,document_manager_ref)"
                                            + " VALUES(?,'TNC',123)";
                                case "requirement" ->
                                        "INSERT INTO"
                                            + " public.product_documentation_requirement(product_id,doc_type)"
                                            + " VALUES(?,'IDENTIFICATION')";
                                case "localization" ->
                                        "INSERT INTO"
                                            + " public.product_localization(product_id,language_code,localized_name)"
                                            + " VALUES(?,'en','Unsealed label')";
                                default -> throw new IllegalArgumentException("Test child");
                            };
                    assertThatThrownBy(() -> execute(c, sql, managed.id()))
                            .isInstanceOfSatisfying(
                                    SQLException.class,
                                    e -> assertThat(e.getSQLState()).isEqualTo("23514"));
                }
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void managedCategoryCannotBeReparented() throws Exception {
        try (var c = owner()) {
            c.setAutoCommit(false);
            try {
                var managed = product(c, true);
                definition(c, managed);
                var other = UUID.randomUUID();
                execute(
                        c,
                        "INSERT INTO public.product_category(product_category_id,category_name)"
                                + " VALUES(?,?)",
                        other,
                        "Unrelated synthetic category");
                assertThatThrownBy(
                                () ->
                                        execute(
                                                c,
                                                "UPDATE public.product_category SET"
                                                        + " parent_category_id=? WHERE"
                                                        + " product_category_id=(SELECT"
                                                        + " product_category_id FROM public.product"
                                                        + " WHERE product_id=?)",
                                                other,
                                                managed.id()))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            } finally {
                c.rollback();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeCreateLocksEveryAncestorBeforeAcquiringProductForeignKeys(boolean holdLeaf)
            throws Exception {
        var ancestor = UUID.randomUUID();
        var leaf = UUID.randomUUID();
        try (var c = owner()) {
            execute(
                    c,
                    "INSERT INTO public.product_category(product_category_id,category_name)"
                            + " VALUES(?,?)",
                    ancestor,
                    "Synthetic root classification");
            execute(
                    c,
                    "INSERT INTO"
                        + " public.product_category(product_category_id,parent_category_id,category_name)"
                        + " VALUES(?,?,?)",
                    leaf,
                    ancestor,
                    "Synthetic leaf classification");
        }
        try (var holder = owner();
                var worker = owner();
                var inspector = owner()) {
            holder.setAutoCommit(false);
            worker.setAutoCommit(false);
            int pid;
            try (var s = worker.createStatement();
                    var rs = s.executeQuery("SELECT pg_backend_pid()")) {
                assertThat(rs.next()).isTrue();
                pid = rs.getInt(1);
            }
            try (var s =
                    holder.prepareStatement(
                            "SELECT product_category_id FROM public.product_category"
                                    + " WHERE product_category_id=? FOR NO KEY UPDATE")) {
                s.setObject(1, holdLeaf ? leaf : ancestor);
                try (var rs = s.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                }
            }
            var executor = Executors.newSingleThreadExecutor();
            var future =
                    executor.submit(
                            () -> {
                                try {
                                    var product = product(worker, true, leaf);
                                    var result = definition(worker, product);
                                    worker.commit();
                                    return result;
                                } catch (Exception e) {
                                    worker.rollback();
                                    throw e;
                                }
                            });
            try {
                boolean waiting = false;
                long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                while (!waiting && !future.isDone() && System.nanoTime() < deadline) {
                    try (var s =
                            inspector.prepareStatement(
                                    "SELECT wait_event_type,query FROM pg_catalog.pg_stat_activity"
                                            + " WHERE pid=?")) {
                        s.setInt(1, pid);
                        try (var rs = s.executeQuery()) {
                            if (rs.next() && "Lock".equals(rs.getString(1))) {
                                assertThat(rs.getString(2))
                                        .startsWith("INSERT INTO public.product(");
                                waiting = true;
                            }
                        }
                    }
                    if (!waiting) Thread.sleep(10);
                }
                assertThat(waiting)
                        .as("native Product INSERT waits on every ancestor before its FK checks")
                        .isTrue();
                holder.commit();
                assertThat(future.get(5, TimeUnit.SECONDS)).isNotNull();
            } finally {
                holder.rollback();
                try {
                    future.get(5, TimeUnit.SECONDS);
                } finally {
                    executor.shutdownNow();
                    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                }
            }
        }
    }

    @Test
    void uncommittedNewParentCannotAuthorizeLegacyChildCreation() throws Exception {
        try (var parent = owner();
                var child = owner()) {
            parent.setAutoCommit(false);
            child.setAutoCommit(false);
            try {
                var managed = product(parent, true);
                definition(parent, managed);
                assertThatThrownBy(
                                () ->
                                        execute(
                                                child,
                                                "INSERT INTO"
                                                    + " public.product_configuration(product_id,config_type,config_key,config_value)"
                                                    + " VALUES(?,'PRICING','unsealed-fee','1')",
                                                managed.id()))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                e -> assertThat(e.getSQLState()).isEqualTo("23503"));
                child.rollback();
                parent.commit();
                assertThatThrownBy(
                                () ->
                                        execute(
                                                child,
                                                "INSERT INTO"
                                                    + " public.product_configuration(product_id,config_type,config_key,config_value)"
                                                    + " VALUES(?,'PRICING','unsealed-fee','1')",
                                                managed.id()))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            } finally {
                parent.rollback();
                child.rollback();
            }
        }
    }

    @Test
    void sameTransactionLegacyChildrenCannotBeAdoptedIntoNativeDefinition() throws Exception {
        try (var c = owner()) {
            c.setAutoCommit(false);
            try {
                var managed = product(c, true);
                execute(
                        c,
                        "INSERT INTO"
                            + " public.product_configuration(product_id,config_type,config_key,config_value)"
                            + " VALUES(?,'PRICING','unsealed-fee','1')",
                        managed.id());
                assertThatThrownBy(() -> definition(c, managed))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void skippedNativeInsertCannotRetroactivelyProveLegacyInsertWasNative() throws Exception {
        try (var c = owner()) {
            c.setAutoCommit(false);
            try {
                var legacy = product(c);
                scope(c, legacy);
                execute(
                        c,
                        "INSERT INTO"
                            + " public.product(product_id,tenant_id,product_category_id,product_type,product_name,product_code,product_status)"
                            + " SELECT"
                            + " product_id,tenant_id,product_category_id,product_type,product_name,product_code,product_status"
                            + " FROM public.product WHERE product_id=? ON CONFLICT(product_id) DO"
                            + " NOTHING",
                        legacy.id());
                assertThatThrownBy(() -> definition(c, legacy))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void nativeProductCategoryChangeCannotReuseItsOriginalAncestorProof() throws Exception {
        try (var c = owner()) {
            c.setAutoCommit(false);
            try {
                var candidate = product(c, true);
                var replacement = UUID.randomUUID();
                execute(
                        c,
                        "INSERT INTO public.product_category(product_category_id,category_name)"
                                + " VALUES(?,?)",
                        replacement,
                        "Different synthetic classification");
                execute(
                        c,
                        "UPDATE public.product SET product_category_id=? WHERE product_id=?",
                        replacement,
                        candidate.id());
                assertThatThrownBy(() -> definition(c, candidate))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            } finally {
                c.rollback();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void categoryReparentOrCascadeWaitingForNativeCreateRechecksProtectedAncestors(boolean delete)
            throws Exception {
        var ancestor = UUID.randomUUID();
        var leaf = UUID.randomUUID();
        try (var c = owner()) {
            categories(c, ancestor, leaf);
        }
        try (var nativeCreate = owner();
                var legacy = owner();
                var inspector = owner()) {
            nativeCreate.setAutoCommit(false);
            legacy.setAutoCommit(false);
            var managed = product(nativeCreate, true, leaf);
            var seed = definition(nativeCreate, managed);
            var sql =
                    delete
                            ? "DELETE FROM public.product_category WHERE product_category_id=?"
                            : "UPDATE public.product_category SET parent_category_id=NULL WHERE"
                                    + " product_category_id=?";
            int pid = backendPid(legacy);
            var executor = Executors.newSingleThreadExecutor();
            var future =
                    executor.submit(
                            () -> {
                                try {
                                    execute(legacy, sql, delete ? ancestor : leaf);
                                    legacy.commit();
                                    return (SQLException) null;
                                } catch (SQLException e) {
                                    legacy.rollback();
                                    return e;
                                }
                            });
            try {
                assertThat(
                                waitForLock(
                                        inspector,
                                        future,
                                        pid,
                                        delete ? "DELETE FROM" : "UPDATE public.product_category"))
                        .as("legacy mutation waits on the native category observation")
                        .isTrue();
                nativeCreate.commit();
                var rejected = future.get(5, TimeUnit.SECONDS);
                assertThat((Object) rejected).isNotNull();
                assertThat(rejected.getSQLState()).isEqualTo("23514");
                assertThat(count(inspector, "product_definition_root", seed.root())).isEqualTo(1);
                try (var s =
                        inspector.prepareStatement(
                                "SELECT parent_category_id FROM public.product_category WHERE"
                                        + " product_category_id=?")) {
                    s.setObject(1, leaf);
                    try (var rs = s.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getObject(1, UUID.class)).isEqualTo(ancestor);
                    }
                }
            } finally {
                nativeCreate.rollback();
                try {
                    future.get(5, TimeUnit.SECONDS);
                } finally {
                    executor.shutdownNow();
                    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                }
            }
        }
    }

    @Test
    void ancestorCascadeWinningBeforeNativeCreateLeavesNoPartialProductOrRoot() throws Exception {
        var ancestor = UUID.randomUUID();
        var leaf = UUID.randomUUID();
        var candidate = new Product(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        try (var c = owner()) {
            categories(c, ancestor, leaf);
        }
        try (var legacy = owner();
                var nativeCreate = owner();
                var inspector = owner()) {
            legacy.setAutoCommit(false);
            nativeCreate.setAutoCommit(false);
            execute(
                    legacy,
                    "DELETE FROM public.product_category WHERE product_category_id=?",
                    ancestor);
            int pid = backendPid(nativeCreate);
            var executor = Executors.newSingleThreadExecutor();
            var future =
                    executor.submit(
                            () -> {
                                try {
                                    product(nativeCreate, true, leaf, candidate);
                                    definition(nativeCreate, candidate);
                                    nativeCreate.commit();
                                    return (SQLException) null;
                                } catch (SQLException e) {
                                    nativeCreate.rollback();
                                    return e;
                                }
                            });
            try {
                assertThat(waitForLock(inspector, future, pid, "INSERT INTO public.product("))
                        .as("native waits before leaf FK when ancestor cascade owns the first lock")
                        .isTrue();
                legacy.commit();
                var rejected = future.get(5, TimeUnit.SECONDS);
                assertThat((Object) rejected).isNotNull();
                assertThat(rejected.getSQLState()).isEqualTo("23514");
                assertThat(count(inspector, "product_definition_root", candidate.root())).isZero();
                try (var s =
                        inspector.prepareStatement(
                                "SELECT count(*) FROM public.product WHERE product_id=?")) {
                    s.setObject(1, candidate.id());
                    try (var rs = s.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isZero();
                    }
                }
            } finally {
                legacy.rollback();
                try {
                    future.get(5, TimeUnit.SECONDS);
                } finally {
                    executor.shutdownNow();
                    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                }
            }
        }
    }

    private static final List<String> LEGACY_CHILD_KINDS =
            List.of(
                    "version",
                    "configuration",
                    "documentation",
                    "requirement",
                    "localization",
                    "relationship-primary",
                    "relationship-related");

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> lateChildCases() {
        var cases = new ArrayList<org.junit.jupiter.params.provider.Arguments>();
        for (var kind : LEGACY_CHILD_KINDS) {
            for (var isolation :
                    List.of(
                            Connection.TRANSACTION_READ_COMMITTED,
                            Connection.TRANSACTION_REPEATABLE_READ,
                            Connection.TRANSACTION_SERIALIZABLE)) {
                cases.add(
                        org.junit.jupiter.params.provider.Arguments.of(
                                kind, isolation, false, false));
            }
        }
        for (var kind : List.of("configuration", "relationship-primary", "relationship-related")) {
            for (var isolation :
                    List.of(
                            Connection.TRANSACTION_READ_COMMITTED,
                            Connection.TRANSACTION_REPEATABLE_READ,
                            Connection.TRANSACTION_SERIALIZABLE)) {
                cases.add(
                        org.junit.jupiter.params.provider.Arguments.of(
                                kind, isolation, true, false));
            }
        }
        cases.add(
                org.junit.jupiter.params.provider.Arguments.of(
                        "configuration", Connection.TRANSACTION_READ_COMMITTED, false, true));
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} isolation={1} update={2} immediate={3}")
    @org.junit.jupiter.params.provider.MethodSource("lateChildCases")
    void childPassingBeforeEnrollmentCannotCommitAfterTheParentForeignKeyBarrier(
            String kind, int isolation, boolean update, boolean immediate) throws Exception {
        var relation = legacyChild(kind);
        var row = UUID.randomUUID();
        long key = UUID.randomUUID().getMostSignificantBits();
        var worker = Executors.newSingleThreadExecutor();
        Future<String> future = null;
        try (var parent = owner();
                var gate = owner();
                var observer = owner();
                var child =
                        DriverManager.getConnection(
                                postgres.getJdbcUrl(), "definition_runtime", RUNTIME_PASSWORD)) {
            var ordinary = product(observer);
            var other = product(observer);
            if (update) insertLegacyChild(observer, kind, row, ordinary.id(), other.id());
            parent.setAutoCommit(false);
            var managed = product(parent, true);
            definition(parent, managed);
            String condition = "NEW.product_id='" + managed.id() + "'::uuid";
            if (kind.startsWith("relationship")) {
                condition += " OR NEW.related_product_id='" + managed.id() + "'::uuid";
            }
            try (var s = observer.createStatement()) {
                s.execute(
                        "CREATE OR REPLACE FUNCTION public.\"AAA_c2_test_pause\"() RETURNS trigger"
                            + " LANGUAGE plpgsql AS $$ BEGIN PERFORM"
                            + " pg_catalog.pg_advisory_xact_lock(TG_ARGV[0]::bigint); RETURN NEW;"
                            + " END $$");
                s.execute(
                        "CREATE TRIGGER \"AAA_c2_test_pause\" AFTER INSERT OR UPDATE ON public."
                                + relation.table()
                                + " FOR EACH ROW WHEN ("
                                + condition
                                + ")"
                                + " EXECUTE FUNCTION public.\"AAA_c2_test_pause\"('"
                                + key
                                + "')");
            }
            try {
                child.setTransactionIsolation(isolation);
                child.setAutoCommit(false);
                if (immediate) {
                    try (var s = child.createStatement()) {
                        s.execute("SET CONSTRAINTS ALL IMMEDIATE");
                    }
                }
                try (var s = gate.createStatement()) {
                    s.execute("SELECT pg_advisory_lock(" + key + ")");
                }
                int pid = backendPid(child);
                future =
                        worker.submit(
                                () -> {
                                    try {
                                        if (update) {
                                            execute(
                                                    child,
                                                    "UPDATE public."
                                                            + relation.table()
                                                            + " SET "
                                                            + relation.targetColumn()
                                                            + "=? WHERE "
                                                            + relation.idColumn()
                                                            + "=?",
                                                    managed.id(),
                                                    row);
                                        } else {
                                            var first =
                                                    kind.equals("relationship-related")
                                                            ? ordinary.id()
                                                            : managed.id();
                                            var second =
                                                    kind.equals("relationship-related")
                                                            ? managed.id()
                                                            : ordinary.id();
                                            insertLegacyChild(child, kind, row, first, second);
                                        }
                                        child.commit();
                                        return "COMMITTED";
                                    } catch (SQLException error) {
                                        child.rollback();
                                        return error.getSQLState();
                                    }
                                });
                assertThat(
                                waitForLock(
                                        observer,
                                        future,
                                        pid,
                                        update ? "UPDATE public." : "INSERT INTO public."))
                        .as(
                                "child passed the production BEFORE guard and reached the pre-FK"
                                    + " scheduling barrier")
                        .isTrue();
                parent.commit();
                try (var s = gate.createStatement()) {
                    s.execute("SELECT pg_advisory_unlock(" + key + ")");
                }
                String outcome = future.get(8, TimeUnit.SECONDS);
                if (isolation == Connection.TRANSACTION_READ_COMMITTED) {
                    assertThat(outcome)
                            .as("late guard must reject after the FK can see native enrollment")
                            .isEqualTo("23514");
                } else {
                    assertThat(outcome)
                            .as(
                                    "snapshot isolation must fail closed, not accept a stale"
                                        + " binding view")
                            .isIn("23514", "23503", "40001");
                }
                try (var s =
                        observer.prepareStatement(
                                "SELECT count(*) FROM public."
                                        + relation.table()
                                        + " WHERE "
                                        + relation.targetColumn()
                                        + "=?")) {
                    s.setObject(1, managed.id());
                    try (var rs = s.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isZero();
                    }
                }
                if (update) {
                    try (var s =
                            observer.prepareStatement(
                                    "SELECT "
                                            + relation.targetColumn()
                                            + " FROM public."
                                            + relation.table()
                                            + " WHERE "
                                            + relation.idColumn()
                                            + "=?")) {
                        s.setObject(1, row);
                        try (var rs = s.executeQuery()) {
                            assertThat(rs.next()).isTrue();
                            assertThat(rs.getObject(1))
                                    .isEqualTo(
                                            kind.equals("relationship-related")
                                                    ? other.id()
                                                    : ordinary.id());
                        }
                    }
                }
            } finally {
                parent.rollback();
                try (var s = gate.createStatement()) {
                    s.execute("SELECT pg_advisory_unlock(" + key + ")");
                }
                if (future != null && !future.isDone()) {
                    child.close();
                    future.cancel(true);
                }
                try (var s = observer.createStatement()) {
                    s.execute("DROP TRIGGER \"AAA_c2_test_pause\" ON public." + relation.table());
                }
            }
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "version",
                "configuration",
                "documentation",
                "requirement",
                "localization",
                "relationship-primary",
                "relationship-related"
            })
    void nonownerUnmanagedChildCreateReparentAndDeleteRemainAvailable(String kind)
            throws Exception {
        var relation = legacyChild(kind);
        var row = UUID.randomUUID();
        try (var c =
                DriverManager.getConnection(
                        postgres.getJdbcUrl(), "definition_runtime", RUNTIME_PASSWORD)) {
            var first = product(c);
            var second = product(c);
            var third = product(c);
            c.setAutoCommit(false);
            insertLegacyChild(c, kind, row, first.id(), second.id());
            c.commit();
            execute(
                    c,
                    "UPDATE public."
                            + relation.table()
                            + " SET "
                            + relation.targetColumn()
                            + "=? WHERE "
                            + relation.idColumn()
                            + "=?",
                    third.id(),
                    row);
            c.commit();
            execute(
                    c,
                    "DELETE FROM public."
                            + relation.table()
                            + " WHERE "
                            + relation.idColumn()
                            + "=?",
                    row);
            c.commit();
            try (var s =
                    c.prepareStatement(
                            "SELECT count(*) FROM public."
                                    + relation.table()
                                    + " WHERE "
                                    + relation.idColumn()
                                    + "=?")) {
                s.setObject(1, row);
                try (var rs = s.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isZero();
                }
            }
        }
    }

    private record LegacyChild(String table, String idColumn, String targetColumn) {}

    private static LegacyChild legacyChild(String kind) {
        var table =
                switch (kind) {
                    case "version" -> "product_version";
                    case "configuration" -> "product_configuration";
                    case "documentation" -> "product_documentation";
                    case "requirement" -> "product_documentation_requirement";
                    case "localization" -> "product_localization";
                    case "relationship-primary", "relationship-related" -> "product_relationship";
                    default -> throw new IllegalArgumentException("Synthetic child kind");
                };
        return new LegacyChild(
                table,
                kind.equals("requirement") ? "product_doc_requirement_id" : table + "_id",
                kind.equals("relationship-related") ? "related_product_id" : "product_id");
    }

    private static void insertLegacyChild(
            Connection c, String kind, UUID id, UUID first, UUID second) throws Exception {
        var relation = legacyChild(kind);
        var sql =
                switch (kind) {
                    case "version" ->
                            "INSERT INTO"
                                + " public.product_version(product_version_id,product_id,version_number,effective_date)"
                                + " VALUES(?,?,1,CURRENT_TIMESTAMP)";
                    case "configuration" ->
                            "INSERT INTO"
                                + " public.product_configuration(product_configuration_id,product_id,config_type,config_key,config_value)"
                                + " VALUES(?,?,'CUSTOM','late-child','synthetic')";
                    case "documentation" ->
                            "INSERT INTO"
                                + " public.product_documentation(product_documentation_id,product_id,doc_type,document_manager_ref)"
                                + " VALUES(?,?,'TNC',123)";
                    case "requirement" ->
                            "INSERT INTO"
                                + " public.product_documentation_requirement(product_doc_requirement_id,product_id,doc_type)"
                                + " VALUES(?,?,'IDENTIFICATION')";
                    case "localization" ->
                            "INSERT INTO"
                                + " public.product_localization(product_localization_id,product_id,language_code,localized_name)"
                                + " VALUES(?,?,'en','Ordinary label')";
                    default ->
                            "INSERT INTO"
                                + " public.product_relationship(product_relationship_id,product_id,related_product_id,relationship_type)"
                                + " VALUES(?,?,?,'CROSS_SELL')";
                };
        if (kind.startsWith("relationship")) execute(c, sql, id, first, second);
        else execute(c, sql, id, first);
    }

    private static void categories(Connection c, UUID ancestor, UUID leaf) throws Exception {
        execute(
                c,
                "INSERT INTO public.product_category(product_category_id,category_name)"
                        + " VALUES(?,?)",
                ancestor,
                "Synthetic root classification");
        execute(
                c,
                "INSERT INTO"
                    + " public.product_category(product_category_id,parent_category_id,category_name)"
                    + " VALUES(?,?,?)",
                leaf,
                ancestor,
                "Synthetic leaf classification");
    }

    private static int backendPid(Connection c) throws Exception {
        try (var s = c.createStatement();
                var rs = s.executeQuery("SELECT pg_backend_pid()")) {
            assertThat(rs.next()).isTrue();
            return rs.getInt(1);
        }
    }

    private static boolean waitForLock(
            Connection inspector, Future<?> future, int pid, String prefix) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!future.isDone() && System.nanoTime() < deadline) {
            try (var s =
                    inspector.prepareStatement(
                            "SELECT wait_event_type,query FROM pg_catalog.pg_stat_activity WHERE"
                                    + " pid=?")) {
                s.setInt(1, pid);
                try (var rs = s.executeQuery()) {
                    if (rs.next() && "Lock".equals(rs.getString(1))) {
                        assertThat(rs.getString(2)).startsWith(prefix);
                        return true;
                    }
                }
            }
            Thread.sleep(10);
        }
        return false;
    }

    private record Product(UUID tenant, UUID id, UUID root) {}

    private record Seed(UUID root, UUID revision, UUID cost) {}

    private static Product product(Connection c) throws Exception {
        return product(c, false);
    }

    private static Product product(Connection c, boolean nativeCreate) throws Exception {
        var category = UUID.randomUUID();
        execute(
                c,
                "INSERT INTO public.product_category(product_category_id,category_name)"
                        + " VALUES(?,?)",
                category,
                "Synthetic definition fixture");
        return product(c, nativeCreate, category);
    }

    private static Product product(Connection c, boolean nativeCreate, UUID category)
            throws Exception {
        var product = new Product(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        return product(c, nativeCreate, category, product);
    }

    private static Product product(
            Connection c, boolean nativeCreate, UUID category, Product product) throws Exception {
        if (nativeCreate) scope(c, product);
        else {
            try (var s = c.createStatement()) {
                s.execute(
                        "SELECT"
                            + " pg_catalog.set_config('firefly.product_definition.purpose','',true)");
            }
        }
        execute(
                c,
                "INSERT INTO"
                    + " public.product(product_id,tenant_id,product_category_id,product_type,product_name,product_code,product_status)"
                    + " VALUES(?,?,?,'FINANCIAL',?,?,'DRAFT')",
                product.id(),
                product.tenant(),
                category,
                "Synthetic governed current account",
                ProfileFixtures.load("account").at("/common/code").asText());
        return product;
    }

    private static Seed definition(Connection c, Product product) throws Exception {
        scope(c, product);
        var seed = new Seed(product.root(), UUID.randomUUID(), UUID.randomUUID());
        var raw = ProfileFixtures.load("account").toString();
        var publicTerms = DefinitionProfiles.canonical(raw);
        var privateCosts = CostRules.canonical(CostRulesTest.costs("account"), raw);
        execute(
                c,
                "INSERT INTO public.product_definition_root(root_id,tenant_id,product_id,"
                        + "product_code,business_area,profile_code,profile_version,phase,"
                        + "public_state_version,public_head) VALUES(?,?,?,?,'BANKING',"
                        + "'ACCOUNT_TARIFF_V1',1,'DRAFT',0,?)",
                seed.root(),
                product.tenant(),
                product.id(),
                ProfileFixtures.load("account").at("/common/code").asText(),
                seed.revision());
        execute(
                c,
                "INSERT INTO public.product_definition_revision(revision_id,tenant_id,root_id,"
                    + "canonical_definition,public_digest,encoding_version,actor_issuer,actor_id)"
                    + " VALUES(?,?,?,?,?,?,'https://synthetic.example.test','maker-public')",
                seed.revision(),
                product.tenant(),
                seed.root(),
                publicTerms.json(),
                publicTerms.digest(),
                DefinitionCanonical.ENCODING);
        execute(
                c,
                "INSERT INTO public.product_definition_cost_revision(cost_revision_id,tenant_id,"
                    + "root_id,canonical_cost,cost_digest,encoding_version,actor_issuer,actor_id)"
                    + " VALUES(?,?,?,?,?,?,'https://synthetic.example.test','maker-private')",
                seed.cost(),
                product.tenant(),
                seed.root(),
                privateCosts.json(),
                privateCosts.digest(),
                DefinitionCanonical.ENCODING);
        execute(
                c,
                "INSERT INTO public.product_definition_control(root_id,tenant_id,cost_head,"
                        + "cost_state_version) VALUES(?,?,?,0)",
                seed.root(),
                product.tenant(),
                seed.cost());
        return seed;
    }

    private static void scope(Connection c, Product product) throws Exception {
        try (var s =
                c.prepareStatement(
                        "SELECT"
                            + " pg_catalog.set_config('firefly.product_definition.purpose','CREATE',true),"
                            + "pg_catalog.set_config('firefly.product_definition.tenant_id',?,true),"
                            + "pg_catalog.set_config('firefly.product_definition.root_id',?,true),"
                            + "pg_catalog.set_config('firefly.product_definition.product_id',?,true)")) {
            s.setString(1, product.tenant().toString());
            s.setString(2, product.root().toString());
            s.setString(3, product.id().toString());
            try (var ignored = s.executeQuery()) {}
        }
    }

    private static void execute(Connection c, String sql, Object... values) throws Exception {
        try (var statement = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    private static int count(Connection c, String table, UUID root) throws Exception {
        if (!NATIVE_TABLES.contains(table)) throw new IllegalArgumentException("Test relation");
        try (var statement =
                c.prepareStatement("SELECT count(*) FROM public." + table + " WHERE root_id=?")) {
            statement.setObject(1, root);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }
}

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
    private static final String OWNER_TAG =
            System.getProperty(
                    "product.definition.native.owner-tag",
                    "pc-lifecycle-original-" + UUID.randomUUID());
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String RUNTIME_PASSWORD = UUID.randomUUID().toString();
    private static final String READER_PASSWORD = UUID.randomUUID().toString();
    private static final String COMMAND_PASSWORD = UUID.randomUUID().toString();
    private static ProductDefinitionCommandDatabaseFixture commands;

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("synthetic_product_definitions")
                    .withUsername("definition_bootstrap")
                    .withPassword(PASSWORD)
                    .withLabel("firefly.qualification.owner", OWNER_TAG)
                    .withCommand("postgres", "-c", "log_min_error_statement=panic")
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
                    "product_definition_cost_outbox",
                    "product_definition_submission",
                    "product_definition_review_decision",
                    "product_definition_version_identity");

    static Connection owner() throws Exception {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static Connection legacy() throws Exception {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), "definition_runtime", RUNTIME_PASSWORD);
    }

    @BeforeAll
    static void migrate() throws Exception {
        try (var c = owner();
                var s = c.createStatement()) {
            s.execute(
                    "CREATE ROLE firefly_product_definition_guard NOLOGIN NOSUPERUSER NOBYPASSRLS;"
                        + " CREATE ROLE firefly_product_definition_serving NOLOGIN NOSUPERUSER"
                        + " NOBYPASSRLS; CREATE ROLE firefly_product_definition_commands NOLOGIN"
                        + " NOSUPERUSER NOBYPASSRLS; CREATE ROLE definition_runtime LOGIN PASSWORD"
                        + " '"
                            + RUNTIME_PASSWORD
                            + "' NOSUPERUSER NOBYPASSRLS;"
                            + " CREATE ROLE definition_reader LOGIN PASSWORD '"
                            + READER_PASSWORD
                            + "' NOSUPERUSER NOBYPASSRLS;"
                            + " CREATE ROLE definition_commands LOGIN PASSWORD '"
                            + COMMAND_PASSWORD
                            + "' NOSUPERUSER NOBYPASSRLS; GRANT firefly_product_definition_serving"
                            + " TO definition_reader; GRANT firefly_product_definition_commands TO"
                            + " definition_commands; REVOKE ALL ON DATABASE"
                            + " synthetic_product_definitions FROM PUBLIC; GRANT CONNECT ON"
                            + " DATABASE synthetic_product_definitions TO"
                            + " definition_runtime,definition_reader,definition_commands; REVOKE"
                            + " CREATE ON SCHEMA public FROM PUBLIC");
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
        commands =
                new ProductDefinitionCommandDatabaseFixture(
                        new ProductDefinitionCommandDatabaseFixture.Settings(
                                "127.0.0.1",
                                postgres.getMappedPort(5432),
                                postgres.getDatabaseName(),
                                "definition_reader",
                                READER_PASSWORD,
                                "definition_commands",
                                COMMAND_PASSWORD));
    }

    @AfterAll
    static void closeCommandFixture() {
        if (commands != null) commands.close();
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
        var product = new Product(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var call =
                commands.holdCreate(
                        product.tenant(),
                        product.root(),
                        product.id(),
                        category(),
                        ProductDefinitionCommandDatabaseFixture.Stage.AFTER_EVIDENCE);
        var completed = call.result().toFuture();
        try (var inspector = owner()) {
            var held = call.gate().reached().block(Duration.ofSeconds(5));
            assertThat(held).isNotNull();
            assertThat(held.backendPid()).isPositive();
            assertThat(held.nativeTransaction()).isNotBlank();
            assertThat(held.readOnly()).isFalse();
            assertThat(held.root()).isEqualTo(product.root());
            assertThat(held.revision()).isNotNull();
            assertThat(held.cost()).isNotNull();
            assertThat(productCount(inspector, product.id())).isZero();
            assertThat(count(inspector, "product_definition_root", product.root())).isZero();
            assertThat(count(inspector, "product_definition_revision", product.root())).isZero();
            assertThat(count(inspector, "product_definition_cost_revision", product.root()))
                    .isZero();
            call.gate().release();
            var receipt = completed.get(5, TimeUnit.SECONDS);
            assertThat(receipt.revisionId()).isEqualTo(held.revision());
            assertThat(productCount(inspector, product.id())).isEqualTo(1);
            assertThat(count(inspector, "product_definition_root", product.root())).isEqualTo(1);
            assertThat(count(inspector, "product_definition_revision", product.root()))
                    .isEqualTo(1);
            assertThat(count(inspector, "product_definition_cost_revision", product.root()))
                    .isEqualTo(1);
        } finally {
            call.gate().release();
            if (!completed.isDone()) completed.cancel(true);
        }
    }

    @Test
    void ordinaryMappedCallbackFailureRollsBackTheCompleteAdmittedCreate() throws Exception {
        var product = new Product(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var call =
                commands.holdCreate(
                        product.tenant(),
                        product.root(),
                        product.id(),
                        category(),
                        ProductDefinitionCommandDatabaseFixture.Stage.AFTER_EVIDENCE);
        var completed = call.result().toFuture();
        try {
            assertThat(call.gate().reached().block(Duration.ofSeconds(5))).isNotNull();
            call.gate().fail(new IllegalStateException("Owned ordinary callback failure"));
            assertThatThrownBy(() -> completed.get(5, TimeUnit.SECONDS))
                    .isInstanceOfSatisfying(
                            ExecutionException.class,
                            error ->
                                    assertThat(error.getCause())
                                            .isInstanceOfSatisfying(
                                                    org.springframework.web.server
                                                            .ResponseStatusException.class,
                                                    status ->
                                                            assertThat(status.getStatusCode())
                                                                    .isEqualTo(
                                                                            org.springframework.http
                                                                                    .HttpStatus
                                                                                    .SERVICE_UNAVAILABLE)));
            try (var inspector = owner()) {
                assertThat(productCount(inspector, product.id())).isZero();
                for (String table : NATIVE_TABLES)
                    assertThat(count(inspector, table, product.root())).as(table).isZero();
                for (String table : List.of("operation_claim", "native_command", "row_witness")) {
                    try (var query =
                            inspector.prepareStatement(
                                    "SELECT count(*) FROM firefly_product_definition_private."
                                            + table
                                            + " WHERE root_id=?")) {
                        query.setObject(1, product.root());
                        try (var rows = query.executeQuery()) {
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getInt(1)).as(table).isZero();
                        }
                    }
                }
            }
        } finally {
            call.gate().fail(new IllegalStateException("Owned fixture cleanup"));
            if (!completed.isDone()) completed.cancel(true);
        }
    }

    @Test
    void unscopedRuntimeCannotReadRealPublicOrPrivateRows() throws Exception {
        Seed seed = admittedDefinition(category()).seed();
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
        try (var c = legacy()) {
            legacy = product(c);
        }
        try (var c = legacy()) {
            c.setAutoCommit(false);
            try {
                String beforeLegacy =
                        rowText(
                                c,
                                "SELECT to_jsonb(p)::text FROM public.product p WHERE product_id=?",
                                legacy.id());
                assertThatThrownBy(
                                () ->
                                        commands.create(
                                                        legacy.tenant(),
                                                        legacy.root(),
                                                        legacy.id(),
                                                        categoryOf(legacy),
                                                        java.time.Instant.now().plusSeconds(300))
                                                .block(Duration.ofSeconds(8)))
                        .isInstanceOfSatisfying(
                                org.springframework.web.server.ResponseStatusException.class,
                                error ->
                                        assertThat(error.getStatusCode())
                                                .isEqualTo(
                                                        org.springframework.http.HttpStatus
                                                                .CONFLICT));
                assertThat(ownedRootCount(legacy.root())).isZero();
                assertThat(productCount(c, legacy.id())).isEqualTo(1);
                assertThat(
                                rowText(
                                        c,
                                        "SELECT to_jsonb(p)::text FROM public.product p WHERE"
                                                + " product_id=?",
                                        legacy.id()))
                        .isEqualTo(beforeLegacy);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void noOpUpdateDoesNotTurnACommittedLegacyProductIntoANewInsert() throws Exception {
        Product legacy;
        try (var c = legacy()) {
            legacy = product(c);
        }
        try (var c = legacy()) {
            c.setAutoCommit(false);
            try {
                execute(
                        c,
                        "UPDATE public.product SET product_name=product_name WHERE product_id=?",
                        legacy.id());
                String beforeLegacy =
                        rowText(
                                c,
                                "SELECT to_jsonb(p)::text FROM public.product p WHERE product_id=?",
                                legacy.id());
                assertThatThrownBy(
                                () ->
                                        commands.create(
                                                        legacy.tenant(),
                                                        legacy.root(),
                                                        legacy.id(),
                                                        categoryOf(legacy),
                                                        java.time.Instant.now().plusSeconds(300))
                                                .block(Duration.ofSeconds(8)))
                        .isInstanceOfSatisfying(
                                org.springframework.web.server.ResponseStatusException.class,
                                error ->
                                        assertThat(error.getStatusCode())
                                                .isEqualTo(
                                                        org.springframework.http.HttpStatus
                                                                .CONFLICT));
                assertThat(ownedRootCount(legacy.root())).isZero();
                assertThat(productCount(c, legacy.id())).isEqualTo(1);
                assertThat(
                                rowText(
                                        c,
                                        "SELECT to_jsonb(p)::text FROM public.product p WHERE"
                                                + " product_id=?",
                                        legacy.id()))
                        .isEqualTo(beforeLegacy);
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
                var seed = admittedDefinition(category()).seed();
                String before =
                        rowText(
                                c,
                                "SELECT to_jsonb(r)::text FROM public.product_definition_revision r"
                                        + " WHERE root_id=?",
                                seed.root());
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
                                e -> {
                                    assertThat(e.getSQLState()).isEqualTo("PD503");
                                    assertThat(e.getMessage())
                                            .contains("PRODUCT_NATIVE_RUNTIME_UNAVAILABLE");
                                });
                c.rollback();
                assertThat(
                                rowText(
                                        c,
                                        "SELECT to_jsonb(r)::text FROM"
                                                + " public.product_definition_revision r WHERE"
                                                + " root_id=?",
                                        seed.root()))
                        .isEqualTo(before);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void legacyProductMutationCannotRewriteManagedIdentityOrTerms() throws Exception {
        try (var c = legacy()) {
            c.setAutoCommit(false);
            try {
                var product = admittedDefinition(category()).product();
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
        try (var c = legacy()) {
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
                var seed = admittedDefinition(category()).seed();
                String before =
                        rowText(
                                c,
                                "SELECT to_jsonb(r)::text FROM"
                                    + " public.product_definition_cost_revision r WHERE root_id=?",
                                seed.root());
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
                                e -> {
                                    assertThat(e.getSQLState()).isEqualTo("PD503");
                                    assertThat(e.getMessage())
                                            .contains("PRODUCT_NATIVE_RUNTIME_UNAVAILABLE");
                                });
                c.rollback();
                assertThat(
                                rowText(
                                        c,
                                        "SELECT to_jsonb(r)::text FROM"
                                                + " public.product_definition_cost_revision r WHERE"
                                                + " root_id=?",
                                        seed.root()))
                        .isEqualTo(before);
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
                var seed = admittedDefinition(category()).seed();
                String before =
                        rowText(
                                c,
                                "SELECT to_jsonb(r)::text FROM public.product_definition_root r"
                                        + " WHERE root_id=?",
                                seed.root());
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
                                e -> {
                                    assertThat(e.getSQLState()).isEqualTo("PD503");
                                    assertThat(e.getMessage())
                                            .contains("PRODUCT_NATIVE_RUNTIME_UNAVAILABLE");
                                });
                c.rollback();
                assertThat(
                                rowText(
                                        c,
                                        "SELECT to_jsonb(r)::text FROM"
                                            + " public.product_definition_root r WHERE root_id=?",
                                        seed.root()))
                        .isEqualTo(before);
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
        try (var c = legacy()) {
            c.setAutoCommit(false);
            try {
                var managed = admittedDefinition(category()).product();
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
                var managed = admittedDefinition(category()).product();
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
        try (var connection = owner()) {
            execute(
                    connection,
                    "INSERT INTO public.product_category(product_category_id,category_name)"
                            + " VALUES(?,?)",
                    ancestor,
                    "Synthetic root classification");
            execute(
                    connection,
                    "INSERT INTO"
                        + " public.product_category(product_category_id,parent_category_id,category_name)"
                        + " VALUES(?,?,?)",
                    leaf,
                    ancestor,
                    "Synthetic leaf classification");
        }
        var product = new Product(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        try (var holder = owner();
                var inspector = owner()) {
            holder.setAutoCommit(false);
            try (var query =
                    holder.prepareStatement(
                            "SELECT product_category_id FROM public.product_category WHERE"
                                    + " product_category_id=? FOR NO KEY UPDATE")) {
                query.setObject(1, holdLeaf ? leaf : ancestor);
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                }
            }
            var completed =
                    commands.create(
                                    product.tenant(),
                                    product.root(),
                                    product.id(),
                                    leaf,
                                    java.time.Instant.now().plusSeconds(300))
                            .toFuture();
            try {
                boolean waiting = false;
                long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                while (!waiting && !completed.isDone() && System.nanoTime() < deadline) {
                    try (var query =
                                    inspector.prepareStatement(
                                            "SELECT pid,query FROM pg_catalog.pg_stat_activity"
                                                + " WHERE usename='definition_commands' AND"
                                                + " wait_event_type='Lock' AND state='active' LIMIT"
                                                + " 2");
                            var rows = query.executeQuery()) {
                        if (rows.next()) {
                            assertThat(rows.getInt(1)).isPositive();
                            assertThat(rows.getString(2))
                                    .contains("begin_product_definition_command");
                            assertThat(rows.next()).isFalse();
                            waiting = true;
                        }
                    }
                    if (!waiting) Thread.sleep(10);
                }
                assertThat(waiting)
                        .as(
                                "native command admission waits on every ancestor before Product FK"
                                        + " SQL")
                        .isTrue();
                assertThat(productCount(inspector, product.id())).isZero();
                holder.commit();
                assertThat(completed.get(5, TimeUnit.SECONDS)).isNotNull();
                assertThat(count(inspector, "product_definition_root", product.root()))
                        .isEqualTo(1);
            } finally {
                holder.rollback();
                if (!completed.isDone()) completed.cancel(true);
            }
        }
    }

    @Test
    void uncommittedNewParentCannotAuthorizeLegacyChildCreation() throws Exception {
        var product = new Product(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var parent =
                commands.holdCreate(
                        product.tenant(),
                        product.root(),
                        product.id(),
                        category(),
                        ProductDefinitionCommandDatabaseFixture.Stage.AFTER_EVIDENCE);
        var completed = parent.result().toFuture();
        try (var child = owner()) {
            child.setAutoCommit(false);
            try {
                assertThat(parent.gate().reached().block(Duration.ofSeconds(5))).isNotNull();
                assertThatThrownBy(
                                () ->
                                        execute(
                                                child,
                                                "INSERT INTO"
                                                    + " public.product_configuration(product_id,config_type,config_key,config_value)"
                                                    + " VALUES(?,'PRICING','unsealed-fee','1')",
                                                product.id()))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                error -> assertThat(error.getSQLState()).isEqualTo("23503"));
                child.rollback();
                parent.gate().release();
                assertThat(completed.get(5, TimeUnit.SECONDS)).isNotNull();
                assertThatThrownBy(
                                () ->
                                        execute(
                                                child,
                                                "INSERT INTO"
                                                    + " public.product_configuration(product_id,config_type,config_key,config_value)"
                                                    + " VALUES(?,'PRICING','unsealed-fee','1')",
                                                product.id()))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                error -> assertThat(error.getSQLState()).isEqualTo("23514"));
            } finally {
                child.rollback();
                parent.gate().release();
                if (!completed.isDone()) completed.cancel(true);
            }
        }
    }

    @Test
    void sameTransactionLegacyChildrenCannotBeAdoptedIntoNativeDefinition() throws Exception {
        Product legacy;
        try (var connection = legacy()) {
            connection.setAutoCommit(false);
            legacy = product(connection);
            execute(
                    connection,
                    "INSERT INTO"
                        + " public.product_configuration(product_id,config_type,config_key,config_value)"
                        + " VALUES(?,'PRICING','unsealed-fee','1')",
                    legacy.id());
            connection.commit();
        }
        try (var inspector = owner()) {
            String before =
                    rowText(
                            inspector,
                            "SELECT to_jsonb(p)::text FROM public.product p WHERE product_id=?",
                            legacy.id());
            assertThatThrownBy(
                            () ->
                                    commands.create(
                                                    legacy.tenant(),
                                                    legacy.root(),
                                                    legacy.id(),
                                                    categoryOf(legacy),
                                                    java.time.Instant.now().plusSeconds(300))
                                            .block(Duration.ofSeconds(8)))
                    .isInstanceOfSatisfying(
                            org.springframework.web.server.ResponseStatusException.class,
                            error ->
                                    assertThat(error.getStatusCode())
                                            .isEqualTo(
                                                    org.springframework.http.HttpStatus.CONFLICT));
            assertThat(count(inspector, "product_definition_root", legacy.root())).isZero();
            assertThat(
                            rowText(
                                    inspector,
                                    "SELECT to_jsonb(p)::text FROM public.product p WHERE"
                                            + " product_id=?",
                                    legacy.id()))
                    .isEqualTo(before);
            try (var statement =
                    inspector.prepareStatement(
                            "SELECT count(*) FROM public.product_configuration WHERE"
                                    + " product_id=?")) {
                statement.setObject(1, legacy.id());
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    @Test
    void skippedNativeInsertCannotRetroactivelyProveLegacyInsertWasNative() throws Exception {
        Product legacy;
        try (var connection = legacy()) {
            connection.setAutoCommit(false);
            legacy = product(connection);
            try (var statement =
                    connection.prepareStatement(
                            "INSERT INTO"
                                + " public.product(product_id,tenant_id,product_category_id,product_type,product_name,product_code,product_status)"
                                + " SELECT"
                                + " product_id,tenant_id,product_category_id,product_type,product_name,product_code,product_status"
                                + " FROM public.product WHERE product_id=? ON CONFLICT(product_id)"
                                + " DO NOTHING")) {
                statement.setObject(1, legacy.id());
                assertThat(statement.executeUpdate()).isZero();
            }
            connection.commit();
        }
        try (var inspector = owner()) {
            String before =
                    rowText(
                            inspector,
                            "SELECT to_jsonb(p)::text FROM public.product p WHERE product_id=?",
                            legacy.id());
            assertThatThrownBy(
                            () ->
                                    commands.create(
                                                    legacy.tenant(),
                                                    legacy.root(),
                                                    legacy.id(),
                                                    categoryOf(legacy),
                                                    java.time.Instant.now().plusSeconds(300))
                                            .block(Duration.ofSeconds(8)))
                    .isInstanceOfSatisfying(
                            org.springframework.web.server.ResponseStatusException.class,
                            error ->
                                    assertThat(error.getStatusCode())
                                            .isEqualTo(
                                                    org.springframework.http.HttpStatus.CONFLICT));
            assertThat(count(inspector, "product_definition_root", legacy.root())).isZero();
            assertThat(
                            rowText(
                                    inspector,
                                    "SELECT to_jsonb(p)::text FROM public.product p WHERE"
                                            + " product_id=?",
                                    legacy.id()))
                    .isEqualTo(before);
            assertThat(
                            rowText(
                                    inspector,
                                    "SELECT native_root_id::text FROM"
                                        + " firefly_product_definition_private.product_birth WHERE"
                                        + " product_id=?",
                                    legacy.id()))
                    .isNull();
        }
    }

    @Test
    void nativeProductCategoryChangeCannotReuseItsOriginalAncestorProof() throws Exception {
        var admitted = admittedDefinition(category());
        UUID replacement = category();
        try (var connection = legacy()) {
            connection.setAutoCommit(false);
            String before =
                    rowText(
                            connection,
                            "SELECT to_jsonb(p)::text FROM public.product p WHERE product_id=?",
                            admitted.product().id());
            try {
                assertThatThrownBy(
                                () ->
                                        execute(
                                                connection,
                                                "UPDATE public.product SET product_category_id=?"
                                                        + " WHERE product_id=?",
                                                replacement,
                                                admitted.product().id()))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                error -> assertThat(error.getSQLState()).isEqualTo("23514"));
                connection.rollback();
                assertThat(
                                rowText(
                                        connection,
                                        "SELECT to_jsonb(p)::text FROM public.product p WHERE"
                                                + " product_id=?",
                                        admitted.product().id()))
                        .isEqualTo(before);
            } finally {
                connection.rollback();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void categoryReparentOrCascadeWaitingForNativeCreateRechecksProtectedAncestors(boolean delete)
            throws Exception {
        var ancestor = UUID.randomUUID();
        var leaf = UUID.randomUUID();
        try (var connection = owner()) {
            categories(connection, ancestor, leaf);
        }
        var product = new Product(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var parent =
                commands.holdCreate(
                        product.tenant(),
                        product.root(),
                        product.id(),
                        leaf,
                        ProductDefinitionCommandDatabaseFixture.Stage.AFTER_EVIDENCE);
        var committed = parent.result().toFuture();
        try (var legacy = owner();
                var inspector = owner()) {
            legacy.setAutoCommit(false);
            assertThat(parent.gate().reached().block(Duration.ofSeconds(5))).isNotNull();
            String sql =
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
                                } catch (SQLException error) {
                                    legacy.rollback();
                                    return error;
                                }
                            });
            try {
                assertThat(
                                waitForLock(
                                        inspector,
                                        future,
                                        pid,
                                        delete ? "DELETE FROM" : "UPDATE public.product_category"))
                        .as("legacy mutation waits on actual command category observation")
                        .isTrue();
                parent.gate().release();
                assertThat(committed.get(5, TimeUnit.SECONDS)).isNotNull();
                var rejected = future.get(5, TimeUnit.SECONDS);
                assertThat((Object) rejected).isNotNull();
                assertThat(rejected.getSQLState()).isEqualTo("23514");
                assertThat(count(inspector, "product_definition_root", product.root()))
                        .isEqualTo(1);
                try (var query =
                        inspector.prepareStatement(
                                "SELECT parent_category_id FROM public.product_category WHERE"
                                        + " product_category_id=?")) {
                    query.setObject(1, leaf);
                    try (var rows = query.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getObject(1, UUID.class)).isEqualTo(ancestor);
                    }
                }
            } finally {
                parent.gate().release();
                legacy.rollback();
                if (!committed.isDone()) committed.cancel(true);
                try {
                    future.get(5, TimeUnit.SECONDS);
                } finally {
                    executor.shutdownNow();
                    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                }
            }
        } finally {
            parent.gate().release();
            if (!committed.isDone()) committed.cancel(true);
        }
    }

    @Test
    void ancestorCascadeWinningBeforeNativeCreateLeavesNoPartialProductOrRoot() throws Exception {
        var ancestor = UUID.randomUUID();
        var leaf = UUID.randomUUID();
        var product = new Product(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        try (var connection = owner()) {
            categories(connection, ancestor, leaf);
        }
        try (var legacy = owner();
                var inspector = owner()) {
            legacy.setAutoCommit(false);
            execute(
                    legacy,
                    "DELETE FROM public.product_category WHERE product_category_id=?",
                    ancestor);
            var completed =
                    commands.create(
                                    product.tenant(),
                                    product.root(),
                                    product.id(),
                                    leaf,
                                    java.time.Instant.now().plusSeconds(300))
                            .toFuture();
            try {
                assertThat(waitForCommandLock(inspector, completed))
                        .as(
                                "admission waits before Product FK SQL when ancestor cascade owns"
                                        + " first lock")
                        .isTrue();
                legacy.commit();
                assertThatThrownBy(() -> completed.get(5, TimeUnit.SECONDS))
                        .isInstanceOfSatisfying(
                                ExecutionException.class,
                                error ->
                                        assertThat(error.getCause())
                                                .isInstanceOfSatisfying(
                                                        org.springframework.web.server
                                                                .ResponseStatusException.class,
                                                        status ->
                                                                assertThat(status.getStatusCode())
                                                                        .isEqualTo(
                                                                                org.springframework
                                                                                        .http
                                                                                        .HttpStatus
                                                                                        .CONFLICT)));
                assertThat(count(inspector, "product_definition_root", product.root())).isZero();
                assertThat(productCount(inspector, product.id())).isZero();
            } finally {
                legacy.rollback();
                if (!completed.isDone()) completed.cancel(true);
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
        var managed = new Product(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var parent =
                commands.holdCreate(
                        managed.tenant(),
                        managed.root(),
                        managed.id(),
                        category(),
                        ProductDefinitionCommandDatabaseFixture.Stage.AFTER_EVIDENCE);
        var parentCommit = parent.result().toFuture();
        try (var gate = owner();
                var observer = owner();
                var ordinarySetup = legacy();
                var child =
                        DriverManager.getConnection(
                                postgres.getJdbcUrl(), "definition_runtime", RUNTIME_PASSWORD)) {
            var ordinary = product(ordinarySetup);
            var other = product(ordinarySetup);
            if (update) insertLegacyChild(ordinarySetup, kind, row, ordinary.id(), other.id());
            assertThat(parent.gate().reached().block(Duration.ofSeconds(5))).isNotNull();
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
                parent.gate().release();
                assertThat(parentCommit.get(5, TimeUnit.SECONDS)).isNotNull();
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
                parent.gate().release();
                if (!parentCommit.isDone()) parentCommit.cancel(true);
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
            parent.gate().release();
            if (!parentCommit.isDone()) parentCommit.cancel(true);
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

    private static boolean waitForCommandLock(Connection inspector, Future<?> completed)
            throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!completed.isDone() && System.nanoTime() < deadline) {
            try (var query =
                            inspector.prepareStatement(
                                    "SELECT pid,query FROM pg_catalog.pg_stat_activity WHERE"
                                            + " usename='definition_commands' AND"
                                            + " wait_event_type='Lock' AND state='active' LIMIT 2");
                    var rows = query.executeQuery()) {
                if (rows.next()) {
                    assertThat(rows.getInt(1)).isPositive();
                    assertThat(rows.getString(2)).contains("begin_product_definition_command");
                    assertThat(rows.next()).isFalse();
                    return true;
                }
            }
            Thread.sleep(10);
        }
        return false;
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

    private record Admitted(Product product, Seed seed) {}

    private static UUID category() throws Exception {
        UUID id = UUID.randomUUID();
        try (var connection = owner()) {
            execute(
                    connection,
                    "INSERT INTO public.product_category(product_category_id,category_name,level)"
                            + " VALUES(?,?,0)",
                    id,
                    "Owned ordinary account category");
        }
        return id;
    }

    private static int ownedRootCount(UUID root) throws Exception {
        try (var inspector = owner()) {
            return count(inspector, "product_definition_root", root);
        }
    }

    private static String rowText(Connection connection, String query, UUID key) throws Exception {
        try (var statement = connection.prepareStatement(query)) {
            statement.setObject(1, key);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                String value = rows.getString(1);
                assertThat(rows.next()).isFalse();
                return value;
            }
        }
    }

    private static UUID categoryOf(Product product) throws Exception {
        try (var connection = owner();
                var statement =
                        connection.prepareStatement(
                                "SELECT product_category_id FROM public.product WHERE"
                                        + " product_id=?")) {
            statement.setObject(1, product.id());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getObject(1, UUID.class);
            }
        }
    }

    private static int productCount(Connection connection, UUID product) throws Exception {
        try (var statement =
                connection.prepareStatement(
                        "SELECT count(*) FROM public.product WHERE product_id=?")) {
            statement.setObject(1, product);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getInt(1);
            }
        }
    }

    private static Admitted admittedDefinition(UUID category) {
        var product = new Product(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var receipt =
                commands.create(
                                product.tenant(),
                                product.root(),
                                product.id(),
                                category,
                                java.time.Instant.now().plusSeconds(300))
                        .block(Duration.ofSeconds(8));
        var cost =
                commands.currentCost(product.tenant(), product.root()).block(Duration.ofSeconds(8));
        assertThat(receipt).isNotNull();
        assertThat(cost).isNotNull();
        return new Admitted(
                product, new Seed(product.root(), receipt.revisionId(), cost.costRevisionId()));
    }

    private record Product(UUID tenant, UUID id, UUID root) {}

    private record Seed(UUID root, UUID revision, UUID cost) {}

    private static Product product(Connection c) throws Exception {
        var category = UUID.randomUUID();
        var product = new Product(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        execute(
                c,
                "INSERT INTO public.product_category(product_category_id,category_name)"
                        + " VALUES(?,?)",
                category,
                "Synthetic definition fixture");
        execute(
                c,
                "INSERT INTO"
                    + " public.product(product_id,tenant_id,product_category_id,product_type,product_name,product_code,product_status)"
                    + " VALUES(?,?,?,'FINANCIAL',?,?,'DRAFT')",
                product.id(),
                product.tenant(),
                category,
                "Synthetic ordinary legacy product",
                ProfileFixtures.load("account").at("/common/code").asText());
        return product;
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

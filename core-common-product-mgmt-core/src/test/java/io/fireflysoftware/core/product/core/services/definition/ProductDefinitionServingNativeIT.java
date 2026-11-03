package io.fireflysoftware.core.product.core.services.definition;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionReadAuthorizationImpl;
import io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionServingServiceImpl;
import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.*;
import io.fireflysoftware.core.product.models.repositories.definition.*;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import reactor.core.publisher.Mono;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

class ProductDefinitionServingNativeIT {
    private static final String ISSUER = "https://synthetic-product.example.test";
    private static String jdbcUrl, ownerUser, ownerPassword, runtimeUser, runtimePassword;
    private static ConnectionPool pool;
    private static R2dbcEntityTemplate operations;
    private static ProductDefinitionRootRepository roots;
    private static ProductDefinitionRevisionRepository revisions;
    private static ProductDefinitionReadContextRepository context;
    private static TransactionalOperator transaction;
    private static Seed first, second;

    private record Seed(UUID tenant, UUID root, UUID product, UUID revision, String digest) {}

    @BeforeAll
    static void prepareOwnedDatabase() throws Exception {
        jdbcUrl = required("PRODUCT_SERVING_JDBC_URL");
        ownerUser = required("PRODUCT_SERVING_OWNER_USERNAME");
        ownerPassword = required("PRODUCT_SERVING_OWNER_PASSWORD");
        runtimeUser = required("PRODUCT_SERVING_RUNTIME_USERNAME");
        runtimePassword = required("PRODUCT_SERVING_RUNTIME_PASSWORD");
        Flyway.configure()
                .dataSource(jdbcUrl, ownerUser, ownerPassword)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        try (var owner = DriverManager.getConnection(jdbcUrl, ownerUser, ownerPassword)) {
            first = seed(owner);
            second = seed(owner);
        }
        var options =
                ConnectionFactoryOptions.parse("r2dbc:" + jdbcUrl.substring("jdbc:".length()))
                        .mutate()
                        .option(ConnectionFactoryOptions.USER, runtimeUser)
                        .option(ConnectionFactoryOptions.PASSWORD, runtimePassword)
                        .build();
        pool =
                new ConnectionPool(
                        ConnectionPoolConfiguration.builder(ConnectionFactories.get(options))
                                .initialSize(0)
                                .maxSize(1)
                                .build());
        operations = new R2dbcEntityTemplate(pool);
        var repositories = new R2dbcRepositoryFactory(operations);
        roots = repositories.getRepository(ProductDefinitionRootRepository.class);
        revisions = repositories.getRepository(ProductDefinitionRevisionRepository.class);
        context = repositories.getRepository(ProductDefinitionReadContextRepository.class);
        var manager = new R2dbcTransactionManager(pool);
        manager.setEnforceReadOnly(true);
        var definition = new DefaultTransactionDefinition();
        definition.setReadOnly(true);
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        definition.setTimeout(10);
        transaction = TransactionalOperator.create(manager, definition);
    }

    @AfterAll
    static void closePool() {
        if (pool != null) pool.disposeLater().block(Duration.ofSeconds(10));
    }

    @Test
    void actualSpringDataServiceReadsCanonicalRootAndExactRevision() {
        var factory = new R2dbcRepositoryFactory(operations);
        var principal =
                SecurityPrincipal.builder()
                        .issuer(ISSUER)
                        .subject("synthetic-read-actor")
                        .tenantId(first.tenant().toString())
                        .scopes(Set.of("product:definitions:read"))
                        .claims(Map.of("exp", Instant.now().plusSeconds(120)))
                        .build();
        var auth =
                new ProductDefinitionReadAuthorizationImpl(
                        () -> Mono.just(principal),
                        (p, a, r, c) -> Mono.just(Decision.permit()),
                        Clock.systemUTC(),
                        Set.of(ISSUER));
        var service =
                new ProductDefinitionServingServiceImpl(
                        auth,
                        roots,
                        revisions,
                        factory.getRepository(ProductDefinitionPublicationRepository.class),
                        factory.getRepository(ProductDefinitionPublicationControlRepository.class),
                        context,
                        factory.getRepository(ProductDefinitionVersionIdentityRepository.class),
                        transaction);
        var root = service.readRoot(first.tenant(), first.root()).block(Duration.ofSeconds(10));
        assertThat(root.publicHead().revisionId()).isEqualTo(first.revision());
        assertThat(root.publicHead().definition().publicDigest()).isEqualTo(first.digest());
        assertThat(root.publicHead().definition().definition().terms().interest())
                .isEqualTo("NONE");
        assertThat(root.operationalAdmission()).isEqualTo(OperationalAdmission.NOT_ADMITTED);
        var revision =
                service.readRevision(first.tenant(), first.root(), first.revision())
                        .block(Duration.ofSeconds(10));
        assertThat(revision.revision().revisionId()).isEqualTo(first.revision());
        assertThat(revision.observation().databaseRecordedAt())
                .isBefore(revision.observation().admissionValidUntil());
    }

    @Test
    void absentContextAndCommittedContextDoNotPersistOnReusedPoolConnection() {
        assertThat(roots.findByTenantIdAndRootId(first.tenant(), first.root()).blockOptional())
                .isEmpty();
        Integer before = backend();
        var row =
                read(first, stamp -> roots.findByTenantIdAndRootId(first.tenant(), first.root()))
                        .block();
        assertThat(row.rootId()).isEqualTo(first.root());
        assertThat(roots.findByTenantIdAndRootId(first.tenant(), first.root()).blockOptional())
                .isEmpty();
        assertThat(backend()).isEqualTo(before);
    }

    @Test
    void rollbackClearsLocalContextOnTheSamePhysicalConnection() {
        Integer before = backend();
        assertThatThrownBy(
                        () ->
                                read(
                                                first,
                                                stamp ->
                                                        Mono.error(
                                                                new IllegalStateException(
                                                                        "synthetic rollback"
                                                                                + " control")))
                                        .block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("synthetic rollback control");
        assertThat(roots.findByTenantIdAndRootId(first.tenant(), first.root()).blockOptional())
                .isEmpty();
        assertThat(backend()).isEqualTo(before);
    }

    @Test
    void cancellationRollsBackBeforeThePooledConnectionIsReused() throws Exception {
        var opened = new java.util.concurrent.CountDownLatch(1);
        Integer before = backend();
        var pending =
                read(
                                first,
                                stamp ->
                                        Mono.<Object>never()
                                                .doOnSubscribe(subscription -> opened.countDown()))
                        .subscribe();
        try {
            assertThat(opened.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally {
            pending.dispose();
        }
        assertThat(
                        roots.findByTenantIdAndRootId(first.tenant(), first.root())
                                .blockOptional(Duration.ofSeconds(10)))
                .isEmpty();
        assertThat(backend()).isEqualTo(before);
    }

    @Test
    void scopedDerivedReadsDoNotReturnDifferentTenantRootOrRevision() {
        assertThat(
                        read(
                                        first,
                                        stamp ->
                                                roots.findByTenantIdAndRootId(
                                                        second.tenant(), second.root()))
                                .blockOptional())
                .isEmpty();
        assertThat(
                        read(
                                        first,
                                        stamp ->
                                                roots.findByTenantIdAndRootId(
                                                        first.tenant(), second.root()))
                                .blockOptional())
                .isEmpty();
        assertThat(
                        read(
                                        first,
                                        stamp ->
                                                revisions.findByTenantIdAndRootIdAndRevisionId(
                                                        first.tenant(),
                                                        first.root(),
                                                        second.revision()))
                                .blockOptional())
                .isEmpty();
        assertThat(
                        read(
                                        second,
                                        stamp ->
                                                roots.findByTenantIdAndRootId(
                                                        second.tenant(), second.root()))
                                .block()
                                .rootId())
                .isEqualTo(second.root());
    }

    @Test
    void databaseClockClampAndCompletionLivenessUseFiniteActualTime() {
        long actorUntil = Instant.now().plusSeconds(120).toEpochMilli();
        var stamp =
                transaction
                        .execute(
                                status ->
                                        context.open(
                                                first.tenant(),
                                                first.root(),
                                                actorUntil,
                                                actorUntil))
                        .single()
                        .block();
        assertThat(stamp.validUntilEpochMillis() - stamp.observedAtEpochMillis())
                .isBetween(1L, 30000L);
        assertThat(stamp.validUntilEpochMillis()).isLessThanOrEqualTo(actorUntil);
        long shortUntil = Instant.now().plusMillis(500).toEpochMilli();
        Boolean live =
                transaction
                        .execute(
                                status ->
                                        context.open(
                                                        first.tenant(),
                                                        first.root(),
                                                        actorUntil,
                                                        shortUntil)
                                                .flatMap(
                                                        value ->
                                                                Mono.delay(Duration.ofMillis(600))
                                                                        .then(
                                                                                context.isCurrent(
                                                                                        first
                                                                                                .tenant(),
                                                                                        first
                                                                                                .root()))))
                        .single()
                        .block();
        assertThat(live).isFalse();
        assertThat(roots.findByTenantIdAndRootId(first.tenant(), first.root()).blockOptional())
                .isEmpty();
    }

    @Test
    void actualReadTransactionIsRepeatableReadAndDatabaseEnforcedReadOnly() {
        var flags =
                read(
                                first,
                                stamp ->
                                        operations
                                                .getDatabaseClient()
                                                .sql(
                                                        "SELECT"
                                                            + " current_setting('transaction_isolation')"
                                                            + " AS isolation,"
                                                            + " current_setting('transaction_read_only')"
                                                            + " AS readonly")
                                                .map(
                                                        (row, metadata) ->
                                                                List.of(
                                                                        row.get(
                                                                                "isolation",
                                                                                String.class),
                                                                        row.get(
                                                                                "readonly",
                                                                                String.class)))
                                                .one())
                        .block();
        assertThat(flags).containsExactly("repeatable read", "on");
    }

    @Test
    void runtimeIsNonownerAndServingGrantsRemainSelectOnly() throws Exception {
        try (var runtime = DriverManager.getConnection(jdbcUrl, runtimeUser, runtimePassword);
                var statement = runtime.createStatement();
                var rows =
                        statement.executeQuery(
                                "SELECT current_user = session_user AND NOT rolsuper AND NOT"
                                    + " rolbypassrls AND"
                                    + " pg_has_role(current_user,'firefly_product_definition_serving','USAGE')"
                                    + " AND NOT"
                                    + " pg_has_role(current_user,'firefly_product_definition_guard','MEMBER')"
                                    + " AND NOT"
                                    + " has_database_privilege(current_user,current_database(),'CREATE,TEMPORARY')"
                                    + " AND NOT"
                                    + " has_schema_privilege(current_user,'public','CREATE') AND"
                                    + " NOT has_schema_privilege(current_user,'firefly_product_definition_private','USAGE,CREATE')"
                                    + " FROM pg_roles WHERE rolname=current_user")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBoolean(1)).isTrue();
        }
        try (var runtime = DriverManager.getConnection(jdbcUrl, runtimeUser, runtimePassword);
                var statement = runtime.createStatement();
                var rows =
                        statement.executeQuery(
                                "SELECT bool_and(c.relrowsecurity AND c.relforcerowsecurity AND NOT"
                                    + " pg_has_role(current_user,c.relowner,'MEMBER') AND NOT"
                                    + " has_table_privilege(current_user,c.oid,'INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN')"
                                    + " AND NOT"
                                    + " has_any_column_privilege(current_user,c.oid,'INSERT,UPDATE,REFERENCES'))"
                                    + " FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace"
                                    + " WHERE n.nspname='public' AND c.relkind='r' AND c.relname"
                                    + " LIKE 'product_definition_%'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBoolean(1)).isTrue();
        }
    }

    private static <T> Mono<T> read(
            Seed seed, Function<ProductDefinitionReadContextRepository.ReadStamp, Mono<T>> action) {
        long until = Instant.now().plusSeconds(20).toEpochMilli();
        return transaction
                .execute(
                        status ->
                                context.open(seed.tenant(), seed.root(), until, until)
                                        .flatMap(action))
                .singleOrEmpty();
    }

    private static Integer backend() {
        return operations
                .getDatabaseClient()
                .sql("SELECT pg_backend_pid() AS pid")
                .map((row, metadata) -> row.get("pid", Integer.class))
                .one()
                .block();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank())
            throw new IllegalStateException(
                    "Explicit owned native fixture environment required: " + name);
        return value;
    }

    private static Seed seed(Connection owner) throws Exception {
        var tenant = UUID.randomUUID();
        var root = UUID.randomUUID();
        var product = UUID.randomUUID();
        var revision = UUID.randomUUID();
        var cost = UUID.randomUUID();
        var category = UUID.randomUUID();
        String raw = ProfileFixtures.load("account").toString();
        var terms = DefinitionProfiles.canonical(raw);
        var costs = CostRules.canonical(CostRulesTest.costs("account"), raw);
        owner.setAutoCommit(false);
        try {
            execute(
                    owner,
                    "INSERT INTO public.product_category(product_category_id,category_name)"
                            + " VALUES(?,?)",
                    category,
                    "Synthetic serving fixture");
            try (var statement =
                    owner.prepareStatement(
                            "SELECT"
                                + " set_config('firefly.product_definition.purpose','CREATE',true),set_config('firefly.product_definition.tenant_id',?,true),set_config('firefly.product_definition.root_id',?,true),set_config('firefly.product_definition.product_id',?,true)")) {
                statement.setString(1, tenant.toString());
                statement.setString(2, root.toString());
                statement.setString(3, product.toString());
                statement.execute();
            }
            execute(
                    owner,
                    "INSERT INTO"
                        + " public.product(product_id,tenant_id,product_category_id,product_type,product_name,product_code,product_status)"
                        + " VALUES(?,?,?,'FINANCIAL',?,?,'DRAFT')",
                    product,
                    tenant,
                    category,
                    "Synthetic current account",
                    "ACCOUNT_TARIFF_V1_DEMO");
            execute(
                    owner,
                    "INSERT INTO"
                        + " public.product_definition_root(root_id,tenant_id,product_id,product_code,business_area,profile_code,profile_version,phase,public_state_version,public_head)"
                        + " VALUES(?,?,?,?,'BANKING','ACCOUNT_TARIFF_V1',1,'DRAFT',0,?)",
                    root,
                    tenant,
                    product,
                    "ACCOUNT_TARIFF_V1_DEMO",
                    revision);
            execute(
                    owner,
                    "INSERT INTO"
                        + " public.product_definition_revision(revision_id,tenant_id,root_id,canonical_definition,public_digest,encoding_version,actor_issuer,actor_id)"
                        + " VALUES(?,?,?,?,?,?,?,?)",
                    revision,
                    tenant,
                    root,
                    terms.json(),
                    terms.digest(),
                    DefinitionCanonical.ENCODING,
                    ISSUER,
                    "synthetic-maker");
            execute(
                    owner,
                    "INSERT INTO"
                        + " public.product_definition_cost_revision(cost_revision_id,tenant_id,root_id,canonical_cost,cost_digest,encoding_version,actor_issuer,actor_id)"
                        + " VALUES(?,?,?,?,?,?,?,?)",
                    cost,
                    tenant,
                    root,
                    costs.json(),
                    costs.digest(),
                    DefinitionCanonical.ENCODING,
                    ISSUER,
                    "synthetic-maker");
            execute(
                    owner,
                    "INSERT INTO"
                        + " public.product_definition_control(root_id,tenant_id,cost_head,cost_state_version)"
                        + " VALUES(?,?,?,0)",
                    root,
                    tenant,
                    cost);
            owner.commit();
            return new Seed(tenant, root, product, revision, terms.digest());
        } finally {
            owner.rollback();
            owner.setAutoCommit(true);
        }
    }

    private static void execute(Connection owner, String sql, Object... values) throws Exception {
        try (var statement = owner.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++)
                statement.setObject(index + 1, values[index]);
            statement.executeUpdate();
        }
    }
}

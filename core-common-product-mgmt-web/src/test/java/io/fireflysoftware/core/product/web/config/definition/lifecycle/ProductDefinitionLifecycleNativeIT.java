package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.*;
import io.fireflysoftware.core.product.models.config.R2dbcConfiguration;
import io.fireflysoftware.core.product.models.repositories.definition.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandPersistence;
import io.fireflysoftware.core.product.web.config.definition.ProductDefinitionServingConfiguration;
import io.r2dbc.spi.ConnectionFactory;

import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.rs.ResourceServerProperties;
import org.fireflyframework.security.spi.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Owned loopback PostgreSQL fixture. Controlled principal/PDP ports prove persistence only. */
class ProductDefinitionLifecycleNativeIT {
    private static final Duration WAIT = Duration.ofSeconds(8);
    private static final String ISSUER = "https://native-staff.example.test";
    private static Map<String, String> settings;
    private static UUID category;
    private static Path source;
    private final AtomicReference<SecurityPrincipal> principal = new AtomicReference<>();
    private final AtomicInteger decisions = new AtomicInteger();
    private final AtomicBoolean permit = new AtomicBoolean(true);

    record Scenario(UUID tenant, UUID root, UUID product) {}

    @BeforeAll
    static void ownedInputOnly() throws Exception {
        if (!"true".equals(System.getenv("PRODUCT_LIFECYCLE_NATIVE_EXECUTE")))
            throw new IllegalStateException("Explicit owned native runner required");
        Path file =
                Path.of(Objects.requireNonNull(System.getenv("PRODUCT_LIFECYCLE_NATIVE_CONFIG")))
                        .toRealPath();
        var parsed = DefinitionCanonical.parse(Files.readString(file));
        var values = new HashMap<String, String>();
        parsed.properties().forEach(e -> values.put(e.getKey(), e.getValue().asText()));
        settings = Map.copyOf(values);
        if (!"127.0.0.1".equals(settings.get("host"))
                || !settings.get("database").startsWith("pc_lifecycle_")
                || !settings.get("ownerTag").startsWith("pc-lifecycle-"))
            throw new IllegalStateException("Owned loopback database required");
        int port = Integer.parseInt(settings.get("port"));
        if (port == 18080 || port == 9001 || port < 1024)
            throw new IllegalStateException("Owned ephemeral native port required");
        source = Path.of(settings.get("sourceRoot")).toRealPath();
        category = UUID.fromString(settings.get("categoryId"));
        try (var connection = owner();
                var statement = connection.createStatement();
                var rows =
                        statement.executeQuery(
                                "SELECT current_setting('cluster_name'),current_database()")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo(settings.get("ownerTag"));
            assertThat(rows.getString(2)).isEqualTo(settings.get("database"));
        }
    }

    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:"
                        + settings.get("port")
                        + "/"
                        + settings.get("database"),
                settings.get("ownerUser"),
                settings.get("ownerPassword"));
    }

    private SecurityPrincipal actor(UUID tenant, String subject, Instant expiry) {
        return SecurityPrincipal.builder()
                .issuer(ISSUER)
                .subject(subject)
                .tenantId(tenant.toString())
                .scopes(
                        Set.of(
                                "product:definitions:read",
                                "product:definitions:write",
                                "product:definitions:cost:read",
                                "product:definitions:cost:write",
                                "product:definitions:review",
                                "product:definitions:publish",
                                "product:definitions:withdraw"))
                .claims(Map.of("exp", expiry))
                .build();
    }

    private Mono<Decision> policy(
            SecurityPrincipal actor, String action, String resource, Map<String, Object> context) {
        return TransactionSynchronizationManager.forCurrentTransaction()
                .map(TransactionSynchronizationManager::isActualTransactionActive)
                .onErrorResume(NoTransactionException.class, e -> Mono.just(false))
                .map(
                        active -> {
                            assertThat(active).isFalse();
                            decisions.incrementAndGet();
                            return permit.get()
                                            && Set.of("maker", "cost-editor", "checker")
                                                    .contains(actor.subject())
                                    ? Decision.permit()
                                    : Decision.deny("controlled fixture denial");
                        });
    }

    private ApplicationContextRunner app(boolean commands) {
        var runner =
                new ApplicationContextRunner()
                        .withConfiguration(
                                AutoConfigurations.of(
                                        org.springframework.boot.r2dbc.autoconfigure
                                                .R2dbcAutoConfiguration.class,
                                        org.springframework.boot.r2dbc.autoconfigure
                                                .R2dbcTransactionManagerAutoConfiguration.class,
                                        org.springframework.boot.data.r2dbc.autoconfigure
                                                .DataR2dbcAutoConfiguration.class,
                                        org.fireflyframework.core.config
                                                .R2dbcTransactionAutoConfiguration.class))
                        .withUserConfiguration(
                                R2dbcConfiguration.class,
                                ReadRepositories.class,
                                ProductDefinitionCommandConfiguration.class,
                                ProductDefinitionLifecycleConfiguration.class,
                                ProductDefinitionServingConfiguration.class)
                        .withBean(
                                SecurityContextPort.class,
                                () -> () -> Mono.justOrEmpty(principal.get()))
                        .withBean(
                                "productDefinitionCommandPolicy",
                                PolicyDecisionPort.class,
                                () -> this::policy)
                        .withBean(
                                "productDefinitionReadPolicy",
                                PolicyDecisionPort.class,
                                () -> this::policy)
                        .withPropertyValues(
                                "DB_HOST=127.0.0.1",
                                "DB_PORT=" + settings.get("port"),
                                "DB_NAME=" + settings.get("database"),
                                "DB_USERNAME=" + settings.get("readerUser"),
                                "DB_PASSWORD=" + settings.get("readerPassword"),
                                "DB_SSL_MODE=disable",
                                "spring.r2dbc.url=r2dbc:postgresql://127.0.0.1:1/unused_boot_target",
                                "spring.r2dbc.pool.initial-size=0",
                                "firefly.security.resource-server.issuer=" + ISSUER,
                                "product.definitions.commands.policy-profile="
                                        + ProductDefinitionCommandAuthorization.POLICY_PROFILE);
        if (commands)
            runner =
                    runner.withPropertyValues(
                            "product.definitions.commands.enabled=true",
                            "product.definitions.commands.username=" + settings.get("commandUser"),
                            "product.definitions.commands.password="
                                    + settings.get("commandPassword"));
        return runner;
    }

    private Scenario scenario() {
        var value = new Scenario(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        principal.set(actor(value.tenant(), "maker", Instant.now().plusSeconds(300)));
        return value;
    }

    private String body(Map<String, Object> value) {
        return DefinitionCanonical.encode(DefinitionCanonical.tree(value));
    }

    private Map<String, Object> create(Scenario scenario) throws Exception {
        var definition =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        DefinitionCanonical.parse(
                                Files.readString(
                                        source.resolve(
                                                "core-common-product-mgmt-core/src/test/resources/definition/v1/account.json")));
        ((com.fasterxml.jackson.databind.node.ObjectNode) definition.get("common"))
                .put(
                        "code",
                        "ACCOUNT_NATIVE_"
                                + scenario.root()
                                        .toString()
                                        .replace("-", "")
                                        .substring(0, 12)
                                        .toUpperCase(Locale.ROOT));
        return Map.of(
                "operationId",
                UUID.randomUUID(),
                "productId",
                scenario.product(),
                "categoryId",
                category,
                "definition",
                definition,
                "cost",
                Map.of(
                        "schemaVersion",
                        1,
                        "businessArea",
                        "BANKING",
                        "profileCode",
                        "ACCOUNT_TARIFF_V1",
                        "mode",
                        "NONE",
                        "lines",
                        List.of()));
    }

    private Receipt execute(
            ProductDefinitionLifecycleService service,
            Scenario scenario,
            ProductDefinitionCommand command,
            Map<String, Object> value) {
        return service.execute(scenario.tenant(), scenario.root(), command, body(value))
                .block(WAIT);
    }

    private PublicReceipt create(ProductDefinitionLifecycleService service, Scenario scenario)
            throws Exception {
        return (PublicReceipt)
                execute(service, scenario, ProductDefinitionCommand.CREATE_DRAFT, create(scenario));
    }

    private Map<String, Object> review(ProductDefinitionReviewSelectionView view) {
        var s = view.selection();
        return Map.of(
                "operationId",
                UUID.randomUUID(),
                "submissionId",
                s.submissionId(),
                "revisionId",
                s.revisionId(),
                "publicDigest",
                s.publicDigest(),
                "publicVersion",
                s.publicVersion(),
                "costRevisionId",
                s.costRevisionId(),
                "costDigest",
                s.costDigest(),
                "costVersion",
                s.costVersion(),
                "selectionFingerprint",
                view.selectionFingerprint());
    }

    private static long count(String table, UUID root) throws Exception {
        if (!Set.of(
                        "product_definition_root",
                        "product_definition_action",
                        "product_definition_publication",
                        "product_definition_submission")
                .contains(table)) throw new IllegalArgumentException();
        try (var c = owner();
                var s =
                        c.prepareStatement(
                                "SELECT count(*) FROM public." + table + " WHERE root_id=?")) {
            s.setObject(1, root);
            try (var r = s.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }

    private static void acceptedEvidence(Scenario scenario, Receipt receipt) throws Exception {
        try (var c = owner();
                var s =
                        c.prepareStatement(
                                "SELECT"
                                    + " c.state,c.session_login,c.recorded_at,a.created_at,count(w.witness_id),jsonb_array_length(c.expected_rows)"
                                    + " FROM firefly_product_definition_private.native_command c"
                                    + " JOIN public.product_definition_action a ON"
                                    + " a.action_id=c.action_id LEFT JOIN"
                                    + " firefly_product_definition_private.row_witness w ON"
                                    + " w.command_id=c.command_id WHERE c.tenant_id=? AND"
                                    + " c.root_id=? AND c.action_id=? AND c.mode='FRESH' GROUP BY"
                                    + " c.command_id,a.created_at")) {
            s.setObject(1, scenario.tenant());
            s.setObject(2, scenario.root());
            s.setObject(3, receipt.actionId());
            try (var r = s.executeQuery()) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString(1)).isEqualTo("FINISHED");
                assertThat(r.getString(2)).isEqualTo(settings.get("commandUser"));
                assertThat(r.getObject(3, java.time.OffsetDateTime.class).toInstant())
                        .isEqualTo(Instant.parse(receipt.recordedAt()));
                assertThat(r.getObject(4, java.time.OffsetDateTime.class).toInstant())
                        .isEqualTo(Instant.parse(receipt.recordedAt()));
                assertThat(r.getLong(5)).isEqualTo(r.getInt(6));
            }
        }
    }

    @Test
    void actualOwnerGraphKeepsReadIdentityAndExactCommandFactoryConverterAndTransaction() {
        scenario();
        app(true)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            var p = context.getBean(ProductDefinitionCommandPersistence.class);
                            var read = context.getBean(ConnectionFactory.class);
                            assertThat(p.operations().getDatabaseClient().getConnectionFactory())
                                    .isSameAs(p.connectionFactory());
                            assertThat(p.transactionManager().getConnectionFactory())
                                    .isSameAs(p.connectionFactory());
                            assertThat(p.operations().getConverter())
                                    .isSameAs(context.getBean(R2dbcConverter.class));
                            assertThat(
                                            context.getBean(R2dbcEntityOperations.class)
                                                    .getDatabaseClient()
                                                    .getConnectionFactory())
                                    .isSameAs(read);
                            assertThat(read).isNotSameAs(p.connectionFactory());
                            var probe =
                                    new R2dbcRepositoryFactory(p.operations())
                                            .getRepository(Probe.class);
                            assertThat(probe.runtimeAllowed().block(WAIT)).isTrue();
                        });
    }

    @Test
    void actualMappedCreateCommitsOwnerStampedRowsAndDeferredEvidence() throws Exception {
        var s = scenario();
        app(true)
                .run(
                        context -> {
                            var receipt =
                                    create(
                                            context.getBean(
                                                    ProductDefinitionLifecycleService.class),
                                            s);
                            assertThat(receipt.operationalAdmission()).isEqualTo("NOT_ADMITTED");
                            acceptedEvidence(s, receipt);
                            assertThat(count("product_definition_root", s.root())).isEqualTo(1);
                            var view =
                                    context.getBean(ProductDefinitionCostReadService.class)
                                            .readCurrent(s.tenant(), s.root())
                                            .block(WAIT);
                            assertThat(view).isNotNull();
                            assertThat(view.costVersion()).isZero();
                            assertThat(view.phase()).isEqualTo("DRAFT");
                            try (var c = owner();
                                    var q =
                                            c.prepareStatement(
                                                    "SELECT"
                                                        + " p.product_status::text,p.date_created,p.date_updated,a.created_at"
                                                        + " FROM public.product p JOIN"
                                                        + " public.product_definition_root r ON"
                                                        + " r.product_id=p.product_id JOIN"
                                                        + " public.product_definition_action a ON"
                                                        + " a.root_id=r.root_id WHERE"
                                                        + " r.root_id=?")) {
                                q.setObject(1, s.root());
                                try (var rows = q.executeQuery()) {
                                    assertThat(rows.next()).isTrue();
                                    assertThat(rows.getString(1)).isEqualTo("DRAFT");
                                    var instant =
                                            rows.getObject(4, OffsetDateTime.class).toInstant();
                                    assertThat(rows.getObject(2, LocalDateTime.class))
                                            .isEqualTo(
                                                    LocalDateTime.ofInstant(
                                                            instant, ZoneOffset.UTC));
                                    assertThat(rows.getObject(3, LocalDateTime.class))
                                            .isEqualTo(
                                                    LocalDateTime.ofInstant(
                                                            instant, ZoneOffset.UTC));
                                }
                            }
                        });
    }

    @Test
    void fullMappedStaffJourneyDiscoversSelectionAndPublishesOnlyNotAdmittedMetadata()
            throws Exception {
        var s = scenario();
        app(true)
                .run(
                        context -> {
                            var service = context.getBean(ProductDefinitionLifecycleService.class);
                            var created = create(service, s);
                            var costService =
                                    context.getBean(ProductDefinitionCostReadService.class);
                            var before = costService.readCurrent(s.tenant(), s.root()).block(WAIT);
                            assertThat(before).isNotNull();
                            var definition = DefinitionCanonical.tree(before.publicDefinition());
                            ((com.fasterxml.jackson.databind.node.ObjectNode)
                                            definition.get("common"))
                                    .put("name", "Actual native configured current account");
                            var edited =
                                    (PublicReceipt)
                                            execute(
                                                    service,
                                                    s,
                                                    ProductDefinitionCommand.EDIT_PUBLIC,
                                                    Map.of(
                                                            "operationId",
                                                            UUID.randomUUID(),
                                                            "expectedPublicVersion",
                                                            created.publicVersion(),
                                                            "expectedPublicHead",
                                                            created.revisionId(),
                                                            "definition",
                                                            definition));
                            acceptedEvidence(s, edited);
                            principal.set(
                                    actor(
                                            s.tenant(),
                                            "cost-editor",
                                            Instant.now().plusSeconds(300)));
                            var editing = costService.readCurrent(s.tenant(), s.root()).block(WAIT);
                            var changed =
                                    (PrivateReceipt)
                                            execute(
                                                    service,
                                                    s,
                                                    ProductDefinitionCommand.EDIT_COST,
                                                    Map.of(
                                                            "operationId",
                                                            UUID.randomUUID(),
                                                            "expectedCostVersion",
                                                            editing.costVersion(),
                                                            "expectedCostHead",
                                                            editing.costRevisionId(),
                                                            "publicRevisionId",
                                                            editing.publicRevisionId(),
                                                            "publicDigest",
                                                            editing.publicDigest(),
                                                            "publicDefinition",
                                                            editing.publicDefinition(),
                                                            "cost",
                                                            editing.cost()));
                            acceptedEvidence(s, changed);
                            principal.set(
                                    actor(s.tenant(), "maker", Instant.now().plusSeconds(300)));
                            var submitted =
                                    (PublicReceipt)
                                            execute(
                                                    service,
                                                    s,
                                                    ProductDefinitionCommand.SUBMIT_PUBLIC,
                                                    Map.of(
                                                            "operationId",
                                                            UUID.randomUUID(),
                                                            "expectedPublicVersion",
                                                            edited.publicVersion(),
                                                            "expectedPublicHead",
                                                            edited.revisionId()));
                            acceptedEvidence(s, submitted);
                            principal.set(
                                    actor(s.tenant(), "checker", Instant.now().plusSeconds(300)));
                            var selectionService =
                                    context.getBean(ProductDefinitionReviewReadService.class);
                            var selection =
                                    selectionService
                                            .readCurrentSelection(s.tenant(), s.root())
                                            .block(WAIT);
                            assertThat(selection).isNotNull();
                            assertThat(selection.selection().publicVersion())
                                    .isEqualTo(submitted.publicVersion());
                            var denied = new LinkedHashMap<>(review(selection));
                            denied.put("reason", "Revise the internal cost justification");
                            var rejected =
                                    (PrivateReceipt)
                                            execute(
                                                    service,
                                                    s,
                                                    ProductDefinitionCommand.REJECT_SELECTION,
                                                    denied);
                            assertThat(rejected.decision()).isEqualTo("REJECTED");
                            acceptedEvidence(s, rejected);
                            var approved =
                                    (PrivateReceipt)
                                            execute(
                                                    service,
                                                    s,
                                                    ProductDefinitionCommand.APPROVE_SELECTION,
                                                    review(selection));
                            acceptedEvidence(s, approved);
                            var resumed =
                                    selectionService
                                            .readCurrentSelection(s.tenant(), s.root())
                                            .block(WAIT);
                            assertThat(resumed.activeReviewId()).isEqualTo(approved.reviewId());
                            var published =
                                    (PublicReceipt)
                                            execute(
                                                    service,
                                                    s,
                                                    ProductDefinitionCommand.PUBLISH,
                                                    Map.of(
                                                            "operationId",
                                                            UUID.randomUUID(),
                                                            "reviewId",
                                                            resumed.activeReviewId(),
                                                            "expectedPublicVersion",
                                                            resumed.selection().publicVersion(),
                                                            "expectedCostVersion",
                                                            resumed.selection().costVersion(),
                                                            "versionNumber",
                                                            17));
                            acceptedEvidence(s, published);
                            assertThat(published.operationalAdmission()).isEqualTo("NOT_ADMITTED");
                            var reader = context.getBean(ProductDefinitionServingService.class);
                            var publicView =
                                    reader.readPublication(
                                                    s.tenant(), s.root(), published.publicationId())
                                            .block(WAIT);
                            assertThat(publicView).isNotNull();
                            assertThat(publicView.publication().productVersionId())
                                    .isEqualTo(published.productVersionId());
                            assertThat(
                                            new com.fasterxml.jackson.databind.ObjectMapper()
                                                    .findAndRegisterModules()
                                                    .writeValueAsString(publicView))
                                    .doesNotContain(
                                            "canonicalCost",
                                            "costDigest",
                                            "costVersion",
                                            "selectionFingerprint");
                            var withdrawn =
                                    (PublicReceipt)
                                            execute(
                                                    service,
                                                    s,
                                                    ProductDefinitionCommand.WITHDRAW_PUBLICATION,
                                                    Map.of(
                                                            "operationId",
                                                            UUID.randomUUID(),
                                                            "publicationId",
                                                            published.publicationId(),
                                                            "expectedStateVersion",
                                                            0));
                            acceptedEvidence(s, withdrawn);
                            assertThat(withdrawn.publicationState()).isEqualTo("WITHDRAWN");
                            assertThat(
                                            reader.readPublication(
                                                            s.tenant(),
                                                            s.root(),
                                                            published.publicationId())
                                                    .block(WAIT)
                                                    .control()
                                                    .state())
                                    .isEqualTo("WITHDRAWN");
                        });
    }

    @Test
    void privateCostContextUsesRealReadOnlyRepeatableReadOnTheCommandFactory() throws Exception {
        var s = scenario();
        app(true)
                .run(
                        context -> {
                            create(context.getBean(ProductDefinitionLifecycleService.class), s);
                            var persistence =
                                    context.getBean(ProductDefinitionCommandPersistence.class);
                            var probe =
                                    new R2dbcRepositoryFactory(persistence.operations())
                                            .getRepository(Probe.class);
                            var until = Instant.now().plusSeconds(20).toEpochMilli();
                            var state =
                                    persistence
                                            .readTransactions()
                                            .execute(
                                                    tx ->
                                                            persistence
                                                                    .reviewReadNative()
                                                                    .openCost(
                                                                            s.tenant(),
                                                                            s.root(),
                                                                            ISSUER,
                                                                            "maker",
                                                                            until,
                                                                            until,
                                                                            ProductDefinitionCommandAuthorization
                                                                                    .POLICY_PROFILE)
                                                                    .flatMap(
                                                                            admission ->
                                                                                    probe.state()
                                                                                            .flatMap(
                                                                                                    observed ->
                                                                                                            persistence
                                                                                                                    .drafts()
                                                                                                                    .findByTenantIdAndRootId(
                                                                                                                            s
                                                                                                                                    .tenant(),
                                                                                                                            s
                                                                                                                                    .root())
                                                                                                                    .switchIfEmpty(
                                                                                                                            Mono
                                                                                                                                    .error(
                                                                                                                                            new AssertionError(
                                                                                                                                                    "Mapped"
                                                                                                                                                        + " root"
                                                                                                                                                        + " missing")))
                                                                                                                    .then(
                                                                                                                            persistence
                                                                                                                                    .costs()
                                                                                                                                    .findByTenantIdAndRootId(
                                                                                                                                            s
                                                                                                                                                    .tenant(),
                                                                                                                                            s
                                                                                                                                                    .root()))
                                                                                                                    .switchIfEmpty(
                                                                                                                            Mono
                                                                                                                                    .error(
                                                                                                                                            new AssertionError(
                                                                                                                                                    "Mapped"
                                                                                                                                                        + " cost"
                                                                                                                                                        + " control"
                                                                                                                                                        + " missing")))
                                                                                                                    .then(
                                                                                                                            persistence
                                                                                                                                    .reviewReadNative()
                                                                                                                                    .isCostCurrent(
                                                                                                                                            admission
                                                                                                                                                    .contextId()))
                                                                                                                    .doOnNext(
                                                                                                                            live ->
                                                                                                                                    assertThat(
                                                                                                                                                    live)
                                                                                                                                            .isTrue())
                                                                                                                    .thenReturn(
                                                                                                                            observed))))
                                            .single()
                                            .block(WAIT);
                            assertThat(state).isNotNull();
                            assertThat(state.readOnly()).isTrue();
                            assertThat(state.isolation()).isEqualTo("repeatable read");
                            assertThat(state.login()).isEqualTo(settings.get("commandUser"));
                            assertThat(state.backendPid()).isPositive();
                        });
    }

    @Test
    void currentSelectionDiscoveryUsesRealReadOnlyContextWithoutAnInputSubmissionId()
            throws Exception {
        var s = scenario();
        app(true)
                .run(
                        context -> {
                            var service = context.getBean(ProductDefinitionLifecycleService.class);
                            var created = create(service, s);
                            var submitted =
                                    (PublicReceipt)
                                            execute(
                                                    service,
                                                    s,
                                                    ProductDefinitionCommand.SUBMIT_PUBLIC,
                                                    Map.of(
                                                            "operationId",
                                                            UUID.randomUUID(),
                                                            "expectedPublicVersion",
                                                            created.publicVersion(),
                                                            "expectedPublicHead",
                                                            created.revisionId()));
                            principal.set(
                                    actor(s.tenant(), "checker", Instant.now().plusSeconds(300)));
                            var selection =
                                    context.getBean(ProductDefinitionReviewReadService.class)
                                            .readCurrentSelection(s.tenant(), s.root())
                                            .block(WAIT);
                            assertThat(selection).isNotNull();
                            assertThat(selection.selection().publicVersion())
                                    .isEqualTo(submitted.publicVersion());
                            var persistence =
                                    context.getBean(ProductDefinitionCommandPersistence.class);
                            var probe =
                                    new R2dbcRepositoryFactory(persistence.operations())
                                            .getRepository(Probe.class);
                            var until = Instant.now().plusSeconds(20).toEpochMilli();
                            var state =
                                    persistence
                                            .readTransactions()
                                            .execute(
                                                    tx ->
                                                            persistence
                                                                    .reviewReadNative()
                                                                    .openCurrent(
                                                                            s.tenant(),
                                                                            s.root(),
                                                                            ISSUER,
                                                                            "checker",
                                                                            until,
                                                                            until,
                                                                            ProductDefinitionCommandAuthorization
                                                                                    .POLICY_PROFILE)
                                                                    .flatMap(
                                                                            admission ->
                                                                                    probe.state()
                                                                                            .flatMap(
                                                                                                    observed ->
                                                                                                            persistence
                                                                                                                    .lifecycleSubmissionRepository()
                                                                                                                    .findTop2ByTenantIdAndRootIdAndRevisionIdAndPublicVersion(
                                                                                                                            s
                                                                                                                                    .tenant(),
                                                                                                                            s
                                                                                                                                    .root(),
                                                                                                                            selection
                                                                                                                                    .selection()
                                                                                                                                    .revisionId(),
                                                                                                                            selection
                                                                                                                                    .selection()
                                                                                                                                    .publicVersion())
                                                                                                                    .single()
                                                                                                                    .doOnNext(
                                                                                                                            row ->
                                                                                                                                    assertThat(
                                                                                                                                                    row
                                                                                                                                                            .submissionId())
                                                                                                                                            .isEqualTo(
                                                                                                                                                    selection
                                                                                                                                                            .selection()
                                                                                                                                                            .submissionId()))
                                                                                                                    .then(
                                                                                                                            persistence
                                                                                                                                    .reviewReadNative()
                                                                                                                                    .isCurrentSelectionLive(
                                                                                                                                            admission
                                                                                                                                                    .contextId()))
                                                                                                                    .doOnNext(
                                                                                                                            live ->
                                                                                                                                    assertThat(
                                                                                                                                                    live)
                                                                                                                                            .isTrue())
                                                                                                                    .thenReturn(
                                                                                                                            observed))))
                                            .single()
                                            .block(WAIT);
                            assertThat(state.readOnly()).isTrue();
                            assertThat(state.isolation()).isEqualTo("repeatable read");
                            assertThat(state.login()).isEqualTo(settings.get("commandUser"));
                        });
    }

    @Test
    void replayKeepsFullReceiptAndRowsButReauthorizesEveryAttempt() throws Exception {
        var s = scenario();
        app(true)
                .run(
                        context -> {
                            var service = context.getBean(ProductDefinitionLifecycleService.class);
                            var request = create(s);
                            var first =
                                    execute(
                                            service,
                                            s,
                                            ProductDefinitionCommand.CREATE_DRAFT,
                                            request);
                            var calls = decisions.get();
                            var replay =
                                    execute(
                                            service,
                                            s,
                                            ProductDefinitionCommand.CREATE_DRAFT,
                                            request);
                            assertThat(replay).isEqualTo(first);
                            assertThat(decisions.get()).isGreaterThan(calls);
                            assertThat(count("product_definition_action", s.root())).isEqualTo(1);
                            permit.set(false);
                            assertThatThrownBy(
                                            () ->
                                                    execute(
                                                            service,
                                                            s,
                                                            ProductDefinitionCommand.CREATE_DRAFT,
                                                            request))
                                    .isInstanceOfSatisfying(
                                            ResponseStatusException.class,
                                            e ->
                                                    assertThat(e.getStatusCode())
                                                            .isEqualTo(HttpStatus.FORBIDDEN));
                            assertThat(count("product_definition_action", s.root())).isEqualTo(1);
                        });
    }

    @Test
    void mappedStaleVersionConflictDoesNotAppendAnActionOrSubmission() throws Exception {
        var s = scenario();
        app(true)
                .run(
                        context -> {
                            var service = context.getBean(ProductDefinitionLifecycleService.class);
                            var created = create(service, s);
                            execute(
                                    service,
                                    s,
                                    ProductDefinitionCommand.SUBMIT_PUBLIC,
                                    Map.of(
                                            "operationId",
                                            UUID.randomUUID(),
                                            "expectedPublicVersion",
                                            created.publicVersion(),
                                            "expectedPublicHead",
                                            created.revisionId()));
                            assertThatThrownBy(
                                            () ->
                                                    execute(
                                                            service,
                                                            s,
                                                            ProductDefinitionCommand.SUBMIT_PUBLIC,
                                                            Map.of(
                                                                    "operationId",
                                                                    UUID.randomUUID(),
                                                                    "expectedPublicVersion",
                                                                    created.publicVersion(),
                                                                    "expectedPublicHead",
                                                                    created.revisionId())))
                                    .isInstanceOfSatisfying(
                                            ResponseStatusException.class,
                                            e ->
                                                    assertThat(e.getStatusCode())
                                                            .isEqualTo(HttpStatus.CONFLICT));
                            assertThat(count("product_definition_action", s.root())).isEqualTo(2);
                            assertThat(count("product_definition_submission", s.root()))
                                    .isEqualTo(1);
                        });
    }

    @Test
    void currentPolicyDenialPrecedesAnyMappedCommandEffects() throws Exception {
        var s = scenario();
        app(true)
                .run(
                        context -> {
                            permit.set(false);
                            assertThatThrownBy(
                                            () ->
                                                    create(
                                                            context.getBean(
                                                                    ProductDefinitionLifecycleService
                                                                            .class),
                                                            s))
                                    .isInstanceOfSatisfying(
                                            ResponseStatusException.class,
                                            e ->
                                                    assertThat(e.getStatusCode())
                                                            .isEqualTo(HttpStatus.FORBIDDEN));
                            assertThat(count("product_definition_root", s.root())).isZero();
                            assertThat(count("product_definition_action", s.root())).isZero();
                        });
    }

    @Test
    void retainedContributorCannotApproveTheSameSelection() throws Exception {
        var s = scenario();
        app(true)
                .run(
                        context -> {
                            var service = context.getBean(ProductDefinitionLifecycleService.class);
                            var created = create(service, s);
                            execute(
                                    service,
                                    s,
                                    ProductDefinitionCommand.SUBMIT_PUBLIC,
                                    Map.of(
                                            "operationId",
                                            UUID.randomUUID(),
                                            "expectedPublicVersion",
                                            created.publicVersion(),
                                            "expectedPublicHead",
                                            created.revisionId()));
                            var selection =
                                    context.getBean(ProductDefinitionReviewReadService.class)
                                            .readCurrentSelection(s.tenant(), s.root())
                                            .block(WAIT);
                            assertThatThrownBy(
                                            () ->
                                                    execute(
                                                            service,
                                                            s,
                                                            ProductDefinitionCommand
                                                                    .APPROVE_SELECTION,
                                                            review(selection)))
                                    .isInstanceOfSatisfying(
                                            ResponseStatusException.class,
                                            e ->
                                                    assertThat(e.getStatusCode())
                                                            .isEqualTo(HttpStatus.CONFLICT));
                            assertThat(count("product_definition_action", s.root())).isEqualTo(2);
                        });
    }

    @Test
    void servingOnlyDeploymentRetainsItsReadGraphAndDoesNotRequireCommandSecrets()
            throws Exception {
        var s = scenario();
        app(true)
                .run(
                        context ->
                                create(
                                        context.getBean(ProductDefinitionLifecycleService.class),
                                        s));
        app(false)
                .run(
                        context -> {
                            assertThat(context)
                                    .hasNotFailed()
                                    .doesNotHaveBean(ProductDefinitionCommandPersistence.class)
                                    .doesNotHaveBean(ProductDefinitionLifecycleService.class);
                            var view =
                                    context.getBean(ProductDefinitionServingService.class)
                                            .readRoot(s.tenant(), s.root())
                                            .block(WAIT);
                            assertThat(view).isNotNull();
                            assertThat(view.root().rootId()).isEqualTo(s.root());
                            try (var c =
                                            DriverManager.getConnection(
                                                    "jdbc:postgresql://127.0.0.1:"
                                                            + settings.get("port")
                                                            + "/"
                                                            + settings.get("database"),
                                                    settings.get("readerUser"),
                                                    settings.get("readerPassword"));
                                    var q = c.createStatement()) {
                                assertThatThrownBy(
                                                () ->
                                                        q.executeQuery(
                                                                "SELECT cost_revision_id FROM"
                                                                    + " public.product_definition_cost_revision"
                                                                    + " LIMIT 1"))
                                        .isInstanceOfSatisfying(
                                                SQLException.class,
                                                e ->
                                                        assertThat(e.getSQLState())
                                                                .isEqualTo("42501"));
                            }
                        });
    }

    @NoRepositoryBean
    interface Probe
            extends Repository<
                    io.fireflysoftware.core.product.models.entities.definition.lifecycle
                            .ProductDefinitionDraftRoot,
                    UUID> {
        @Query("SELECT public.product_definition_command_runtime_allowed() AS allowed")
        Mono<Boolean> runtimeAllowed();

        @Query(
                "SELECT pg_backend_pid() AS backend_pid,current_user AS"
                        + " login,current_setting('transaction_read_only')='on' AS"
                        + " read_only,current_setting('transaction_isolation') AS isolation")
        Mono<ExecutionState> state();
    }

    record ExecutionState(
            @Column("backend_pid") Integer backendPid,
            @Column("login") String login,
            @Column("read_only") Boolean readOnly,
            @Column("isolation") String isolation) {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ResourceServerProperties.class)
    @EnableR2dbcAuditing
    @EnableR2dbcRepositories(basePackages = "io.fireflysoftware.core.product.models.repositories")
    static class ReadRepositories {}
}

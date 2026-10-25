package io.fireflysoftware.core.product.core.services.definition;

import io.fireflysoftware.core.product.core.services.definition.impl.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.PublicReceipt;
import io.fireflysoftware.core.product.models.config.ProductDefinitionReadConnectionSource;
import io.fireflysoftware.core.product.models.config.R2dbcConfiguration;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionDraftRoot;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionLifecycleRows.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandPersistence;
import io.r2dbc.pool.*;
import io.r2dbc.spi.*;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.mapping.OutboundRow;
import org.springframework.data.r2dbc.mapping.event.*;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;

import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Controlled authority ports and mapped callback coordination for the original native suite. */
final class ProductDefinitionCommandDatabaseFixture implements AutoCloseable {
    private static final String ISSUER = "https://owned-native-staff.example.test";

    record Settings(
            String host,
            int port,
            String database,
            String readUser,
            String readPassword,
            String commandUser,
            String commandPassword) {
        @Override
        public String toString() {
            return "OwnedProductDefinitionDatabaseSettings";
        }
    }

    enum Stage {
        BEFORE_PRODUCT,
        AFTER_PRODUCT,
        AFTER_ROOT,
        AFTER_EVIDENCE
    }

    record Observation(
            Integer backendPid,
            String nativeTransaction,
            Boolean readOnly,
            UUID root,
            UUID revision,
            UUID cost) {}

    record NativeState(
            @Column("backend_pid") Integer backendPid,
            @Column("native_transaction") String nativeTransaction,
            @Column("read_only") Boolean readOnly) {}

    record ControlledCreate(
            ProductDefinitionFixtureGate<Observation> gate, Mono<PublicReceipt> result) {}

    private record Control(Stage stage, ProductDefinitionFixtureGate<Observation> gate) {}

    private final AnnotationConfigApplicationContext context;
    private final ProductDefinitionCommandPersistence persistence;
    private final ProductDefinitionLifecycleService commands;
    private final ProductDefinitionCostReadService costReads;
    private final Probe probe;
    private final Map<UUID, UUID> productRoots = new ConcurrentHashMap<>();
    private final Map<UUID, Control> controls = new ConcurrentHashMap<>();

    ProductDefinitionCommandDatabaseFixture(Settings settings) {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment()
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "owned-native-settings",
                                Map.of(
                                        "DB_HOST",
                                        settings.host(),
                                        "DB_PORT",
                                        settings.port(),
                                        "DB_NAME",
                                        settings.database(),
                                        "DB_USERNAME",
                                        settings.readUser(),
                                        "DB_PASSWORD",
                                        settings.readPassword(),
                                        "DB_SSL_MODE",
                                        "disable")));
        context.registerBean(
                "ownedMappedCreateCallbacks", Callbacks.class, () -> new Callbacks(this));
        context.register(
                R2dbcConfiguration.class,
                Auditing.class,
                org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration.class,
                org.springframework.boot.r2dbc.autoconfigure
                        .R2dbcTransactionManagerAutoConfiguration.class,
                org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration.class,
                org.fireflyframework.core.config.R2dbcTransactionAutoConfiguration.class);
        ConnectionPool pool = null;
        try {
            context.refresh();
            var read = context.getBean(ConnectionFactory.class);
            var options =
                    context.getBean(ProductDefinitionReadConnectionSource.class).optionsFor(read);
            if (settings.commandUser().equals(options.getValue(ConnectionFactoryOptions.USER)))
                throw new IllegalArgumentException("Distinct owned command credentials required");
            var factory =
                    org.springframework.boot.r2dbc.ConnectionFactoryBuilder.withOptions(
                                    options.mutate())
                            .username(settings.commandUser())
                            .password(settings.commandPassword())
                            .build();
            pool =
                    new ConnectionPool(
                            ConnectionPoolConfiguration.builder(factory)
                                    .initialSize(0)
                                    .maxSize(1)
                                    .maxAcquireTime(Duration.ofSeconds(5))
                                    .maxCreateConnectionTime(Duration.ofSeconds(5))
                                    .build());
            persistence =
                    new ProductDefinitionCommandPersistence(
                            pool, context.getBean(R2dbcConverter.class), context);
            probe = new R2dbcRepositoryFactory(persistence.operations()).getRepository(Probe.class);
            var authority =
                    new ProductDefinitionCommandAuthorizationImpl(
                            () ->
                                    Mono.deferContextual(
                                            c ->
                                                    Mono.justOrEmpty(
                                                            c.<SecurityPrincipal>getOrEmpty(
                                                                    SecurityPrincipal.class))),
                            (actor, action, resource, attributes) ->
                                    TransactionSynchronizationManager.forCurrentTransaction()
                                            .map(
                                                    TransactionSynchronizationManager
                                                            ::isActualTransactionActive)
                                            .onErrorResume(
                                                    NoTransactionException.class,
                                                    error -> Mono.just(false))
                                            .flatMap(
                                                    active ->
                                                            active
                                                                    ? Mono.error(
                                                                            new AssertionError(
                                                                                    "Fixture PDP"
                                                                                        + " called"
                                                                                        + " inside"
                                                                                        + " SQL transaction"))
                                                                    : Mono.just(Decision.permit())),
                            Clock.systemUTC(),
                            Set.of(ISSUER),
                            ProductDefinitionCommandAuthorization.POLICY_PROFILE);
            commands = new ProductDefinitionLifecycleServiceImpl(authority, persistence);
            costReads = new ProductDefinitionCostReadServiceImpl(authority, persistence);
        } catch (RuntimeException | Error failure) {
            if (pool != null) pool.dispose();
            context.close();
            throw failure;
        }
    }

    AnnotationConfigApplicationContext context() {
        return context;
    }

    ProductDefinitionCommandPersistence persistence() {
        return persistence;
    }

    Mono<PublicReceipt> create(
            UUID tenant, UUID root, UUID product, UUID category, Instant expiry) {
        return Mono.defer(
                        () -> {
                            productRoots.put(product, root);
                            var definition = ProfileFixtures.load("account").deepCopy();
                            ((com.fasterxml.jackson.databind.node.ObjectNode)
                                            definition.get("common"))
                                    .put(
                                            "code",
                                            "ACCOUNT_TEST_"
                                                    + root.toString()
                                                            .replace("-", "")
                                                            .substring(0, 12)
                                                            .toUpperCase(Locale.ROOT));
                            String request =
                                    DefinitionCanonical.encode(
                                            DefinitionCanonical.tree(
                                                    Map.of(
                                                            "operationId",
                                                            UUID.randomUUID(),
                                                            "productId",
                                                            product,
                                                            "categoryId",
                                                            category,
                                                            "definition",
                                                            definition,
                                                            "cost",
                                                            DefinitionCanonical.parse(
                                                                    CostRulesTest.costs(
                                                                            "account")))));
                            return commands.execute(
                                            tenant,
                                            root,
                                            ProductDefinitionCommand.CREATE_DRAFT,
                                            request)
                                    .cast(PublicReceipt.class)
                                    .doFinally(
                                            signal -> {
                                                productRoots.remove(product);
                                                controls.remove(root);
                                            });
                        })
                .contextWrite(c -> c.put(SecurityPrincipal.class, actor(tenant, expiry)));
    }

    ControlledCreate holdCreate(UUID tenant, UUID root, UUID product, UUID category, Stage stage) {
        var gate = new ProductDefinitionFixtureGate<Observation>();
        controls.put(root, new Control(stage, gate));
        return new ControlledCreate(
                gate, create(tenant, root, product, category, Instant.now().plusSeconds(300)));
    }

    Mono<ProductDefinitionCostView> currentCost(UUID tenant, UUID root) {
        return costReads
                .readCurrent(tenant, root)
                .contextWrite(
                        c ->
                                c.put(
                                        SecurityPrincipal.class,
                                        actor(tenant, Instant.now().plusSeconds(300))));
    }

    private static SecurityPrincipal actor(UUID tenant, Instant expiry) {
        return SecurityPrincipal.builder()
                .issuer(ISSUER)
                .subject("owned-fixture-maker")
                .tenantId(tenant.toString())
                .scopes(
                        Set.of(
                                "product:definitions:write",
                                "product:definitions:cost:write",
                                "product:definitions:cost:read"))
                .claims(Map.of("exp", expiry))
                .build();
    }

    private <T> Mono<T> pause(Stage stage, UUID tenant, UUID root, T value) {
        var control = controls.get(root);
        if (control == null || control.stage() != stage) return Mono.just(value);
        Mono<Observation> observed =
                probe.state()
                        .flatMap(
                                state -> {
                                    if (stage != Stage.AFTER_EVIDENCE)
                                        return Mono.just(
                                                new Observation(
                                                        state.backendPid(),
                                                        state.nativeTransaction(),
                                                        state.readOnly(),
                                                        root,
                                                        null,
                                                        null));
                                    return persistence
                                            .drafts()
                                            .findByTenantIdAndRootId(tenant, root)
                                            .switchIfEmpty(
                                                    Mono.error(
                                                            new AssertionError(
                                                                    "Root absent in command"
                                                                        + " transaction")))
                                            .flatMap(
                                                    header ->
                                                            persistence
                                                                    .costs()
                                                                    .findByTenantIdAndRootId(
                                                                            tenant, root)
                                                                    .switchIfEmpty(
                                                                            Mono.error(
                                                                                    new AssertionError(
                                                                                            "Cost head"
                                                                                                + " absent"
                                                                                                + " in command"
                                                                                                + " transaction")))
                                                                    .flatMap(
                                                                            cost ->
                                                                                    persistence
                                                                                            .lifecycleRevisionRepository()
                                                                                            .findByTenantIdAndRootIdAndRevisionId(
                                                                                                    tenant,
                                                                                                    root,
                                                                                                    header
                                                                                                            .publicHead())
                                                                                            .switchIfEmpty(
                                                                                                    Mono
                                                                                                            .error(
                                                                                                                    new AssertionError(
                                                                                                                            "Public"
                                                                                                                                + " revision"
                                                                                                                                + " absent"
                                                                                                                                + " in command"
                                                                                                                                + " transaction")))
                                                                                            .then(
                                                                                                    persistence
                                                                                                            .lifecycleCostRevisionRepository()
                                                                                                            .findByTenantIdAndRootIdAndCostRevisionId(
                                                                                                                    tenant,
                                                                                                                    root,
                                                                                                                    cost
                                                                                                                            .costHead()))
                                                                                            .switchIfEmpty(
                                                                                                    Mono
                                                                                                            .error(
                                                                                                                    new AssertionError(
                                                                                                                            "Cost revision"
                                                                                                                                + " absent"
                                                                                                                                + " in command"
                                                                                                                                + " transaction")))
                                                                                            .map(
                                                                                                    revision ->
                                                                                                            new Observation(
                                                                                                                    state
                                                                                                                            .backendPid(),
                                                                                                                    state
                                                                                                                            .nativeTransaction(),
                                                                                                                    state
                                                                                                                            .readOnly(),
                                                                                                                    root,
                                                                                                                    header
                                                                                                                            .publicHead(),
                                                                                                                    cost
                                                                                                                            .costHead()))));
                                });
        return observed.flatMap(control.gate()::pause).thenReturn(value);
    }

    @Override
    public void close() {
        controls.values()
                .forEach(
                        control ->
                                control.gate()
                                        .fail(new IllegalStateException("Owned fixture closed")));
        persistence.close();
        context.close();
    }

    @NoRepositoryBean
    interface Probe extends Repository<ProductDefinitionDraftRoot, UUID> {
        @Query(
                "SELECT pg_backend_pid() AS backend_pid,pg_current_xact_id()::text AS"
                    + " native_transaction,current_setting('transaction_read_only')='on' AS"
                    + " read_only")
        Mono<NativeState> state();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableR2dbcAuditing
    static class Auditing {}

    static final class Callbacks
            implements BeforeConvertCallback<Object>, AfterSaveCallback<Object>, Ordered {
        private final ProductDefinitionCommandDatabaseFixture fixture;

        Callbacks(ProductDefinitionCommandDatabaseFixture fixture) {
            this.fixture = fixture;
        }

        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }

        public Mono<Object> onBeforeConvert(Object entity, SqlIdentifier table) {
            if (entity instanceof ProductBirth product) {
                UUID root = fixture.productRoots.get(product.productId());
                if (root != null)
                    return fixture.pause(Stage.BEFORE_PRODUCT, product.tenantId(), root, entity);
            }
            return Mono.just(entity);
        }

        public Mono<Object> onAfterSave(Object entity, OutboundRow row, SqlIdentifier table) {
            if (entity instanceof ProductBirth product) {
                UUID root = fixture.productRoots.get(product.productId());
                if (root != null)
                    return fixture.pause(Stage.AFTER_PRODUCT, product.tenantId(), root, entity);
            } else if (entity instanceof ProductDefinitionDraftRoot root)
                return fixture.pause(Stage.AFTER_ROOT, root.tenantId(), root.rootId(), entity);
            else if (entity instanceof CostOutbox event)
                return fixture.pause(
                        Stage.AFTER_EVIDENCE, event.tenantId(), event.rootId(), entity);
            return Mono.just(entity);
        }
    }
}

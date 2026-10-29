package io.fireflysoftware.core.product.core.services.definition;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.product.core.services.definition.impl.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleContracts.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.*;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.*;
import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionLifecycleRows.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.*;
import io.r2dbc.pool.*;
import io.r2dbc.spi.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.relational.core.mapping.Column;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;

/** Controlled driver publishers exercise actual mapping/Root ordering, not PostgreSQL or owner authority. */
class DepositPublicationCoordinatorTest {
    static final Instant NOW=TermPcLifecycleProfileTest.NOW;
    static final UUID TENANT=TermPcLifecycleProfileTest.TENANT,ROOT=TermPcLifecycleProfileTest.ROOT;
    static final String ISSUER="https://issuer.example.invalid";
    static UUID id(long n){return new UUID(0,n);}
    static Map<String,Object> row(Object record) {
        var out=new LinkedHashMap<String,Object>();
        try {for(var component:record.getClass().getRecordComponents()) {
            var field=record.getClass().getDeclaredField(component.getName());var column=field.getAnnotation(Column.class);
            out.put(column==null?component.getName():column.value(),component.getAccessor().invoke(record));
        }}catch(ReflectiveOperationException error){throw new AssertionError(error);}return out;
    }
    static PreparedDepositPublication changed(PreparedDepositPublication value,String field,Object replacement) {
        var components=value.getClass().getRecordComponents();var arguments=new Object[components.length];var types=new Class<?>[components.length];
        try{for(int i=0;i<components.length;i++){types[i]=components[i].getType();arguments[i]=components[i].getName().equals(field)?replacement:components[i].getAccessor().invoke(value);}
            return (PreparedDepositPublication)value.getClass().getConstructor(types).newInstance(arguments);
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    static <T>T proxy(Class<T> type,InvocationHandler handler){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},handler));}
    final class Fixture implements AutoCloseable {
        final AtomicLong tick=new AtomicLong();final java.util.function.LongSupplier nanos=tick::get;
        final Clock clock=Clock.fixed(NOW,ZoneOffset.UTC);final List<String> order=new ArrayList<>();
        final Driver driver=new Driver();final ConnectionPool pool=new ConnectionPool(ConnectionPoolConfiguration.builder(driver).initialSize(0).maxSize(1).build());
        final GenericApplicationContext context=new GenericApplicationContext();
        final ProductDefinitionCommandPersistence persistence;
        final Parsed parsed;final PublishBody request;final DefinitionProfiles.Canonical publicValue;
        final Map<String,Map<String,Object>> tables=new LinkedHashMap<>();
        final AtomicReference<R2dbcOperationBudget> original=new AtomicReference<>();
        final java.util.concurrent.CountDownLatch ownerReady=new java.util.concurrent.CountDownLatch(1);
        int policyCalls,owners;String mode="normal";PreparedDepositPublication prepared;
        final ProductDefinitionCommandAuthorization authority=new ProductDefinitionCommandAuthorization(){
            public GovernedDefinitionProfile definitionProfile(){return GovernedDefinitionProfile.DEPOSIT_ACCOUNT;}
            public Mono<Admission> authorize(Scope scope){return Mono.defer(()->{order.add("authorize");return Mono.just(new Admission(
                SecurityPrincipal.builder().issuer(ISSUER).subject("publisher").tenantId(TENANT.toString()).build(),scope,
                NOW.plusSeconds(60).toEpochMilli(),NOW.plusSeconds(30).toEpochMilli()));});}
            public Mono<Void> reauthorize(Admission admission){return Mono.defer(()->{
                policyCalls++;order.add("policy"+policyCalls);
                if(mode.equals("latePolicy")&&policyCalls==4)return Mono.error(new IllegalStateException("denied"));
                if(mode.equals("beforeSecondPolicy")&&policyCalls==2)return Mono.error(new IllegalStateException("denied"));
                return Mono.empty();
            });}
        };
        Fixture(){
            context.refresh();persistence=new TermPcEngineBindingTest().persistence(pool,context,true);
            var helper=new TermPcLifecycleProfileTest();parsed=helper.parse(ProductDefinitionCommand.PUBLISH,
                helper.body(ProductDefinitionCommand.PUBLISH,"account").toString(),GovernedDefinitionProfile.DEPOSIT_ACCOUNT);
            request=(PublishBody)parsed.instruction().body();publicValue=DefinitionProfiles.canonical(DepositAccountProfileTest.deposit().toString());
            var costNode=(com.fasterxml.jackson.databind.node.ObjectNode)helper.body(ProductDefinitionCommand.CREATE_DRAFT,"account").get("cost");
            costNode.put("profileCode","DEPOSIT_ACCOUNT_V1");var cost=CostRules.canonical(costNode.toString(),publicValue.json());
            var selection=new Selection(TENANT,ROOT,id(304),id(305),publicValue.digest(),request.expectedPublicVersion(),id(306),cost.digest(),request.expectedCostVersion());
            String fingerprint=DefinitionCanonical.hash("product-definition-selection-v1",DefinitionCanonical.encode(DefinitionCanonical.tree(selection)));
            tables.put("product_definition_root",row(new ProductDefinitionDraftRoot(ROOT,TENANT,id(303),DefinitionProfiles.decode(publicValue.json()).common().code(), "BANKING","DEPOSIT_ACCOUNT_V1",1,
                "IN_REVIEW",request.expectedPublicVersion(),id(305),request.reviewId(),NOW)));
            tables.put("product_definition_control",row(new ProductDefinitionCostControl(ROOT,TENANT,id(306),request.expectedCostVersion())));
            tables.put("product_definition_revision",row(new ProductDefinitionDraftRevision(id(305),TENANT,ROOT,publicValue.json(),publicValue.digest(),
                DefinitionCanonical.ENCODING,null,null,ISSUER,"maker",NOW)));
            tables.put("product_definition_cost_revision",row(new ProductDefinitionCostRevision(id(306),TENANT,ROOT,cost.json(),cost.digest(),DefinitionCanonical.ENCODING,ISSUER,"maker",NOW)));
            tables.put("product_definition_review",row(new Review(request.reviewId(),TENANT,ROOT,id(305),publicValue.digest(),ISSUER,"checker",NOW)));
            tables.put("product_definition_review_private",row(new ReviewPrivate(request.reviewId(),TENANT,ROOT,id(306),fingerprint)));
            tables.put("product_definition_review_decision",row(new Decision(request.reviewId(),TENANT,ROOT,id(304),request.expectedPublicVersion(),request.expectedCostVersion(),"APPROVED",null,ISSUER,"checker",NOW)));
            tables.put("product_definition_submission",row(new Submission(id(304),TENANT,ROOT,id(305),publicValue.digest(),request.expectedPublicVersion(),ISSUER,"maker",NOW)));
            tables.put("product_definition_deposit_publication_prepared_snapshot",row(new DepositPublicationPreparationSnapshot(TENANT,ROOT,id(303),request.reviewId(),id(304),id(305),publicValue.digest(),publicValue.json(),
                DefinitionCanonical.ENCODING,request.expectedPublicVersion(),id(306),cost.digest(),request.expectedCostVersion(),fingerprint)));
        }
        Mono<Receipt> execute(){
            PreparedDepositPublicationService first=new PreparedDepositPublicationServiceImpl(authority,persistence,clock,nanos);
            PreparedDepositPublicationService capture=(p,resources,budget)->{
                assertNull(original.getAndSet(budget));return first.prepare(p,resources,budget).map(value->{
                    if(mode.equals("replacedOriginal"))return changed(value,"originalBudget",R2dbcOperationBudget.observed(NOW,NOW.plusSeconds(30),0,Duration.ofSeconds(30),clock,nanos));
                    if(mode.equals("missingPreparation"))return changed(value,"nativePreparation",null);
                    if(mode.equals("preparedFingerprint"))return changed(value,"selectionFingerprint","b".repeat(64));
                    if(mode.equals("preparedCanonical"))return changed(value,"canonicalDefinition",value.canonicalDefinition()+" ");
                    return value;
                }).doOnNext(value->{
                    prepared=value;order.add("prepared");assertFalse(driver.active);assertEquals(1,driver.commits);
                });
            };
            DepositAccountDocumentReferenceResolver resolver=(target,budget,until)->Mono.defer(()->{
                owners++;order.add("owner");assertSame(original.get(),budget);assertFalse(driver.active);
                assertEquals(0,pool.getMetrics().orElseThrow().acquiredSize());
                assertEquals(prepared.resources().publicationId(),target.scope().publicationId());
                if(mode.equals("ownerNever")){ownerReady.countDown();return Mono.never();}
                if(mode.equals("ownerExpired"))tick.set(Duration.ofSeconds(5).toNanos());
                if(mode.equals("staleControl"))tables.get("product_definition_control").put("cost_state_version",99);
                if(mode.equals("staleApproval"))tables.get("product_definition_root").put("active_review_id",id(999));
                if(mode.equals("ownerEmpty"))return Mono.empty();
                var observed=mode.equals("ownerFuture")?NOW.plusMillis(1):NOW;
                var effective=mode.equals("ownerOverlong")?NOW.plusSeconds(6):NOW.plusSeconds(4);
                var observedTarget=target;
                if(mode.equals("foreignOwner"))observedTarget=new DepositAccountDocumentReferenceResolver.Target(
                    new DepositAccountDocumentReferenceResolver.Scope(TENANT,ROOT,id(999),target.scope().revisionId(),target.scope().publicationId(),target.scope().definitionDigest()),target.purpose(),target.document());
                if(mode.equals("inventedNative"))return Mono.just(new DepositAccountDocumentReferenceResolver.Observation(observedTarget,observed,effective,effective,
                    DepositAccountDocumentReferenceResolver.WindowEvidence.EFFECTIVE_ONLY));
                return Mono.just(DepositAccountDocumentReferenceResolver.Observation.effectiveOnly(observedTarget,observed,effective));
            });
            ProductDefinitionLifecycleService engine;
            try {engine=(ProductDefinitionLifecycleService)ProductDefinitionLifecycleServiceImpl.class.getConstructor(
                ProductDefinitionCommandAuthorization.class,ProductDefinitionCommandPersistence.class,GovernedDefinitionProfile.class,Clock.class,LongSupplier.class,
                PreparedDepositPublicationService.class,DepositAccountDocumentReferenceResolver.class)
                .newInstance(authority,persistence,GovernedDefinitionProfile.DEPOSIT_ACCOUNT,clock,nanos,capture,resolver);
            }catch(NoSuchMethodException oldSource){engine=new ProductDefinitionLifecycleServiceImpl(authority,persistence,GovernedDefinitionProfile.DEPOSIT_ACCOUNT,clock,nanos);}
            catch(ReflectiveOperationException error){throw new AssertionError(error);}
            return engine.execute(TENANT,ROOT,ProductDefinitionCommand.PUBLISH,DefinitionCanonical.parse(parsed.canonicalIntent()).get("request").toString());
        }
        final class Driver implements ConnectionFactory {
            int creates,begins,commits,rollbacks,writes;boolean active,closed;Map<String,Object> evidence;UUID action;
            final List<String> statements=new ArrayList<>();final Set<String> inserted=new LinkedHashSet<>();
            public ConnectionFactoryMetadata getMetadata(){return ()->"PostgreSQL";}
            public org.reactivestreams.Publisher<? extends Connection> create(){return Mono.fromSupplier(()->{creates++;return proxy(Connection.class,(p,m,a)->switch(m.getName()){
                case "isAutoCommit"->!active;case "getTransactionIsolationLevel"->IsolationLevel.READ_COMMITTED;
                case "getMetadata"->proxy(ConnectionMetadata.class,(x,n,z)->n.getName().equals("getDatabaseProductName")?"PostgreSQL":"17");
                case "toString"->"ControlledPublicationLease";case "hashCode"->System.identityHashCode(p);case "equals"->p==a[0];
                case "beginTransaction"->Mono.fromRunnable(()->{begins++;active=true;var definition=(TransactionDefinition)a[0];
                    assertFalse(Boolean.TRUE.equals(definition.getAttribute(TransactionDefinition.READ_ONLY)));
                    assertEquals(IsolationLevel.REPEATABLE_READ,definition.getAttribute(TransactionDefinition.ISOLATION_LEVEL));order.add("begin"+begins);});
                case "commitTransaction"->Mono.fromRunnable(()->{commits++;active=false;order.add("commit"+commits);});
                case "rollbackTransaction"->Mono.fromRunnable(()->{rollbacks++;active=false;order.add("rollback");});
                case "close"->Mono.fromRunnable(()->closed=true);case "validate"->Mono.just(!closed);case "createStatement"->statement((String)a[0]);
                default->Mono.empty();});});}
            Statement statement(String sql){var bindings=new TreeMap<Integer,Object>();return proxy(Statement.class,(p,m,a)->{
                if(m.getName().equals("bind")){bindings.put((Integer)a[0],a[1]);return p;}
                if(m.getName().equals("bindNull")){bindings.put((Integer)a[0],null);return p;}
                if(!m.getName().equals("execute"))return p;
                return Mono.defer(()->{statements.add(sql);var values=new ArrayList<Map<String,Object>>();long changed=1;
                    if(sql.contains("open_product_definition_deposit_publish_preparation")) {
                        String text=(String)bindings.get(0);values.add(Map.of("context_id",id(700),"observed_at_ms",NOW.toEpochMilli(),"effective_until_ms",NOW.plusSeconds(20).toEpochMilli(),
                            "request_digest",DefinitionCanonical.hash("deposit-publication-preparation-v1",text)));
                    }else if(sql.contains("assert_product_definition_deposit_publish_preparation_live"))values.add(Map.of("live",true));
                    else if(sql.contains("begin_product_definition_deposit_publication")){
                        order.add("nativeSecond");assertEquals(1,commits);assertTrue(owners>0);assertSame(original.get(),prepared.originalBudget());
                        var plan=DefinitionCanonical.parse((String)bindings.get(11));action=UUID.fromString(plan.at("/resources/actionId").asText());
                        assertEquals(prepared.resources().actionId(),action);
                        var wire=DefinitionCanonical.parse((String)bindings.get(4));assertTrue(wire.get("documents").get(0).get("nativeUntilMillis").isNull());
                        if(!mode.equals("nativeAbsent"))values.add(Map.of("command_id",id(701),"replay",mode.equals("nativeReplay"),"command_recorded_at",NOW,"action_id",action));
                    }else if(sql.contains("product_definition_command_admission"))values.add(Map.of("recorded_at",NOW,"db_now_ms",NOW.toEpochMilli(),"admission_until_ms",NOW.plusSeconds(mode.equals("nativeOverlong")?5:3).toEpochMilli()));
                    else if(sql.contains("assert_product_definition_command_live"))values.add(Map.of("live",true));
                    else if(sql.contains("finish_product_definition_command")){order.add("finish");values.add(Map.of("finished",true));}
                    else if(sql.startsWith("INSERT")){writes++;var matcher=java.util.regex.Pattern.compile("INSERT INTO (?:\\\"?public\\\"?\\.)?\\\"?([a-z_]+)\\\"? ").matcher(sql);assertTrue(matcher.find(),sql);inserted.add(matcher.group(1));
                        // Assigned insert results are supplied only to exercise real mapped mutation ordering.
                        String names=sql.substring(sql.indexOf('(')+1,sql.indexOf(')'));var row=new LinkedHashMap<String,Object>();int index=0;
                        for(String name:names.split(","))row.put(name.trim().replace("\"",""),bindings.get(index++));values.add(row);
                    }else if(sql.startsWith("UPDATE")){writes++;order.add("cas");if(mode.equals("casZero"))changed=0;
                        if(mode.equals("afterWriteExpiry"))tick.set(Duration.ofSeconds(4).toNanos());
                    }else if(sql.contains("COUNT(")){values.add(Map.of("count",0L));}
                    else if(sql.contains("product_definition_version_identity")){/* No existing assigned version. */}
                    else {
                        for(var entry:tables.entrySet())if(sql.contains("\""+entry.getKey()+"\"")||sql.contains("public."+entry.getKey()+" ")){
                            if(sql.startsWith("SELECT DISTINCT"))
                                values.add(Map.of("actor_issuer",ISSUER,"actor_id","maker"));
                            else values.add(entry.getValue());break;
                        }
                    }
                    return Mono.just(result(values,changed));
                });
            });}
            Result result(List<Map<String,Object>> values,long changed){return proxy(Result.class,(p,m,a)->switch(m.getName()){
                case "getRowsUpdated"->Mono.just(changed);case "filter"->p;
                case "map"->Flux.fromIterable(values).map(value->{var metadata=metadata(value);var row=rowValue(value,metadata);
                    return a[0] instanceof BiFunction<?,?,?>?((BiFunction<Row,RowMetadata,Object>)a[0]).apply(row,metadata):((Function<io.r2dbc.spi.Readable,Object>)a[0]).apply(row);});
                case "flatMap"->Flux.fromIterable(values).concatMap(value->((Function<Result.Segment,org.reactivestreams.Publisher<Object>>)a[0])
                    .apply(proxy(Result.RowSegment.class,(x,n,z)->rowValue(value,metadata(value)))));
                default->throw new UnsupportedOperationException(m.getName());});}
            Row rowValue(Map<String,Object> values,RowMetadata metadata){return proxy(Row.class,(p,m,a)->switch(m.getName()){
                case "getMetadata"->metadata;case "get"->{Object key=a[0];yield values.get(key instanceof Integer?new ArrayList<>(values.keySet()).get((Integer)key):key);}
                default->throw new UnsupportedOperationException(m.getName());});}
            RowMetadata metadata(Map<String,Object> values){var columns=new ArrayList<ColumnMetadata>();for(var entry:values.entrySet())columns.add(proxy(ColumnMetadata.class,(p,m,a)->switch(m.getName()){
                case "getName"->entry.getKey();case "getJavaType"->entry.getValue()==null?Object.class:entry.getValue().getClass();case "getNullability"->Nullability.NULLABLE;
                default->null;}));return proxy(RowMetadata.class,(p,m,a)->switch(m.getName()){
                    case "getColumnMetadatas"->columns;case "contains"->values.containsKey(a[0]);case "getColumnMetadata"->a[0] instanceof Integer?columns.get((Integer)a[0]):columns.get(new ArrayList<>(values.keySet()).indexOf(a[0]));
                    default->throw new UnsupportedOperationException(m.getName());});}
        }
        public void close(){context.close();pool.disposeLater().block(Duration.ofSeconds(1));}
    }
    @Test void preparationCommitsBeforeOwnersAndSecondPassRetainsActualMappedAtomicWrites(){try(var f=new Fixture()){
        try{assertNotNull(f.execute().block());}catch(RuntimeException failure){throw new AssertionError("Controlled driver order "+f.order,failure);}assertEquals(2,f.driver.begins);assertEquals(2,f.driver.commits);assertEquals(0,f.driver.rollbacks);
        assertTrue(f.order.indexOf("prepared")<f.order.indexOf("owner"));assertTrue(f.order.indexOf("owner")<f.order.indexOf("nativeSecond"));
        assertTrue(f.order.indexOf("policy4")<f.order.indexOf("finish"));
        assertTrue(f.driver.inserted.containsAll(Set.of("product_version","product_definition_publication","product_definition_version_identity","product_definition_publication_control",
            "product_definition_action","product_definition_action_private","product_definition_history","product_definition_audit","product_definition_outbox")));
    }}
    @ParameterizedTest @ValueSource(strings={"ownerExpired","foreignOwner","beforeSecondPolicy","ownerFuture","ownerOverlong","ownerEmpty","inventedNative", "replacedOriginal","missingPreparation","preparedFingerprint","preparedCanonical"})
    void ownerOrPermissionRefusalCannotAcquireSecondSql(String mode){try(var f=new Fixture()){
        f.mode=mode;assertThrows(RuntimeException.class,()->f.execute().block());assertEquals(1,f.driver.begins);assertEquals(1,f.driver.commits);assertEquals(0,f.driver.writes);
    }}
    @ParameterizedTest @ValueSource(strings={"staleApproval","staleControl","casZero","latePolicy","afterWriteExpiry","nativeAbsent","nativeReplay","nativeOverlong"})
    void lateChangeDenialOrCasRefusalRollsBackSecondRoot(String mode){try(var f=new Fixture()){
        f.mode=mode;assertThrows(RuntimeException.class,()->f.execute().block());assertEquals(2,f.driver.begins);assertEquals(1,f.driver.commits);assertEquals(1,f.driver.rollbacks);
        assertFalse(f.order.contains("finish"));if(Set.of("staleApproval","staleControl","nativeAbsent","nativeReplay","nativeOverlong").contains(mode))assertEquals(0,f.driver.writes);
        else assertTrue(f.driver.writes>0);
        if(mode.equals("latePolicy"))assertTrue(f.driver.inserted.containsAll(Set.of("product_definition_action","product_definition_audit","product_definition_outbox")));
    }}
    @Test void stalledOwnerCannotRenewOriginalOrPreparationWindow(){try(var f=new Fixture()){
        f.mode="ownerNever";StepVerifier.withVirtualTime(f::execute).then(()->{try{assertTrue(f.ownerReady.await(1,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException error){throw new AssertionError(error);}}).thenAwait(Duration.ofSeconds(6)).expectError().verify(Duration.ofSeconds(1));
        assertEquals(1,f.driver.begins);assertEquals(1,f.driver.commits);assertEquals(0,f.driver.writes);
    }}
}

package io.fireflysoftware.core.product.core.services.definition;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.Parsed;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandPersistence;
import io.r2dbc.pool.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import reactor.core.publisher.Mono;

/** Networkless driver refusals, not native permission or positive publication evidence. */
class DepositPublicationPreparationTest {
    final Instant now=TermPcLifecycleProfileTest.NOW;
    final Clock clock=Clock.fixed(now,ZoneOffset.UTC);
    final AtomicLong tick=new AtomicLong();
    final java.util.function.LongSupplier nanos=tick::get;
    final AtomicInteger authorizationCalls=new AtomicInteger();
    Parsed parsed;
    ProductDefinitionCommandAuthorization authority(boolean permit) {
        return new ProductDefinitionCommandAuthorization() {
            public GovernedDefinitionProfile definitionProfile(){return GovernedDefinitionProfile.DEPOSIT_ACCOUNT;}
            public Mono<Admission> authorize(Scope scope){return Mono.defer(()->{
                authorizationCalls.incrementAndGet();
                return permit?Mono.just(new Admission(SecurityPrincipal.builder().issuer("https://issuer.example.invalid")
                    .subject("publisher").tenantId(scope.tenantId().toString()).build(),scope,now.plusSeconds(60).toEpochMilli(),now.plusSeconds(30).toEpochMilli()))
                    :Mono.error(new IllegalStateException("denied"));
            });}
            public Mono<Void> reauthorize(Admission value){return Mono.empty();}
        };
    }
    Class<?> resourcesType(){try{return Class.forName("io.fireflysoftware.core.product.core.services.definition.PreparedDepositPublicationService$Resources");}
        catch(ReflectiveOperationException e){throw new AssertionError("Required stable publication preparation resources missing",e);}}
    Object resources(boolean duplicate){try{var args=new Object[6];for(int i=0;i<6;i++)args[i]=new UUID(0,100+i);
        if(duplicate)args[1]=args[0];return resourcesType().getConstructors()[0].newInstance(args);
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    Object service(ProductDefinitionCommandAuthorization auth,ProductDefinitionCommandPersistence p){try{
        return Class.forName("io.fireflysoftware.core.product.core.services.definition.impl.PreparedDepositPublicationServiceImpl")
            .getConstructor(ProductDefinitionCommandAuthorization.class,ProductDefinitionCommandPersistence.class,Clock.class,java.util.function.LongSupplier.class)
            .newInstance(auth,p,clock,nanos);
        }catch(InvocationTargetException e){if(e.getCause()instanceof RuntimeException x)throw x;throw new AssertionError(e.getCause());}
        catch(ReflectiveOperationException e){throw new AssertionError("Real publication preparation service missing",e);}}
    Parsed instruction(){if(parsed==null){var f=new TermPcLifecycleProfileTest();parsed=f.parse(ProductDefinitionCommand.PUBLISH,
        f.body(ProductDefinitionCommand.PUBLISH,"account").toString(),GovernedDefinitionProfile.DEPOSIT_ACCOUNT);}return parsed;}
    R2dbcOperationBudget budget(){return R2dbcOperationBudget.observed(now,now.plusSeconds(30),0,Duration.ofSeconds(30),clock,nanos);}
    @SuppressWarnings("unchecked") Mono<Object> prepare(Object service,Object resources,R2dbcOperationBudget original){try{
        return (Mono<Object>)service.getClass().getMethod("prepare",Parsed.class,resourcesType(),R2dbcOperationBudget.class)
            .invoke(service,instruction(),resources,original);
        }catch(InvocationTargetException e){if(e.getCause()instanceof RuntimeException x)throw x;throw new AssertionError(e.getCause());}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    @Test void serviceUsesFunctionalInterfaceAndImplementationPackage()throws Exception {
        var contract=Class.forName("io.fireflysoftware.core.product.core.services.definition.PreparedDepositPublicationService");
        var implementation=Class.forName("io.fireflysoftware.core.product.core.services.definition.impl.PreparedDepositPublicationServiceImpl");
        assertTrue(contract.isInterface());assertTrue(contract.isAssignableFrom(implementation));
    }
    @Test void permissionDenialPrecedesAllDatabaseAcquisition(){run(false,false,false,false);}
    @Test void expiredOriginalBudgetCannotBeRenewedBeforeAuthorization(){run(true,true,false,false);}
    @Test void duplicateStableResourcesRefuseBeforeAuthorization(){run(true,false,true,false);}
    @Test void missingActualNativeContextRefusesInsideMetadataWritableRepeatableRead(){run(true,false,false,true);}
    void run(boolean permit,boolean expired,boolean duplicate,boolean reachesNative){
        var driver=new InsuranceQuotePurposeTransactionTest.Driver();var pool=new ConnectionPool(ConnectionPoolConfiguration.builder(driver).initialSize(0).build());
        try(var context=new GenericApplicationContext()){
            context.refresh();var persistence=new TermPcEngineBindingTest().persistence(pool,context,true);
            var service=service(authority(permit),persistence);var original=budget();if(expired)tick.set(Duration.ofSeconds(30).toNanos());
            assertThrows(RuntimeException.class,()->prepare(service,resources(duplicate),original).block());
            assertEquals(reachesNative?1:0,driver.creates);
            assertEquals(expired||duplicate?0:1,authorizationCalls.get());
            if(reachesNative){assertFalse(driver.readOnlyIntent);assertEquals(io.r2dbc.spi.IsolationLevel.REPEATABLE_READ,driver.isolationIntent);
                assertEquals(0,driver.commits);assertEquals(1,driver.rollbacks);}
        }finally{pool.disposeLater().block(Duration.ofSeconds(1));}
    }
    @Test void optionalV24ExistsWithoutChangingDefaultMigrationDiscovery()throws Exception {
        var root=java.nio.file.Path.of(System.getProperty("product.reference.source-root",System.getProperty("user.dir"))).toAbsolutePath();
        while(root!=null && !java.nio.file.Files.isDirectory(root.resolve("ops/database/purpose-activation")))root=root.getParent();
        assertNotNull(root,"Product source root required");
        var path=root.resolve("ops/database/purpose-activation/V24__Deposit_Publication_Preparation.sql");
        assertTrue(java.nio.file.Files.isRegularFile(path),"Missing native preparation implementation");
        var sql=java.nio.file.Files.readString(path);
        for(String field:List.of("p_request_until","p_actor_until","p_permit_until","transaction_read_only","repeatable read",
                "PRODUCT_DEPOSIT_PREPARATION_UNAVAILABLE","open_product_definition_deposit_publish_preparation"))assertTrue(sql.contains(field),field);
        assertFalse(java.nio.file.Files.exists(root.resolve("core-common-product-mgmt-models/src/main/resources/db/migration/"+path.getFileName())));
        assertTrue(sql.contains("CREATE TABLE firefly_product_definition_private.deposit_publication_preparation"));
        assertTrue(sql.contains("DEFERRABLE INITIALLY DEFERRED"));
        assertTrue(sql.contains("native_transaction=pg_catalog.pg_current_xact_id()"));
        assertTrue(sql.contains("product_definition_preparation_business_writes_allowed"));
        assertTrue(sql.contains("WITH (security_barrier=true)"));
        assertFalse(sql.contains("ON public.product_definition_cost_revision FOR SELECT"),"No raw private-cost preparation policy");
    }

    final class Fixture implements AutoCloseable {
        final InsuranceQuotePurposeTransactionTest.Driver driver=new InsuranceQuotePurposeTransactionTest.Driver();
        final ConnectionPool pool=new ConnectionPool(ConnectionPoolConfiguration.builder(driver).initialSize(0).build());
        final GenericApplicationContext context=new GenericApplicationContext();
        final Object service;
        Fixture(){this(authority(true));}
        Fixture(ProductDefinitionCommandAuthorization auth){context.refresh();service=service(auth,new TermPcEngineBindingTest().persistence(pool,context,true));}
        Object window(R2dbcOperationBudget original)throws Exception {
            var type=Class.forName(service.getClass().getName()+"$Window");var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
            var p=instruction();var body=(io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.PublishBody)p.instruction().body();
            var scope=p.instruction().scope();var admission=authority(true).authorize(new ProductDefinitionCommandAuthorization.Scope(scope.tenantId(),scope.rootId(),scope.operationId(),ProductDefinitionCommand.PUBLISH,body.reviewId())).block();
            return constructor.newInstance(service,original,admission);
        }
        public void close(){context.close();pool.disposeLater().block(Duration.ofSeconds(1));}
    }
    static Object call(Object target,String method,Object...args)throws Exception {
        var type=target instanceof Class<?> c?c:target.getClass();var m=Arrays.stream(type.getDeclaredMethods()).filter(x->x.getName().equals(method)).findFirst().orElseThrow();m.setAccessible(true);
        try{return m.invoke(target instanceof Class<?>?null:target,args);}catch(InvocationTargetException e){if(e.getCause()instanceof RuntimeException x)throw x;throw new AssertionError(e.getCause());}
    }
    @Test void emptyPublisherAuthorizationHasNoNativeFallback(){
        var delegate=authority(true);
        var empty=new ProductDefinitionCommandAuthorization(){
            public GovernedDefinitionProfile definitionProfile(){return GovernedDefinitionProfile.DEPOSIT_ACCOUNT;}
            public Mono<Admission> authorize(Scope scope){return Mono.empty();}
            public Mono<Void> reauthorize(Admission admission){return delegate.reauthorize(admission);}
        };
        try(var fixture=new Fixture(empty)){
            assertThrows(RuntimeException.class,()->prepare(fixture.service,resources(false),budget()).block());assertEquals(0,fixture.driver.creates);
        }
    }
    @Test void ambientTransactionRefusesBeforePublisherAuthorization(){
        try(var fixture=new Fixture()){
            var operation=org.springframework.transaction.reactive.TransactionSynchronizationManager.forCurrentTransaction().flatMap(manager->{
                manager.setActualTransactionActive(true);return prepare(fixture.service,resources(false),budget());
            }).contextWrite(org.springframework.transaction.reactive.TransactionContextManager.createTransactionContext());
            assertThrows(RuntimeException.class,operation::block);assertEquals(0,authorizationCalls.get());assertEquals(0,fixture.driver.creates);
        }
    }
    @Test void malformedOriginalIntentIsRejectedBeforePublisherAuthorization(){
        var valid=instruction();parsed=new Parsed(valid.instruction(),valid.canonicalIntent(),"a".repeat(64));
        try(var fixture=new Fixture()){
            assertThrows(RuntimeException.class,()->prepare(fixture.service,resources(false),budget()).block());assertEquals(0,authorizationCalls.get());assertEquals(0,fixture.driver.creates);
        }
    }
    void observe(Object window,long observed,long until,String digest)throws Exception {
        call(window,"observe",new io.fireflysoftware.core.product.models.repositories.definition.lifecycle.DepositPublicationPreparationRepository.NativeContext(new UUID(0,300),observed,until,digest),"f".repeat(64),0L);
    }
    @Test void nativeWindowRetainsMonotonicExpiryWhenWallClockDoesNotAdvance()throws Exception {
        try(var fixture=new Fixture()){
            var original=budget();var window=fixture.window(original);observe(window,now.toEpochMilli(),now.plusSeconds(5).toEpochMilli(),"f".repeat(64));
            tick.set(Duration.ofSeconds(5).toNanos());assertDoesNotThrow(original::remaining);
            assertThrows(IllegalStateException.class,()->call(window,"remaining"));
        }
    }
    @ParameterizedTest @ValueSource(strings={"future","overOriginal","emptyQuantum","wrongRequest"})
    void malformedNativeWindowCannotBecomePreparationAuthority(String mutation)throws Exception {
        try(var fixture=new Fixture()){
            var window=fixture.window(budget());long observed=now.toEpochMilli(),until=now.plusSeconds(5).toEpochMilli();String digest="f".repeat(64);
            if(mutation.equals("future"))observed++;
            if(mutation.equals("overOriginal"))until=now.plusSeconds(31).toEpochMilli();
            if(mutation.equals("emptyQuantum"))until=observed+1;
            if(mutation.equals("wrongRequest"))digest="e".repeat(64);
            final long start=observed,end=until;final String value=digest;
            assertThrows(IllegalStateException.class,()->observe(window,start,end,value));
        }
    }
    Object[] rowValues() {
        var parsed=instruction();var body=(io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.PublishBody)parsed.instruction().body();
        var scope=parsed.instruction().scope();var canonical=DefinitionProfiles.canonical(DepositAccountProfileTest.deposit().toString());
        var selection=new io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleContracts.Selection(scope.tenantId(),scope.rootId(),new UUID(0,304),new UUID(0,305),canonical.digest(),body.expectedPublicVersion(),new UUID(0,306),"a".repeat(64),body.expectedCostVersion());
        return new Object[]{scope.tenantId(),scope.rootId(),new UUID(0,303),body.reviewId(),selection.submissionId(),selection.revisionId(),canonical.digest(),canonical.json(),DefinitionCanonical.ENCODING,body.expectedPublicVersion(),selection.costRevisionId(),selection.costDigest(),body.expectedCostVersion(),DefinitionCanonical.hash("product-definition-selection-v1",DefinitionCanonical.encode(DefinitionCanonical.tree(selection)))};
    }
    Object assemble(Fixture fixture,Object[] values,R2dbcOperationBudget original)throws Exception {
        var row=io.fireflysoftware.core.product.models.entities.definition.lifecycle.DepositPublicationPreparationSnapshot.class.getConstructors()[0].newInstance(values);
        var request=call(fixture.service.getClass(),"request",instruction(),resources(false));var window=fixture.window(original);
        observe(window,now.toEpochMilli(),now.plusSeconds(5).toEpochMilli(),"f".repeat(64));
        return call(fixture.service.getClass(),"snapshot",request,row,original,window);
    }
    @Test void controlledSnapshotRetainsExactOriginalBudgetResourcesAndPublicDefinitionOnly()throws Exception {
        try(var fixture=new Fixture()){
            var original=budget();var prepared=(PreparedDepositPublication)assemble(fixture,rowValues(),original);
            assertSame(original,prepared.originalBudget());assertEquals(resources(false),prepared.resources());
            assertEquals(instruction().instruction().scope(),prepared.requestScope());assertEquals("publisher",prepared.publisher().subject());
            assertEquals(DefinitionProfiles.canonical(DepositAccountProfileTest.deposit().toString()).json(),prepared.canonicalDefinition());
            assertEquals(now.plusMillis(4999),prepared.effectiveUntil());assertEquals(0,fixture.driver.creates);
            assertTrue(Arrays.stream(PreparedDepositPublication.class.getRecordComponents()).noneMatch(x->x.getType()==CostTerms.class));
        }
    }
    @ParameterizedTest @ValueSource(strings={"tenant","review","publicVersion","costVersion","publicDigest","costDigest","fingerprint","nilProduct","canonical","profile"})
    void snapshotMismatchCannotEscapeAsPreparedPublication(String mutation)throws Exception {
        try(var fixture=new Fixture()){
            var values=rowValues();switch(mutation){
                case "tenant"->values[0]=new UUID(0,999);case "review"->values[3]=new UUID(0,999);
                case "publicVersion"->values[9]=99;case "costVersion"->values[12]=99;
                case "publicDigest"->values[6]="b".repeat(64);case "costDigest"->values[11]="invalid";
                case "fingerprint"->values[13]="b".repeat(64);case "nilProduct"->values[2]=new UUID(0,0);
                case "canonical"->values[7]=values[7]+" ";case "profile"->{var c=DefinitionProfiles.canonical(ProfileFixtures.load("account").toString());values[7]=c.json();values[6]=c.digest();}
            }
            assertThrows(RuntimeException.class,()->assemble(fixture,values,budget()));assertEquals(0,fixture.driver.creates);
        }
    }
    @Test void ordinarySnapshotReadHasDerivedMethodAndNoSaveOrHandwrittenQuery()throws Exception {
        var type=Class.forName("io.fireflysoftware.core.product.models.repositories.definition.lifecycle.DepositPublicationPreparationSnapshotRepository");
        var method=type.getMethod("findByTenantIdAndRootIdAndReviewIdAndPublicVersionAndCostVersion",UUID.class,UUID.class,UUID.class,int.class,int.class);
        assertNull(method.getAnnotation(org.springframework.data.r2dbc.repository.Query.class));
        assertTrue(Arrays.stream(type.getMethods()).noneMatch(m->Set.of("save","saveAll","delete","deleteAll","insert","update").contains(m.getName())));
        var entity=Class.forName("io.fireflysoftware.core.product.models.entities.definition.lifecycle.DepositPublicationPreparationSnapshot");
        var table=entity.getAnnotation(org.springframework.data.relational.core.mapping.Table.class);
        assertEquals("public",table.schema());assertEquals("product_definition_deposit_publication_prepared_snapshot",table.value());
        var nativeType=io.fireflysoftware.core.product.models.repositories.definition.lifecycle.DepositPublicationPreparationRepository.class;
        assertTrue(Arrays.stream(nativeType.getDeclaredMethods()).allMatch(m->Set.of("open","isCurrent").contains(m.getName())),"Only fixed native operations may use SQL text");
    }
    @Test void preparedSnapshotViewRetainsInvokerRlsAndApprovalJoinPredicates()throws Exception {
        var root=java.nio.file.Path.of(System.getProperty("product.reference.source-root",System.getProperty("user.dir"))).toAbsolutePath();
        while(root!=null && !java.nio.file.Files.isDirectory(root.resolve("ops/database/purpose-activation")))root=root.getParent();assertNotNull(root);
        String sql=java.nio.file.Files.readString(root.resolve("ops/database/purpose-activation/V24__Deposit_Publication_Preparation.sql"));
        assertTrue(sql.contains("CREATE VIEW public.product_definition_deposit_publication_prepared_snapshot WITH (security_barrier=true,security_invoker=true)"));
        for(String predicate:List.of("d.state='APPROVED'","d.actor_issuer=a.actor_issuer","d.actor_id=a.actor_id","d.created_at=a.created_at",
                "s.public_digest=v.public_digest","s.public_version=r.public_state_version","p.cost_revision_id=c.cost_head",
                "r.phase='IN_REVIEW'","r.profile_code='DEPOSIT_ACCOUNT_V1'","product_definition_deposit_preparation_cost_identity k"))assertTrue(sql.contains(predicate),predicate);
    }
    @Test void realDerivedRepositoryEmitsFiveBoundScopePredicatesWithoutJoinSql()throws Exception {
        var driver=new InsuranceQuotePurposeTransactionTest.Driver();var statements=new ArrayList<String>();
        io.r2dbc.spi.ConnectionFactory recording=new io.r2dbc.spi.ConnectionFactory(){
            public io.r2dbc.spi.ConnectionFactoryMetadata getMetadata(){return driver.getMetadata();}
            public org.reactivestreams.Publisher<? extends io.r2dbc.spi.Connection> create(){return Mono.from(driver.create()).map(connection->
                (io.r2dbc.spi.Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{io.r2dbc.spi.Connection.class},(proxy,method,args)->{
                    if(method.getName().equals("createStatement"))statements.add((String)args[0]);
                    try{return method.invoke(connection,args);}catch(InvocationTargetException e){throw e.getCause();}
                }));}
        };
        var pool=new ConnectionPool(ConnectionPoolConfiguration.builder(recording).initialSize(0).build());
        try(var context=new GenericApplicationContext()){
            context.refresh();var persistence=new TermPcEngineBindingTest().persistence(pool,context,true);
            Object repo=persistence.getClass().getMethod("publicationPreparationSnapshots").invoke(persistence);
            var method=repo.getClass().getMethod("findByTenantIdAndRootIdAndReviewIdAndPublicVersionAndCostVersion",UUID.class,UUID.class,UUID.class,int.class,int.class);
            var publisher=(Mono<?>)method.invoke(repo,TermPcLifecycleProfileTest.TENANT,TermPcLifecycleProfileTest.ROOT,new UUID(0,500),3,2);
            assertEquals("no-row",persistence.executePreparation(budget(),scope->publisher.thenReturn("no-row")).block());
            String sql=String.join("\n",statements);
            assertTrue(sql.contains("product_definition_deposit_publication_prepared_snapshot"));assertFalse(sql.toUpperCase(Locale.ROOT).contains("JOIN"));
            for(String column:List.of("tenant_id","root_id","review_id","public_version","cost_version"))assertTrue(sql.contains(column+" = $")||sql.contains('"'+column+'"'+" = $"),sql);
        }finally{pool.disposeLater().block(Duration.ofSeconds(1));}
    }
}

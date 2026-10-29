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

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.data.relational.core.mapping.Table;

/** Real mapping metadata, not a database/SQL parser or ACL execution. */
class InsuranceQuotePurposeRepositoryContractTest {
    private static Class<?> type(String name){try{return Class.forName("io.fireflysoftware.core.product.models.repositories.definition.purpose."+name);}catch(ClassNotFoundException missing){throw new AssertionError("Exact purpose mapped repository contract missing",missing);}}
    @Test void fiveReadonlyMappingsSelectExactlyThirtyFivePublicColumns() {
        var context=new R2dbcMappingContext();int columns=0;
        for(var name:List.of("Root","Revision","Publication","Control","Version")){
            var row=type("InsuranceQuotePurposeRows$"+name);var entity=context.getRequiredPersistentEntity(row);var names=new HashSet<String>();entity.forEach(p->names.add(p.getColumnName().getReference()));columns+=names.size();
            assertEquals("public",row.getAnnotation(Table.class).schema());assertFalse(names.contains("cost_head"));assertFalse(names.contains("canonical_cost"));assertFalse(names.contains("actor_subject"));assertFalse(names.contains("review_reason"));
        }assertEquals(35,columns);
    }
    @Test void markerRepositoriesHaveNoGenericWriteOrDeleteMethods(){for(var name:List.of("Roots","Revisions","Publications","Controls","Versions","Native")){var repo=type("InsuranceQuotePurposeRepositories$"+name);assertTrue(repo.isInterface());for(var method:repo.getMethods())assertFalse(method.getName().startsWith("save")||method.getName().startsWith("delete"));}}
    @Test void onlyNativeContextQueriesUseExplicitSql(){var type=type("InsuranceQuotePurposeRepositories$Native");assertEquals(2,type.getMethods().length);for(var m:type.getMethods()){var q=m.getAnnotation(org.springframework.data.r2dbc.repository.Query.class);assertNotNull(q);assertTrue(q.value().startsWith("SELECT "));assertFalse(q.value().contains("SELECT *"));}}

    @Test void realRepositoryFactoryBuildsAllSixProxiesWithoutAcquiringAConnection() {
        var driver=new InsuranceQuotePurposeTransactionTest.Driver();
        try(var application=new org.springframework.context.support.GenericApplicationContext()){
            application.refresh();var clock=java.time.Clock.fixed(java.time.Instant.parse("2026-10-26T12:00:00Z"),java.time.ZoneOffset.UTC);
            var converter=new org.springframework.data.r2dbc.core.DefaultReactiveDataAccessStrategy(org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE).getConverter();
            var transaction=new io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeTransaction(driver,converter,application,clock,()->0L);
            var store=new io.fireflysoftware.core.product.core.services.definition.impl.InsuranceQuotePurposeReadStoreImpl(transaction,application,clock,()->0L);
            assertNotNull(store);assertSame(transaction.operations().getDatabaseClient().getConnectionFactory(),transaction.manager().getConnectionFactory());assertEquals(0,driver.creates);
        }
    }
}

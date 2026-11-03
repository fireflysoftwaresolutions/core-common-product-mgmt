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
import io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.Scope;
import io.fireflysoftware.core.product.models.repositories.definition.purpose.*;
import io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeReadStore.*;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import java.lang.reflect.*;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.atomic.*;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/** Technical source-aggregate budgets, not native identity/authentication evidence. */
class ProductPurposeSharedBudgetTest {
    private static final Instant BASE=Instant.parse("2026-10-26T12:00:00Z");
    private final AtomicReference<Instant> now=new AtomicReference<>(BASE);
    private final AtomicLong nano=new AtomicLong();
    private final Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now.get();}};
    private final LongSupplier ticks=nano::get;
    private Admission admission(){return new Admission(new Scope(new UUID(0,1),new UUID(0,2),new UUID(0,3),new UUID(0,4),new UUID(0,5)),"https://issuer.example","actor",BASE.plusSeconds(60),BASE.plusSeconds(5),BASE,0);}
    private R2dbcOperationBudget budget(Object value,Admission original){try{return (R2dbcOperationBudget)(original==null?value.getClass().getMethod("operationBudget",Clock.class,LongSupplier.class).invoke(value,clock,ticks):value.getClass().getMethod("operationBudget",Admission.class,Clock.class,LongSupplier.class).invoke(value,original,clock,ticks));}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError("Explicit shared budget mapping missing",e);}}
    @Test void originalAbsoluteAndPreObservationElapsedTimeAreRetained(){var a=admission();nano.set(2_000_000_000L);now.set(BASE.plusSeconds(1));var b=budget(a,null);assertEquals(Duration.ofSeconds(3),b.remaining());now.set(BASE.plusSeconds(5));assertThrows(IllegalStateException.class,b::remaining);}
    @Test void nativeDeadlineCannotBecomeNowPlusANewRemainingTtl(){var a=admission();var w=NativeWindow.from(BASE,BASE.toEpochMilli(),BASE.toEpochMilli()+2000,0);nano.set(100_000_000L);now.set(BASE.plusMillis(500));var b=budget(w,a);now.set(BASE.plusSeconds(2));assertThrows(IllegalStateException.class,b::remaining);}
    @Test void nativePreObservationOffsetIsChargedAgainstTheOriginalAnchor(){var a=admission();var w=NativeWindow.from(BASE.plusMillis(500),BASE.toEpochMilli()+500,BASE.toEpochMilli()+1500,500_000_000L);nano.set(1_000_000_000L);now.set(BASE.plusSeconds(1));var b=budget(w,a);assertEquals(Duration.ofMillis(499),b.remaining());}
    @Test void externalAndDefinitionAbsoluteCapsCannotBeRenewed(){var a=admission();var w=NativeWindow.from(BASE,BASE.toEpochMilli(),BASE.toEpochMilli()+5000,0).narrowUntil(BASE.plusSeconds(3)).narrowExternal(BASE.plusMillis(500),BASE.plusMillis(2500),500_000_000L);nano.set(1_000_000_000L);now.set(BASE.plusSeconds(1));var b=budget(w,a);now.set(BASE.plusMillis(2500));assertThrows(IllegalStateException.class,b::remaining);}
    @Test void staleWindowFromBeforeTheCurrentOriginalAnchorRefuses(){var old=NativeWindow.from(BASE,BASE.toEpochMilli(),BASE.toEpochMilli()+3000,0);var a=new Admission(admission().scope(),"https://issuer.example","actor",BASE.plusSeconds(60),BASE.plusSeconds(5),BASE.plusSeconds(1),1_000_000_000L);nano.set(1_000_000_000L);now.set(BASE.plusSeconds(1));assertThrows(IllegalArgumentException.class,()->budget(old,a));}
    @Test void actualFactoryIsReviewedJaflyOwnedLeaseRatherThanOwnerDuplicate(){var driver=new InsuranceQuotePurposeTransactionTest.Driver();try(var c=new org.springframework.context.support.GenericApplicationContext()){c.refresh();var converter=new org.springframework.data.r2dbc.core.DefaultReactiveDataAccessStrategy(org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE).getConverter();var tx=new InsuranceQuotePurposeTransaction(driver,converter,c,clock,ticks);assertEquals("org.fireflyframework.core.transaction.OwnedLeaseConnectionFactory",tx.operations().getDatabaseClient().getConnectionFactory().getClass().getName());assertEquals(0,driver.creates);}}
}

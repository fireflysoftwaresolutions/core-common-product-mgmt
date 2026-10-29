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
import org.junit.jupiter.api.Test;
class InsuranceQuotePurposeBackendContractTest {
    @Test void exactReadonlyBackendAndMandatoryReferenceResolverAreExplicitInterfaces() throws Exception {
        for(String name:new String[]{"io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeReadStore","io.fireflysoftware.core.product.core.services.definition.InsuranceQuotePurposeReferenceResolver","io.fireflysoftware.core.product.core.services.definition.InsuranceQuotePurposeService"}){
            final Class<?> type;try{type=Class.forName(name);}catch(ClassNotFoundException missing){throw new AssertionError("Exact purpose backend/source contract missing: "+name,missing);}assertTrue(type.isInterface());
        }
    }

    private static Object window(java.time.Instant stamp,long dbNow,long until,long before) {
        try{return Class.forName("io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeReadStore$NativeWindow").getMethod("from",java.time.Instant.class,long.class,long.class,long.class).invoke(null,stamp,dbNow,until,before);}
        catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}
        catch(ReflectiveOperationException e){throw new AssertionError("Native window implementation missing",e);}
    }
    private static java.time.Duration remaining(Object w,java.time.Instant now,long nano) {
        try{return (java.time.Duration)w.getClass().getMethod("remaining",java.time.Instant.class,long.class).invoke(w,now,nano);}
        catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    @Test void nativeFloorQuantizationChargesOneMillisecondAndKeepsExactStamp(){var stamp=java.time.Instant.parse("2026-10-26T12:00:00.123456Z");long db=stamp.toEpochMilli();var w=window(stamp,db,db+1000,10);assertEquals(java.time.Duration.ofMillis(999),remaining(w,stamp,10));assertThrows(IllegalStateException.class,()->remaining(w,stamp,999_000_010));}
    @Test void nativeAbsoluteClockCannotOutliveDeadline(){var stamp=java.time.Instant.parse("2026-10-26T12:00:00Z");long db=stamp.toEpochMilli();var w=window(stamp,db,db+1000,0);assertThrows(IllegalStateException.class,()->remaining(w,stamp.plusSeconds(1),1));}
    @Test void nativeMonotonicClockCannotGoBackwards(){var stamp=java.time.Instant.parse("2026-10-26T12:00:00Z");long db=stamp.toEpochMilli();var w=window(stamp,db,db+1000,10);assertThrows(IllegalStateException.class,()->remaining(w,stamp,9));}
    @Test void mismatchedNativeClockOrTooLongHorizonRefuses(){var stamp=java.time.Instant.parse("2026-10-26T12:00:00Z");long db=stamp.toEpochMilli();assertThrows(IllegalArgumentException.class,()->window(stamp,db+1,db+1000,0));assertThrows(IllegalArgumentException.class,()->window(stamp,db,db+30001,0));}
}

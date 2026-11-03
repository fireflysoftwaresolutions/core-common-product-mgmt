/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.product.core.services.definition;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import reactor.core.publisher.Mono;

/** Current namespace observation; it cannot grant the caller a product permission. */
public interface InsuranceQuoteTenantBindingResolver {
    Mono<Observation> resolve(SecurityPrincipal original,UUID resourceTenant,Instant originalUntil,Supplier<Duration> remaining);
    record Observation(String issuer,String platformTenant,UUID resourceTenant,UUID bindingId,long version,
            String definitionDigest,long controlVersion,long tenantGeneration,UUID statusId,long statusGeneration,
            Instant observedAt,Instant validUntil,Instant businessUntil) {
        public Observation {
            if(issuer==null||issuer.isBlank()||platformTenant==null||platformTenant.isBlank()
                    ||resourceTenant==null||bindingId==null||statusId==null||resourceTenant.equals(new UUID(0,0))
                    ||bindingId.equals(new UUID(0,0))||statusId.equals(new UUID(0,0))||version<1||version==Long.MAX_VALUE
                    ||controlVersion<1||controlVersion==Long.MAX_VALUE||tenantGeneration<0||tenantGeneration==Long.MAX_VALUE
                    ||statusGeneration<0||statusGeneration==Long.MAX_VALUE||definitionDigest==null||!definitionDigest.matches("[0-9a-f]{64}")
                    ||observedAt==null||validUntil==null||businessUntil==null||!observedAt.isBefore(validUntil)
                    ||validUntil.isAfter(observedAt.plusSeconds(5))||validUntil.isAfter(businessUntil))throw new IllegalArgumentException("Invalid current quote tenant binding");
        }
        public boolean sameAuthority(Observation other) {
            return other!=null&&issuer.equals(other.issuer)&&platformTenant.equals(other.platformTenant)
                &&resourceTenant.equals(other.resourceTenant)&&bindingId.equals(other.bindingId)&&version==other.version
                &&definitionDigest.equals(other.definitionDigest)&&controlVersion==other.controlVersion
                &&tenantGeneration==other.tenantGeneration&&statusId.equals(other.statusId)&&statusGeneration==other.statusGeneration
                &&businessUntil.equals(other.businessUntil);
        }
        @Override public String toString(){return "InsuranceQuoteTenantBindingObservation";}
    }
}

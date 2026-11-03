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

package io.fireflysoftware.core.product.core.services.definition.impl;

import io.fireflysoftware.core.product.models.repositories.definition.purpose.*;
import io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeReadStore.*;
import io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeRepositories.*;
import java.time.*;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.springframework.context.ApplicationContext;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import reactor.core.publisher.Mono;

/** One same-factory physical read-only RR transaction, with serial bounded mapped queries. */
public final class InsuranceQuotePurposeReadStoreImpl implements InsuranceQuotePurposeReadStore {
    private final InsuranceQuotePurposeTransaction transaction;
    private final Roots roots;private final Revisions revisions;private final Publications publications;private final Controls controls;private final Versions versions;
    private final InsuranceQuotePurposeRepositories.Native context;
    private final Clock clock;private final LongSupplier nanos;
    public InsuranceQuotePurposeReadStoreImpl(InsuranceQuotePurposeTransaction transaction,ApplicationContext application,Clock clock,LongSupplier nanos) {
        this.transaction=Objects.requireNonNull(transaction);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
        var factory=new R2dbcRepositoryFactory(transaction.operations());factory.setBeanFactory(application);
        roots=factory.getRepository(Roots.class);revisions=factory.getRepository(Revisions.class);publications=factory.getRepository(Publications.class);
        controls=factory.getRepository(Controls.class);versions=factory.getRepository(Versions.class);context=factory.getRepository(InsuranceQuotePurposeRepositories.Native.class);
    }
    @Override public Mono<Read> read(Admission admission) {
        return transaction.read(admission,invocation->{
            var scope=admission.scope();long before=nanos.getAsLong();
            return context.open(scope.tenantId(),scope.rootId(),scope.productId(),scope.revisionId(),scope.publicationId(),admission.issuer(),admission.actor(),admission.actorUntil().toEpochMilli(),admission.permitUntil().toEpochMilli())
                .switchIfEmpty(Mono.error(unavailable())).flatMap(stamp->{
                    if(stamp.contextId()==null || stamp.recordedAt()==null || stamp.dbNowMillis()==null || stamp.validUntilMillis()==null
                            || stamp.validUntilMillis()>admission.actorUntil().toEpochMilli() || stamp.validUntilMillis()>admission.permitUntil().toEpochMilli())throw unavailable();
                    var window=NativeWindow.from(stamp.recordedAt().toInstant(),stamp.dbNowMillis(),stamp.validUntilMillis(),before);invocation.observeNative(window);
                    return roots.findByTenantIdAndRootId(scope.tenantId(),scope.rootId()).flatMap(root->
                        revisions.findByTenantIdAndRootIdAndRevisionId(scope.tenantId(),scope.rootId(),scope.revisionId()).flatMap(revision->
                            publications.findByTenantIdAndRootIdAndPublicationId(scope.tenantId(),scope.rootId(),scope.publicationId()).flatMap(publication->
                                controls.findByTenantIdAndRootIdAndPublicationId(scope.tenantId(),scope.rootId(),scope.publicationId()).flatMap(control->
                                    versions.findFirstByTenantIdAndRootIdOrderByVersionNumberDesc(scope.tenantId(),scope.rootId()).flatMap(latest->{
                                        var configuration=InsuranceQuotePurposeProjection.project(scope,root,revision,publication,control,latest);
                                        var end=configuration.definition().common().effectiveTo();var admittedWindow=end==null?window:window.narrowUntil(Instant.parse(end));invocation.observeNative(admittedWindow);
                                        admittedWindow.remaining(clock.instant(),nanos.getAsLong());admission.remaining(clock.instant(),nanos.getAsLong());
                                        return context.isCurrent(stamp.contextId()).filter(Boolean.TRUE::equals).switchIfEmpty(Mono.error(unavailable()))
                                            .map(live->{admittedWindow.remaining(clock.instant(),nanos.getAsLong());return new Read(configuration,admittedWindow);});
                                    }))))).switchIfEmpty(Mono.error(unavailable()));
                });
        }).onErrorMap(error->unavailable());
    }
    private static IllegalStateException unavailable(){return new IllegalStateException("INSURANCE_QUOTE_PURPOSE_READ_UNAVAILABLE");}
    @Override public String toString(){return "InsuranceQuotePurposeReadStore";}
}

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

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.TermPcInsuranceTerms;
import io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeReadStore;
import io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeReadStore.*;
import io.fireflysoftware.core.product.core.services.definition.InsuranceQuotePurposeReferenceResolver.VerifiedReferences;
import java.time.*;
import java.util.*;
import java.util.function.LongSupplier;
import reactor.core.publisher.Mono;

/** Functional current-configuration projection; it does not decide applicant eligibility or bind cover. */
public final class InsuranceQuotePurposeServiceImpl implements InsuranceQuotePurposeService {
    private final InsuranceQuotePurposeAuthorization authority;
    private final InsuranceQuotePurposeReadStore store;
    private final InsuranceQuotePurposeReferenceResolver references;
    private final Clock clock;
    private final LongSupplier nanos;
    public InsuranceQuotePurposeServiceImpl(InsuranceQuotePurposeAuthorization authority,InsuranceQuotePurposeReadStore store,
            InsuranceQuotePurposeReferenceResolver references,Clock clock,LongSupplier nanos) {
        this.authority=Objects.requireNonNull(authority);this.store=Objects.requireNonNull(store);this.references=Objects.requireNonNull(references);
        this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
    }
    @Override public Mono<Observation> observe(Scope scope) {
        return ProductDefinitionCommandTransactionBoundary.outsideSql(()->authority.authorize(scope).flatMap(permit->{
            var admission=new Admission(scope,permit.issuer(),permit.subject(),permit.actorUntil(),permit.until(),permit.observedAt(),permit.beforeNanos());
            return load(admission).flatMap(first->{
                validate(scope,permit,first);var firstWindow=definitionWindow(first);long beforeReferences=nanos.getAsLong();
                return ProductDefinitionCommandTransactionBoundary.outsideSql(()->Mono.defer(()->references.verify(first.configuration())))
                    .timeout(firstWindow.remaining(clock.instant(),nanos.getAsLong())).switchIfEmpty(Mono.error(unavailable()))
                    .flatMap(resolved->{
                        verifyReferences(first.configuration(),resolved);
                        var referenceWindow=firstWindow.narrowExternal(resolved.observedAt(),resolved.effectiveUntil(),beforeReferences);
                        referenceWindow.remaining(clock.instant(),nanos.getAsLong());permit.remaining();
                        return load(admission.withPriorWindow(referenceWindow)).timeout(referenceWindow.remaining(clock.instant(),nanos.getAsLong())).flatMap(current->{
                            validate(scope,permit,current);
                            if(!InsuranceQuotePurposeCodec.digest(first.configuration()).equals(InsuranceQuotePurposeCodec.digest(current.configuration())))throw unavailable();
                            var finalWindow=referenceWindow.intersect(definitionWindow(current));finalWindow.remaining(clock.instant(),nanos.getAsLong());permit.remaining();
                            return authority.reauthorize(permit).timeout(finalWindow.remaining(clock.instant(),nanos.getAsLong()))
                                .then(Mono.defer(()->{
                                    permit.remaining();finalWindow.remaining(clock.instant(),nanos.getAsLong());effective(current.configuration(),clock.instant());
                                    var response=new Observation(current.configuration(),current.window().recordedAt(),finalWindow.until());
                                    permit.remaining();finalWindow.remaining(clock.instant(),nanos.getAsLong());return Mono.just(response);
                                }));
                        });
                    });
            }).timeout(permit.remaining());
        })).onErrorMap(error->unavailable());
    }
    private Mono<Read> load(Admission admission){return Mono.defer(()->store.read(admission)).timeout(admission.remaining(clock.instant(),nanos.getAsLong())).switchIfEmpty(Mono.error(unavailable()));}
    private void validate(Scope scope,InsuranceQuotePurposeAuthorization.Permit permit,Read read) {
        if(read==null || !scope.equals(read.configuration().scope()) || read.window().until().isAfter(permit.until())
                || read.window().until().isAfter(permit.actorUntil()))throw unavailable();
        InsuranceQuotePurposeCodec.encode(read.configuration());effective(read.configuration(),read.window().recordedAt());
        permit.remaining();read.window().remaining(clock.instant(),nanos.getAsLong());
    }
    private void verifyReferences(Configuration configuration,VerifiedReferences resolved) {
        if(resolved==null || !configuration.scope().equals(resolved.scope()) || !InsuranceQuotePurposeCodec.digest(configuration).equals(resolved.configurationDigest())
                || !configuration.definition().common().market().currencyPolicy().equals(resolved.currencyPolicy())
                || !required(configuration).equals(resolved.references()) || resolved.observedAt().isAfter(clock.instant())
                || !clock.instant().isBefore(resolved.effectiveUntil()))throw unavailable();
    }
    private static Set<ExactReference> required(Configuration configuration) {
        var result=new HashSet<ExactReference>();var terms=(TermPcInsuranceTerms)configuration.definition().terms();
        terms.taxComponents().forEach(value->result.add(value.policy()));terms.coverages().forEach(value->result.add(value.wording()));result.addAll(terms.exclusions());
        for(var document:configuration.definition().common().requirements().documents()){
            if(document.template()!=null)result.add(document.template());if(document.issuer()!=null)result.add(document.issuer());if(document.layout()!=null)result.add(document.layout());
        }
        return Set.copyOf(result);
    }
    private static NativeWindow definitionWindow(Read read) {
        var end=read.configuration().definition().common().effectiveTo();
        return end==null?read.window():read.window().narrowUntil(Instant.parse(end));
    }
    private static void effective(Configuration configuration,Instant at) {
        var common=configuration.definition().common();var from=Instant.parse(common.effectiveFrom());var to=common.effectiveTo()==null?null:Instant.parse(common.effectiveTo());
        if(at.isBefore(from) || to!=null && !at.isBefore(to))throw unavailable();
    }
    private static IllegalStateException unavailable(){return new IllegalStateException("INSURANCE_QUOTE_PURPOSE_UNAVAILABLE");}
    @Override public String toString(){return "InsuranceQuotePurposeService";}
}

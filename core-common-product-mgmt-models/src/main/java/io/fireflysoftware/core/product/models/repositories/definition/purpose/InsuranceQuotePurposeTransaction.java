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

package io.fireflysoftware.core.product.models.repositories.definition.purpose;

import io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeReadStore.*;
import io.r2dbc.spi.ConnectionFactory;
import java.time.*;
import java.util.Objects;
import java.util.function.*;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction;
import org.springframework.context.ApplicationContext;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Mono;

/** Owner policy adapter over the exact source-qualified opt-in Jafly aggregate. */
public final class InsuranceQuotePurposeTransaction {
    private final R2dbcEntityTemplate operations;
    private final R2dbcTransactionManager manager;
    private final BoundedRootTransaction transaction;
    private final DefaultTransactionDefinition definition;
    private final Clock clock;
    private final LongSupplier nanos;
    public InsuranceQuotePurposeTransaction(ConnectionFactory reader,R2dbcConverter converter,ApplicationContext context,Clock clock,LongSupplier nanos) {
        this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
        var factory=new OwnedLeaseConnectionFactory(Objects.requireNonNull(reader));var client=DatabaseClient.create(factory);
        operations=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE,Objects.requireNonNull(converter));operations.setBeanFactory(context);operations.setApplicationContext(context);
        manager=new R2dbcTransactionManager(factory);manager.setEnforceReadOnly(true);
        transaction=new DefaultBoundedRootTransaction(BoundedR2dbcResources.verify(factory,client,operations,manager),new CleanupPolicy(Duration.ofSeconds(2),Duration.ofSeconds(2)));
        definition=new DefaultTransactionDefinition();definition.setName("product-insurance-quote-purpose-read");definition.setReadOnly(true);
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);definition.setTimeout(5);
    }
    public R2dbcEntityTemplate operations(){return operations;}
    public R2dbcTransactionManager manager(){return manager;}
    public <T> Mono<T> read(Admission admission,Function<Invocation,Mono<T>> work) {
        return Mono.defer(()->transaction.execute(admission.operationBudget(clock,nanos),definition,scope->Mono.defer(()->work.apply(new Invocation(scope,admission)))))
                .map(RootCompletion::value).onErrorMap(RootTransactionException.class,error->new ReadUncertain(error.transactionFacts().commitAcknowledged()));
    }
    public final class Invocation {
        private final RootTransactionScope scope;private final Admission original;
        private Invocation(RootTransactionScope scope,Admission original){this.scope=scope;this.original=original;}
        public void observeNative(NativeWindow window){scope.narrow(Objects.requireNonNull(window).operationBudget(original,clock,nanos));}
        public Duration remaining(){return scope.remaining();}
    }
    /** Technical readonly boundary fact only, never a business commit/no-effect claim. */
    public static final class ReadUncertain extends IllegalStateException {
        private final boolean commitKnown;
        public ReadUncertain(boolean commitKnown){super("INSURANCE_PURPOSE_READ_COMPLETION_UNCERTAIN");this.commitKnown=commitKnown;}
        public boolean commitKnown(){return commitKnown;}
    }
    @Override public String toString(){return "InsuranceQuotePurposeTransaction";}
}

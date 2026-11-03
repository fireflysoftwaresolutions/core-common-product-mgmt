package io.fireflysoftware.core.product.core.services.definition.impl;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.util.function.Supplier;

final class ProductDefinitionCommandTransactionBoundary {
    private ProductDefinitionCommandTransactionBoundary() {}

    static <T> Mono<T> outsideSql(Supplier<Mono<T>> action) {
        return Mono.defer(
                () ->
                        TransactionSynchronizationManager.forCurrentTransaction()
                                .map(TransactionSynchronizationManager::isActualTransactionActive)
                                .onErrorResume(
                                        NoTransactionException.class, error -> Mono.just(false))
                                .flatMap(
                                        active ->
                                                active
                                                        ? Mono.error(
                                                                new ResponseStatusException(
                                                                        HttpStatus
                                                                                .SERVICE_UNAVAILABLE,
                                                                        "Product command admission"
                                                                                + " unavailable"))
                                                        : Mono.defer(action)));
    }
}

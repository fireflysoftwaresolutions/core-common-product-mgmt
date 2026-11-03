package io.fireflysoftware.core.product.core.services.definition;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

final class ProductDefinitionFixtureGate<T> {
    private final Sinks.One<T> observation = Sinks.one();
    private final Sinks.One<Void> outcome = Sinks.one();

    Mono<T> pause(T value) {
        return Mono.defer(
                () -> {
                    if (observation.tryEmitValue(value) != Sinks.EmitResult.OK) {
                        return Mono.error(
                                new IllegalStateException(
                                        "Owned fixture gate was already reached"));
                    }
                    return outcome.asMono().thenReturn(value);
                });
    }

    Mono<T> reached() {
        return observation.asMono();
    }

    void release() {
        outcome.tryEmitEmpty();
    }

    void fail(RuntimeException failure) {
        outcome.tryEmitError(failure);
    }
}

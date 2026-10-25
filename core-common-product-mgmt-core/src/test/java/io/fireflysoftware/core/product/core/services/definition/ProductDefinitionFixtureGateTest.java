package io.fireflysoftware.core.product.core.services.definition;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

class ProductDefinitionFixtureGateTest {
    @Test
    void retainsTheActualPublisherUntilTheTestThreadReleasesItWithoutBlockingTheScheduler() {
        var gate = new ProductDefinitionFixtureGate<String>();
        var observed = new AtomicReference<String>();
        var error = new AtomicReference<Throwable>();
        var subscription =
                gate.pause("same transaction")
                        .subscribeOn(Schedulers.parallel())
                        .subscribe(observed::set, error::set);
        try {
            assertThat(gate.reached().block(Duration.ofSeconds(2))).isEqualTo("same transaction");
            assertThat(observed.get()).isNull();
            assertThat(error.get()).isNull();
            assertThat(
                            reactor.core.publisher.Mono.just("scheduler remains live")
                                    .subscribeOn(Schedulers.parallel())
                                    .block(Duration.ofSeconds(2)))
                    .isEqualTo("scheduler remains live");
            gate.release();
            reactor.test.StepVerifier.create(gate.reached())
                    .expectNext("same transaction")
                    .verifyComplete();
            org.awaitility.Awaitility.await()
                    .atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(observed.get()).isEqualTo("same transaction"));
        } finally {
            subscription.dispose();
        }
    }

    @Test
    void aTestCallbackFailurePropagatesToTheRetainedTransactionInsteadOfCompletingIndependently() {
        var gate = new ProductDefinitionFixtureGate<String>();
        var failure = new IllegalStateException("owned callback failure");
        reactor.test.StepVerifier.create(gate.pause("uncommitted"))
                .then(() -> gate.fail(failure))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void cancellationDoesNotReportACommitOrInventACompletedReceipt() {
        var gate = new ProductDefinitionFixtureGate<String>();
        reactor.test.StepVerifier.create(gate.pause("uncommitted"))
                .thenCancel()
                .verify(Duration.ofSeconds(2));
        assertThat(gate.reached().block(Duration.ofSeconds(2))).isEqualTo("uncommitted");
    }
}

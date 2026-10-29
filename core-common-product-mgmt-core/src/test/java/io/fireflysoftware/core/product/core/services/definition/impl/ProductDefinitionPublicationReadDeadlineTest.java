package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionPublicationReadPersistence;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

class ProductDefinitionPublicationReadDeadlineTest {
    final ProductDefinitionPublicationReadServiceTest fixture;
    final MutableClock clock;
    final AtomicLong nanos = new AtomicLong();

    ProductDefinitionPublicationReadDeadlineTest() throws Exception {
        fixture = new ProductDefinitionPublicationReadServiceTest();
        clock = new MutableClock(fixture.f.stamp);
    }

    ProductDefinitionPublicationReadService service(
            ProductDefinitionPublicationReadAuthorization auth) {
        try {
            var constructor =
                    ProductDefinitionPublicationReadServiceImpl.class.getDeclaredConstructor(
                            ProductDefinitionPublicationReadAuthorization.class,
                                    ProductDefinitionPublicationReadPersistence.class,
                            Clock.class, LongSupplier.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    auth, fixture.persistence, clock, (LongSupplier) nanos::get);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Missing deterministic native-deadline clock boundary", error);
        }
    }

    void denied(Mono<?> result) {
        assertThatThrownBy(result::block)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void postReadAuthorityReceivesTheNarrowerNativeDeadline() {
        service(fixture.auth).readiness(fixture.f.tenant, fixture.f.root).block();
        var argument =
                org.mockito.ArgumentCaptor.forClass(
                        ProductDefinitionPublicationReadAuthorization.Admission.class);
        verify(fixture.auth).reauthorize(argument.capture());
        assertThat(argument.getValue().validUntilEpochMillis())
                .isEqualTo(fixture.f.stamp.plusSeconds(20).toEpochMilli());
    }

    @Test
    void actualAuthorityRejectsPdpCompletionAfterNativeExpiryInsideOriginalPermit() {
        var issuer = "https://issuer.example.invalid";
        var actor =
                SecurityPrincipal.builder()
                        .issuer(issuer)
                        .subject("resumed-maker")
                        .tenantId(fixture.f.tenant.toString())
                        .scopes(Set.of("product:definitions:publish"))
                        .claims(Map.of("exp", fixture.f.stamp.plusSeconds(300)))
                        .build();
        var calls = new AtomicInteger();
        var auth =
                new ProductDefinitionCommandAuthorizationImpl(
                        () -> Mono.just(actor),
                        (principal, action, resource, context) ->
                                fixture.f
                                        .outside("pdp")
                                        .then(
                                                Mono.defer(
                                                        () -> {
                                                            if (calls.incrementAndGet() == 2)
                                                                clock.advance(
                                                                        Duration.ofSeconds(21));
                                                            return Mono.just(Decision.permit());
                                                        })),
                        clock,
                        Set.of(issuer),
                        ProductDefinitionCommandAuthorization.POLICY_PROFILE);
        denied(service(auth).readiness(fixture.f.tenant, fixture.f.root));
        assertThat(calls).hasValue(2);
        assertThat(fixture.f.order).containsSubsequence("commit", "pdp");
    }

    @Test
    void finalEmissionChecksNativeExpiryEvenWhenPostReadAuthorityCompletes() {
        when(fixture.auth.reauthorize(any()))
                .thenAnswer(
                        call ->
                                fixture.f
                                        .outside("reauthorize")
                                        .then(
                                                Mono.fromRunnable(
                                                        () ->
                                                                clock.advance(
                                                                        Duration.ofSeconds(21)))));
        denied(service(fixture.auth).readiness(fixture.f.tenant, fixture.f.root));
    }

    @Test
    void monotonicBudgetCannotBeExtendedByALaggingApplicationClock() {
        clock.advance(Duration.ofSeconds(-10));
        when(fixture.auth.reauthorize(any()))
                .thenAnswer(
                        call ->
                                fixture.f
                                        .outside("reauthorize")
                                        .then(
                                                Mono.fromRunnable(
                                                        () ->
                                                                nanos.set(
                                                                        Duration.ofSeconds(21)
                                                                                .toNanos()))));
        denied(service(fixture.auth).readiness(fixture.f.tenant, fixture.f.root));
    }

    @Test
    void validCompletionRetainsTheExactNativeDeadlineWithoutRenewal() {
        when(fixture.auth.reauthorize(any()))
                .thenAnswer(
                        call ->
                                fixture.f
                                        .outside("reauthorize")
                                        .then(
                                                Mono.fromRunnable(
                                                        () -> {
                                                            clock.advance(Duration.ofSeconds(19));
                                                            nanos.set(
                                                                    Duration.ofSeconds(19)
                                                                            .toNanos());
                                                        })));
        var result = service(fixture.auth).readiness(fixture.f.tenant, fixture.f.root).block();
        assertThat(result.observation().admissionValidUntil())
                .isEqualTo(fixture.f.stamp.plusSeconds(20));
    }

    @Test
    void expiredNativeBudgetAfterCommitFailsBeforePostReadPdp() {
        when(fixture.nativeReads.isReadinessCurrent(any()))
                .thenReturn(
                        fixture.inside("native-live", true),
                        fixture.inside("native-live", true)
                                .doOnNext(value -> nanos.set(Duration.ofSeconds(21).toNanos())));
        denied(service(fixture.auth).readiness(fixture.f.tenant, fixture.f.root));
        verify(fixture.auth, never()).reauthorize(any());
        assertThat(fixture.f.order).endsWith("commit");
    }

    static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException();
            return this;
        }

        public Instant instant() {
            return instant;
        }
    }
}

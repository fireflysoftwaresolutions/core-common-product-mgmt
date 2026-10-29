package io.fireflysoftware.core.product.core.services.definition.impl;

import static org.assertj.core.api.Assertions.*;

import io.r2dbc.spi.R2dbcNonTransientException;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ProductDefinitionCommandErrorsTest {
    Throwable map(Throwable input) {
        try {
            return (Throwable)
                    Class.forName(
                                    "io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionCommandErrors")
                            .getDeclaredMethod("safe", Throwable.class)
                            .invoke(null, input);
        } catch (ReflectiveOperationException missing) {
            throw new AssertionError("Missing exact owner native error boundary", missing);
        }
    }

    @Test
    void ownerSqlStatesAreMappedWithoutDatabaseMessagesOrCauses() {
        for (var pair :
                java.util.Map.of("PD409", 409, "PD403", 403, "PD404", 404, "PD503", 503)
                        .entrySet()) {
            var db = new R2dbcNonTransientException("private diagnostic payload", pair.getKey()) {};
            var wrapped =
                    new org.springframework.r2dbc.UncategorizedR2dbcException(
                            "operation", "SELECT private diagnostic payload", db);
            var mapped = (ResponseStatusException) map(wrapped);
            assertThat(mapped.getStatusCode().value()).isEqualTo(pair.getValue());
            assertThat(mapped.getReason()).doesNotContain("private diagnostic payload", "SELECT");
            assertThat(mapped.getCause()).isNull();
        }
    }

    @Test
    void ordinaryDuplicateAndOptimisticConflictsRemainSanitized409() {
        assertThat(
                        ((ResponseStatusException)
                                        map(
                                                new org.springframework.dao.DuplicateKeyException(
                                                        "private index")))
                                .getStatusCode()
                                .value())
                .isEqualTo(409);
        assertThat(
                        ((ResponseStatusException)
                                        map(
                                                new org.springframework.dao
                                                        .OptimisticLockingFailureException(
                                                        "private row")))
                                .getStatusCode()
                                .value())
                .isEqualTo(409);
    }

    @Test
    void unknownStatesAndMessageTextDoNotBecomeOwnerPermissionOrConflictCodes() {
        assertThat(
                        ((ResponseStatusException)
                                        map(
                                                new R2dbcNonTransientException(
                                                        "PD409 private diagnostic", "XX000") {}))
                                .getStatusCode()
                                .value())
                .isEqualTo(503);
        assertThat(
                        ((ResponseStatusException) map(new RuntimeException("PD403")))
                                .getStatusCode()
                                .value())
                .isEqualTo(503);
    }

    @Test
    void existingBoundedApplicationStatusIsPreserved() {
        var error =
                new ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Exact product command required");
        assertThat(map(error)).isSameAs(error);
    }
}

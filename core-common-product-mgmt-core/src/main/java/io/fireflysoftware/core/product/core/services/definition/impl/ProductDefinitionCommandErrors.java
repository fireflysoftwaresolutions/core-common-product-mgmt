package io.fireflysoftware.core.product.core.services.definition.impl;

import io.r2dbc.spi.R2dbcException;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class ProductDefinitionCommandErrors {
    private ProductDefinitionCommandErrors() {}

    static Throwable safe(Throwable error) {
        if (error instanceof ResponseStatusException) return error;
        Throwable current = error;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof DuplicateKeyException
                    || current instanceof OptimisticLockingFailureException)
                return status(HttpStatus.CONFLICT);
            if (current instanceof R2dbcException sql) {
                String state = sql.getSqlState();
                if ("PD409".equals(state) || "23505".equals(state))
                    return status(HttpStatus.CONFLICT);
                if ("PD403".equals(state)) return status(HttpStatus.FORBIDDEN);
                if ("PD404".equals(state)) return status(HttpStatus.NOT_FOUND);
                if ("PD503".equals(state)) return status(HttpStatus.SERVICE_UNAVAILABLE);
            }
            var next = current.getCause();
            if (next == current) break;
            current = next;
        }
        return status(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static ResponseStatusException status(HttpStatus status) {
        String message =
                switch (status) {
                    case CONFLICT -> "Product definition selection conflict";
                    case FORBIDDEN -> "Current product permission required";
                    case NOT_FOUND -> "Product definition not found";
                    default -> "Product lifecycle unavailable";
                };
        return new ResponseStatusException(status, message);
    }
}

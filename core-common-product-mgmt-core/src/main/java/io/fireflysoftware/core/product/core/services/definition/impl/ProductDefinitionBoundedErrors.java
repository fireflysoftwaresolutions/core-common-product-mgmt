package io.fireflysoftware.core.product.core.services.definition.impl;

import java.util.function.Supplier;
import org.fireflyframework.core.transaction.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Retain only a safe owner status; uncertain completion never becomes an ordinary refusal. */
final class ProductDefinitionBoundedErrors {
    private volatile Integer status;
    <T> Mono<T> capture(Mono<T> work) {
        return work.doOnError(error->{
            var safe=ProductDefinitionCommandErrors.safe(error);
            if(safe instanceof ResponseStatusException response){
                int code=response.getStatusCode().value();
                if(code==400 || code==403 || code==404 || code==409)status=code;
            }
        });
    }
    Throwable resolve(RootTransactionException failure,Supplier<R2dbcOperationBudget> current) {
        var facts=failure.transactionFacts();
        if(status==null || (failure.stage()!=RootTransactionException.Stage.WORK && failure.stage()!=RootTransactionException.Stage.ROLLBACK)
                || facts.commitAcknowledged() || !facts.rollbackAcknowledged() || failure.cleanupFact()!=CleanupFact.COMPLETE)return failure;
        try{current.get().remaining();}catch(RuntimeException exhausted){return failure;}
        var code=HttpStatus.valueOf(status);
        String message=switch(code){
            case BAD_REQUEST->"Exact product command required";
            case FORBIDDEN->"Current product permission required";
            case NOT_FOUND->"Product definition not found";
            case CONFLICT->"Product definition selection conflict";
            default->throw new IllegalStateException("Unsupported product status");
        };
        return new ResponseStatusException(code,message);
    }
}

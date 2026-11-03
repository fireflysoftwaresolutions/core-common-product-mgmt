package io.fireflysoftware.core.product.web.adapters.document;

import io.fireflysoftware.core.product.core.services.definition.DepositAccountDocumentReferenceResolver.Target;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import reactor.core.publisher.Mono;

/** Trusted delegation must verify the actual actor, issuer, audience and exact resource authority. */
public interface ProductDocumentOwnerTokenProvider {
    enum Owner { CONFIGURATION, MASTERS }
    record AuthorizedToken(Owner owner,Target target,String issuer,String subject,UUID resourceTenant,
            Instant until,String token) {
        public AuthorizedToken {
            Objects.requireNonNull(owner);Objects.requireNonNull(target);Objects.requireNonNull(resourceTenant);
            Objects.requireNonNull(until);
            if(issuer==null||issuer.isBlank()||subject==null||subject.isBlank()||token==null||token.isBlank()
                    ||token.chars().anyMatch(c->Character.isISOControl(c)||Character.isWhitespace(c)))
                throw new IllegalArgumentException("Trusted owner delegation required");
        }
        @Override public String toString(){return "ProductDocumentOwnerToken";}
    }
    Mono<AuthorizedToken> authorize(Owner owner,Target target,R2dbcOperationBudget originalBudget,Instant inheritedUntil);
}

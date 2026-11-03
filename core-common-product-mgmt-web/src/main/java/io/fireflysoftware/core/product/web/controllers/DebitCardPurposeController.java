package io.fireflysoftware.core.product.web.controllers;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DebitCardPurposeContracts.*;
import io.fireflysoftware.core.product.web.config.definition.lifecycle.DebitCardPurposeRuntime;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/product-definitions/{rootId}/debit-card-configurations")
@ConditionalOnProperty(prefix="product.definitions.debit-card-read",name="enabled",havingValue="true")
@SecurityRequirement(name="productDefinitionBearer")
public final class DebitCardPurposeController {
    private final DebitCardPurposeRuntime runtime;
    public DebitCardPurposeController(DebitCardPurposeRuntime runtime){this.runtime=Objects.requireNonNull(runtime);}
    @GetMapping("/{productId}/revisions/{revisionId}/publications/{publicationId}")
    @org.fireflyframework.web.idempotency.annotation.DisableIdempotency
    @Operation(operationId="observeCurrentDebitCardDefinition",summary="Observe exact current debit-card configuration",description="Requires current CARD_ENROLLMENT permission, native read context and genuine current document and currency owner observations. This metadata observation does not establish KYC, account eligibility, card issuance or issuer execution.")
    public Mono<ResponseEntity<Observation>> observe(@PathVariable UUID tenantId,@PathVariable UUID rootId,@PathVariable UUID productId,@PathVariable UUID revisionId,@PathVariable UUID publicationId){
        return runtime.service().observe(new Scope(tenantId,rootId,productId,revisionId,publicationId))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Debit card configuration unavailable")))
            .onErrorMap(IllegalStateException.class,error->new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Debit card configuration unavailable"))
            .map(value->ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).varyBy("Authorization").body(value));
    }
}

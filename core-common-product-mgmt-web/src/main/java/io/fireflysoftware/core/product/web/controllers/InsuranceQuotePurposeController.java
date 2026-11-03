package io.fireflysoftware.core.product.web.controllers;
import io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.*;
import io.fireflysoftware.core.product.web.config.definition.lifecycle.InsuranceQuotePurposeRuntime;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/product-definitions/{rootId}/insurance-quote-configurations")
@ConditionalOnProperty(prefix="product.definitions.insurance-quote-read",name="enabled",havingValue="true")
@SecurityRequirement(name="productDefinitionBearer")
public final class InsuranceQuotePurposeController {
    private final InsuranceQuotePurposeRuntime runtime;
    public InsuranceQuotePurposeController(InsuranceQuotePurposeRuntime runtime){this.runtime=Objects.requireNonNull(runtime);}
    @GetMapping("/{productId}/revisions/{revisionId}/publications/{publicationId}")
    @Operation(summary="Observe exact current fixed-premium TERM_PC configuration",description="Requires current owner-purpose permission, native read context and genuine current reference records. This metadata observation establishes neither applicant eligibility nor insurance cover or financial admission.")
    public Mono<ResponseEntity<Observation>> observe(@PathVariable UUID tenantId,@PathVariable UUID rootId,@PathVariable UUID productId,@PathVariable UUID revisionId,@PathVariable UUID publicationId){
        return runtime.service().observe(new Scope(tenantId,rootId,productId,revisionId,publicationId))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Insurance quote configuration unavailable")))
            .map(value->ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).varyBy("Authorization").body(value));
    }
}

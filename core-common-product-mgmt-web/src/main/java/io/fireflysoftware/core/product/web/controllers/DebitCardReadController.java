package io.fireflysoftware.core.product.web.controllers;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.web.config.definition.lifecycle.DebitCardRuntime;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/product-definitions/{rootId}")
@ConditionalOnProperty(prefix="product.definitions.debit-card",name="enabled",havingValue="true")
@SecurityRequirement(name="productDefinitionBearer")
public final class DebitCardReadController {
    private final DebitCardRuntime runtime;
    public DebitCardReadController(DebitCardRuntime runtime){this.runtime=Objects.requireNonNull(runtime);}
    @GetMapping("/staff-cost/debit-card/current") @Operation(operationId="readCurrentDebitCardDefinitionCost",summary="Observe current private DEBIT_CARD costs without product admission")
    public Mono<ResponseEntity<ProductDefinitionCostView>> cost(@PathVariable UUID tenantId,@PathVariable UUID rootId){return response(runtime.costs().readCurrent(tenantId,rootId));}
    @GetMapping("/staff-review/debit-card/selection/current") @Operation(operationId="readCurrentDebitCardDefinitionSelection",summary="Observe the current submitted DEBIT_CARD checker selection")
    public Mono<ResponseEntity<ProductDefinitionReviewSelectionView>> current(@PathVariable UUID tenantId,@PathVariable UUID rootId){return response(runtime.reviews().readCurrentSelection(tenantId,rootId));}
    @GetMapping("/staff-review/debit-card/selections/{submissionId}") @Operation(operationId="readDebitCardDefinitionSelection",summary="Observe an exact current DEBIT_CARD checker selection")
    public Mono<ResponseEntity<ProductDefinitionReviewSelectionView>> selection(@PathVariable UUID tenantId,@PathVariable UUID rootId,@PathVariable UUID submissionId){return response(runtime.reviews().readSelection(tenantId,rootId,submissionId));}
    private static <T> Mono<ResponseEntity<T>> response(Mono<T> source){return source.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Current product private view unavailable"))).map(value->ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).varyBy("Authorization").body(value));}
}

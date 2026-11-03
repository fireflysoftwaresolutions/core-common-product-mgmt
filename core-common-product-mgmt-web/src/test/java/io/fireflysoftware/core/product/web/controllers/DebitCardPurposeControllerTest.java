package io.fireflysoftware.core.product.web.controllers;

import static org.mockito.Mockito.*;
import io.fireflysoftware.core.product.core.services.definition.DebitCardPurposeService;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DebitCardPurposeContracts.*;
import io.fireflysoftware.core.product.web.config.definition.lifecycle.DebitCardPurposeRuntime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class DebitCardPurposeControllerTest {
    final Scope scope=new Scope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
    final DebitCardPurposeService service=mock(DebitCardPurposeService.class);
    WebTestClient client(){var runtime=mock(DebitCardPurposeRuntime.class);when(runtime.service()).thenReturn(service);return WebTestClient.bindToController(new DebitCardPurposeController(runtime)).build();}
    String path(){return "/api/v1/tenants/"+scope.tenantId()+"/product-definitions/"+scope.rootId()+"/debit-card-configurations/"+scope.productId()+"/revisions/"+scope.revisionId()+"/publications/"+scope.publicationId();}
    @Test void emptyCurrentOwnerResponseIsUnavailableAndNeverAConfiguration(){when(service.observe(scope)).thenReturn(Mono.empty());client().get().uri(path()).exchange().expectStatus().isEqualTo(503).expectBody().isEmpty();verify(service).observe(scope);}
    @Test void missingOrRevokedCurrentOwnerEvidenceIsUnavailableAndDoesNotDiscloseInternalMessage(){when(service.observe(scope)).thenReturn(Mono.error(new IllegalStateException("private owner evidence")));client().get().uri(path()).exchange().expectStatus().isEqualTo(503).expectBody().isEmpty();verify(service).observe(scope);}
}

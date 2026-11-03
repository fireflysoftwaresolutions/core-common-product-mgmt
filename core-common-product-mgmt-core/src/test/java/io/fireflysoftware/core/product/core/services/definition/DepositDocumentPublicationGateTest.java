package io.fireflysoftware.core.product.core.services.definition;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCommand;
import io.r2dbc.pool.*;
import java.time.Duration;
import org.fireflyframework.security.api.domain.Decision;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Controlled driver boundary only; no actual native admission is synthesized. */
class DepositDocumentPublicationGateTest {
    @Test void depositPublicationWithoutBoundCurrentDocumentAdmissionNeverAcquiresDatabase() {
        var helper=new TermPcEngineBindingTest();var driver=new InsuranceQuotePurposeTransactionTest.Driver();
        var pool=new ConnectionPool(ConnectionPoolConfiguration.builder(driver).initialSize(0).build());
        try(var context=new GenericApplicationContext()) {
            context.refresh();var backend=GovernedDefinitionProfile.DEPOSIT_ACCOUNT;
            var authority=helper.source.authority(backend.policyProfile(),backend,(p,a,r,c)->Mono.just(Decision.permit()));
            var service=helper.engine(authority,helper.persistence(pool,context,true),backend);
            var request=helper.source.body(ProductDefinitionCommand.PUBLISH,"account").toString();
            var failure=assertThrows(ResponseStatusException.class,()->service.execute(TermPcLifecycleProfileTest.TENANT,
                    TermPcLifecycleProfileTest.ROOT,ProductDefinitionCommand.PUBLISH,request).block());
            assertEquals(503,failure.getStatusCode().value());
            assertEquals(0,driver.creates,"Missing finite document admission must refuse before native database acquisition");
        } finally {pool.disposeLater().block(Duration.ofSeconds(1));}
    }
}

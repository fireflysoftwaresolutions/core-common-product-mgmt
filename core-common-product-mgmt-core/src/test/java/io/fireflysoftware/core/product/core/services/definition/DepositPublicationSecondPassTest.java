package io.fireflysoftware.core.product.core.services.definition;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.repository.Query;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandPersistence;

/** Source contracts only. No native owner admission, publication or financial effect is qualified. */
class DepositPublicationSecondPassTest {
    static Path root(){return Path.of(System.getProperty("product.reference.source-root",System.getProperty("user.dir"))).toAbsolutePath();}
    @Test void lifecycleHostsTheConcretePreparationAndOwnerCoordinator() {
        assertDoesNotThrow(()->Class.forName("io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionLifecycleServiceImpl")
            .getConstructor(ProductDefinitionCommandAuthorization.class,ProductDefinitionCommandPersistence.class,
                GovernedDefinitionProfile.class,Clock.class,LongSupplier.class,
                PreparedDepositPublicationService.class,DepositAccountDocumentReferenceResolver.class));
    }
    @Test void secondPassUsesARealTypedSpringDataNativeAdmission() {
        var type=assertDoesNotThrow(()->Class.forName("io.fireflysoftware.core.product.models.repositories.definition.lifecycle.DepositPublicationAdmissionRepository"));
        assertTrue(type.isInterface());
        var methods=type.getDeclaredMethods();assertEquals(1,methods.length);
        var query=methods[0].getAnnotation(Query.class);assertNotNull(query);
        assertTrue(query.value().contains("public.begin_product_definition_deposit_publication"));
        assertFalse(query.value().contains(" JOIN "),"Ordinary reads stay mapped Spring Data operations");
    }
    @Test void optionalNativeCompanionKeepsOriginalAndOwnerCutoffsDistinctAndActivationClosed()throws Exception {
        var path=root().resolve("ops/database/purpose-activation/V25__Deposit_Publication_Admission.sql");
        assertTrue(Files.isRegularFile(path),"Missing exact deposit native second-pass contract");
        String sql=Files.readString(path);
        for(String value:new String[]{"deposit_publication_admission","preparation_context_id","request_until_millis","owner_effective_until_millis",
                "deposit_publication_activation_allowed","current_command","verify_complete","begin_product_definition_deposit_publication"})assertTrue(sql.contains(value),value);
        assertTrue(sql.contains("RETURN FALSE"),"Actual activation must stay closed pending native/finality qualification");
        assertFalse(Files.exists(root().resolve("core-common-product-mgmt-models/src/main/resources/db/migration/"+path.getFileName())));
    }
}

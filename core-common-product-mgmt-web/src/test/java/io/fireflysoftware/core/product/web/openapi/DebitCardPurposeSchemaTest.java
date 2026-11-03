package io.fireflysoftware.core.product.web.openapi;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DebitCardPurposeContracts;
import io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class DebitCardPurposeSchemaTest {
    record Both(InsuranceQuotePurposeContracts.Observation insurance,DebitCardPurposeContracts.Observation debit) {}
    @Test void actualCombinedProducerKeepsInsuranceAndDebitConfigurationsDistinct(){
        var schemas=new ModelConverters().resolveAsResolvedSchema(new AnnotatedType(Both.class)).referencedSchemas;
        assertNotNull(schemas.get("DebitCardPurposeObservation"));assertNotNull(schemas.get("DebitCardPurposeConfiguration"));
        assertEquals("#/components/schemas/DebitCardPurposeConfiguration",property(schemas.get("DebitCardPurposeObservation"),"configuration").get$ref());
        assertEquals("#/components/schemas/Configuration",property(schemas.get("Observation"),"configuration").get$ref());
        assertEquals("#/components/schemas/TermPcDefinition",property(schemas.get("Configuration"),"definition").get$ref());
        assertEquals("#/components/schemas/DebitCardPurposeDefinition",property(schemas.get("DebitCardPurposeConfiguration"),"definition").get$ref());
        assertEquals("#/components/schemas/DebitCardPurposeScope",property(schemas.get("DebitCardPurposeConfiguration"),"scope").get$ref());
    }
    @Test void debitCurrentResponseUsesExactScalarTimesAndClosedFiveFieldScope(){
        var schemas=new ModelConverters().readAll(DebitCardPurposeContracts.Observation.class);var observation=schemas.get("DebitCardPurposeObservation");assertNotNull(observation);
        assertEquals(Set.of("configuration","observedAt","effectiveUntil","canonicalConfiguration"),observation.getProperties().keySet());
        assertEquals(Set.of("tenantId","rootId","productId","revisionId","publicationId"),schemas.get("DebitCardPurposeScope").getProperties().keySet());
        for(String name:List.of("observedAt","effectiveUntil"))assertEquals("#/components/schemas/FireflyInstant",property(observation,name).get$ref());
        assertEquals(131072,property(observation,"canonicalConfiguration").getMaxLength());
        assertEquals(List.of(Instant.class,Instant.class),Arrays.stream(DebitCardPurposeContracts.Observation.class.getRecordComponents()).filter(c->Set.of("observedAt","effectiveUntil").contains(c.getName())).map(java.lang.reflect.RecordComponent::getType).toList());
    }
    private static Schema<?> property(Schema<?> schema,String name){assertNotNull(schema);return (Schema<?>)schema.getProperties().get(name);}
}

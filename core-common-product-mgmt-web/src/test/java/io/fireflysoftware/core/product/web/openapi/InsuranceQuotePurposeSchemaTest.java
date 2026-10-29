package io.fireflysoftware.core.product.web.openapi;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleContracts;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.fireflyframework.web.openapi.InstantScalarOpenApiCustomizer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class InsuranceQuotePurposeSchemaTest {
    private static final Set<String> PURPOSE_SCOPE = Set.of("tenantId", "rootId", "productId", "revisionId", "publicationId");

    record BothScopes(ProductDefinitionLifecycleContracts.Scope lifecycle,
            InsuranceQuotePurposeContracts.Observation purpose) { }

    @Test void actualPurposeScopeRemainsFiveTypedUuidFields() {
        var fields = InsuranceQuotePurposeContracts.Scope.class.getRecordComponents();
        assertThat(Arrays.stream(fields).map(java.lang.reflect.RecordComponent::getName).toList())
                .containsExactlyInAnyOrderElementsOf(PURPOSE_SCOPE);
        assertThat(Arrays.stream(fields).map(java.lang.reflect.RecordComponent::getType).toList())
                .containsOnly(UUID.class);
    }

    @Test void observationFieldsRemainActualJavaInstants() {
        var fields = InsuranceQuotePurposeContracts.Observation.class.getRecordComponents();
        assertThat(Arrays.stream(fields).filter(f -> Set.of("observedAt", "effectiveUntil").contains(f.getName()))
                .map(java.lang.reflect.RecordComponent::getType).toList()).containsExactly(Instant.class, Instant.class);
    }

    @Test void purposeUsesItsOwnFiveFieldSchemaName() {
        var schemas = new ModelConverters().readAll(InsuranceQuotePurposeContracts.Observation.class);
        var configuration = schemas.get("Configuration");
        assertThat(property(configuration, "scope").get$ref()).isEqualTo("#/components/schemas/InsuranceQuotePurposeScope");
        assertThat(schemas.get("InsuranceQuotePurposeScope").getProperties().keySet()).containsExactlyInAnyOrderElementsOf(PURPOSE_SCOPE);
    }

    @Test void bothRealScopesRemainDistinctInTheSameConverterContext() {
        var resolved = new ModelConverters().resolveAsResolvedSchema(new AnnotatedType(BothScopes.class));
        var schemas = resolved.referencedSchemas;
        assertThat(schemas.get("Scope").getProperties().keySet())
                .containsExactlyInAnyOrder("tenantId", "rootId", "operationId");
        assertThat(schemas.get("InsuranceQuotePurposeScope").getProperties().keySet())
                .containsExactlyInAnyOrderElementsOf(PURPOSE_SCOPE);
        assertThat(property(schemas.get("Configuration"), "scope").get$ref())
                .isEqualTo("#/components/schemas/InsuranceQuotePurposeScope");
    }

    @Test void purposeTimesResolveThroughTheActualNamedScalarRegistrar() {
        var schemas = new ModelConverters().readAll(InsuranceQuotePurposeContracts.Observation.class);
        for (String field : Set.of("observedAt", "effectiveUntil"))
            assertThat(property(schemas.get("Observation"), field).get$ref())
                    .isEqualTo("#/components/schemas/FireflyInstant");
        var api = new OpenAPI().components(new Components().schemas(new java.util.TreeMap<>(schemas)));
        new InstantScalarOpenApiCustomizer().customise(api);
        assertThat(api.getComponents().getSchemas().get("FireflyInstant").getType()).isEqualTo("string");
        assertThat(api.getComponents().getSchemas().get("FireflyInstant").getFormat()).isEqualTo("date-time");
    }

    @Test void allPublishedPurposeNestedRecordsKeepTheirActualPropertySets() {
        var schemas = new ModelConverters().readAll(InsuranceQuotePurposeContracts.Observation.class);
        assertThat(schemas.get("Observation").getProperties().keySet()).containsExactlyInAnyOrder("configuration", "observedAt", "effectiveUntil");
        assertThat(schemas.get("Configuration").getProperties().keySet()).containsExactlyInAnyOrder("scope", "publicDigest", "definition");
        assertThat(schemas.get("TermPcDefinition").getProperties().keySet()).containsExactlyInAnyOrder("common", "terms");
        assertThat(schemas.get("TermPcInsuranceTerms").getProperties()).hasSize(13);
        assertThat(schemas.get("ExactReference").getProperties().keySet())
                .containsExactlyInAnyOrder("owner", "kind", "id", "version", "digest", "versionFormat", "versionText");
        assertThat(schemas.get("CurrencyPolicyReference").getProperties().keySet())
                .containsExactlyInAnyOrder("policyId", "currencyId", "isoCode", "version", "digest", "encodingVersion");
    }

    private static Schema<?> property(Schema<?> value, String name) {
        return (Schema<?>) value.getProperties().get(name);
    }
}

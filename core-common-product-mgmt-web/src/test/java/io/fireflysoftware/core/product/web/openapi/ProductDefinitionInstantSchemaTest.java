package io.fireflysoftware.core.product.web.openapi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.AccountTariffServingContracts.*;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.*;

import org.fireflyframework.web.openapi.InstantScalarOpenAPIAutoConfiguration;
import org.fireflyframework.web.openapi.InstantScalarOpenApiCustomizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;
import java.util.stream.Stream;

class ProductDefinitionInstantSchemaTest {
    static Stream<Arguments> instantFields() {
        return Stream.of(
                Arguments.of(ReadObservation.class, "databaseRecordedAt"),
                Arguments.of(ReadObservation.class, "admissionValidUntil"),
                Arguments.of(RootState.class, "createdAt"),
                Arguments.of(RetainedRevision.class, "createdAt"),
                Arguments.of(RetainedPublication.class, "createdAt"),
                Arguments.of(ProductDefinitionCostView.class, "observedAt"),
                Arguments.of(ProductDefinitionReviewSelectionView.class, "observedAt"));
    }

    ApplicationContextRunner applicationSettings() {
        return new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(InstantScalarOpenAPIAutoConfiguration.class))
                .withInitializer(
                        context -> {
                            try {
                                for (var source :
                                        new YamlPropertySourceLoader()
                                                .load(
                                                        "actual-owner",
                                                        new ClassPathResource("application.yaml")))
                                    context.getEnvironment().getPropertySources().addLast(source);
                            } catch (java.io.IOException error) {
                                throw new java.io.UncheckedIOException(error);
                            }
                        });
    }

    @ParameterizedTest
    @MethodSource("instantFields")
    void everyRealServingInstantDeclaresTheNamedScalarAndRegistersThroughActualApplicationSettings(
            Class<?> type, String field) {
        var schemas = new ModelConverters().readAll(type);
        assertThat(
                        ((io.swagger.v3.oas.models.media.Schema<?>)
                                        schemas.get(type.getSimpleName())
                                                .getProperties()
                                                .get(field))
                                .get$ref())
                .isEqualTo("#/components/schemas/FireflyInstant");
        var api = new OpenAPI().components(new Components().schemas(new java.util.TreeMap<>(schemas)));
        applicationSettings()
                .run(
                        context -> {
                            assertThat(context)
                                    .hasNotFailed()
                                    .hasSingleBean(InstantScalarOpenApiCustomizer.class);
                            context.getBean(InstantScalarOpenApiCustomizer.class).customise(api);
                            assertThat(
                                            api.getComponents()
                                                    .getSchemas()
                                                    .get("FireflyInstant")
                                                    .getType())
                                    .isEqualTo("string");
                            assertThat(
                                            api.getComponents()
                                                    .getSchemas()
                                                    .get("FireflyInstant")
                                                    .getFormat())
                                    .isEqualTo("date-time");
                        });
    }

    @Test
    void actualApplicationResourceExplicitlyEnablesTheRegistrar() {
        applicationSettings()
                .run(
                        context ->
                                assertThat(context)
                                        .hasNotFailed()
                                        .hasSingleBean(InstantScalarOpenApiCustomizer.class));
    }

    @Test
    void
            originalProducerMainExplicitlyEnablesScalarAndBothProtectedReadRoutesWithoutStartingAServer() {
        try (var construction = mockConstruction(SpringApplication.class)) {
            OpenApiGenApplication.main(new String[0]);
            var application = construction.constructed().getFirst();
            @SuppressWarnings("unchecked")
            var capture = org.mockito.ArgumentCaptor.forClass(Map.class);
            verify(application).setDefaultProperties(capture.capture());
            assertThat(capture.getValue())
                    .containsEntry("product.definitions.commands.enabled", "true")
                    .containsEntry("product.definitions.publication-read.enabled", "true")
                    .containsEntry("firefly.openapi.instant-scalar.enabled", "true");
            verify(application).run(any(String[].class));
        }
    }
}

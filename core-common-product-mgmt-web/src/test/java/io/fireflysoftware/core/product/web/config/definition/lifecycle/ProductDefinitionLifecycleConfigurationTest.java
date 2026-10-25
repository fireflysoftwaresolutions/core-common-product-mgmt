package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandPersistence;

import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.spi.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import reactor.core.publisher.Mono;

class ProductDefinitionLifecycleConfigurationTest {
    Class<?> configuration() {
        try {
            return Class.forName(
                    "io.fireflysoftware.core.product.web.config.definition.lifecycle.ProductDefinitionLifecycleConfiguration");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Missing lifecycle production wiring", missing);
        }
    }

    ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(configuration())
                .withBean(
                        ProductDefinitionCommandPersistence.class,
                        () -> mock(ProductDefinitionCommandPersistence.class))
                .withBean(SecurityContextPort.class, () -> () -> Mono.empty())
                .withBean(
                        "productDefinitionCommandPolicy",
                        PolicyDecisionPort.class,
                        () ->
                                (principal, action, resource, context) ->
                                        Mono.just(Decision.deny("not configured")));
    }

    @Test
    void disabledDefaultDoesNotCreateCommandOrPrivateReadServices() {
        runner().run(
                        context ->
                                assertThat(context)
                                        .hasNotFailed()
                                        .doesNotHaveBean(ProductDefinitionLifecycleService.class)
                                        .doesNotHaveBean(ProductDefinitionReviewReadService.class)
                                        .doesNotHaveBean(
                                                ProductDefinitionPublicationReadService.class)
                                        .doesNotHaveBean(
                                                ProductDefinitionPublicationReadAuthorization
                                                        .class));
    }

    @Test
    void enabledBoundaryUsesOneCurrentStaffAuthorityAndActualOwnedPersistence() {
        runner().withPropertyValues("product.definitions.commands.enabled=true")
                .run(
                        context -> {
                            assertThat(context)
                                    .hasNotFailed()
                                    .hasSingleBean(ProductDefinitionLifecycleService.class)
                                    .hasSingleBean(ProductDefinitionReviewReadService.class)
                                    .hasSingleBean(ProductDefinitionCostReadService.class)
                                    .hasSingleBean(ProductDefinitionCostReadAuthorization.class)
                                    .hasSingleBean(ProductDefinitionCommandAuthorization.class)
                                    .hasSingleBean(ProductDefinitionReviewAuthorization.class);
                            assertThat(context)
                                    .doesNotHaveBean(ProductDefinitionPublicationReadService.class)
                                    .hasSingleBean(
                                            ProductDefinitionPublicationReadAuthorization.class);
                            assertThat(context.getBean(ProductDefinitionCommandAuthorization.class))
                                    .isSameAs(
                                            context.getBean(
                                                    ProductDefinitionReviewAuthorization.class));
                            assertThat(
                                            context.getBean(
                                                    ProductDefinitionPublicationReadAuthorization
                                                            .class))
                                    .isSameAs(
                                            context.getBean(
                                                    ProductDefinitionCommandAuthorization.class));
                            verifyNoInteractions(
                                    context.getBean(ProductDefinitionCommandPersistence.class));
                        });
    }
}

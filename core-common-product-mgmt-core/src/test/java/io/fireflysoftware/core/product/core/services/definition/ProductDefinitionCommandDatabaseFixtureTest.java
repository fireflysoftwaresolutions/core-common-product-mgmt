package io.fireflysoftware.core.product.core.services.definition;

import static org.assertj.core.api.Assertions.*;

import io.r2dbc.spi.ConnectionFactory;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

class ProductDefinitionCommandDatabaseFixtureTest {
    private ProductDefinitionCommandDatabaseFixture.Settings settings() {
        return new ProductDefinitionCommandDatabaseFixture.Settings(
                "127.0.0.1",
                1,
                "unused_fixture_target",
                "fixture_reader",
                UUID.randomUUID().toString(),
                "fixture_commands",
                UUID.randomUUID().toString());
    }

    @Test
    void actualOwnerMappingAndCommandFactoryRemainBoundWithoutOpeningAnyConnection() {
        try (var fixture = new ProductDefinitionCommandDatabaseFixture(settings())) {
            var persistence = fixture.persistence();
            var context = fixture.context();
            assertThat(context.getBeansOfType(ConnectionFactory.class)).hasSize(1);
            assertThat(
                            context.getBean(R2dbcEntityOperations.class)
                                    .getDatabaseClient()
                                    .getConnectionFactory())
                    .isSameAs(context.getBean(ConnectionFactory.class));
            assertThat(persistence.operations().getConverter())
                    .isSameAs(context.getBean(R2dbcConverter.class));
            assertThat(persistence.operations().getDatabaseClient().getConnectionFactory())
                    .isSameAs(persistence.connectionFactory());
            assertThat(persistence.transactionManager().getConnectionFactory())
                    .isSameAs(persistence.connectionFactory());
            assertThat(persistence.connectionFactory())
                    .isNotSameAs(context.getBean(ConnectionFactory.class));
            assertThat(
                            context.getBeansOfType(
                                            org.springframework.data.r2dbc.mapping.event
                                                    .BeforeConvertCallback.class)
                                    .values())
                    .anyMatch(
                            callback ->
                                    callback
                                            instanceof
                                            org.springframework.data.r2dbc.mapping.event
                                                    .ReactiveAuditingEntityCallback);
            assertThat(context.getBean("ownedMappedCreateCallbacks"))
                    .isInstanceOf(ProductDefinitionCommandDatabaseFixture.Callbacks.class);
            assertThat(persistence.connectionFactory().getMetrics().orElseThrow().allocatedSize())
                    .isZero();
        }
    }

    @Test
    void expiredControlledActorCannotAcquireACommandConnection() {
        try (var fixture = new ProductDefinitionCommandDatabaseFixture(settings())) {
            assertThatThrownBy(
                            () ->
                                    fixture.create(
                                                    UUID.randomUUID(),
                                                    UUID.randomUUID(),
                                                    UUID.randomUUID(),
                                                    UUID.randomUUID(),
                                                    Instant.now().minusSeconds(1))
                                            .block(Duration.ofSeconds(2)))
                    .isInstanceOfSatisfying(
                            ResponseStatusException.class,
                            error ->
                                    assertThat(error.getStatusCode())
                                            .isEqualTo(HttpStatus.UNAUTHORIZED));
            assertThat(
                            fixture.persistence()
                                    .connectionFactory()
                                    .getMetrics()
                                    .orElseThrow()
                                    .allocatedSize())
                    .isZero();
        }
    }

    @Test
    void fixtureSettingsNeverRenderEitherCredential() {
        var value = settings();
        assertThat(value.toString())
                .doesNotContain(
                        value.readPassword(),
                        value.commandPassword(),
                        value.readUser(),
                        value.commandUser());
    }
}

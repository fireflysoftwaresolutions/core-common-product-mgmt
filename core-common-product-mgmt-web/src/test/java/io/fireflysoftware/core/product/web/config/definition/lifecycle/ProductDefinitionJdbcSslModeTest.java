package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Properties;
import java.util.UUID;

class ProductDefinitionJdbcSslModeTest {
    MockEnvironment settings(String mode) throws Exception {
        var env =
                new MockEnvironment()
                        .withProperty("DB_HOST", "127.0.0.1")
                        .withProperty("DB_PORT", "59992")
                        .withProperty("DB_NAME", "owner_metadata")
                        .withProperty("DB_USERNAME", "owner_reader")
                        .withProperty("DB_PASSWORD", UUID.randomUUID().toString())
                        .withProperty("DB_SSL_MODE", mode);
        for (var source :
                new YamlPropertySourceLoader()
                        .load("actual-owner", new ClassPathResource("application.yaml")))
            env.getPropertySources().addLast(source);
        return env;
    }

    @ParameterizedTest
    @ValueSource(strings = {"verify-full", "disable"})
    void actualOwnerYamlResolvesTheCanonicalJdbcDriverSslMode(String mode) throws Exception {
        var env = settings(mode);
        var flyway =
                Binder.get(env)
                        .bind("spring.flyway", Bindable.of(FlywayProperties.class))
                        .orElseThrow(AssertionError::new);
        var parsed = org.postgresql.Driver.parseURL(flyway.getUrl(), new Properties());
        assertThat(parsed).isNotNull();
        assertThat(org.postgresql.PGProperty.SSL_MODE.get(parsed)).isEqualTo(mode);
        assertThat(flyway.getUrl())
                .isEqualTo("jdbc:postgresql://127.0.0.1:59992/owner_metadata?sslmode=" + mode);
    }

    @ParameterizedTest
    @ValueSource(strings = {"verify-full", "disable"})
    void existingR2dbcMixedCaseOptionRetainsItsActualDriverMeaning(String mode) throws Exception {
        var url = settings(mode).getRequiredProperty("spring.r2dbc.url");
        assertThat(url)
                .isEqualTo("r2dbc:postgresql://127.0.0.1:59992/owner_metadata?sslMode=" + mode);
        var options = io.r2dbc.spi.ConnectionFactoryOptions.parse(url);
        assertThat(
                        String.valueOf(
                                options.getValue(
                                        io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider
                                                .SSL_MODE)))
                .isEqualTo(mode);
    }
}

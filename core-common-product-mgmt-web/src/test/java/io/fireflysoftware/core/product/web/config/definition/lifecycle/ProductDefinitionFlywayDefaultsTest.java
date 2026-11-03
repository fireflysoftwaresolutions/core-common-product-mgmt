package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.util.UUID;

class ProductDefinitionFlywayDefaultsTest {
    MockEnvironment environment() throws Exception {
        var env =
                new MockEnvironment()
                        .withProperty("DB_HOST", "127.0.0.1")
                        .withProperty("DB_PORT", "59992")
                        .withProperty("DB_NAME", "owner_metadata")
                        .withProperty("DB_USERNAME", "owner_reader")
                        .withProperty("DB_PASSWORD", UUID.randomUUID().toString())
                        .withProperty("DB_SSL_MODE", "require")
                        .withProperty("FLYWAY_USERNAME", "owner_migrator")
                        .withProperty("FLYWAY_PASSWORD", UUID.randomUUID().toString());
        for (var source :
                new YamlPropertySourceLoader()
                        .load("actual-owner", new ClassPathResource("application.yaml")))
            env.getPropertySources().addLast(source);
        return env;
    }

    FlywayProperties bind(MockEnvironment environment) {
        return Binder.get(environment)
                .bind("spring.flyway", Bindable.of(FlywayProperties.class))
                .orElseThrow(AssertionError::new);
    }

    @Test
    void migrationSafetyIsExplicitInTheOwnerResourceAndActuallyBinds() throws Exception {
        var env = environment();
        assertThat(env.getProperty("spring.flyway.baseline-on-migrate", Boolean.class)).isFalse();
        assertThat(env.getProperty("spring.flyway.validate-on-migrate", Boolean.class)).isTrue();
        assertThat(env.getProperty("spring.flyway.clean-disabled", Boolean.class)).isTrue();
        var actual = bind(env);
        assertThat(actual.isBaselineOnMigrate()).isFalse();
        assertThat(actual.isValidateOnMigrate()).isTrue();
        assertThat(actual.isCleanDisabled()).isTrue();
    }

    @Test
    void unsupportedRepairOnMigrateIsNotPresentedAsAnActiveSafetySetting() throws Exception {
        assertThat(environment().containsProperty("spring.flyway.repair-on-migrate")).isFalse();
    }

    @Test
    void realBootBindingKeepsTheExistingTargetCredentialsAndOriginalMigrationDiscovery()
            throws Exception {
        var env = environment();
        var actual = bind(env);
        assertThat(actual.isEnabled()).isTrue();
        assertThat(actual.getUrl())
                .isEqualTo("jdbc:postgresql://127.0.0.1:59992/owner_metadata?sslmode=require");
        assertThat(actual.getUser()).isEqualTo("owner_migrator");
        assertThat(actual.getPassword()).isEqualTo(env.getProperty("FLYWAY_PASSWORD"));
        assertThat(actual.getLocations()).containsExactly("classpath:db/migration");
    }

    @Test
    void actualBootPropertyContractHasTheThreeSwitchesAndNoRepairOnMigrateBinding() {
        var properties =
                java.util.Arrays.stream(
                                org.springframework.beans.BeanUtils.getPropertyDescriptors(
                                        FlywayProperties.class))
                        .map(java.beans.PropertyDescriptor::getName)
                        .toList();
        assertThat(properties)
                .contains("baselineOnMigrate", "validateOnMigrate", "cleanDisabled")
                .doesNotContain("repairOnMigrate");
    }
}

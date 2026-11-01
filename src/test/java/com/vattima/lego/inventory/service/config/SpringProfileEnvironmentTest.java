package com.vattima.lego.inventory.service.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each environment's Spring profile must connect to its own database and report its own
 * telemetry environment (HOMELAB-26 B9). A pod runs {@code SPRING_PROFILES_ACTIVE=<env>,kubernetes}
 * (see {@link KubernetesOverlayTest}), so the effective configuration is {@code application.yml},
 * then {@code application-<env>.yml}, then {@code application-kubernetes.yml}, later files winning.
 * The {@code kubernetes} profile is shared by every environment and must not override either value.
 */
class SpringProfileEnvironmentTest {

    @ParameterizedTest
    @ValueSource(strings = {"sandbox", "dev", "prod"})
    void podConnectsToItsOwnEnvironmentDatabase(String env) {
        Properties effective = podConfiguration(env);

        assertThat(effective.getProperty("spring.datasource.database-key-name")).isEqualTo("rasppi5-01-lego-" + env);
        assertThat(effective.getProperty("spring.config.import")).isEqualTo("file:${import-path}/database-configuration.yml");
        assertThat(effective.getProperty("import-path")).isEqualTo("/etc/.credentials");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sandbox", "dev", "prod"})
    void podReportsItsOwnTelemetryEnvironment(String env) {
        assertThat(podConfiguration(env).getProperty("otel.resource.attributes.deployment.environment")).isEqualTo(env);
    }

    @Test
    void sharedProfilesDoNotChooseAnEnvironment() {
        for (String shared : new String[]{"application.yml", "application-kubernetes.yml"}) {
            assertThat(load(shared))
                    .as(shared)
                    .doesNotContainKeys("spring.datasource.database-key-name", "otel.resource.attributes.deployment.environment");
        }
    }

    @Test
    void tracesAreExportedToTheOtlpTracesPath() {
        assertThat(load("application-kubernetes.yml").getProperty("management.otlp.tracing.endpoint")).endsWith("/v1/traces");
    }

    private static Properties podConfiguration(String env) {
        Properties effective = new Properties();
        for (String file : new String[]{"application.yml", "application-%s.yml".formatted(env), "application-kubernetes.yml"}) {
            effective.putAll(load(file));
        }
        return effective;
    }

    private static Properties load(String resourceName) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(resourceName));
        factory.afterPropertiesSet();
        return factory.getObject();
    }
}

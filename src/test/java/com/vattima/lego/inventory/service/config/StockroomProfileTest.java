package com.vattima.lego.inventory.service.config;

import io.legohunter.data.environment.EnvironmentDatabasePolicy;
import io.legohunter.lego.data.environment.LegoEnvironmentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Every real Spring profile combination binds to a draft stockroom that the startup check accepts,
 * with each lower environment in its own stockroom (HOMELAB-20). A pod runs
 * {@code <env>,kubernetes}; a run with no profile uses {@code application.yml} alone, whose
 * placeholders default to the {@code local} environment. Placeholders are resolved as Spring does,
 * with no environment variables set.
 */
class StockroomProfileTest {

    @ParameterizedTest(name = "{1}")
    @CsvSource({
            "'sandbox,kubernetes', sandbox, C",
            "'dev,kubernetes',     dev,     B",
            "'',                   local,   C",
    })
    void eachLowerEnvironmentStampsDraftsWithItsOwnStockroom(String profiles, String environment, String stockroom) {
        MarketplaceListingDraftProperties drafts = drafts(profiles);

        assertThat(environment(profiles).getCode()).isEqualTo(environment);
        assertThat(environment(profiles).isProduction()).isFalse();
        assertThat(drafts.isProduction()).isFalse();
        assertThat(drafts.getEnvironmentCode()).isEqualTo(environment);
        assertThat(drafts.getNonProdBricklinkStockroomId()).isEqualTo(stockroom);
        assertThat(MarketplaceListingDraftStockroomValidator.violations(drafts, environment(profiles))).isEmpty();
    }

    @Test
    void prodIsProductionAndNotForcedIntoAStockroom() {
        MarketplaceListingDraftProperties drafts = drafts("prod,kubernetes");

        assertThat(environment("prod,kubernetes").getCode()).isEqualTo("prod");
        assertThat(environment("prod,kubernetes").isProduction()).isTrue();
        assertThat(drafts.isProduction()).isTrue();
        assertThat(drafts.getEnvironmentCode()).isEqualTo("prod");
        assertThatCode(() -> new MarketplaceListingDraftStockroomValidator(drafts, environment("prod,kubernetes")).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    /**
     * Each real profile points its data source at its own environment's schema. The database URL
     * lives in the mounted credentials file, so this relies on the key-naming convention
     * {@code rasppi5-01-<schema with dashes>}. The lego-data starter checks {@code lego.environment}
     * against the real connection at startup.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "'sandbox,kubernetes', lego_sandbox",
            "'dev,kubernetes',     lego_dev",
            "'prod,kubernetes',    lego_prod",
    })
    void eachProfileUsesTheDatabaseOfItsEnvironment(String profiles, String schema) {
        LegoEnvironmentProperties environment = environment(profiles);

        assertThat(effective(profiles).getProperty("spring.datasource.database-key-name"))
                .isEqualTo("rasppi5-01-" + schema.replace('_', '-'));
        assertThat(EnvironmentDatabasePolicy.violations("lego.environment.code", "lego.environment.production",
                environment.getCode(), environment.isProduction(), schema))
                .isEmpty();
    }

    private static MarketplaceListingDraftProperties drafts(String profiles) {
        return binder(profiles)
                .bind("lego.marketplace-listing-drafts", Bindable.ofInstance(new MarketplaceListingDraftProperties()))
                .get();
    }

    private static LegoEnvironmentProperties environment(String profiles) {
        return binder(profiles)
                .bind("lego.environment", Bindable.ofInstance(new LegoEnvironmentProperties()))
                .get();
    }

    private static Binder binder(String profiles) {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new PropertiesPropertySource("profiles", effective(profiles)));
        return new Binder(ConfigurationPropertySources.from(sources), new PropertySourcesPlaceholdersResolver(sources));
    }

    private static Properties effective(String profiles) {
        Properties effective = new Properties();
        effective.putAll(load("application.yml"));
        for (String profile : profiles.split(",")) {
            if (!profile.isBlank()) {
                effective.putAll(load("application-%s.yml".formatted(profile.trim())));
            }
        }
        return effective;
    }

    private static Properties load(String resourceName) {
        ClassPathResource resource = new ClassPathResource(resourceName);
        assertThat(resource.exists()).as(resourceName).isTrue();
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(resource);
        factory.afterPropertiesSet();
        return factory.getObject();
    }
}

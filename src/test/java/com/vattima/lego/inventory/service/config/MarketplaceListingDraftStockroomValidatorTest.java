package com.vattima.lego.inventory.service.config;

import io.legohunter.lego.data.environment.LegoEnvironmentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rules themselves are tested in lego-data ({@code BricklinkStockroomPolicyTest}); here they
 * are checked to be wired in, with this service's setting names in the messages.
 */
class MarketplaceListingDraftStockroomValidatorTest {

    @ParameterizedTest
    @CsvSource({"sandbox, C", "local, C", "dev, B"})
    void startsWithEachLowerEnvironmentInItsOwnStockroom(String environment, String stockroom) {
        assertThatCode(() -> validator(lowerEnvironment(environment, stockroom)).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void refusesToStartWithSandboxInStockroomA() {
        assertThatThrownBy(() -> validator(lowerEnvironment("sandbox", "A")).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsafe marketplace listing draft configuration")
                .hasMessageNotContaining("HOMELAB")
                .hasMessageContaining("lego.marketplace-listing-drafts.non-prod-bricklink-stockroom-id must not be A for environment 'sandbox'");
    }

    @Test
    void refusesToStartWithAnUnknownEnvironmentCode() {
        assertThatThrownBy(() -> validator(lowerEnvironment("sandbx", "B")).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lego.marketplace-listing-drafts.environment-code 'sandbx' is not a known environment");
    }

    @Test
    void refusesToStartWithoutAStockroomOutsideProd() {
        assertThat(MarketplaceListingDraftStockroomValidator.violations(lowerEnvironment("dev", null), sameEnvironment(lowerEnvironment("dev", null))))
                .singleElement().asString()
                .contains("lego.marketplace-listing-drafts.non-prod-bricklink-stockroom-id must be set for environment 'dev' (expected B)");
    }

    @Test
    void refusesAProductionFlagThatDisagreesWithTheEnvironment() {
        MarketplaceListingDraftProperties properties = lowerEnvironment("sandbox", "C");
        properties.setProduction(true);

        assertThat(MarketplaceListingDraftStockroomValidator.violations(properties, sameEnvironment(properties)))
                .singleElement().asString()
                .contains("lego.marketplace-listing-drafts.production is true but environment-code 'sandbox' is not PROD");
    }

    @Test
    void refusesDraftsNamingAnotherEnvironmentThanLegoEnvironment() {
        // lego.environment is what the lego-data starter checked against the database.
        assertThat(MarketplaceListingDraftStockroomValidator.violations(lowerEnvironment("sandbox", "C"), environment("dev", false)))
                .singleElement().asString()
                .contains("lego.marketplace-listing-drafts.environment-code 'sandbox' must equal lego.environment.code 'dev'");
    }

    @Test
    void refusesADraftProductionFlagThatDisagreesWithLegoEnvironment() {
        assertThat(MarketplaceListingDraftStockroomValidator.violations(lowerEnvironment("sandbox", "C"), environment("sandbox", true)))
                .singleElement().asString()
                .contains("lego.marketplace-listing-drafts.production (false) must equal lego.environment.production (true)");
    }

    @Test
    void prodNeedsNoNonProdStockroom() {
        MarketplaceListingDraftProperties prod = new MarketplaceListingDraftProperties();
        prod.setProduction(true);
        prod.setEnvironmentCode("prod");

        assertThatCode(() -> validator(prod).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void hasNoDefaultStockroom() {
        assertThat(new MarketplaceListingDraftProperties().getNonProdBricklinkStockroomId()).isNull();
    }

    private static MarketplaceListingDraftStockroomValidator validator(MarketplaceListingDraftProperties properties) {
        return new MarketplaceListingDraftStockroomValidator(properties, sameEnvironment(properties));
    }

    /** A {@code lego.environment} that agrees with the draft settings. */
    private static LegoEnvironmentProperties sameEnvironment(MarketplaceListingDraftProperties properties) {
        return environment(properties.getEnvironmentCode(), properties.isProduction());
    }

    private static LegoEnvironmentProperties environment(String code, boolean production) {
        LegoEnvironmentProperties environment = new LegoEnvironmentProperties();
        environment.setCode(code);
        environment.setProduction(production);
        return environment;
    }

    private static MarketplaceListingDraftProperties lowerEnvironment(String environment, String stockroom) {
        MarketplaceListingDraftProperties properties = new MarketplaceListingDraftProperties();
        properties.setProduction(false);
        properties.setEnvironmentCode(environment);
        properties.setNonProdBricklinkStockroomId(stockroom);
        return properties;
    }
}

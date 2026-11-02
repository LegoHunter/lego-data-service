package com.vattima.lego.inventory.service.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketplaceListingDraftStockroomValidatorTest {

    @ParameterizedTest
    @CsvSource({"sandbox, C", "local, C", "dev, B", "' Sandbox ', ' c '"})
    void startsWithEachLowerEnvironmentInItsOwnStockroom(String environment, String stockroom) {
        assertThatCode(() -> validator(lowerEnvironment(environment, stockroom)).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sandbox", "dev", "local", "some-new-env"})
    void refusesToStartWithStockroomAOutsideProd(String environment) {
        assertThatThrownBy(() -> validator(lowerEnvironment(environment, "A")).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HOMELAB-20")
                .hasMessageContaining("must not be A outside PROD");
    }

    @ParameterizedTest
    @CsvSource({"sandbox, B", "local, B", "dev, C"})
    void refusesAnotherEnvironmentsStockroom(String environment, String stockroom) {
        assertThat(MarketplaceListingDraftStockroomValidator.violations(lowerEnvironment(environment, stockroom)))
                .singleElement().asString().contains("for environment '" + environment + "' must be");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void refusesAMissingStockroomOutsideProd(String stockroom) {
        assertThat(MarketplaceListingDraftStockroomValidator.violations(lowerEnvironment("sandbox", stockroom)))
                .singleElement().asString().contains("must be set outside PROD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"D", "AB", "1"})
    void refusesValuesThatAreNotBricklinkStockrooms(String stockroom) {
        assertThat(MarketplaceListingDraftStockroomValidator.violations(lowerEnvironment("sandbox", stockroom)))
                .singleElement().asString().contains("is not a BrickLink stockroom");
    }

    @Test
    void anUnknownLowerEnvironmentMayUseBOrCButNeverA() {
        assertThat(MarketplaceListingDraftStockroomValidator.violations(lowerEnvironment("test", "B"))).isEmpty();
        assertThat(MarketplaceListingDraftStockroomValidator.violations(lowerEnvironment("test", "C"))).isEmpty();
        assertThat(MarketplaceListingDraftStockroomValidator.violations(lowerEnvironment("test", "A"))).isNotEmpty();
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
        return new MarketplaceListingDraftStockroomValidator(properties);
    }

    private static MarketplaceListingDraftProperties lowerEnvironment(String environment, String stockroom) {
        MarketplaceListingDraftProperties properties = new MarketplaceListingDraftProperties();
        properties.setProduction(false);
        properties.setEnvironmentCode(environment);
        properties.setNonProdBricklinkStockroomId(stockroom);
        return properties;
    }
}

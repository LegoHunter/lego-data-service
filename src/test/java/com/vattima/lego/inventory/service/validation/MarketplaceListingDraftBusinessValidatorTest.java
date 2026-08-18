package com.vattima.lego.inventory.service.validation;

import com.vattima.lego.inventory.service.config.MarketplaceListingDraftProperties;
import com.vattima.lego.inventory.service.dto.MarketplaceListingReadinessIssue;
import io.legohunter.data.dto.BricklinkMarketplaceListing;
import io.legohunter.data.dto.ExternalCatalogItem;
import io.legohunter.data.dto.ItemInventory;
import io.legohunter.data.dto.ItemInventoryExternalCatalogItem;
import io.legohunter.data.dto.ItemInventoryPhoto;
import io.legohunter.data.dto.MarketplaceListing;
import io.legohunter.data.enums.PhotoStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceListingDraftBusinessValidatorTest {
    private MarketplaceListingDraftBusinessValidator validator;
    private MarketplaceListingDraftProperties properties;

    @BeforeEach
    void setUp() {
        validator = new MarketplaceListingDraftBusinessValidator();
        properties = new MarketplaceListingDraftProperties();
        properties.setEnvironmentCode("sandbox");
        properties.setNonProdBricklinkStockroomId("A");
    }

    @Test
    void normalizeMarketplaceCodeTrimsAndUppercasesValue() {
        assertThat(validator.normalizeMarketplaceCode(" bricklink ")).isEqualTo("BRICKLINK");
        assertThat(validator.normalizeMarketplaceCode(null)).isNull();
    }

    @Test
    void hardBlockersReturnsUnsupportedMarketplaceOnly() {
        List<MarketplaceListingReadinessIssue> blockers = validator.hardBlockers(
                inventory("KEEP", "RESERVED", false),
                "EBAY",
                Set.of(),
                null,
                null,
                properties
        );

        assertThat(blockers).singleElement()
                .satisfies(blocker -> assertThat(blocker.getCode()).isEqualTo("UNSUPPORTED_MARKETPLACE"));
    }

    @Test
    void hardBlockersDetectsInvalidInventoryAndMissingDraft() {
        List<MarketplaceListingReadinessIssue> blockers = validator.hardBlockers(
                inventory("KEEP", "RESERVED_FOR_ORDER", false),
                "BRICKLINK",
                Set.of(),
                null,
                null,
                properties
        );

        assertThat(blockers).extracting("code")
                .containsExactly(
                        "INVENTORY_INACTIVE",
                        "INVENTORY_NOT_SELLABLE",
                        "INVENTORY_NOT_AVAILABLE",
                        "MISSING_PRIMARY_BRICKLINK_CATALOG_LINK",
                        "MISSING_MARKETPLACE_LISTING_DRAFT"
                );
    }

    @Test
    void hardBlockersDetectsInvalidUnitPriceAndMissingNonProdBricklinkDetails() {
        List<MarketplaceListingReadinessIssue> blockers = validator.hardBlockers(
                inventory("SELLABLE", "AVAILABLE", true),
                "BRICKLINK",
                Set.of(primaryBricklinkCatalogLink(303)),
                listing(101, new BigDecimal("0.00"), "DRAFT"),
                null,
                properties
        );

        assertThat(blockers).extracting("code")
                .containsExactly("INVALID_UNIT_PRICE", "MISSING_BRICKLINK_LISTING_DETAILS");
    }

    @Test
    void hardBlockersReportsInitialPricingPendingForUnpricedNonFixedDraft() {
        List<MarketplaceListingReadinessIssue> blockers = validator.hardBlockers(
                inventory("SELLABLE", "AVAILABLE", true),
                "BRICKLINK",
                Set.of(primaryBricklinkCatalogLink(303)),
                listing(101, null, "DRAFT"),
                BricklinkMarketplaceListing.builder()
                        .marketplaceListingId(101)
                        .isStockRoom(true)
                        .stockRoomId("A")
                        .build(),
                properties
        );

        assertThat(blockers).singleElement()
                .satisfies(blocker -> assertThat(blocker.getCode()).isEqualTo("INITIAL_PRICE_PENDING"));
    }

    @Test
    void hardBlockersReportsMissingFixedUnitPriceForAnActiveListing() {
        MarketplaceListing activeListing = listing(101, null, "ACTIVE");
        activeListing.setFixedPrice(true);

        List<MarketplaceListingReadinessIssue> blockers = validator.hardBlockers(
                inventory("SELLABLE", "AVAILABLE", true),
                "BRICKLINK",
                Set.of(primaryBricklinkCatalogLink(303)),
                activeListing,
                BricklinkMarketplaceListing.builder()
                        .marketplaceListingId(101)
                        .isStockRoom(true)
                        .stockRoomId("A")
                        .build(),
                properties
        );

        assertThat(blockers).singleElement()
                .satisfies(blocker -> {
                    assertThat(blocker.getCode()).isEqualTo("MISSING_FIXED_UNIT_PRICE");
                    assertThat(blocker.getMessage()).isEqualTo("Marketplace listing requires a fixed price and therefore must have a non-zero unitPrice before marketplace sync");
                });
    }

    @Test
    void hardBlockersDetectsMissingColorForColorSpecificBricklinkItem() {
        ItemInventoryExternalCatalogItem partLink = primaryBricklinkCatalogLink(303);
        partLink.getExternalCatalogItem().setItemTypeCode("PART");

        List<MarketplaceListingReadinessIssue> blockers = validator.hardBlockers(
                inventory("SELLABLE", "AVAILABLE", true),
                "BRICKLINK",
                Set.of(partLink),
                listing(101, new BigDecimal("12.00"), "DRAFT"),
                BricklinkMarketplaceListing.builder()
                        .marketplaceListingId(101)
                        .isStockRoom(true)
                        .stockRoomId("A")
                        .build(),
                properties
        );

        assertThat(blockers).singleElement()
                .satisfies(blocker -> assertThat(blocker.getCode()).isEqualTo("MISSING_BRICKLINK_COLOR_ID"));
    }

    @Test
    void hardBlockersDetectsNonProdStockroomViolations() {
        List<MarketplaceListingReadinessIssue> blockers = validator.hardBlockers(
                inventory("SELLABLE", "AVAILABLE", true),
                "BRICKLINK",
                Set.of(primaryBricklinkCatalogLink(303)),
                listing(101, new BigDecimal("12.00"), "DRAFT"),
                BricklinkMarketplaceListing.builder()
                        .marketplaceListingId(101)
                        .isStockRoom(false)
                        .stockRoomId("B")
                        .build(),
                properties
        );

        assertThat(blockers).extracting("code")
                .containsExactly("NON_PROD_BRICKLINK_STOCKROOM_REQUIRED", "NON_PROD_BRICKLINK_STOCKROOM_ID_REQUIRED");
    }

    @Test
    void hardBlockersDoesNotRequireStockroomDetailsInProduction() {
        properties.setProduction(true);

        List<MarketplaceListingReadinessIssue> blockers = validator.hardBlockers(
                inventory("SELLABLE", "AVAILABLE", true),
                "BRICKLINK",
                Set.of(primaryBricklinkCatalogLink(303)),
                listing(101, new BigDecimal("12.00"), "DRAFT"),
                null,
                properties
        );

        assertThat(blockers).isEmpty();
    }

    @Test
    void warningsWarnForSellableInventoryWithoutPhotosOnly() {
        ItemInventory sellable = inventory("SELLABLE", "AVAILABLE", true);
        ItemInventory keep = inventory("KEEP", "AVAILABLE", true);

        assertThat(validator.warnings(sellable, Set.of())).singleElement()
                .satisfies(warning -> {
                    assertThat(warning.getCode()).isEqualTo("MISSING_ITEM_INVENTORY_PHOTOS");
                    assertThat(warning.getSeverity()).isEqualTo("WARN");
                });
        assertThat(validator.warnings(keep, Set.of())).isEmpty();
        assertThat(validator.warnings(sellable, Set.of(ItemInventoryPhoto.builder()
                .itemInventoryId(202)
                .md5("photo-md5")
                .status(PhotoStatus.PROCESSED)
                .build()))).isEmpty();
    }

    @Test
    void findsPrimaryAndRequestedBricklinkCatalogLinks() {
        ItemInventoryExternalCatalogItem primaryBricklink = primaryBricklinkCatalogLink(303);
        ItemInventoryExternalCatalogItem alternateBricklink = bricklinkCatalogLink(304, false);
        ItemInventoryExternalCatalogItem unresolved = ItemInventoryExternalCatalogItem.builder()
                .externalCatalogItemId(306)
                .primary(true)
                .build();
        ItemInventoryExternalCatalogItem rebrickable = ItemInventoryExternalCatalogItem.builder()
                .externalCatalogItemId(305)
                .externalCatalogItem(ExternalCatalogItem.builder()
                        .externalCatalogItemId(305)
                        .externalServiceId(1)
                        .externalItemKey("6390-1")
                        .build())
                .primary(true)
                .build();

        Set<ItemInventoryExternalCatalogItem> links = Set.of(primaryBricklink, alternateBricklink, rebrickable, unresolved);

        assertThat(validator.findPrimaryBricklinkCatalogLink(links)).contains(primaryBricklink);
        assertThat(validator.findPrimaryBricklinkCatalogLink(Set.of(unresolved))).isEmpty();
        assertThat(validator.findBricklinkCatalogLink(links, null)).contains(primaryBricklink);
        assertThat(validator.findBricklinkCatalogLink(links, 304)).contains(alternateBricklink);
        assertThat(validator.findBricklinkCatalogLink(links, 305)).isEmpty();
        assertThat(validator.findBricklinkCatalogLink(links, 306)).isEmpty();
    }

    @Test
    void detectsOpenMarketplaceListingsExcludingCurrentListing() {
        MarketplaceListing draft = listing(101, new BigDecimal("12.00"), "DRAFT");
        MarketplaceListing removed = listing(102, new BigDecimal("12.00"), "REMOVED");
        MarketplaceListing ended = listing(103, new BigDecimal("12.00"), "ENDED");

        assertThat(validator.hasOpenListingForMarketplace(Set.of(draft, removed, ended), 2, null)).isTrue();
        assertThat(validator.hasOpenListingForMarketplace(Set.of(draft, removed, ended), 2, 101)).isFalse();
        assertThat(validator.isOpenListing(draft)).isTrue();
        assertThat(validator.isOpenListing(removed)).isFalse();
        assertThat(validator.isOpenListing(ended)).isFalse();
    }

    private ItemInventory inventory(String saleIntentCode, String inventoryStateCode, boolean active) {
        ItemInventory inventory = new ItemInventory();
        inventory.setItemInventoryId(202);
        inventory.setSaleIntentCode(saleIntentCode);
        inventory.setInventoryStateCode(inventoryStateCode);
        inventory.setActive(active);
        return inventory;
    }

    private MarketplaceListing listing(Integer marketplaceListingId, BigDecimal unitPrice, String status) {
        return MarketplaceListing.builder()
                .marketplaceListingId(marketplaceListingId)
                .itemInventoryId(202)
                .listingExternalServiceId(2)
                .externalCatalogItemId(303)
                .listingStatusCode(status)
                .unitPrice(unitPrice)
                .currencyCode("USD")
                .build();
    }

    private ItemInventoryExternalCatalogItem primaryBricklinkCatalogLink(Integer externalCatalogItemId) {
        return bricklinkCatalogLink(externalCatalogItemId, true);
    }

    private ItemInventoryExternalCatalogItem bricklinkCatalogLink(Integer externalCatalogItemId, boolean primary) {
        return ItemInventoryExternalCatalogItem.builder()
                .itemInventoryId(202)
                .externalCatalogItemId(externalCatalogItemId)
                .primary(primary)
                .externalCatalogItem(ExternalCatalogItem.builder()
                        .externalCatalogItemId(externalCatalogItemId)
                        .externalServiceId(2)
                        .externalItemKey("6390-1")
                        .itemTypeCode("S")
                        .build())
                .build();
    }
}

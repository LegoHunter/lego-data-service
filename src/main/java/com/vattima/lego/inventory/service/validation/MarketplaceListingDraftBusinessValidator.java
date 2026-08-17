package com.vattima.lego.inventory.service.validation;

import com.vattima.lego.inventory.service.config.MarketplaceListingDraftProperties;
import com.vattima.lego.inventory.service.dto.MarketplaceListingReadinessIssue;
import io.legohunter.data.bricklink.BricklinkInventoryColorPolicy;
import io.legohunter.data.dto.BricklinkMarketplaceListing;
import io.legohunter.data.dto.ItemInventory;
import io.legohunter.data.dto.ItemInventoryExternalCatalogItem;
import io.legohunter.data.dto.ItemInventoryPhoto;
import io.legohunter.data.dto.MarketplaceListing;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.legohunter.data.dto.ExternalService.Service.BRICKLINK;

@Component
public class MarketplaceListingDraftBusinessValidator {
    public static final String LISTING_STATUS_REMOVED = "REMOVED";
    public static final String LISTING_STATUS_ENDED = "ENDED";
    public static final String SALE_INTENT_SELLABLE = "SELLABLE";
    public static final String INVENTORY_STATE_AVAILABLE = "AVAILABLE";
    public static final String SEVERITY_BLOCKER = "BLOCKER";
    public static final String SEVERITY_WARN = "WARN";

    public String normalizeMarketplaceCode(String marketplaceCode) {
        return marketplaceCode == null ? null : marketplaceCode.trim().toUpperCase();
    }

    public List<MarketplaceListingReadinessIssue> hardBlockers(
            ItemInventory itemInventory,
            String marketplaceCode,
            Set<ItemInventoryExternalCatalogItem> catalogLinks,
            MarketplaceListing marketplaceListing,
            BricklinkMarketplaceListing bricklinkMarketplaceListing,
            MarketplaceListingDraftProperties properties
    ) {
        List<MarketplaceListingReadinessIssue> blockers = new ArrayList<>();
        if (!BRICKLINK.getServiceCode().equals(marketplaceCode)) {
            blockers.add(blocker("UNSUPPORTED_MARKETPLACE",
                    "Only BRICKLINK marketplace listing drafts are supported in Phase 4"));
            return blockers;
        }
        if (!Boolean.TRUE.equals(itemInventory.getActive())) {
            blockers.add(blocker("INVENTORY_INACTIVE",
                    "Inventory item must be active before it can be synced to a marketplace"));
        }
        if (!SALE_INTENT_SELLABLE.equals(itemInventory.getSaleIntentCode())) {
            blockers.add(blocker("INVENTORY_NOT_SELLABLE",
                    "Inventory item must have saleIntentCode SELLABLE before marketplace sync"));
        }
        if (!INVENTORY_STATE_AVAILABLE.equals(itemInventory.getInventoryStateCode())) {
            blockers.add(blocker("INVENTORY_NOT_AVAILABLE",
                    "Inventory item must have inventoryStateCode AVAILABLE before marketplace sync"));
        }
        if (findPrimaryBricklinkCatalogLink(catalogLinks).isEmpty()) {
            blockers.add(blocker("MISSING_PRIMARY_BRICKLINK_CATALOG_LINK",
                    "Inventory item must have a primary BrickLink catalog link before marketplace sync"));
        }
        Optional.ofNullable(marketplaceListing).ifPresentOrElse(listing -> {
            if (listing.getUnitPrice() == null) {
                blockers.add(blocker("MISSING_UNIT_PRICE",
                        "Marketplace listing must have a unitPrice before marketplace sync"));
            } else if (listing.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                blockers.add(blocker("INVALID_UNIT_PRICE",
                        "Marketplace listing must have a positive unitPrice before marketplace sync"));
            }
            if (!properties.isProduction()) {
                addNonProdBricklinkBlockers(blockers, bricklinkMarketplaceListing, properties);
            }
            findBricklinkCatalogLink(catalogLinks, listing.getExternalCatalogItemId())
                    .map(ItemInventoryExternalCatalogItem::getExternalCatalogItem)
                    .ifPresent(catalogItem -> {
                        BricklinkInventoryColorPolicy.Resolution color = BricklinkInventoryColorPolicy.resolve(
                                catalogItem.getItemTypeCode(),
                                bricklinkMarketplaceListing == null ? null : bricklinkMarketplaceListing.getColorId()
                        );
                        if (!color.valid()) {
                            blockers.add(blocker(color.errorCode(), color.message()));
                        }
                    });
        }, () -> blockers.add(blocker("MISSING_MARKETPLACE_LISTING_DRAFT",
                "Inventory item must have a marketplace listing draft before marketplace sync")));
        return blockers;
    }

    public List<MarketplaceListingReadinessIssue> warnings(ItemInventory itemInventory, Set<ItemInventoryPhoto> photos) {
        List<MarketplaceListingReadinessIssue> warnings = new ArrayList<>();
        if (SALE_INTENT_SELLABLE.equals(itemInventory.getSaleIntentCode()) && photos.isEmpty()) {
            warnings.add(MarketplaceListingReadinessIssue.builder()
                    .code("MISSING_ITEM_INVENTORY_PHOTOS")
                    .severity(SEVERITY_WARN)
                    .message("SELLABLE inventory item has no item_inventory_photos. Marketplace sync is allowed, but listing quality may be reduced.")
                    .build());
        }
        return warnings;
    }

    public Optional<ItemInventoryExternalCatalogItem> findPrimaryBricklinkCatalogLink(Set<ItemInventoryExternalCatalogItem> catalogLinks) {
        return catalogLinks.stream()
                .filter(link -> link.getExternalCatalogItem() != null)
                .filter(link -> BRICKLINK.getExternalServiceId().equals(link.getExternalCatalogItem().getExternalServiceId()))
                .filter(link -> Boolean.TRUE.equals(link.getPrimary()))
                .findFirst();
    }

    public Optional<ItemInventoryExternalCatalogItem> findBricklinkCatalogLink(
            Set<ItemInventoryExternalCatalogItem> catalogLinks,
            Integer externalCatalogItemId
    ) {
        if (externalCatalogItemId == null) {
            return findPrimaryBricklinkCatalogLink(catalogLinks);
        }
        return catalogLinks.stream()
                .filter(link -> externalCatalogItemId.equals(link.getExternalCatalogItemId()))
                .filter(link -> link.getExternalCatalogItem() != null)
                .filter(link -> BRICKLINK.getExternalServiceId().equals(link.getExternalCatalogItem().getExternalServiceId()))
                .findFirst();
    }

    public boolean hasOpenListingForMarketplace(
            Set<MarketplaceListing> listings,
            Integer listingExternalServiceId,
            Integer currentMarketplaceListingId
    ) {
        return listings.stream()
                .filter(listing -> listingExternalServiceId.equals(listing.getListingExternalServiceId()))
                .filter(listing -> currentMarketplaceListingId == null
                        || !currentMarketplaceListingId.equals(listing.getMarketplaceListingId()))
                .anyMatch(this::isOpenListing);
    }

    public boolean isOpenListing(MarketplaceListing listing) {
        return !LISTING_STATUS_REMOVED.equals(listing.getListingStatusCode())
                && !LISTING_STATUS_ENDED.equals(listing.getListingStatusCode());
    }

    private void addNonProdBricklinkBlockers(
            List<MarketplaceListingReadinessIssue> blockers,
            BricklinkMarketplaceListing bricklinkMarketplaceListing,
            MarketplaceListingDraftProperties properties
    ) {
        if (bricklinkMarketplaceListing == null) {
            blockers.add(blocker("MISSING_BRICKLINK_LISTING_DETAILS",
                    "Non-production BrickLink drafts must include BrickLink stockroom details before marketplace sync"));
            return;
        }
        if (!Boolean.TRUE.equals(bricklinkMarketplaceListing.getIsStockRoom())) {
            blockers.add(blocker("NON_PROD_BRICKLINK_STOCKROOM_REQUIRED",
                    "Non-production BrickLink drafts must remain stockroom-only before marketplace sync"));
        }
        if (!properties.getNonProdBricklinkStockroomId().equals(bricklinkMarketplaceListing.getStockRoomId())) {
            blockers.add(blocker("NON_PROD_BRICKLINK_STOCKROOM_ID_REQUIRED",
                    "Non-production BrickLink drafts must target stockroom "
                            + properties.getNonProdBricklinkStockroomId() + " before marketplace sync"));
        }
    }

    private MarketplaceListingReadinessIssue blocker(String code, String message) {
        return MarketplaceListingReadinessIssue.builder()
                .code(code)
                .severity(SEVERITY_BLOCKER)
                .message(message)
                .build();
    }
}

package com.vattima.lego.inventory.service.impl;

import com.vattima.lego.inventory.service.api.MarketplaceListingService;
import com.vattima.lego.inventory.service.config.MarketplaceListingDraftProperties;
import com.vattima.lego.inventory.service.dto.BricklinkListingDraftRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftCreateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftResponse;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftUpdateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingReadinessIssue;
import com.vattima.lego.inventory.service.dto.MarketplaceListingReadinessResponse;
import com.vattima.lego.inventory.service.exception.NotFoundException;
import com.vattima.lego.inventory.service.exception.ValidationException;
import com.vattima.lego.inventory.service.validation.MarketplaceListingDraftBusinessValidator;
import io.legohunter.data.dao.BricklinkMarketplaceListingDao;
import io.legohunter.data.dao.ExternalServiceDao;
import io.legohunter.data.dao.ItemInventoryDao;
import io.legohunter.data.dao.ItemInventoryExternalCatalogItemDao;
import io.legohunter.data.dao.ItemInventoryPhotoDao;
import io.legohunter.data.dao.MarketplaceListingDao;
import io.legohunter.data.dto.BricklinkMarketplaceListing;
import io.legohunter.data.dto.ExternalService;
import io.legohunter.data.dto.ItemInventory;
import io.legohunter.data.dto.ItemInventoryExternalCatalogItem;
import io.legohunter.data.dto.MarketplaceListing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.legohunter.data.dto.ExternalService.Service.BRICKLINK;

@Component
@RequiredArgsConstructor
public class MarketplaceListingServiceImpl implements MarketplaceListingService {
    private static final String LISTING_STATUS_DRAFT = "DRAFT";
    private static final String LISTING_STATUS_REMOVED = "REMOVED";

    private final BricklinkMarketplaceListingDao bricklinkMarketplaceListingDao;
    private final ExternalServiceDao externalServiceDao;
    private final ItemInventoryDao itemInventoryDao;
    private final ItemInventoryExternalCatalogItemDao itemInventoryExternalCatalogItemDao;
    private final ItemInventoryPhotoDao itemInventoryPhotoDao;
    private final MarketplaceListingDao marketplaceListingDao;
    private final MarketplaceListingDraftBusinessValidator validator;
    private final MarketplaceListingDraftProperties properties;

    @Override
    @Transactional
    public MarketplaceListingDraftResponse createDraft(MarketplaceListingDraftCreateRequest request) {
        String marketplaceCode = requireSupportedMarketplace(request.getMarketplaceCode());
        ExternalService marketplace = requireExternalService(marketplaceCode);
        ItemInventory itemInventory = requireInventory(request.getItemInventoryId());
        if (Boolean.TRUE.equals(request.getUpdateSaleIntentToSellable())
                && !MarketplaceListingDraftBusinessValidator.SALE_INTENT_SELLABLE.equals(itemInventory.getSaleIntentCode())) {
            itemInventoryDao.updateSaleIntent(
                    itemInventory.getItemInventoryId(),
                    MarketplaceListingDraftBusinessValidator.SALE_INTENT_SELLABLE,
                    null,
                    request.getSaleIntentNote()
            );
            itemInventory.setSaleIntentCode(MarketplaceListingDraftBusinessValidator.SALE_INTENT_SELLABLE);
        }

        Set<ItemInventoryExternalCatalogItem> catalogLinks = itemInventoryExternalCatalogItemDao
                .findByItemInventoryId(itemInventory.getItemInventoryId());
        ItemInventoryExternalCatalogItem catalogLink = validator
                .findBricklinkCatalogLink(catalogLinks, request.getExternalCatalogItemId())
                .orElseThrow(() -> new ValidationException("Inventory item must have a BrickLink catalog link for the requested draft"));
        validateDraftPreconditions(itemInventory, marketplace, null);

        MarketplaceListing listing = MarketplaceListing.builder()
                .itemInventoryId(itemInventory.getItemInventoryId())
                .listingExternalServiceId(marketplace.getExternalServiceId())
                .externalCatalogItemId(catalogLink.getExternalCatalogItemId())
                .listingStatusCode(LISTING_STATUS_DRAFT)
                .title(request.getTitle())
                .description(request.getDescription())
                .privateNotes(request.getPrivateNotes())
                .unitPrice(request.getUnitPrice())
                .currencyCode(request.getCurrencyCode())
                .fixedPrice(Boolean.TRUE.equals(request.getFixedPrice()))
                .build();
        MarketplaceListing persistedListing = marketplaceListingDao.insert(listing);
        BricklinkMarketplaceListing persistedBricklink = bricklinkMarketplaceListingDao.insert(
                toBricklinkMarketplaceListing(persistedListing.getMarketplaceListingId(), request.getBricklink())
        );
        return toResponse(persistedListing, Optional.of(persistedBricklink));
    }

    @Override
    public MarketplaceListingDraftResponse findByMarketplaceListingId(Integer marketplaceListingId) {
        MarketplaceListing listing = requireMarketplaceListing(marketplaceListingId);
        return toResponse(listing, bricklinkMarketplaceListingDao.findByMarketplaceListingId(marketplaceListingId));
    }

    @Override
    public Set<MarketplaceListingDraftResponse> findByItemInventoryId(Integer itemInventoryId) {
        requireInventory(itemInventoryId);
        return marketplaceListingDao.findByItemInventoryId(itemInventoryId).stream()
                .map(listing -> toResponse(listing, bricklinkMarketplaceListingDao.findByMarketplaceListingId(listing.getMarketplaceListingId())))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    @Transactional
    public MarketplaceListingDraftResponse updateDraft(Integer marketplaceListingId, MarketplaceListingDraftUpdateRequest request) {
        MarketplaceListing listing = requireMarketplaceListing(marketplaceListingId);
        requireSupportedMarketplace(serviceCode(listing));
        ItemInventory itemInventory = requireInventory(listing.getItemInventoryId());
        Set<ItemInventoryExternalCatalogItem> catalogLinks = itemInventoryExternalCatalogItemDao
                .findByItemInventoryId(itemInventory.getItemInventoryId());

        if (request.getExternalCatalogItemId() != null) {
            ItemInventoryExternalCatalogItem catalogLink = validator
                    .findBricklinkCatalogLink(catalogLinks, request.getExternalCatalogItemId())
                    .orElseThrow(() -> new ValidationException("Inventory item must have the requested BrickLink catalog link"));
            listing.setExternalCatalogItemId(catalogLink.getExternalCatalogItemId());
        }
        if (request.getListingStatusCode() != null) {
            listing.setListingStatusCode(request.getListingStatusCode());
        }
        if (request.getTitle() != null) {
            listing.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            listing.setDescription(request.getDescription());
        }
        if (request.getPrivateNotes() != null) {
            listing.setPrivateNotes(request.getPrivateNotes());
        }
        if (request.getUnitPrice() != null) {
            listing.setUnitPrice(request.getUnitPrice());
        }
        if (request.getCurrencyCode() != null) {
            listing.setCurrencyCode(request.getCurrencyCode());
        }
        if (request.getFixedPrice() != null) {
            listing.setFixedPrice(request.getFixedPrice());
        }
        validateDraftPreconditions(itemInventory, requireExternalService(BRICKLINK.getServiceCode()), marketplaceListingId);
        MarketplaceListing persistedListing = marketplaceListingDao.update(listing);

        Optional<BricklinkMarketplaceListing> existingBricklink = bricklinkMarketplaceListingDao.findByMarketplaceListingId(marketplaceListingId);
        BricklinkMarketplaceListing bricklink = applyBricklinkUpdate(
                existingBricklink.orElseGet(() -> BricklinkMarketplaceListing.builder()
                        .marketplaceListingId(marketplaceListingId)
                        .build()),
                request.getBricklink()
        );
        BricklinkMarketplaceListing persistedBricklink = existingBricklink.isPresent()
                ? bricklinkMarketplaceListingDao.update(bricklink)
                : bricklinkMarketplaceListingDao.insert(bricklink);
        return toResponse(persistedListing, Optional.of(persistedBricklink));
    }

    @Override
    @Transactional
    public MarketplaceListingDraftResponse removeDraft(Integer marketplaceListingId) {
        MarketplaceListing listing = requireMarketplaceListing(marketplaceListingId);
        listing.setListingStatusCode(LISTING_STATUS_REMOVED);
        listing.setEndedAt(ZonedDateTime.now(ZoneOffset.UTC));
        return toResponse(
                marketplaceListingDao.update(listing),
                bricklinkMarketplaceListingDao.findByMarketplaceListingId(marketplaceListingId)
        );
    }

    @Override
    public MarketplaceListingReadinessResponse evaluateReadiness(Integer itemInventoryId, String marketplaceCode) {
        String normalizedMarketplaceCode = marketplaceCode == null
                ? BRICKLINK.getServiceCode()
                : validator.normalizeMarketplaceCode(marketplaceCode);
        ItemInventory itemInventory = requireInventory(itemInventoryId);
        Set<ItemInventoryExternalCatalogItem> catalogLinks = itemInventoryExternalCatalogItemDao.findByItemInventoryId(itemInventoryId);
        Optional<MarketplaceListing> listing = marketplaceListingDao.findByItemInventoryId(itemInventoryId).stream()
                .filter(candidate -> BRICKLINK.getExternalServiceId().equals(candidate.getListingExternalServiceId()))
                .filter(validator::isOpenListing)
                .findFirst();
        Optional<BricklinkMarketplaceListing> bricklinkListing = listing
                .flatMap(candidate -> bricklinkMarketplaceListingDao.findByMarketplaceListingId(candidate.getMarketplaceListingId()));
        List<MarketplaceListingReadinessIssue> blockers = validator.hardBlockers(
                itemInventory,
                normalizedMarketplaceCode,
                catalogLinks,
                listing,
                bricklinkListing,
                properties
        );
        List<MarketplaceListingReadinessIssue> warnings = validator.warnings(
                itemInventory,
                itemInventoryPhotoDao.findByItemInventoryId(itemInventoryId)
        );
        return MarketplaceListingReadinessResponse.builder()
                .itemInventoryId(itemInventoryId)
                .marketplaceCode(normalizedMarketplaceCode)
                .readyForMarketplaceSync(blockers.isEmpty())
                .itemInventory(itemInventory)
                .marketplaceListing(listing.orElse(null))
                .bricklinkMarketplaceListing(bricklinkListing.orElse(null))
                .blockers(blockers)
                .warnings(warnings)
                .build();
    }

    private void validateDraftPreconditions(
            ItemInventory itemInventory,
            ExternalService marketplace,
            Integer currentMarketplaceListingId
    ) {
        if (!Boolean.TRUE.equals(itemInventory.getActive())) {
            throw new ValidationException("Inventory item must be active before creating a marketplace listing draft");
        }
        if (!MarketplaceListingDraftBusinessValidator.SALE_INTENT_SELLABLE.equals(itemInventory.getSaleIntentCode())) {
            throw new ValidationException("Inventory item must have saleIntentCode SELLABLE before creating a marketplace listing draft");
        }
        if (!MarketplaceListingDraftBusinessValidator.INVENTORY_STATE_AVAILABLE.equals(itemInventory.getInventoryStateCode())) {
            throw new ValidationException("Inventory item must have inventoryStateCode AVAILABLE before creating a marketplace listing draft");
        }
        if (validator.hasOpenListingForMarketplace(
                marketplaceListingDao.findByItemInventoryId(itemInventory.getItemInventoryId()),
                marketplace.getExternalServiceId(),
                currentMarketplaceListingId
        )) {
            throw new ValidationException("Inventory item already has an open marketplace listing for " + marketplace.getServiceCode());
        }
    }

    private MarketplaceListingDraftResponse toResponse(
            MarketplaceListing listing,
            Optional<BricklinkMarketplaceListing> bricklinkMarketplaceListing
    ) {
        return MarketplaceListingDraftResponse.builder()
                .marketplaceListing(listing)
                .bricklinkMarketplaceListing(bricklinkMarketplaceListing.orElse(null))
                .readiness(evaluateReadiness(listing.getItemInventoryId(), serviceCode(listing)))
                .build();
    }

    private BricklinkMarketplaceListing toBricklinkMarketplaceListing(Integer marketplaceListingId, BricklinkListingDraftRequest request) {
        return applyBricklinkUpdate(
                BricklinkMarketplaceListing.builder()
                        .marketplaceListingId(marketplaceListingId)
                        .build(),
                request
        );
    }

    private BricklinkMarketplaceListing applyBricklinkUpdate(
            BricklinkMarketplaceListing listing,
            BricklinkListingDraftRequest request
    ) {
        if (request != null) {
            listing.setColorId(request.getColorId());
            listing.setColorName(request.getColorName());
            listing.setBulk(request.getBulk());
            listing.setIsRetain(request.getIsRetain());
            listing.setIsStockRoom(request.getIsStockRoom());
            listing.setStockRoomId(request.getStockRoomId());
            listing.setSaleRate(request.getSaleRate());
            listing.setTierQuantity1(request.getTierQuantity1());
            listing.setTierPrice1(request.getTierPrice1());
            listing.setTierQuantity2(request.getTierQuantity2());
            listing.setTierPrice2(request.getTierPrice2());
            listing.setTierQuantity3(request.getTierQuantity3());
            listing.setTierPrice3(request.getTierPrice3());
            listing.setMyWeight(request.getMyWeight());
            listing.setRemarks(request.getRemarks());
        }
        if (!properties.isProduction()) {
            // Lower environments share the real BrickLink account; drafts must stay stockroom-only
            // until a future sync layer revalidates them.
            listing.setIsStockRoom(true);
            listing.setStockRoomId(properties.getNonProdBricklinkStockroomId());
            listing.setEnvironmentCode(properties.getEnvironmentCode());
            listing.setLastRemoteSafetyStatusCode("NOT_VERIFIED");
            listing.setLastRemoteSafetyMessage("Local draft only; remote BrickLink inventory has not been created or verified");
        }
        return listing;
    }

    private ItemInventory requireInventory(Integer itemInventoryId) {
        return itemInventoryDao.findByItemInventoryId(itemInventoryId)
                .orElseThrow(() -> new NotFoundException("Item inventory was not found: " + itemInventoryId));
    }

    private MarketplaceListing requireMarketplaceListing(Integer marketplaceListingId) {
        return marketplaceListingDao.findByMarketplaceListingId(marketplaceListingId)
                .orElseThrow(() -> new NotFoundException("Marketplace listing was not found: " + marketplaceListingId));
    }

    private ExternalService requireExternalService(String marketplaceCode) {
        return externalServiceDao.findByServiceCode(marketplaceCode)
                .orElseThrow(() -> new ValidationException("Marketplace external service was not found: " + marketplaceCode));
    }

    private String requireSupportedMarketplace(String marketplaceCode) {
        String normalizedMarketplaceCode = validator.normalizeMarketplaceCode(marketplaceCode);
        if (!BRICKLINK.getServiceCode().equals(normalizedMarketplaceCode)) {
            throw new ValidationException("Only BRICKLINK marketplace listing drafts are supported in Phase 3");
        }
        return normalizedMarketplaceCode;
    }

    private String serviceCode(MarketplaceListing listing) {
        if (BRICKLINK.getExternalServiceId().equals(listing.getListingExternalServiceId())) {
            return BRICKLINK.getServiceCode();
        }
        return String.valueOf(listing.getListingExternalServiceId());
    }
}

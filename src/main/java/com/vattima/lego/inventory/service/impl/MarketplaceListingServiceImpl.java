package com.vattima.lego.inventory.service.impl;

import com.vattima.lego.inventory.service.api.MarketplaceListingService;
import com.vattima.lego.inventory.service.config.MarketplaceListingDraftProperties;
import com.vattima.lego.inventory.service.dto.BricklinkListingDraftRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftCreateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftResponse;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftUpdateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingReadinessIssue;
import com.vattima.lego.inventory.service.dto.MarketplaceListingReadinessResponse;
import com.vattima.lego.inventory.service.dto.MarketplaceListingSyncRequestCancelRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingSyncRequestCreateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingSyncRequestPreviewResponse;
import com.vattima.lego.inventory.service.exception.NotFoundException;
import com.vattima.lego.inventory.service.exception.ValidationException;
import com.vattima.lego.inventory.service.validation.MarketplaceListingDraftBusinessValidator;
import io.legohunter.data.dao.BricklinkMarketplaceListingDao;
import io.legohunter.data.dao.ExternalServiceDao;
import io.legohunter.data.dao.ItemInventoryDao;
import io.legohunter.data.dao.ItemInventoryExternalCatalogItemDao;
import io.legohunter.data.dao.ItemInventoryPhotoDao;
import io.legohunter.data.dao.MarketplaceListingDao;
import io.legohunter.data.dao.MarketplaceListingSyncRequestDao;
import io.legohunter.data.dto.BricklinkMarketplaceListing;
import io.legohunter.data.dto.ExternalService;
import io.legohunter.data.dto.ItemInventory;
import io.legohunter.data.dto.ItemInventoryExternalCatalogItem;
import io.legohunter.data.dto.MarketplaceListingSyncRequest;
import io.legohunter.data.dto.MarketplaceListing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
    private static final String SYNC_REQUEST_TYPE_LISTING_CREATE = "LISTING_CREATE";
    private static final String SYNC_REQUEST_STATUS_PENDING = "PENDING";
    private static final String SYNC_REQUEST_STATUS_CLAIMED = "CLAIMED";
    private static final String SYNC_REQUEST_STATUS_CANCELLED = "CANCELLED";
    private static final String SYNC_REASON_MANUAL_LISTING_CREATE = "MANUAL_LISTING_CREATE";
    private static final String CREATED_BY_SERVICE = "LegoDataServiceMarketplaceListingSyncRequest";
    private static final String REMOTE_SCOPE_PUBLIC = "PUBLIC";
    private static final String REMOTE_SCOPE_STOCKROOM = "STOCKROOM";

    private final BricklinkMarketplaceListingDao bricklinkMarketplaceListingDao;
    private final ExternalServiceDao externalServiceDao;
    private final ItemInventoryDao itemInventoryDao;
    private final ItemInventoryExternalCatalogItemDao itemInventoryExternalCatalogItemDao;
    private final ItemInventoryPhotoDao itemInventoryPhotoDao;
    private final MarketplaceListingDao marketplaceListingDao;
    private final MarketplaceListingSyncRequestDao marketplaceListingSyncRequestDao;
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
                listing.orElse(null),
                bricklinkListing.orElse(null),
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

    @Override
    public MarketplaceListingSyncRequestPreviewResponse previewListingCreateSyncRequest(Integer marketplaceListingId) {
        MarketplaceListing listing = requireMarketplaceListing(marketplaceListingId);
        return syncRequestPreview(listing, requestOrDefault(null), Optional.empty());
    }

    @Override
    @Transactional
    public MarketplaceListingSyncRequestPreviewResponse createListingCreateSyncRequest(
            Integer marketplaceListingId,
            MarketplaceListingSyncRequestCreateRequest request
    ) {
        MarketplaceListing listing = requireMarketplaceListing(marketplaceListingId);
        MarketplaceListingSyncRequestCreateRequest effectiveRequest = requestOrDefault(request);
        MarketplaceListingSyncRequestPreviewResponse preview = syncRequestPreview(listing, effectiveRequest, Optional.empty());
        if (!preview.isReadyForSyncRequest()) {
            throw new ValidationException("Marketplace listing is not ready for LISTING_CREATE sync: "
                    + preview.getBlockers().stream()
                    .map(MarketplaceListingReadinessIssue::getCode)
                    .collect(Collectors.joining(", ")));
        }
        MarketplaceListingSyncRequest inserted = marketplaceListingSyncRequestDao.insert(preview.getSyncRequestCandidate());
        return syncRequestPreview(listing, effectiveRequest, Optional.of(inserted));
    }

    @Override
    public Set<MarketplaceListingSyncRequest> findSyncRequestsByMarketplaceListingId(Integer marketplaceListingId) {
        requireMarketplaceListing(marketplaceListingId);
        return marketplaceListingSyncRequestDao.findByMarketplaceListingId(marketplaceListingId);
    }

    @Override
    public MarketplaceListingSyncRequest findSyncRequestById(Long marketplaceListingSyncRequestId) {
        return requireSyncRequest(marketplaceListingSyncRequestId);
    }

    @Override
    @Transactional
    public MarketplaceListingSyncRequest cancelSyncRequest(
            Long marketplaceListingSyncRequestId,
            MarketplaceListingSyncRequestCancelRequest request
    ) {
        MarketplaceListingSyncRequest syncRequest = requireSyncRequest(marketplaceListingSyncRequestId);
        if (!SYNC_REQUEST_STATUS_PENDING.equals(syncRequest.getSyncRequestStatusCode())) {
            throw new ValidationException("Only PENDING marketplace listing sync requests can be cancelled");
        }
        syncRequest.setSyncRequestStatusCode(SYNC_REQUEST_STATUS_CANCELLED);
        syncRequest.setClaimedAt(null);
        syncRequest.setCompletedAt(ZonedDateTime.now(ZoneOffset.UTC));
        syncRequest.setLastErrorMessage(cancelReason(request));
        return marketplaceListingSyncRequestDao.update(syncRequest);
    }

    private MarketplaceListingSyncRequestPreviewResponse syncRequestPreview(
            MarketplaceListing listing,
            MarketplaceListingSyncRequestCreateRequest request,
            Optional<MarketplaceListingSyncRequest> insertedSyncRequest
    ) {
        String syncRequestTypeCode = normalizeSyncRequestTypeCode(request.getSyncRequestTypeCode());
        String marketplaceCode = requireSupportedMarketplace(serviceCode(listing));
        MarketplaceListingReadinessResponse readiness = evaluateReadiness(listing.getItemInventoryId(), marketplaceCode);
        Optional<BricklinkMarketplaceListing> bricklinkListing = bricklinkMarketplaceListingDao
                .findByMarketplaceListingId(listing.getMarketplaceListingId());
        Set<MarketplaceListingSyncRequest> activeRequests = marketplaceListingSyncRequestDao
                .findByMarketplaceListingIdAndSyncRequestTypeCodeAndSyncRequestStatusCodes(
                        listing.getMarketplaceListingId(),
                        syncRequestTypeCode,
                        Set.of(SYNC_REQUEST_STATUS_PENDING, SYNC_REQUEST_STATUS_CLAIMED)
                );
        List<MarketplaceListingReadinessIssue> blockers = new ArrayList<>(readiness.getBlockers());
        if (!SYNC_REQUEST_TYPE_LISTING_CREATE.equals(syncRequestTypeCode)) {
            blockers.add(blocker(
                    "UNSUPPORTED_SYNC_REQUEST_TYPE",
                    "Only LISTING_CREATE marketplace listing sync requests are supported by lego-data-service in Phase 4"
            ));
        }
        bricklinkListing.map(BricklinkMarketplaceListing::getBricklinkInventoryId)
                .ifPresent(remoteInventoryId -> blockers.add(blocker(
                        "BRICKLINK_REMOTE_INVENTORY_ALREADY_EXISTS",
                        "LISTING_CREATE is only valid for local drafts that do not already have a BrickLink inventory id"
                )));
        if (!activeRequests.isEmpty() && insertedSyncRequest.isEmpty()) {
            blockers.add(blocker(
                    "ACTIVE_SYNC_REQUEST_ALREADY_EXISTS",
                    "Marketplace listing already has an active LISTING_CREATE sync request"
            ));
        }
        return MarketplaceListingSyncRequestPreviewResponse.builder()
                .marketplaceListingId(listing.getMarketplaceListingId())
                .syncRequestTypeCode(syncRequestTypeCode)
                .readyForSyncRequest(blockers.isEmpty())
                .syncRequest(insertedSyncRequest.orElse(null))
                .syncRequestCandidate(syncRequestCandidate(listing, request, syncRequestTypeCode, bricklinkListing))
                .readiness(readiness)
                .activeSyncRequests(activeRequests)
                .blockers(blockers)
                .warnings(readiness.getWarnings())
                .build();
    }

    private MarketplaceListingSyncRequest syncRequestCandidate(
            MarketplaceListing listing,
            MarketplaceListingSyncRequestCreateRequest request,
            String syncRequestTypeCode,
            Optional<BricklinkMarketplaceListing> bricklinkListing
    ) {
        boolean production = properties.isProduction();
        return MarketplaceListingSyncRequest.builder()
                .marketplaceListingId(listing.getMarketplaceListingId())
                .listingExternalServiceId(listing.getListingExternalServiceId())
                .syncRequestTypeCode(syncRequestTypeCode)
                .syncRequestStatusCode(SYNC_REQUEST_STATUS_PENDING)
                .syncReasonCode(syncReasonCode(request.getSyncReasonCode()))
                .requestedUnitPrice(money(listing.getUnitPrice()))
                .currencyCode(listing.getCurrencyCode())
                .remoteInventoryId(bricklinkListing
                        .map(BricklinkMarketplaceListing::getBricklinkInventoryId)
                        .map(String::valueOf)
                        .orElse(null))
                .remoteVisibilityScopeCode(production ? REMOTE_SCOPE_PUBLIC : REMOTE_SCOPE_STOCKROOM)
                .remoteVisibilityContainerId(production ? null : properties.getNonProdBricklinkStockroomId())
                .remoteIsPubliclyAvailable(production)
                .environmentCode(properties.getEnvironmentCode())
                .createdByJobName(CREATED_BY_SERVICE)
                .attemptCount(0)
                .maxAttempts(request.getMaxAttempts() == null ? 3 : request.getMaxAttempts())
                .nextAttemptAt(ZonedDateTime.now(ZoneOffset.UTC))
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

    private MarketplaceListingSyncRequest requireSyncRequest(Long marketplaceListingSyncRequestId) {
        return marketplaceListingSyncRequestDao.findByMarketplaceListingSyncRequestId(marketplaceListingSyncRequestId)
                .orElseThrow(() -> new NotFoundException("Marketplace listing sync request was not found: " + marketplaceListingSyncRequestId));
    }

    private ExternalService requireExternalService(String marketplaceCode) {
        return externalServiceDao.findByServiceCode(marketplaceCode)
                .orElseThrow(() -> new ValidationException("Marketplace external service was not found: " + marketplaceCode));
    }

    private String requireSupportedMarketplace(String marketplaceCode) {
        String normalizedMarketplaceCode = validator.normalizeMarketplaceCode(marketplaceCode);
        if (!BRICKLINK.getServiceCode().equals(normalizedMarketplaceCode)) {
            throw new ValidationException("Only BRICKLINK marketplace listing drafts are supported in Phase 4");
        }
        return normalizedMarketplaceCode;
    }

    private MarketplaceListingSyncRequestCreateRequest requestOrDefault(MarketplaceListingSyncRequestCreateRequest request) {
        return request == null ? MarketplaceListingSyncRequestCreateRequest.builder().build() : request;
    }

    private String normalizeSyncRequestTypeCode(String syncRequestTypeCode) {
        if (syncRequestTypeCode == null || syncRequestTypeCode.isBlank()) {
            return SYNC_REQUEST_TYPE_LISTING_CREATE;
        }
        return syncRequestTypeCode.trim().toUpperCase();
    }

    private String syncReasonCode(String syncReasonCode) {
        if (syncReasonCode == null || syncReasonCode.isBlank()) {
            return SYNC_REASON_MANUAL_LISTING_CREATE;
        }
        return syncReasonCode.trim().toUpperCase();
    }

    private String cancelReason(MarketplaceListingSyncRequestCancelRequest request) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            return "Cancelled from lego-data-service";
        }
        return "Cancelled from lego-data-service: " + request.getReason().trim();
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private MarketplaceListingReadinessIssue blocker(String code, String message) {
        return MarketplaceListingReadinessIssue.builder()
                .code(code)
                .severity(MarketplaceListingDraftBusinessValidator.SEVERITY_BLOCKER)
                .message(message)
                .build();
    }

    private String serviceCode(MarketplaceListing listing) {
        if (BRICKLINK.getExternalServiceId().equals(listing.getListingExternalServiceId())) {
            return BRICKLINK.getServiceCode();
        }
        return String.valueOf(listing.getListingExternalServiceId());
    }
}

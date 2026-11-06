package com.vattima.lego.inventory.seed;

import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftCreateRequest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * The lego-data-service REST calls the seeding tool makes. Every write goes through the normal
 * public API, so the service's own validation and environment guards apply.
 */
public interface SeedServiceClient {

    /** {@code GET /api/v1/inventory/seed-sources}. */
    List<SeedSourceView> findSeedSources(String sourceSystem, String seedRunId);

    /** {@code GET /api/v1/items/number/{itemNumber}}: is the item in the target's BrickLink catalog. */
    boolean catalogItemExists(String itemNumber);

    /** {@code POST /api/v1/inventory}: one acquisition transaction holding every new item. */
    void addItemInventory(AddItemInventoryRequest request);

    /** {@code GET /api/v1/inventory/{id}/marketplace-listings}. */
    List<ListingView> findListings(int itemInventoryId);

    /** {@code POST /api/v1/marketplace-listings}; returns the new listing id. */
    int createDraft(MarketplaceListingDraftCreateRequest request);

    /** {@code GET /api/v1/marketplace-listings/{id}/sync-requests}. */
    List<SyncRequestView> findSyncRequests(int marketplaceListingId);

    record SeedSourceView(int itemInventoryId, String sourceSystem, String sourceKey, String seedRunId) {
    }

    record ListingView(
            int marketplaceListingId,
            Integer listingExternalServiceId,
            String listingStatusCode,
            BigDecimal unitPrice,
            Boolean fixedPrice,
            Integer bricklinkInventoryId,
            String stockRoomId,
            String environmentCode,
            String lastRemoteSafetyStatusCode
    ) {
    }

    record SyncRequestView(
            long marketplaceListingSyncRequestId,
            String syncRequestTypeCode,
            String syncRequestStatusCode,
            ZonedDateTime createdAt,
            String lastErrorMessage
    ) {
    }

    /** A non-2xx answer from the service; the body carries its validation messages. */
    class SeedServiceException extends RuntimeException {
        private final int status;

        public SeedServiceException(String message, int status) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}

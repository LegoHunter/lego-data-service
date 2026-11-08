package com.vattima.lego.inventory.service.impl;

import com.vattima.lego.inventory.service.api.SeededItemTeardownService;
import com.vattima.lego.inventory.service.config.MarketplaceListingDraftProperties;
import com.vattima.lego.inventory.service.dto.SeededItemTeardownPrepareResponse;
import com.vattima.lego.inventory.service.exception.ValidationException;
import io.legohunter.data.dao.BricklinkMarketplaceListingDao;
import io.legohunter.data.dao.MarketplaceListingDao;
import io.legohunter.data.dao.MarketplaceListingSyncRequestDao;
import io.legohunter.data.dao.SeededItemTeardownDao;
import io.legohunter.data.dto.BricklinkMarketplaceListing;
import io.legohunter.data.dto.MarketplaceListing;
import io.legohunter.data.dto.MarketplaceListingSyncRequest;
import io.legohunter.data.dto.SeededItemTeardownCheck;
import io.legohunter.data.dto.SeededItemTeardownResult;
import io.legohunter.lego.data.environment.LegoEnvironmentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeededItemTeardownServiceImpl implements SeededItemTeardownService {
    static final String TYPE_LISTING_DELETE = "LISTING_DELETE";
    static final String REASON_SEED_TEARDOWN = "SEED_TEARDOWN";
    static final String LISTING_STATUS_ENDED = "ENDED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_CLAIMED = "CLAIMED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String CREATED_BY = "LegoDataServiceSeedTeardown";

    private final LegoEnvironmentProperties environment;
    private final MarketplaceListingDraftProperties listingProperties;
    private final SeededItemTeardownDao seededItemTeardownDao;
    private final MarketplaceListingDao marketplaceListingDao;
    private final BricklinkMarketplaceListingDao bricklinkMarketplaceListingDao;
    private final MarketplaceListingSyncRequestDao marketplaceListingSyncRequestDao;

    @Override
    public SeededItemTeardownCheck check(Integer itemInventoryId) {
        refuseInProd();
        return seededItemTeardownDao.check(itemInventoryId);
    }

    @Override
    @Transactional
    public SeededItemTeardownPrepareResponse prepare(Integer itemInventoryId) {
        refuseInProd();
        if (!seededItemTeardownDao.check(itemInventoryId).isSeededItem()) {
            throw new ValidationException("Item inventory " + itemInventoryId + " is not a seeded item; only seeded items can be torn down");
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        List<Long> cancelled = new ArrayList<>();
        List<Long> lotDeletes = new ArrayList<>();
        List<Integer> ended = new ArrayList<>();
        List<Long> inProgress = new ArrayList<>();

        List<MarketplaceListing> listings = marketplaceListingDao.findByItemInventoryId(itemInventoryId).stream()
                .sorted(Comparator.comparing(MarketplaceListing::getMarketplaceListingId))
                .toList();
        for (MarketplaceListing listing : listings) {
            Set<MarketplaceListingSyncRequest> requests = marketplaceListingSyncRequestDao.findByMarketplaceListingId(listing.getMarketplaceListingId());
            Optional<MarketplaceListingSyncRequest> activeDelete = Optional.empty();
            for (MarketplaceListingSyncRequest request : requests) {
                String status = request.getSyncRequestStatusCode();
                boolean delete = TYPE_LISTING_DELETE.equals(request.getSyncRequestTypeCode());
                if (delete && (STATUS_PENDING.equals(status) || STATUS_CLAIMED.equals(status))) {
                    activeDelete = Optional.of(request);
                } else if (STATUS_CLAIMED.equals(status)) {
                    inProgress.add(request.getMarketplaceListingSyncRequestId());
                } else if (STATUS_PENDING.equals(status)) {
                    request.setSyncRequestStatusCode(STATUS_CANCELLED);
                    request.setClaimedAt(null);
                    request.setCompletedAt(now);
                    request.setLastErrorMessage("Cancelled by seeded-item teardown");
                    marketplaceListingSyncRequestDao.update(request);
                    cancelled.add(request.getMarketplaceListingSyncRequestId());
                }
            }

            Integer lotId = bricklinkMarketplaceListingDao.findByMarketplaceListingId(listing.getMarketplaceListingId())
                    .map(BricklinkMarketplaceListing::getBricklinkInventoryId)
                    .orElse(null);
            if (lotId != null) {
                MarketplaceListingSyncRequest delete = activeDelete
                        .orElseGet(() -> marketplaceListingSyncRequestDao.insert(listingDelete(listing, lotId, now)));
                lotDeletes.add(delete.getMarketplaceListingSyncRequestId());
            } else if (!LISTING_STATUS_ENDED.equals(listing.getListingStatusCode())) {
                listing.setListingStatusCode(LISTING_STATUS_ENDED);
                listing.setLastSynchronizedAt(now);
                marketplaceListingDao.update(listing);
                ended.add(listing.getMarketplaceListingId());
            }
        }
        log.info("seed_teardown.prepared itemInventoryId={} cancelled={} lotDeletes={} ended={} inProgress={}",
                itemInventoryId, cancelled, lotDeletes, ended, inProgress);
        return SeededItemTeardownPrepareResponse.builder()
                .itemInventoryId(itemInventoryId)
                .cancelledSyncRequestIds(cancelled)
                .lotDeleteSyncRequestIds(lotDeletes)
                .endedMarketplaceListingIds(ended)
                .inProgressSyncRequestIds(inProgress)
                .check(seededItemTeardownDao.check(itemInventoryId))
                .build();
    }

    @Override
    @Transactional
    public SeededItemTeardownResult deleteLocalRows(Integer itemInventoryId) {
        refuseInProd();
        try {
            SeededItemTeardownResult result = seededItemTeardownDao.teardown(itemInventoryId);
            log.info("seed_teardown.local_rows_deleted itemInventoryId={} deletedRows={} deletedTransactionIds={}",
                    itemInventoryId, result.getDeletedRows(), result.getDeletedTransactionIds());
            return result;
        } catch (IllegalStateException e) {
            throw new ValidationException(e.getMessage());
        }
    }

    private MarketplaceListingSyncRequest listingDelete(MarketplaceListing listing, Integer lotId, ZonedDateTime now) {
        return MarketplaceListingSyncRequest.builder()
                .marketplaceListingId(listing.getMarketplaceListingId())
                .listingExternalServiceId(listing.getListingExternalServiceId())
                .syncRequestTypeCode(TYPE_LISTING_DELETE)
                .syncRequestStatusCode(STATUS_PENDING)
                .syncReasonCode(REASON_SEED_TEARDOWN)
                .currencyCode(listing.getCurrencyCode())
                .remoteInventoryId(String.valueOf(lotId))
                .remoteVisibilityScopeCode("STOCKROOM")
                .remoteVisibilityContainerId(listingProperties.getNonProdBricklinkStockroomId())
                .remoteIsPubliclyAvailable(false)
                .environmentCode(listingProperties.getEnvironmentCode())
                .createdByJobName(CREATED_BY)
                .attemptCount(0)
                .maxAttempts(3)
                .nextAttemptAt(now)
                .build();
    }

    private void refuseInProd() {
        if (environment.isProduction() || listingProperties.isProduction()) {
            throw new ValidationException("Seeded-item teardown is refused in PROD: it only removes lower-environment seed data");
        }
    }
}

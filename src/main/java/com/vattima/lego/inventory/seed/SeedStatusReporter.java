package com.vattima.lego.inventory.seed;

import com.vattima.lego.inventory.seed.SeedServiceClient.ListingView;
import com.vattima.lego.inventory.seed.SeedServiceClient.SeedSourceView;
import com.vattima.lego.inventory.seed.SeedServiceClient.SyncRequestView;
import io.legohunter.data.dto.ExternalService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.legohunter.data.dto.ItemInventorySeedSource.SOURCE_SYSTEM_LEGO_PROD_LEGACY;

/**
 * Reports how far each seeded item has got: draft, priced by the pricing plane, lot created and
 * verified. Read-only.
 */
public class SeedStatusReporter {

    static final String SYNC_TYPE_LISTING_CREATE = "LISTING_CREATE";
    static final String SAFETY_VERIFIED = "VERIFIED";
    static final Set<String> SYNC_PROBLEM_STATUSES = Set.of("FAILED", "BLOCKED", "CANCELLED");

    public enum Stage {
        /** Seeded, but no BrickLink listing yet: re-run import with the same manifest. */
        NO_LISTING,
        /** Unpriced draft waiting for the pricing plane's initial price. */
        AWAITING_PRICE,
        /** Priced; the LISTING_CREATE has not produced a lot yet. */
        LOT_PENDING,
        /** The LISTING_CREATE ended FAILED, BLOCKED or CANCELLED. */
        SYNC_PROBLEM,
        /** A lot exists but its last remote safety check is not VERIFIED. */
        LOT_UNVERIFIED,
        /** A lot exists and its system block was verified. */
        LOT_VERIFIED
    }

    public record Row(
            String sourceKey,
            int itemInventoryId,
            String seedRunId,
            Integer marketplaceListingId,
            String listingStatusCode,
            BigDecimal unitPrice,
            Long listingCreateSyncRequestId,
            String listingCreateStatusCode,
            Integer bricklinkInventoryId,
            String stockRoomId,
            String environmentCode,
            String safetyStatusCode,
            Stage stage
    ) {
    }

    public record Report(List<Row> rows) {
        public Map<Stage, Long> counts() {
            Map<Stage, Long> counts = new EnumMap<>(Stage.class);
            for (Stage stage : Stage.values()) {
                counts.put(stage, rows.stream().filter(row -> row.stage() == stage).count());
            }
            return counts;
        }
    }

    private final SeedServiceClient client;

    public SeedStatusReporter(SeedServiceClient client) {
        this.client = client;
    }

    public Report report(String seedRunId) {
        Integer bricklinkServiceId = ExternalService.Service.BRICKLINK.getExternalServiceId();
        List<SeedSourceView> sources = new ArrayList<>(client.findSeedSources(SOURCE_SYSTEM_LEGO_PROD_LEGACY, seedRunId));
        sources.sort(Comparator.comparingInt(SeedSourceView::itemInventoryId));
        List<Row> rows = new ArrayList<>();
        for (SeedSourceView source : sources) {
            ListingView listing = client.findListings(source.itemInventoryId()).stream()
                    .filter(view -> bricklinkServiceId.equals(view.listingExternalServiceId()))
                    .findFirst()
                    .orElse(null);
            if (listing == null) {
                rows.add(new Row(source.sourceKey(), source.itemInventoryId(), source.seedRunId(),
                        null, null, null, null, null, null, null, null, null, Stage.NO_LISTING));
                continue;
            }
            SyncRequestView create = client.findSyncRequests(listing.marketplaceListingId()).stream()
                    .filter(request -> SYNC_TYPE_LISTING_CREATE.equals(request.syncRequestTypeCode()))
                    .max(Comparator.comparingLong(SyncRequestView::marketplaceListingSyncRequestId))
                    .orElse(null);
            rows.add(new Row(
                    source.sourceKey(),
                    source.itemInventoryId(),
                    source.seedRunId(),
                    listing.marketplaceListingId(),
                    listing.listingStatusCode(),
                    listing.unitPrice(),
                    create == null ? null : create.marketplaceListingSyncRequestId(),
                    create == null ? null : create.syncRequestStatusCode(),
                    listing.bricklinkInventoryId(),
                    listing.stockRoomId(),
                    listing.environmentCode(),
                    listing.lastRemoteSafetyStatusCode(),
                    stage(listing, create)
            ));
        }
        return new Report(List.copyOf(rows));
    }

    static Stage stage(ListingView listing, SyncRequestView create) {
        if (listing.bricklinkInventoryId() != null) {
            return SAFETY_VERIFIED.equals(listing.lastRemoteSafetyStatusCode()) ? Stage.LOT_VERIFIED : Stage.LOT_UNVERIFIED;
        }
        if (create != null && SYNC_PROBLEM_STATUSES.contains(create.syncRequestStatusCode())) {
            return Stage.SYNC_PROBLEM;
        }
        if (listing.unitPrice() == null) {
            return Stage.AWAITING_PRICE;
        }
        return Stage.LOT_PENDING;
    }
}

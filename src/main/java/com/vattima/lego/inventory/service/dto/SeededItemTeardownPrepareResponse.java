package com.vattima.lego.inventory.service.dto;

import io.legohunter.data.dto.SeededItemTeardownCheck;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** What {@code prepare} did for one seeded item (HOMELAB-40 teardown), and what still blocks it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeededItemTeardownPrepareResponse {
    private Integer itemInventoryId;
    /** PENDING requests cancelled so they cannot change or create a lot during teardown. */
    private List<Long> cancelledSyncRequestIds;
    /** LISTING_DELETE requests queued (or already queued) for listings that hold a lot. */
    private List<Long> lotDeleteSyncRequestIds;
    /** Listings with no lot that were ended. */
    private List<Integer> endedMarketplaceListingIds;
    /** CLAIMED requests a worker is running now; prepare again once they finish. */
    private List<Long> inProgressSyncRequestIds;
    /** The blockers after prepare. */
    private SeededItemTeardownCheck check;
}

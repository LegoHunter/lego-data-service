package com.vattima.lego.inventory.service.api;

import com.vattima.lego.inventory.service.dto.SeededItemTeardownPrepareResponse;
import io.legohunter.data.dto.SeededItemTeardownCheck;
import io.legohunter.data.dto.SeededItemTeardownResult;

/**
 * Teardown of lower-environment seeded items (HOMELAB-40): refused in PROD and for any item without
 * a seed-source row. The order is prepare, wait until {@link #check} has no BrickLink lots and no
 * active sync requests, then {@link #deleteLocalRows}.
 */
public interface SeededItemTeardownService {
    SeededItemTeardownCheck check(Integer itemInventoryId);

    /**
     * Stops further marketplace work on the item and queues the deletion of its lots: cancels its
     * PENDING sync requests (other than LISTING_DELETE), queues one LISTING_DELETE per BrickLink
     * listing that holds a lot, and ends its listings that hold none, so the pricing plane no longer
     * creates lots for them. CLAIMED requests are left alone and reported.
     */
    SeededItemTeardownPrepareResponse prepare(Integer itemInventoryId);

    /** Deletes every local row of the item once nothing blocks it. */
    SeededItemTeardownResult deleteLocalRows(Integer itemInventoryId);
}

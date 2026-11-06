package com.vattima.lego.inventory.seed;

import java.util.List;
import java.util.Set;

/**
 * Which unsold legacy items {@code export} selects.
 *
 * <p>Theme filtering is not offered: the legacy tables the read-only identity may read hold a single
 * "Lego Set" category for unsold items, so there is nothing to filter on.
 *
 * @param buckets     buckets to include; empty means all
 * @param sourceKeys  explicit legacy {@code bl_inventory_id}s; empty means no restriction
 * @param itemNumbers explicit BrickLink item numbers; empty means no restriction
 * @param limit       maximum number of seedable items in the manifest
 */
public record ExportFilter(Set<SeedBucket> buckets, List<String> sourceKeys, List<String> itemNumbers, int limit) {

    public ExportFilter {
        buckets = buckets == null || buckets.isEmpty() ? Set.of(SeedBucket.values()) : Set.copyOf(buckets);
        sourceKeys = sourceKeys == null ? List.of() : List.copyOf(sourceKeys);
        itemNumbers = itemNumbers == null ? List.of() : List.copyOf(itemNumbers);
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
    }
}

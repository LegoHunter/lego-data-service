package com.vattima.lego.inventory.seed;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * The reviewable output of {@code export} and the input of {@code import}.
 *
 * <p>It holds item data only. Every listing and lot column of the legacy row (remarks, price,
 * stockroom, the BrickLink lot id, order, internal comments, the legacy uuid and description) is
 * left out by construction: {@link ManifestItem} has no field for any of them.
 *
 * @param sourceSystem the provenance source, always {@code LEGO_PROD_LEGACY} today
 * @param exportedAt   when the manifest was written (UTC)
 * @param filter       the selection that produced it, for the reviewer
 * @param items        seedable items, in source-key order
 * @param skipped      unsold source items that were not seedable, with the reason
 */
public record SeedManifest(
        String sourceSystem,
        ZonedDateTime exportedAt,
        ExportFilter filter,
        List<ManifestItem> items,
        List<SkippedItem> skipped
) {

    /**
     * One item to seed, already mapped to the intake API's vocabulary.
     *
     * @param sourceKey                 legacy {@code bricklink_inventory.bl_inventory_id}
     * @param itemNumber                BrickLink catalog number, e.g. {@code 6365-1}
     * @param itemName                  catalog name, for the reviewer only (never sent)
     * @param bucket                    the selection bucket the item falls in
     * @param newOrUsed                 {@code N} or {@code U}
     * @param completeness              always {@code C} (legacy {@code S} becomes {@code C} + sealed)
     * @param sealed                    factory sealed
     * @param builtOnce                 built at least once (legacy NULL becomes false)
     * @param itemConditionCode         {@code SL} for sealed items, otherwise {@code NA}
     * @param boxConditionCode          box grade code
     * @param instructionsConditionCode instructions grade code
     * @param cost                      acquisition cost in USD (legacy NULL becomes 0.00)
     */
    public record ManifestItem(
            String sourceKey,
            String itemNumber,
            String itemName,
            SeedBucket bucket,
            String newOrUsed,
            String completeness,
            boolean sealed,
            boolean builtOnce,
            String itemConditionCode,
            String boxConditionCode,
            String instructionsConditionCode,
            java.math.BigDecimal cost
    ) {
    }

    /**
     * An unsold source item that {@code export} did not select, and why.
     */
    public record SkippedItem(String sourceKey, String itemNumber, SkipReason reason) {
    }
}

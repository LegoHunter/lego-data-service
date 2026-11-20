package com.vattima.lego.inventory.seed;

import com.vattima.lego.inventory.seed.SeedManifest.ManifestItem;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Maps one legacy item row to a manifest item, or says why it can't be seeded.
 *
 * <p>Rules agreed on HOMELAB-40 (2026-11-04):
 * <ul>
 *     <li>Only SET items with quantity 1.</li>
 *     <li>Completeness C stays C; S becomes C with sealed=true; anything else is skipped.</li>
 *     <li>Sealed items: item condition SL; a missing box grade becomes E and a missing
 *     instructions grade becomes M.</li>
 *     <li>Unsealed items (used, or new and opened): item condition NA; both grades are required,
 *     otherwise the item is skipped.</li>
 *     <li>A NULL built_once becomes false; a NULL cost becomes 0.00.</li>
 * </ul>
 */
public final class SeedItemMapper {

    static final String ITEM_TYPE_SET = "SET";
    static final String CONDITION_SEALED = "SL";
    static final String CONDITION_NOT_APPLICABLE = "NA";
    static final String SEALED_DEFAULT_BOX_CONDITION = "E";
    static final String SEALED_DEFAULT_INSTRUCTIONS_CONDITION = "M";

    private SeedItemMapper() {
    }

    /**
     * The outcome of mapping one row: exactly one of {@code item} and {@code skipReason} is set.
     */
    public record Result(ManifestItem item, SkipReason skipReason) {
        static Result of(ManifestItem item) {
            return new Result(item, null);
        }

        static Result skip(SkipReason reason) {
            return new Result(null, reason);
        }

        public boolean seedable() {
            return item != null;
        }
    }

    public static Result map(LegacyItemRow row) {
        if (!ITEM_TYPE_SET.equalsIgnoreCase(trim(row.itemType()))) {
            return Result.skip(SkipReason.NOT_A_SET);
        }
        if (row.quantity() != 1) {
            return Result.skip(SkipReason.QUANTITY_NOT_ONE);
        }
        String newOrUsed = upper(row.newOrUsed());
        if (!"N".equals(newOrUsed) && !"U".equals(newOrUsed)) {
            return Result.skip(SkipReason.UNSUPPORTED_NEW_OR_USED);
        }
        String completeness = upper(row.completeness());
        boolean sealed;
        if ("S".equals(completeness)) {
            sealed = true;
        } else if ("C".equals(completeness)) {
            sealed = row.sealed();
        } else {
            return Result.skip(SkipReason.UNSUPPORTED_COMPLETENESS);
        }

        String box = upper(row.boxConditionCode());
        String instructions = upper(row.instructionsConditionCode());
        String itemCondition;
        SeedBucket bucket;
        if (sealed) {
            box = box == null ? SEALED_DEFAULT_BOX_CONDITION : box;
            instructions = instructions == null ? SEALED_DEFAULT_INSTRUCTIONS_CONDITION : instructions;
            itemCondition = CONDITION_SEALED;
            bucket = SeedBucket.SEALED;
        } else {
            if (box == null || instructions == null) {
                return Result.skip(SkipReason.MISSING_GRADE);
            }
            itemCondition = CONDITION_NOT_APPLICABLE;
            bucket = "U".equals(newOrUsed) ? SeedBucket.USED : SeedBucket.NEW;
        }

        BigDecimal cost = row.cost() == null ? BigDecimal.ZERO : row.cost();
        return Result.of(new ManifestItem(
                row.sourceKey(),
                trim(row.itemNumber()),
                row.itemName(),
                bucket,
                newOrUsed,
                "C",
                sealed,
                Boolean.TRUE.equals(row.builtOnce()),
                itemCondition,
                box,
                instructions,
                cost.setScale(2, RoundingMode.HALF_UP)
        ));
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String upper(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(java.util.Locale.ROOT);
    }
}

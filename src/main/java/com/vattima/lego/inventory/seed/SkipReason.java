package com.vattima.lego.inventory.seed;

/**
 * Why an unsold source item was not seeded.
 */
public enum SkipReason {
    /** Only SET items are seeded (books and gear are left out). */
    NOT_A_SET,
    /** The intake API accepts quantity 1 only. */
    QUANTITY_NOT_ONE,
    /** Completeness other than C or S (for example X). */
    UNSUPPORTED_COMPLETENESS,
    /** new_or_used is neither N nor U. */
    UNSUPPORTED_NEW_OR_USED,
    /** An unsealed item without both a box and an instructions grade. */
    MISSING_GRADE,
    /** The item is not in the requested buckets. */
    BUCKET_NOT_SELECTED,
    /** More seedable items than the export limit; this one was past it. */
    OVER_LIMIT,
    /** The item number is not in the target environment's BrickLink catalog (found at import). */
    NOT_IN_TARGET_CATALOG
}

package com.vattima.lego.inventory.seed;

/**
 * The selection buckets agreed for HOMELAB-40 seeding.
 */
public enum SeedBucket {
    /** Used and complete; needs both box and instructions grades. */
    USED,
    /** New and factory sealed; missing grades default to box E and instructions M. */
    SEALED,
    /** New, complete and not sealed; needs both grades. */
    NEW
}

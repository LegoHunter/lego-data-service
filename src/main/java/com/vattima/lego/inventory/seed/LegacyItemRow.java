package com.vattima.lego.inventory.seed;

import java.math.BigDecimal;

/**
 * The item columns {@code export} reads from one unsold legacy {@code bricklink_inventory} row.
 *
 * <p>There is deliberately no field for any listing or lot column. See
 * {@link LegacyItemExporter#SELECTED_COLUMNS}.
 */
public record LegacyItemRow(
        String sourceKey,
        String itemNumber,
        String itemName,
        String itemType,
        int quantity,
        String newOrUsed,
        String completeness,
        boolean sealed,
        Boolean builtOnce,
        BigDecimal cost,
        String boxConditionCode,
        String instructionsConditionCode
) {
}

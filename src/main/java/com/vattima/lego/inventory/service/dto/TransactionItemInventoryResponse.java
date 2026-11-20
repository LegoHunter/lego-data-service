package com.vattima.lego.inventory.service.dto;

import io.legohunter.data.dto.ItemInventory;
import io.legohunter.data.dto.ItemInventoryExternalCatalogItem;
import io.legohunter.data.dto.TransactionItem;
import io.legohunter.data.dto.TransactionItemCost;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionItemInventoryResponse {
    private TransactionItem transactionItem;
    private ItemInventory itemInventory;
    private Set<ItemInventoryExternalCatalogItem> catalogItems;
    private List<TransactionItemCost> costs;
}

package com.vattima.lego.inventory.service.dto;

import io.legohunter.data.dto.ItemInventory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventorySearchResponse {
    private Set<ItemInventory> items;
    private int total;
    private int limit;
    private int offset;
}

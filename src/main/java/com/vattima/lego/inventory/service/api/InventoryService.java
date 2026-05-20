package com.vattima.lego.inventory.service.api;

import io.legohunter.data.dto.ItemInventory;

import java.util.List;

public interface InventoryService {
    List<ItemInventory> findAllForSale();
}

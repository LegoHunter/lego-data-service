package com.vattima.lego.inventory.service.api;

import net.lego.data.v2.dto.ItemInventory;

import java.util.List;

public interface InventoryService {
    List<ItemInventory> findAllForSale();
}

package com.vattima.lego.inventory.service.internal.impl;

import com.vattima.lego.inventory.service.api.InventoryService;
import lombok.RequiredArgsConstructor;
import io.legohunter.data.dao.ItemInventoryDao;
import io.legohunter.data.dto.ItemInventory;

import java.util.List;

import static java.lang.Boolean.TRUE;

@RequiredArgsConstructor
public class InternalInventoryService implements InventoryService {
    private final ItemInventoryDao itemInventoryDao;

    @Override
    public List<ItemInventory> findAllForSale() {
        return itemInventoryDao.findAll()
                .stream()
                .filter(itemInventory -> Boolean.TRUE.equals(itemInventory.getForSale()))
                .toList();
    }
}

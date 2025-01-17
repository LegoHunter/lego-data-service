package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.impl.ItemInventoryService;
import lombok.RequiredArgsConstructor;
import net.lego.data.v2.dao.ItemInventoryDao;
import net.lego.data.v2.dto.ItemInventory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class ItemInventoryController {

    private final ItemInventoryService itemInventoryService;
    private final ItemInventoryDao itemInventoryDao;

    @GetMapping
    public ResponseEntity<List<ItemInventory>> findAll() {
        return ResponseEntity.ok(itemInventoryDao.findAll());
    }

    @GetMapping("/uuid/{uuid}")
    public ResponseEntity<ItemInventory> findByUuid(@PathVariable final String uuid) {
        return itemInventoryDao.findByUuid(uuid).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{itemInventoryId}")
    public ResponseEntity<ItemInventory> findByUuid(@PathVariable final Integer itemInventoryId) {
        return itemInventoryDao.findByItemInventoryId(itemInventoryId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ItemInventory> addItemInventory(@RequestBody AddItemInventoryRequest addItemInventoryRequest) {
        return ResponseEntity.ok(itemInventoryService.addItemInventory(addItemInventoryRequest));
    }
}

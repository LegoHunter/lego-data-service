package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.api.ItemInventoryService;
import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.AddItemInventoryResponse;
import com.vattima.lego.inventory.service.logging.LogExecution;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.legohunter.data.dao.ItemInventoryDao;
import io.legohunter.data.dto.ItemInventory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Validated
public class ItemInventoryController {

    private final ItemInventoryService itemInventoryService;
    private final ItemInventoryDao itemInventoryDao;

    @GetMapping
    @LogExecution
    public ResponseEntity<Set<ItemInventory>> findAll() {
        return ResponseEntity.ok(itemInventoryDao.findAll());
    }

    @GetMapping("/uuid/{uuid}")
    @LogExecution
    public ResponseEntity<ItemInventory> findByUuid(@PathVariable final String uuid) {
        return itemInventoryDao.findByUuid(uuid).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{itemInventoryId}")
    @LogExecution
    public ResponseEntity<ItemInventory> findByUuid(@PathVariable final Integer itemInventoryId) {
        return itemInventoryDao.findByItemInventoryId(itemInventoryId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AddItemInventoryResponse> addItemInventory(@Valid @RequestBody AddItemInventoryRequest addItemInventoryRequest) {
        return ResponseEntity.ok(itemInventoryService.addItemInventory(addItemInventoryRequest));
    }
}

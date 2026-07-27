package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.api.ItemInventoryService;
import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.AddItemInventoryResponse;
import com.vattima.lego.inventory.service.dto.InventoryPhysicalUpdateRequest;
import com.vattima.lego.inventory.service.dto.InventorySearchResponse;
import com.vattima.lego.inventory.service.dto.InventoryStateUpdateRequest;
import com.vattima.lego.inventory.service.dto.SaleIntentUpdateRequest;
import com.vattima.lego.inventory.service.logging.LogExecution;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.legohunter.data.dao.ItemInventoryDao;
import io.legohunter.data.dto.ItemInventory;
import io.legohunter.data.dto.ItemInventorySearchCriteria;
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

    @PostMapping("/search")
    @LogExecution
    public ResponseEntity<InventorySearchResponse> search(@RequestBody(required = false) ItemInventorySearchCriteria criteria) {
        return ResponseEntity.ok(itemInventoryService.searchInventory(criteria));
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

    @PatchMapping("/{itemInventoryId}/physical")
    public ResponseEntity<ItemInventory> updatePhysical(
            @PathVariable final Integer itemInventoryId,
            @Valid @RequestBody InventoryPhysicalUpdateRequest request
    ) {
        return ResponseEntity.ok(itemInventoryService.updateInventoryPhysical(itemInventoryId, request));
    }

    @PatchMapping("/{itemInventoryId}/state")
    public ResponseEntity<ItemInventory> updateState(
            @PathVariable final Integer itemInventoryId,
            @Valid @RequestBody InventoryStateUpdateRequest request
    ) {
        return ResponseEntity.ok(itemInventoryService.updateInventoryState(itemInventoryId, request));
    }

    @PatchMapping("/{itemInventoryId}/sale-intent")
    public ResponseEntity<ItemInventory> updateSaleIntent(
            @PathVariable final Integer itemInventoryId,
            @Valid @RequestBody SaleIntentUpdateRequest request
    ) {
        return ResponseEntity.ok(itemInventoryService.updateSaleIntent(itemInventoryId, request));
    }
}

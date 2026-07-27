package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.api.ItemInventoryService;
import com.vattima.lego.inventory.service.dto.AddItemInventoryResponse;
import com.vattima.lego.inventory.service.dto.CostRequest;
import com.vattima.lego.inventory.service.dto.TransactionItemUpdateRequest;
import com.vattima.lego.inventory.service.logging.LogExecution;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transaction-items")
@RequiredArgsConstructor
@Validated
public class TransactionItemController {
    private final ItemInventoryService itemInventoryService;

    @PatchMapping("/{transactionItemId}")
    @LogExecution
    public ResponseEntity<AddItemInventoryResponse> updateTransactionItem(
            @PathVariable Long transactionItemId,
            @Valid @RequestBody TransactionItemUpdateRequest request
    ) {
        return ResponseEntity.ok(itemInventoryService.updateTransactionItem(transactionItemId, request));
    }

    @PostMapping("/{transactionItemId}/costs")
    @LogExecution
    public ResponseEntity<AddItemInventoryResponse> addTransactionItemCost(
            @PathVariable Long transactionItemId,
            @Valid @RequestBody CostRequest request
    ) {
        return ResponseEntity.ok(itemInventoryService.addTransactionItemCost(transactionItemId, request));
    }

    @PutMapping("/{transactionItemId}/costs/{transactionItemCostId}")
    @LogExecution
    public ResponseEntity<AddItemInventoryResponse> updateTransactionItemCost(
            @PathVariable Long transactionItemId,
            @PathVariable Long transactionItemCostId,
            @Valid @RequestBody CostRequest request
    ) {
        return ResponseEntity.ok(itemInventoryService.updateTransactionItemCost(transactionItemId, transactionItemCostId, request));
    }

    @DeleteMapping("/{transactionItemId}/costs/{transactionItemCostId}")
    @LogExecution
    public ResponseEntity<AddItemInventoryResponse> deleteTransactionItemCost(
            @PathVariable Long transactionItemId,
            @PathVariable Long transactionItemCostId
    ) {
        return ResponseEntity.ok(itemInventoryService.deleteTransactionItemCost(transactionItemId, transactionItemCostId));
    }
}

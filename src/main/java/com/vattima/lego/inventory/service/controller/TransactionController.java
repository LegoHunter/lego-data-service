package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.api.TransactionService;
import com.vattima.lego.inventory.service.api.ItemInventoryService;
import com.vattima.lego.inventory.service.dto.AddTransactionRequest;
import com.vattima.lego.inventory.service.dto.AddTransactionResponse;
import com.vattima.lego.inventory.service.dto.AddItemInventoryResponse;
import com.vattima.lego.inventory.service.dto.CostRequest;
import com.vattima.lego.inventory.service.dto.PaymentRequest;
import com.vattima.lego.inventory.service.dto.TransactionHeaderUpdateRequest;
import com.vattima.lego.inventory.service.logging.LogExecution;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Validated
public class TransactionController {
    private final TransactionService transactionService;
    private final ItemInventoryService itemInventoryService;

    @PostMapping
    @Transactional
    @LogExecution
    public ResponseEntity<AddTransactionResponse> addTransaction(@RequestBody AddTransactionRequest request) {
        AddTransactionResponse response = transactionService.addTransaction(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{transactionId}")
    @LogExecution
    public ResponseEntity<AddItemInventoryResponse> findTransactionTree(@PathVariable Long transactionId) {
        return ResponseEntity.ok(itemInventoryService.findTransactionTree(transactionId));
    }

    @PatchMapping("/{transactionId}")
    @LogExecution
    public ResponseEntity<AddItemInventoryResponse> updateTransactionHeader(
            @PathVariable Long transactionId,
            @Valid @RequestBody TransactionHeaderUpdateRequest request
    ) {
        return ResponseEntity.ok(itemInventoryService.updateTransactionHeader(transactionId, request));
    }

    @PostMapping("/{transactionId}/costs")
    @LogExecution
    public ResponseEntity<AddItemInventoryResponse> addTransactionCost(
            @PathVariable Long transactionId,
            @Valid @RequestBody CostRequest request
    ) {
        return ResponseEntity.ok(itemInventoryService.addTransactionCost(transactionId, request));
    }

    @PutMapping("/{transactionId}/costs/{transactionCostId}")
    @LogExecution
    public ResponseEntity<AddItemInventoryResponse> updateTransactionCost(
            @PathVariable Long transactionId,
            @PathVariable Long transactionCostId,
            @Valid @RequestBody CostRequest request
    ) {
        return ResponseEntity.ok(itemInventoryService.updateTransactionCost(transactionId, transactionCostId, request));
    }

    @DeleteMapping("/{transactionId}/costs/{transactionCostId}")
    @LogExecution
    public ResponseEntity<AddItemInventoryResponse> deleteTransactionCost(
            @PathVariable Long transactionId,
            @PathVariable Long transactionCostId
    ) {
        return ResponseEntity.ok(itemInventoryService.deleteTransactionCost(transactionId, transactionCostId));
    }

    @PostMapping("/{transactionId}/payments")
    @LogExecution
    public ResponseEntity<AddItemInventoryResponse> addPayment(
            @PathVariable Long transactionId,
            @Valid @RequestBody PaymentRequest request
    ) {
        return ResponseEntity.ok(itemInventoryService.addPayment(transactionId, request));
    }

    @PutMapping("/{transactionId}/payments/{paymentId}")
    @LogExecution
    public ResponseEntity<AddItemInventoryResponse> updatePayment(
            @PathVariable Long transactionId,
            @PathVariable Long paymentId,
            @Valid @RequestBody PaymentRequest request
    ) {
        return ResponseEntity.ok(itemInventoryService.updatePayment(transactionId, paymentId, request));
    }

    @DeleteMapping("/{transactionId}/payments/{paymentId}")
    @LogExecution
    public ResponseEntity<AddItemInventoryResponse> deletePayment(
            @PathVariable Long transactionId,
            @PathVariable Long paymentId
    ) {
        return ResponseEntity.ok(itemInventoryService.deletePayment(transactionId, paymentId));
    }
}

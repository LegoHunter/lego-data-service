package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.api.TransactionService;
import com.vattima.lego.inventory.service.dto.AddTransactionRequest;
import com.vattima.lego.inventory.service.dto.AddTransactionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Validated
public class TransactionController {
    private final TransactionService transactionService;

    @PostMapping
    @Transactional
    public ResponseEntity<AddTransactionResponse> addTransaction(@RequestBody AddTransactionRequest request) {
        AddTransactionResponse response = transactionService.addTransaction(request);
        return ResponseEntity.ok(response);
    }
}

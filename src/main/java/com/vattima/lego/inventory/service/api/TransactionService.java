package com.vattima.lego.inventory.service.api;

import com.vattima.lego.inventory.service.dto.AddTransactionRequest;
import com.vattima.lego.inventory.service.dto.AddTransactionResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

@Validated
public interface TransactionService {
    AddTransactionResponse addTransaction(@Valid AddTransactionRequest addTransactionRequest);

}

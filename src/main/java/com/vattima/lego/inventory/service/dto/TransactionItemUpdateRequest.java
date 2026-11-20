package com.vattima.lego.inventory.service.dto;

import io.legohunter.data.validation.TransactionTypeExists;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionItemUpdateRequest {
    @TransactionTypeExists
    private String transactionTypeCode;

    private String notes;
}

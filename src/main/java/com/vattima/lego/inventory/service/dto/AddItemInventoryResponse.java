package com.vattima.lego.inventory.service.dto;

import io.legohunter.data.dto.Payment;
import io.legohunter.data.dto.TransactionCost;
import io.legohunter.data.dto.Transactions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddItemInventoryResponse {
    private Transactions transaction;
    private List<TransactionCost> costs;
    private List<Payment> payments;
    private List<TransactionItemInventoryResponse> transactionItems;
}

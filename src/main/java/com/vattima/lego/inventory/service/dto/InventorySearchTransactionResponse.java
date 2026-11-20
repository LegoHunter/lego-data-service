package com.vattima.lego.inventory.service.dto;

import io.legohunter.data.dto.TransactionCost;
import io.legohunter.data.dto.TransactionItem;
import io.legohunter.data.dto.TransactionItemCost;
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
public class InventorySearchTransactionResponse {
    private Transactions transaction;
    private List<TransactionCost> transactionCosts;
    private TransactionItem transactionItem;
    private List<TransactionItemCost> transactionItemCosts;
}

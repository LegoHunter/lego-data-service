package com.vattima.lego.inventory.service.api;

import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.AddItemInventoryResponse;
import com.vattima.lego.inventory.service.dto.CostRequest;
import com.vattima.lego.inventory.service.dto.InventoryPhysicalUpdateRequest;
import com.vattima.lego.inventory.service.dto.InventorySearchResponse;
import com.vattima.lego.inventory.service.dto.InventoryStateUpdateRequest;
import com.vattima.lego.inventory.service.dto.PaymentRequest;
import com.vattima.lego.inventory.service.dto.SaleIntentUpdateRequest;
import com.vattima.lego.inventory.service.dto.TransactionHeaderUpdateRequest;
import com.vattima.lego.inventory.service.dto.TransactionItemUpdateRequest;
import io.legohunter.data.dto.ItemInventory;
import io.legohunter.data.dto.ItemInventorySearchCriteria;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

@Validated
public interface ItemInventoryService {
    AddItemInventoryResponse addItemInventory(@Valid AddItemInventoryRequest addItemInventoryRequest);

    AddItemInventoryResponse findTransactionTree(Long transactionId);

    InventorySearchResponse searchInventory(ItemInventorySearchCriteria criteria);

    AddItemInventoryResponse updateTransactionHeader(Long transactionId, @Valid TransactionHeaderUpdateRequest request);

    AddItemInventoryResponse addTransactionCost(Long transactionId, @Valid CostRequest request);

    AddItemInventoryResponse updateTransactionCost(Long transactionId, Long transactionCostId, @Valid CostRequest request);

    AddItemInventoryResponse deleteTransactionCost(Long transactionId, Long transactionCostId);

    AddItemInventoryResponse addPayment(Long transactionId, @Valid PaymentRequest request);

    AddItemInventoryResponse updatePayment(Long transactionId, Long paymentId, @Valid PaymentRequest request);

    AddItemInventoryResponse deletePayment(Long transactionId, Long paymentId);

    AddItemInventoryResponse updateTransactionItem(Long transactionItemId, @Valid TransactionItemUpdateRequest request);

    AddItemInventoryResponse addTransactionItemCost(Long transactionItemId, @Valid CostRequest request);

    AddItemInventoryResponse updateTransactionItemCost(Long transactionItemId, Long transactionItemCostId, @Valid CostRequest request);

    AddItemInventoryResponse deleteTransactionItemCost(Long transactionItemId, Long transactionItemCostId);

    ItemInventory updateInventoryPhysical(Integer itemInventoryId, @Valid InventoryPhysicalUpdateRequest request);

    ItemInventory updateInventoryState(Integer itemInventoryId, @Valid InventoryStateUpdateRequest request);

    ItemInventory updateSaleIntent(Integer itemInventoryId, @Valid SaleIntentUpdateRequest request);
}

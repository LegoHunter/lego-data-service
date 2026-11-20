package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.api.ItemInventoryService;
import com.vattima.lego.inventory.service.dto.AddItemInventoryResponse;
import com.vattima.lego.inventory.service.dto.CostRequest;
import com.vattima.lego.inventory.service.dto.TransactionItemUpdateRequest;
import io.legohunter.data.dto.Transactions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionItemControllerTest {
    @Mock
    private ItemInventoryService itemInventoryService;

    @Test
    void updateTransactionItemDelegatesToInventoryService() {
        TransactionItemUpdateRequest request = TransactionItemUpdateRequest.builder().notes("updated").build();
        AddItemInventoryResponse serviceResponse = transactionTree();
        when(itemInventoryService.updateTransactionItem(303L, request)).thenReturn(serviceResponse);

        assertThat(controller().updateTransactionItem(303L, request).getBody()).isSameAs(serviceResponse);
    }

    @Test
    void transactionItemCostRoutesDelegateToInventoryService() {
        CostRequest request = CostRequest.builder()
                .costTypeCode("TAX")
                .amount(new BigDecimal("8.25"))
                .currencyCode("USD")
                .build();
        AddItemInventoryResponse serviceResponse = transactionTree();
        when(itemInventoryService.addTransactionItemCost(303L, request)).thenReturn(serviceResponse);
        when(itemInventoryService.updateTransactionItemCost(303L, 606L, request)).thenReturn(serviceResponse);
        when(itemInventoryService.deleteTransactionItemCost(303L, 606L)).thenReturn(serviceResponse);

        assertThat(controller().addTransactionItemCost(303L, request).getBody()).isSameAs(serviceResponse);
        assertThat(controller().updateTransactionItemCost(303L, 606L, request).getBody()).isSameAs(serviceResponse);
        assertThat(controller().deleteTransactionItemCost(303L, 606L).getBody()).isSameAs(serviceResponse);
    }

    private TransactionItemController controller() {
        return new TransactionItemController(itemInventoryService);
    }

    private AddItemInventoryResponse transactionTree() {
        return AddItemInventoryResponse.builder()
                .transaction(Transactions.builder().transactionId(101L).build())
                .build();
    }
}

package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.api.ItemInventoryService;
import com.vattima.lego.inventory.service.api.TransactionService;
import com.vattima.lego.inventory.service.dto.AddItemInventoryResponse;
import com.vattima.lego.inventory.service.dto.AddTransactionRequest;
import com.vattima.lego.inventory.service.dto.AddTransactionResponse;
import com.vattima.lego.inventory.service.dto.CostRequest;
import com.vattima.lego.inventory.service.dto.PaymentRequest;
import com.vattima.lego.inventory.service.dto.TransactionHeaderUpdateRequest;
import io.legohunter.data.dto.Transactions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {
    @Mock
    private TransactionService transactionService;

    @Mock
    private ItemInventoryService itemInventoryService;

    @Test
    void addTransactionDelegatesToLegacyTransactionService() {
        AddTransactionRequest request = AddTransactionRequest.builder().build();
        AddTransactionResponse serviceResponse = AddTransactionResponse.builder().transactionId(101L).build();
        when(transactionService.addTransaction(request)).thenReturn(serviceResponse);

        ResponseEntity<AddTransactionResponse> response = controller().addTransaction(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(serviceResponse);
    }

    @Test
    void findTransactionTreeDelegatesToInventoryService() {
        AddItemInventoryResponse serviceResponse = transactionTree();
        when(itemInventoryService.findTransactionTree(101L)).thenReturn(serviceResponse);

        ResponseEntity<AddItemInventoryResponse> response = controller().findTransactionTree(101L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(serviceResponse);
    }

    @Test
    void updateTransactionHeaderDelegatesToInventoryService() {
        TransactionHeaderUpdateRequest request = TransactionHeaderUpdateRequest.builder().notes("updated").build();
        AddItemInventoryResponse serviceResponse = transactionTree();
        when(itemInventoryService.updateTransactionHeader(101L, request)).thenReturn(serviceResponse);

        ResponseEntity<AddItemInventoryResponse> response = controller().updateTransactionHeader(101L, request);

        assertThat(response.getBody()).isSameAs(serviceResponse);
    }

    @Test
    void transactionCostRoutesDelegateToInventoryService() {
        CostRequest request = cost("SHIPPING", "12.50");
        AddItemInventoryResponse serviceResponse = transactionTree();
        when(itemInventoryService.addTransactionCost(101L, request)).thenReturn(serviceResponse);
        when(itemInventoryService.updateTransactionCost(101L, 505L, request)).thenReturn(serviceResponse);
        when(itemInventoryService.deleteTransactionCost(101L, 505L)).thenReturn(serviceResponse);

        assertThat(controller().addTransactionCost(101L, request).getBody()).isSameAs(serviceResponse);
        assertThat(controller().updateTransactionCost(101L, 505L, request).getBody()).isSameAs(serviceResponse);
        assertThat(controller().deleteTransactionCost(101L, 505L).getBody()).isSameAs(serviceResponse);
    }

    @Test
    void paymentRoutesDelegateToInventoryService() {
        PaymentRequest request = PaymentRequest.builder()
                .paymentDate(LocalDate.parse("2026-07-27"))
                .currencyCode("USD")
                .sellerCurrencyCode("USD")
                .exchangeRate(new BigDecimal("1.00000"))
                .amount(new BigDecimal("112.49"))
                .paymentPlatformName("PayPal")
                .paymentPlatformTransactionId("PAYPAL-123")
                .build();
        AddItemInventoryResponse serviceResponse = transactionTree();
        when(itemInventoryService.addPayment(101L, request)).thenReturn(serviceResponse);
        when(itemInventoryService.updatePayment(101L, 404L, request)).thenReturn(serviceResponse);
        when(itemInventoryService.deletePayment(101L, 404L)).thenReturn(serviceResponse);

        assertThat(controller().addPayment(101L, request).getBody()).isSameAs(serviceResponse);
        assertThat(controller().updatePayment(101L, 404L, request).getBody()).isSameAs(serviceResponse);
        assertThat(controller().deletePayment(101L, 404L).getBody()).isSameAs(serviceResponse);
        verify(itemInventoryService).deletePayment(101L, 404L);
    }

    private TransactionController controller() {
        return new TransactionController(transactionService, itemInventoryService);
    }

    private AddItemInventoryResponse transactionTree() {
        return AddItemInventoryResponse.builder()
                .transaction(Transactions.builder().transactionId(101L).build())
                .build();
    }

    private CostRequest cost(String costTypeCode, String amount) {
        return CostRequest.builder()
                .costTypeCode(costTypeCode)
                .amount(new BigDecimal(amount))
                .currencyCode("USD")
                .build();
    }
}

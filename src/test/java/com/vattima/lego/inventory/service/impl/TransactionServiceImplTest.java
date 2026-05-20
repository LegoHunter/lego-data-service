package com.vattima.lego.inventory.service.impl;

import com.vattima.lego.inventory.service.dto.AddTransactionRequest;
import com.vattima.lego.inventory.service.dto.CostRequest;
import com.vattima.lego.inventory.service.dto.ItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.TransactionItemRequest;
import io.legohunter.data.dao.ConditionDao;
import io.legohunter.data.dao.ExternalItemDao;
import io.legohunter.data.dao.ExternalItemInventoryDao;
import io.legohunter.data.dao.ItemInventoryDao;
import io.legohunter.data.dao.TransactionCostDao;
import io.legohunter.data.dao.TransactionItemDao;
import io.legohunter.data.dao.TransactionPlatformDao;
import io.legohunter.data.dao.TransactionsDao;
import io.legohunter.data.dto.Condition;
import io.legohunter.data.dto.ExternalItem;
import io.legohunter.data.dto.ExternalItemInventory;
import io.legohunter.data.dto.ItemInventory;
import io.legohunter.data.dto.TransactionItem;
import io.legohunter.data.dto.TransactionPlatform;
import io.legohunter.data.dto.Transactions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private ExternalItemDao externalItemDao;

    @Mock
    private ExternalItemInventoryDao externalItemInventoryDao;

    @Mock
    private ConditionDao conditionDao;

    @Mock
    private ItemInventoryDao itemInventoryDao;

    @Mock
    private TransactionPlatformDao transactionPlatformDao;

    @Mock
    private TransactionsDao transactionsDao;

    @Mock
    private TransactionItemDao transactionItemDao;

    @Mock
    private TransactionCostDao transactionCostDao;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    @Test
    void addTransactionCreatesInventoryAndExternalItemInventoryLink() {
        when(transactionPlatformDao.findTransactionPlatformByName("BRICKLINK"))
                .thenReturn(Optional.of(TransactionPlatform.builder()
                        .transactionPlatformId(7)
                        .transactionPlatformName("BRICKLINK")
                        .build()));

        ExternalItem externalItem = new ExternalItem();
        externalItem.setExternalItemId(42);
        externalItem.setExternalNumber("1234-1");
        when(externalItemDao.findByExternalServiceAndNumber(2, "1234-1"))
                .thenReturn(Optional.of(externalItem));

        when(conditionDao.findByConditionCode("N"))
                .thenReturn(Optional.of(Condition.builder().conditionId(1).conditionCode("N").build()));
        when(conditionDao.findByConditionCode("G"))
                .thenReturn(Optional.of(Condition.builder().conditionId(2).conditionCode("G").build()));

        doAnswer(invocation -> {
            Transactions transactions = invocation.getArgument(0);
            transactions.setTransactionId(101L);
            return null;
        }).when(transactionsDao).insert(any(Transactions.class));

        doAnswer(invocation -> {
            ItemInventory itemInventory = invocation.getArgument(0);
            itemInventory.setItemInventoryId(202);
            return null;
        }).when(itemInventoryDao).insert(any(ItemInventory.class));

        doAnswer(invocation -> {
            TransactionItem transactionItem = invocation.getArgument(0);
            transactionItem.setTransactionItemId(303L);
            return null;
        }).when(transactionItemDao).insert(any(TransactionItem.class));

        transactionService.addTransaction(transactionRequest());

        ArgumentCaptor<ItemInventory> itemInventoryCaptor = ArgumentCaptor.forClass(ItemInventory.class);
        verify(itemInventoryDao).insert(itemInventoryCaptor.capture());
        ItemInventory itemInventory = itemInventoryCaptor.getValue();
        assertThat(itemInventory.getItemInventoryId()).isEqualTo(202);
        assertThat(itemInventory.getBoxNumber()).isEqualTo(12);
        assertThat(itemInventory.getDescription()).isEqualTo("Sealed box");
        assertThat(itemInventory.getItemConditionId()).isEqualTo(1);
        assertThat(itemInventory.getBoxConditionId()).isEqualTo(2);
        assertThat(itemInventory.getInstructionsConditionId()).isEqualTo(2);
        assertThat(itemInventory.getBuiltOnce()).isFalse();

        ArgumentCaptor<ExternalItemInventory> externalItemInventoryCaptor = ArgumentCaptor.forClass(ExternalItemInventory.class);
        verify(externalItemInventoryDao).insert(externalItemInventoryCaptor.capture());
        assertThat(externalItemInventoryCaptor.getValue().getExternalItemId()).isEqualTo(42);
        assertThat(externalItemInventoryCaptor.getValue().getItemInventoryId()).isEqualTo(202);

        ArgumentCaptor<TransactionItem> transactionItemCaptor = ArgumentCaptor.forClass(TransactionItem.class);
        verify(transactionItemDao).insert(transactionItemCaptor.capture());
        assertThat(transactionItemCaptor.getValue().getTransactionId()).isEqualTo(101L);
        assertThat(transactionItemCaptor.getValue().getItemInventoryId()).isEqualTo(202);
        assertThat(transactionItemCaptor.getValue().getTransactionTypeCode()).isEqualTo("PURCHASE");
    }

    private AddTransactionRequest transactionRequest() {
        return AddTransactionRequest.builder()
                .transactionDateTime(ZonedDateTime.parse("2026-05-20T10:00:00-04:00"))
                .fromPartyId(1L)
                .toPartyId(2L)
                .notes("Order notes")
                .platformName("BRICKLINK")
                .orderId("BL-100")
                .costs(List.of(CostRequest.builder()
                        .costTypeCode("SHIPPING")
                        .amount(4.95)
                        .currencyCode("USD")
                        .notes("shipping")
                        .build()))
                .transactionItems(List.of(TransactionItemRequest.builder()
                        .transactionTypeCode("PURCHASE")
                        .notes("item notes")
                        .costs(List.of())
                        .itemInventory(ItemInventoryRequest.builder()
                                .itemNumber("1234-1")
                                .description("Sealed box")
                                .boxNumber(12)
                                .newOrUsed("N")
                                .completeness("C")
                                .sealed(true)
                                .builtOnce(false)
                                .itemConditionCode("N")
                                .boxConditionCode("G")
                                .instructionsConditionCode("G")
                                .forSale(false)
                                .quantity(1)
                                .active(true)
                                .build())
                        .build()))
                .build();
    }
}

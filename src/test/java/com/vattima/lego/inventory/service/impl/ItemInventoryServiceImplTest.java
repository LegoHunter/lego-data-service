package com.vattima.lego.inventory.service.impl;

import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.AddItemInventoryResponse;
import com.vattima.lego.inventory.service.dto.CostRequest;
import com.vattima.lego.inventory.service.dto.ItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.PaymentRequest;
import com.vattima.lego.inventory.service.exception.ValidationException;
import com.vattima.lego.inventory.service.validation.InventoryAcquisitionBusinessValidator;
import io.legohunter.data.dao.ConditionDao;
import io.legohunter.data.dao.ExternalCatalogItemDao;
import io.legohunter.data.dao.ItemInventoryDao;
import io.legohunter.data.dao.ItemInventoryExternalCatalogItemDao;
import io.legohunter.data.dao.PaymentDao;
import io.legohunter.data.dao.PaymentPlatformDao;
import io.legohunter.data.dao.TransactionCostDao;
import io.legohunter.data.dao.TransactionItemDao;
import io.legohunter.data.dao.TransactionPlatformDao;
import io.legohunter.data.dao.TransactionsDao;
import io.legohunter.data.dto.Condition;
import io.legohunter.data.dto.ExternalCatalogItem;
import io.legohunter.data.dto.ItemInventory;
import io.legohunter.data.dto.ItemInventoryExternalCatalogItem;
import io.legohunter.data.dto.Payment;
import io.legohunter.data.dto.PaymentPlatform;
import io.legohunter.data.dto.TransactionCost;
import io.legohunter.data.dto.TransactionItem;
import io.legohunter.data.dto.TransactionItemCost;
import io.legohunter.data.dto.TransactionPlatform;
import io.legohunter.data.dto.Transactions;
import io.legohunter.data.enums.CurrencyCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemInventoryServiceImplTest {

    @Mock private ConditionDao conditionDao;
    @Mock private ExternalCatalogItemDao externalCatalogItemDao;
    @Mock private ItemInventoryDao itemInventoryDao;
    @Mock private ItemInventoryExternalCatalogItemDao itemInventoryExternalCatalogItemDao;
    @Mock private PaymentDao paymentDao;
    @Mock private PaymentPlatformDao paymentPlatformDao;
    @Mock private TransactionCostDao transactionCostDao;
    @Mock private TransactionItemDao transactionItemDao;
    @Mock private TransactionPlatformDao transactionPlatformDao;
    @Mock private TransactionsDao transactionsDao;
    @Spy private InventoryAcquisitionBusinessValidator inventoryAcquisitionBusinessValidator = new InventoryAcquisitionBusinessValidator();

    @InjectMocks
    private ItemInventoryServiceImpl service;

    @Test
    void addItemInventoryCreatesCompletePrivateAcquisitionTree() {
        AddItemInventoryRequest request = acquisitionRequest();
        Transactions insertedTransaction = Transactions.builder().transactionId(101L).notes("Order notes").build();
        ItemInventory insertedInventory = new ItemInventory();
        insertedInventory.setItemInventoryId(202);
        insertedInventory.setUuid("generated-uuid");
        TransactionItem insertedTransactionItem = TransactionItem.builder()
                .transactionItemId(303L)
                .transactionId(101L)
                .itemInventoryId(202)
                .transactionTypeCode("PURCHASE")
                .build();
        ItemInventoryExternalCatalogItem catalogLink = ItemInventoryExternalCatalogItem.builder()
                .itemInventoryId(202)
                .externalCatalogItemId(42)
                .primary(true)
                .build();
        Payment readBackPayment = Payment.builder().paymentId(404L).transactionId(101L).amount(new BigDecimal("99.99000")).build();
        TransactionCost readBackCost = TransactionCost.builder().transactionCostId(505L).transactionId(101L).costTypeCode("SHIPPING").amount(12.50).currencyCode(CurrencyCode.USD).build();
        TransactionItemCost readBackItemCost = TransactionItemCost.builder().transactionItemCostId(606L).transactionItemId(303L).costTypeCode("PRICE").amount(99.99).currencyCode("USD").build();

        when(transactionPlatformDao.findTransactionPlatformByName("BrickLink"))
                .thenReturn(Optional.of(TransactionPlatform.builder().transactionPlatformId(7).transactionPlatformName("BrickLink").build()));
        when(paymentPlatformDao.findPaymentPlatformByName("PayPal"))
                .thenReturn(Optional.of(PaymentPlatform.builder().paymentPlatformId(8).paymentPlatformName("PayPal").build()));
        when(externalCatalogItemDao.findByExternalServiceIdAndExternalItemKey(2, "1234-1"))
                .thenReturn(Optional.of(ExternalCatalogItem.builder().externalCatalogItemId(42).externalItemKey("1234-1").build()));
        when(conditionDao.findByConditionCode("N")).thenReturn(Optional.of(Condition.builder().conditionId(1).conditionCode("N").build()));
        when(conditionDao.findByConditionCode("G")).thenReturn(Optional.of(Condition.builder().conditionId(2).conditionCode("G").build()));
        doAnswer(invocation -> {
            Transactions transaction = invocation.getArgument(0);
            transaction.setTransactionId(101L);
            return null;
        }).when(transactionsDao).insert(any(Transactions.class));
        doAnswer(invocation -> {
            ItemInventory inventory = invocation.getArgument(0);
            inventory.setItemInventoryId(202);
            inventory.setUuid("generated-uuid");
            return inventory;
        }).when(itemInventoryDao).insert(any(ItemInventory.class));
        doAnswer(invocation -> {
            TransactionItem transactionItem = invocation.getArgument(0);
            transactionItem.setTransactionItemId(303L);
            return null;
        }).when(transactionItemDao).insert(any(TransactionItem.class));
        when(transactionsDao.findById(101L)).thenReturn(Optional.of(insertedTransaction));
        when(transactionCostDao.findByTransactionId(101L)).thenReturn(List.of(readBackCost));
        when(paymentDao.findByTransactionId(101L)).thenReturn(List.of(readBackPayment));
        when(transactionItemDao.findByTransactionId(101L)).thenReturn(List.of(insertedTransactionItem));
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(insertedInventory));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of(catalogLink));
        when(transactionCostDao.findByTransactionItemId(303L)).thenReturn(List.of(readBackItemCost));

        AddItemInventoryResponse response = service.addItemInventory(request);

        assertThat(response.getTransaction()).isSameAs(insertedTransaction);
        assertThat(response.getCosts()).containsExactly(readBackCost);
        assertThat(response.getPayments()).containsExactly(readBackPayment);
        assertThat(response.getTransactionItems()).hasSize(1);
        assertThat(response.getTransactionItems().getFirst().getItemInventory()).isSameAs(insertedInventory);
        assertThat(response.getTransactionItems().getFirst().getCatalogItems()).containsExactly(catalogLink);
        assertThat(response.getTransactionItems().getFirst().getCosts()).containsExactly(readBackItemCost);

        ArgumentCaptor<ItemInventory> inventoryCaptor = ArgumentCaptor.forClass(ItemInventory.class);
        verify(itemInventoryDao).insert(inventoryCaptor.capture());
        ItemInventory createdInventory = inventoryCaptor.getValue();
        assertThat(createdInventory.getActive()).isTrue();
        assertThat(createdInventory.getForSale()).isFalse();
        assertThat(createdInventory.getPurchasePrice()).isNull();
        assertThat(createdInventory.getInventoryStateCode()).isEqualTo("AVAILABLE");
        assertThat(createdInventory.getSaleIntentCode()).isEqualTo("KEEP");
        assertThat(createdInventory.getSaleIntentNote()).isEqualTo("Defaulted by acquisition intake");

        ArgumentCaptor<List<TransactionCost>> costCaptor = ArgumentCaptor.captor();
        verify(transactionCostDao).setTransactionCosts(org.mockito.ArgumentMatchers.eq(101L), costCaptor.capture());
        assertThat(costCaptor.getValue()).singleElement()
                .satisfies(cost -> {
                    assertThat(cost.getCostTypeCode()).isEqualTo("SHIPPING");
                    assertThat(cost.getAmount()).isEqualTo(12.50);
                    assertThat(cost.getCurrencyCode()).isEqualTo(CurrencyCode.USD);
                });

        ArgumentCaptor<List<TransactionItemCost>> itemCostCaptor = ArgumentCaptor.captor();
        verify(transactionCostDao).setTransactionItemCosts(org.mockito.ArgumentMatchers.eq(303L), itemCostCaptor.capture());
        assertThat(itemCostCaptor.getValue()).singleElement()
                .satisfies(cost -> {
                    assertThat(cost.getCostTypeCode()).isEqualTo("PRICE");
                    assertThat(cost.getAmount()).isEqualTo(99.99);
                    assertThat(cost.getCurrencyCode()).isEqualTo("USD");
                });

        ArgumentCaptor<List<Payment>> paymentCaptor = ArgumentCaptor.captor();
        verify(paymentDao).setTransactionPayments(org.mockito.ArgumentMatchers.eq(101L), paymentCaptor.capture());
        assertThat(paymentCaptor.getValue()).singleElement()
                .satisfies(payment -> {
                    assertThat(payment.getPaymentPlatformId()).isEqualTo(8);
                    assertThat(payment.getAmount()).isEqualByComparingTo("99.99000");
                    assertThat(payment.getExchangeRate()).isEqualByComparingTo("1.00000");
                });
    }

    @Test
    void addItemInventoryRejectsConflictingPlatformNamesBeforeAnyWrites() {
        AddItemInventoryRequest request = acquisitionRequest();
        request.setPlatformName("BrickLink");
        request.setTransactionPlatformName("eBay");

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("platformName and transactionPlatformName must match when both are provided");

        verifyNoInteractions(transactionsDao, itemInventoryDao, paymentDao, transactionCostDao);
    }

    @Test
    void addItemInventoryRejectsForSaleLegacyFlagBeforeAnyWrites() {
        AddItemInventoryRequest request = acquisitionRequest();
        request.getInventoryItems().getFirst().setForSale(true);

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("New acquisition intake must use forSale=false; sale intent is set to KEEP by the service");

        verifyNoInteractions(transactionsDao, itemInventoryDao, paymentDao, transactionCostDao);
    }

    @Test
    void addItemInventoryRejectsMissingCatalogItem() {
        AddItemInventoryRequest request = acquisitionRequest();

        when(transactionPlatformDao.findTransactionPlatformByName("BrickLink"))
                .thenReturn(Optional.of(TransactionPlatform.builder().transactionPlatformId(7).transactionPlatformName("BrickLink").build()));
        when(paymentPlatformDao.findPaymentPlatformByName("PayPal"))
                .thenReturn(Optional.of(PaymentPlatform.builder().paymentPlatformId(8).paymentPlatformName("PayPal").build()));
        doAnswer(invocation -> {
            Transactions transaction = invocation.getArgument(0);
            transaction.setTransactionId(101L);
            return null;
        }).when(transactionsDao).insert(any(Transactions.class));
        when(externalCatalogItemDao.findByExternalServiceIdAndExternalItemKey(2, "1234-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("BrickLink catalog item was not found for itemNumber 1234-1");
    }

    @Test
    void addItemInventoryDefaultsSameCurrencyPaymentExchangeRate() {
        AddItemInventoryRequest request = acquisitionRequest();
        request.getPayments().getFirst().setExchangeRate(null);
        stubThroughPaymentConversion();

        service.addItemInventory(request);

        ArgumentCaptor<List<Payment>> paymentCaptor = ArgumentCaptor.captor();
        verify(paymentDao).setTransactionPayments(org.mockito.ArgumentMatchers.eq(101L), paymentCaptor.capture());
        assertThat(paymentCaptor.getValue()).singleElement()
                .satisfies(payment -> assertThat(payment.getExchangeRate()).isEqualByComparingTo("1.00000"));
    }

    @Test
    void addItemInventoryRejectsMissingCrossCurrencyExchangeRateBeforeAnyWrites() {
        AddItemInventoryRequest request = acquisitionRequest();
        request.getPayments().getFirst().setSellerCurrencyCode("EUR");
        request.getPayments().getFirst().setExchangeRate(null);

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("exchangeRate is required when currencyCode and sellerCurrencyCode differ");

        verifyNoInteractions(transactionsDao, itemInventoryDao, paymentDao, transactionCostDao);
    }

    @Test
    void addItemInventoryRejectsMissingPlatformNameBeforeAnyWrites() {
        AddItemInventoryRequest request = acquisitionRequest();
        request.setPlatformName(null);
        request.setTransactionPlatformName(null);

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("transactionPlatformName or platformName is required");

        verifyNoInteractions(transactionsDao, itemInventoryDao, paymentDao, transactionCostDao);
    }

    @Test
    void addItemInventoryRejectsEmptyCostsBeforeAnyWrites() {
        AddItemInventoryRequest request = acquisitionRequest();
        request.setCosts(null);

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Transaction costs must not be null");

        verifyNoInteractions(transactionsDao, itemInventoryDao, paymentDao, transactionCostDao);
    }

    @Test
    void addItemInventoryAllowsEmptyTransactionCosts() {
        AddItemInventoryRequest request = acquisitionRequest();
        request.setCosts(List.of());
        stubThroughPaymentConversion();

        service.addItemInventory(request);

        verify(transactionCostDao).setTransactionCosts(org.mockito.ArgumentMatchers.eq(101L), org.mockito.ArgumentMatchers.eq(List.of()));
    }

    @Test
    void addItemInventoryRejectsTransactionLevelPriceBeforeAnyWrites() {
        AddItemInventoryRequest request = acquisitionRequest();
        request.setCosts(List.of(cost("PRICE", "99.99", "transaction-level price")));

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Transaction-level costs must not include costTypeCode PRICE; PRICE belongs on each transaction item");

        verifyNoInteractions(transactionsDao, itemInventoryDao, paymentDao, transactionCostDao);
    }

    @Test
    void addItemInventoryRejectsMissingItemCostsBeforeAnyWrites() {
        AddItemInventoryRequest request = acquisitionRequest();
        request.getInventoryItems().getFirst().setCosts(List.of());

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Each inventory item must include at least one transaction item cost");

        verifyNoInteractions(transactionsDao, itemInventoryDao, paymentDao, transactionCostDao);
    }

    @Test
    void addItemInventoryRejectsMissingItemPriceBeforeAnyWrites() {
        AddItemInventoryRequest request = acquisitionRequest();
        request.getInventoryItems().getFirst().setCosts(List.of(cost("FEE", "2.00", "item fee")));

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Each inventory item must include a transaction item cost with costTypeCode PRICE");

        verifyNoInteractions(transactionsDao, itemInventoryDao, paymentDao, transactionCostDao);
    }

    @Test
    void addItemInventoryRejectsEmptyInventoryItemsBeforeAnyWrites() {
        AddItemInventoryRequest request = acquisitionRequest();
        request.setInventoryItems(List.of());

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("At least one inventory item is required");

        verifyNoInteractions(transactionsDao, itemInventoryDao, paymentDao, transactionCostDao);
    }

    @Test
    void addItemInventoryRejectsQuantityOtherThanOneBeforeAnyWrites() {
        AddItemInventoryRequest request = acquisitionRequest();
        request.getInventoryItems().getFirst().setQuantity(2);

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Inventory item quantity must be 1");

        verifyNoInteractions(transactionsDao, itemInventoryDao, paymentDao, transactionCostDao);
    }

    @Test
    void addItemInventoryRejectsEmptyPaymentsBeforeAnyWrites() {
        AddItemInventoryRequest request = acquisitionRequest();
        request.setPayments(List.of());

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("At least one payment is required");

        verifyNoInteractions(transactionsDao, itemInventoryDao, paymentDao, transactionCostDao);
    }

    @Test
    void addItemInventoryRejectsNullInventoryEntryBeforeAnyWrites() {
        AddItemInventoryRequest request = acquisitionRequest();
        request.setInventoryItems(new java.util.ArrayList<>(request.getInventoryItems()));
        request.getInventoryItems().add(null);

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Inventory item entries must not be null");

        verifyNoInteractions(transactionsDao, itemInventoryDao, paymentDao, transactionCostDao);
    }

    @Test
    void addItemInventoryRejectsNullPaymentEntryBeforeAnyWrites() {
        AddItemInventoryRequest request = acquisitionRequest();
        request.setPayments(new java.util.ArrayList<>(request.getPayments()));
        request.getPayments().add(null);

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Payment entries must not be null");

        verifyNoInteractions(transactionsDao, itemInventoryDao, paymentDao, transactionCostDao);
    }

    @Test
    void addItemInventoryRejectsMissingTransactionPlatform() {
        AddItemInventoryRequest request = acquisitionRequest();
        when(transactionPlatformDao.findTransactionPlatformByName("BrickLink")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Transaction platform was not found");

        verifyNoInteractions(transactionsDao, itemInventoryDao, paymentDao, transactionCostDao);
    }

    @Test
    void addItemInventoryRejectsMissingPaymentPlatform() {
        AddItemInventoryRequest request = acquisitionRequest();
        when(transactionPlatformDao.findTransactionPlatformByName("BrickLink"))
                .thenReturn(Optional.of(TransactionPlatform.builder().transactionPlatformId(7).transactionPlatformName("BrickLink").build()));
        when(paymentPlatformDao.findPaymentPlatformByName("PayPal")).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            Transactions transaction = invocation.getArgument(0);
            transaction.setTransactionId(101L);
            return null;
        }).when(transactionsDao).insert(any(Transactions.class));

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Payment platform was not found: PayPal");
    }

    @Test
    void addItemInventoryRejectsMissingConditionCode() {
        AddItemInventoryRequest request = acquisitionRequest();
        when(transactionPlatformDao.findTransactionPlatformByName("BrickLink"))
                .thenReturn(Optional.of(TransactionPlatform.builder().transactionPlatformId(7).transactionPlatformName("BrickLink").build()));
        when(paymentPlatformDao.findPaymentPlatformByName("PayPal"))
                .thenReturn(Optional.of(PaymentPlatform.builder().paymentPlatformId(8).paymentPlatformName("PayPal").build()));
        when(externalCatalogItemDao.findByExternalServiceIdAndExternalItemKey(2, "1234-1"))
                .thenReturn(Optional.of(ExternalCatalogItem.builder().externalCatalogItemId(42).externalItemKey("1234-1").build()));
        when(conditionDao.findByConditionCode("N")).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            Transactions transaction = invocation.getArgument(0);
            transaction.setTransactionId(101L);
            return null;
        }).when(transactionsDao).insert(any(Transactions.class));

        assertThatThrownBy(() -> service.addItemInventory(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Condition code was not found: N");
    }

    private void stubThroughPaymentConversion() {
        when(transactionPlatformDao.findTransactionPlatformByName("BrickLink"))
                .thenReturn(Optional.of(TransactionPlatform.builder().transactionPlatformId(7).transactionPlatformName("BrickLink").build()));
        when(paymentPlatformDao.findPaymentPlatformByName("PayPal"))
                .thenReturn(Optional.of(PaymentPlatform.builder().paymentPlatformId(8).paymentPlatformName("PayPal").build()));
        when(externalCatalogItemDao.findByExternalServiceIdAndExternalItemKey(2, "1234-1"))
                .thenReturn(Optional.of(ExternalCatalogItem.builder().externalCatalogItemId(42).externalItemKey("1234-1").build()));
        when(conditionDao.findByConditionCode("N")).thenReturn(Optional.of(Condition.builder().conditionId(1).conditionCode("N").build()));
        when(conditionDao.findByConditionCode("G")).thenReturn(Optional.of(Condition.builder().conditionId(2).conditionCode("G").build()));
        doAnswer(invocation -> {
            Transactions transaction = invocation.getArgument(0);
            transaction.setTransactionId(101L);
            return null;
        }).when(transactionsDao).insert(any(Transactions.class));
        doAnswer(invocation -> {
            ItemInventory inventory = invocation.getArgument(0);
            inventory.setItemInventoryId(202);
            return inventory;
        }).when(itemInventoryDao).insert(any(ItemInventory.class));
        doAnswer(invocation -> {
            TransactionItem transactionItem = invocation.getArgument(0);
            transactionItem.setTransactionItemId(303L);
            return null;
        }).when(transactionItemDao).insert(any(TransactionItem.class));
        when(transactionsDao.findById(101L)).thenReturn(Optional.of(Transactions.builder().transactionId(101L).build()));
        when(transactionCostDao.findByTransactionId(101L)).thenReturn(List.of());
        when(paymentDao.findByTransactionId(101L)).thenReturn(List.of());
        when(transactionItemDao.findByTransactionId(101L)).thenReturn(List.of(TransactionItem.builder()
                .transactionItemId(303L)
                .itemInventoryId(202)
                .build()));
        ItemInventory inventory = new ItemInventory();
        inventory.setItemInventoryId(202);
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of());
        when(transactionCostDao.findByTransactionItemId(303L)).thenReturn(List.of());
    }

    private AddItemInventoryRequest acquisitionRequest() {
        return AddItemInventoryRequest.builder()
                .transactionDate(LocalDate.parse("2026-05-20"))
                .fromPartyId(1L)
                .toPartyId(2L)
                .notes("Order notes")
                .platformName("BrickLink")
                .transactionPlatformName("BrickLink")
                .costs(List.of(cost("SHIPPING", "12.50", "shipping")))
                .payments(List.of(PaymentRequest.builder()
                        .paymentDate(LocalDate.parse("2026-05-20"))
                        .currencyCode("USD")
                        .sellerCurrencyCode("USD")
                        .exchangeRate(new BigDecimal("1.00000"))
                        .amount(new BigDecimal("99.99000"))
                        .paymentPlatformName("PayPal")
                        .paymentPlatformTransactionId("PAYPAL-123")
                        .build()))
                .inventoryItems(List.of(ItemInventoryRequest.builder()
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
                        .transactionTypeCode("PURCHASE")
                        .costs(List.of(cost("PRICE", "99.99", "item price")))
                        .forSale(false)
                        .quantity(1)
                        .active(true)
                        .build()))
                .build();
    }

    private CostRequest cost(String costTypeCode, String amount, String notes) {
        return CostRequest.builder()
                .costTypeCode(costTypeCode)
                .amount(new BigDecimal(amount))
                .currencyCode("USD")
                .notes(notes)
                .build();
    }
}

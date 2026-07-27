package com.vattima.lego.inventory.service.impl;

import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.AddItemInventoryResponse;
import com.vattima.lego.inventory.service.dto.CostRequest;
import com.vattima.lego.inventory.service.dto.InventoryPhysicalUpdateRequest;
import com.vattima.lego.inventory.service.dto.InventorySearchResponse;
import com.vattima.lego.inventory.service.dto.InventoryStateUpdateRequest;
import com.vattima.lego.inventory.service.dto.ItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.PaymentRequest;
import com.vattima.lego.inventory.service.dto.SaleIntentUpdateRequest;
import com.vattima.lego.inventory.service.dto.TransactionHeaderUpdateRequest;
import com.vattima.lego.inventory.service.dto.TransactionItemUpdateRequest;
import com.vattima.lego.inventory.service.exception.NotFoundException;
import com.vattima.lego.inventory.service.exception.ValidationException;
import com.vattima.lego.inventory.service.validation.InventoryAcquisitionBusinessValidator;
import io.legohunter.data.dao.ConditionDao;
import io.legohunter.data.dao.ExternalCatalogItemDao;
import io.legohunter.data.dao.ItemInventoryDao;
import io.legohunter.data.dao.ItemInventoryExternalCatalogItemDao;
import io.legohunter.data.dao.ItemInventorySaleIntentDao;
import io.legohunter.data.dao.ItemInventoryStateDao;
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
import io.legohunter.data.dto.ItemInventorySaleIntent;
import io.legohunter.data.dto.ItemInventorySearchCriteria;
import io.legohunter.data.dto.ItemInventoryState;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemInventoryServiceImplTest {

    @Mock private ConditionDao conditionDao;
    @Mock private ExternalCatalogItemDao externalCatalogItemDao;
    @Mock private ItemInventoryDao itemInventoryDao;
    @Mock private ItemInventoryExternalCatalogItemDao itemInventoryExternalCatalogItemDao;
    @Mock private ItemInventorySaleIntentDao itemInventorySaleIntentDao;
    @Mock private ItemInventoryStateDao itemInventoryStateDao;
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
        Payment readBackPayment = Payment.builder().paymentId(404L).transactionId(101L).amount(new BigDecimal("112.49000")).build();
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
                    assertThat(payment.getAmount()).isEqualByComparingTo("112.49000");
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
        request.getPayments().getFirst().setAmount(new BigDecimal("99.99000"));
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

    @Test
    void findTransactionTreeReturnsReadBackAcquisitionTree() {
        Transactions transaction = Transactions.builder().transactionId(101L).build();
        TransactionItem transactionItem = TransactionItem.builder().transactionItemId(303L).transactionId(101L).itemInventoryId(202).build();
        ItemInventory inventory = new ItemInventory();
        inventory.setItemInventoryId(202);
        TransactionCost transactionCost = transactionCost("SHIPPING", "12.50");
        Payment payment = payment("112.49000");
        TransactionItemCost transactionItemCost = transactionItemCost(303L, "PRICE", "99.99");

        when(transactionsDao.findById(101L)).thenReturn(Optional.of(transaction));
        when(transactionCostDao.findByTransactionId(101L)).thenReturn(List.of(transactionCost));
        when(paymentDao.findByTransactionId(101L)).thenReturn(List.of(payment));
        when(transactionItemDao.findByTransactionId(101L)).thenReturn(List.of(transactionItem));
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of());
        when(transactionCostDao.findByTransactionItemId(303L)).thenReturn(List.of(transactionItemCost));

        AddItemInventoryResponse response = service.findTransactionTree(101L);

        assertThat(response.getTransaction()).isSameAs(transaction);
        assertThat(response.getCosts()).containsExactly(transactionCost);
        assertThat(response.getPayments()).containsExactly(payment);
        assertThat(response.getTransactionItems()).singleElement()
                .satisfies(item -> assertThat(item.getCosts()).containsExactly(transactionItemCost));
    }

    @Test
    void findTransactionTreeThrowsNotFoundWhenTransactionIsMissing() {
        when(transactionsDao.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findTransactionTree(404L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Transaction was not found: 404");
    }

    @Test
    void searchInventoryNormalizesDefaultsAndDelegates() {
        ItemInventory inventory = new ItemInventory();
        inventory.setItemInventoryId(202);
        ItemInventorySearchCriteria criteria = ItemInventorySearchCriteria.builder().itemNumber("6390-1").build();
        when(itemInventoryDao.search(criteria)).thenReturn(Set.of(inventory));
        when(itemInventoryDao.countSearch(criteria)).thenReturn(1);

        InventorySearchResponse response = service.searchInventory(criteria);

        assertThat(response.getItems()).containsExactly(inventory);
        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getLimit()).isEqualTo(100);
        assertThat(response.getOffset()).isZero();
        assertThat(criteria.getLimit()).isEqualTo(100);
        assertThat(criteria.getOffset()).isZero();
    }

    @Test
    void searchInventoryRejectsInvalidLimit() {
        ItemInventorySearchCriteria criteria = ItemInventorySearchCriteria.builder().limit(501).offset(0).build();

        assertThatThrownBy(() -> service.searchInventory(criteria))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Inventory search limit must be between 1 and 500");
    }

    @Test
    void updateTransactionHeaderPatchesOnlyProvidedFields() {
        Transactions transaction = Transactions.builder()
                .transactionId(101L)
                .transactionDate(LocalDate.parse("2026-07-01"))
                .fromPartyId(1L)
                .toPartyId(2L)
                .transactionPlatformId(7)
                .notes("old")
                .build();
        when(transactionsDao.findById(101L)).thenReturn(Optional.of(transaction));
        when(transactionPlatformDao.findTransactionPlatformByName("eBay"))
                .thenReturn(Optional.of(TransactionPlatform.builder().transactionPlatformId(8).build()));
        stubEmptyReadBack(101L);

        service.updateTransactionHeader(101L, TransactionHeaderUpdateRequest.builder()
                .transactionDate(LocalDate.parse("2026-07-27"))
                .transactionPlatformName("eBay")
                .transactionOrderId("EBAY-123")
                .notes("updated")
                .build());

        assertThat(transaction.getTransactionDate()).isEqualTo(LocalDate.parse("2026-07-27"));
        assertThat(transaction.getFromPartyId()).isEqualTo(1L);
        assertThat(transaction.getToPartyId()).isEqualTo(2L);
        assertThat(transaction.getTransactionPlatformId()).isEqualTo(8);
        assertThat(transaction.getTransactionOrderId()).isEqualTo("EBAY-123");
        assertThat(transaction.getNotes()).isEqualTo("updated");
        verify(transactionsDao).update(transaction);
    }

    @Test
    void addTransactionCostRejectsPriceAtTransactionLevel() {
        when(transactionsDao.findById(101L)).thenReturn(Optional.of(Transactions.builder().transactionId(101L).build()));

        assertThatThrownBy(() -> service.addTransactionCost(101L, cost("PRICE", "1.00", "bad")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Transaction-level costs must not include costTypeCode PRICE; PRICE belongs on each transaction item");

        verifyNoMoreInteractions(transactionCostDao);
    }

    @Test
    void addTransactionCostPersistsWhenTransactionRemainsBalanced() {
        Transactions transaction = Transactions.builder().transactionId(101L).build();
        TransactionItem transactionItem = TransactionItem.builder().transactionItemId(303L).transactionId(101L).itemInventoryId(202).build();
        ItemInventory inventory = new ItemInventory();
        inventory.setItemInventoryId(202);
        when(transactionsDao.findById(101L)).thenReturn(Optional.of(transaction));
        when(transactionCostDao.findByTransactionIdAndCostTypeCode(101L, "SHIPPING")).thenReturn(Optional.empty());
        when(transactionCostDao.findByTransactionId(101L)).thenReturn(List.of(transactionCost("SHIPPING", "12.50")));
        when(transactionItemDao.findByTransactionId(101L)).thenReturn(List.of(transactionItem));
        when(transactionCostDao.findByTransactionItemId(303L)).thenReturn(List.of(transactionItemCost(303L, "PRICE", "99.99")));
        when(paymentDao.findByTransactionId(101L)).thenReturn(List.of(payment("112.49000")));
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of());

        AddItemInventoryResponse response = service.addTransactionCost(101L, cost("SHIPPING", "12.50", "shipping"));

        verify(transactionCostDao).insert(any(TransactionCost.class));
        assertThat(response.getTransaction()).isSameAs(transaction);
    }

    @Test
    void addTransactionCostRejectsDuplicateCostType() {
        when(transactionsDao.findById(101L)).thenReturn(Optional.of(Transactions.builder().transactionId(101L).build()));
        when(transactionCostDao.findByTransactionIdAndCostTypeCode(101L, "SHIPPING"))
                .thenReturn(Optional.of(TransactionCost.builder().transactionCostId(1L).transactionId(101L).costTypeCode("SHIPPING").build()));

        assertThatThrownBy(() -> service.addTransactionCost(101L, cost("SHIPPING", "12.50", "shipping")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Transaction cost type already exists for transaction 101: SHIPPING");
    }

    @Test
    void addPaymentRejectsMissingExchangeRateForCrossCurrencyPayment() {
        when(transactionsDao.findById(101L)).thenReturn(Optional.of(Transactions.builder().transactionId(101L).build()));
        PaymentRequest request = paymentRequest("112.49");
        request.setSellerCurrencyCode("EUR");
        request.setExchangeRate(null);

        assertThatThrownBy(() -> service.addPayment(101L, request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("exchangeRate is required when currencyCode and sellerCurrencyCode differ");

        verifyNoInteractions(paymentDao);
    }

    @Test
    void updatePaymentRejectsPaymentOwnedByAnotherTransaction() {
        when(transactionsDao.findById(101L)).thenReturn(Optional.of(Transactions.builder().transactionId(101L).build()));
        when(paymentDao.findById(404L)).thenReturn(Optional.of(Payment.builder().paymentId(404L).transactionId(999L).build()));

        assertThatThrownBy(() -> service.updatePayment(101L, 404L, paymentRequest("112.49")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Payment 404 does not belong to transaction 101");
    }

    @Test
    void updateTransactionItemPatchesTypeAndNotes() {
        TransactionItem transactionItem = TransactionItem.builder()
                .transactionItemId(303L)
                .transactionId(101L)
                .transactionTypeCode("P")
                .notes("old")
                .build();
        when(transactionItemDao.findById(303L)).thenReturn(Optional.of(transactionItem));
        when(transactionsDao.findById(101L)).thenReturn(Optional.of(Transactions.builder().transactionId(101L).build()));
        stubEmptyReadBack(101L);

        service.updateTransactionItem(303L, TransactionItemUpdateRequest.builder()
                .transactionTypeCode("R")
                .notes("replacement")
                .build());

        assertThat(transactionItem.getTransactionTypeCode()).isEqualTo("R");
        assertThat(transactionItem.getNotes()).isEqualTo("replacement");
        verify(transactionItemDao).update(transactionItem);
    }

    @Test
    void addTransactionItemCostRejectsDuplicateCostType() {
        when(transactionItemDao.findById(303L)).thenReturn(Optional.of(TransactionItem.builder()
                .transactionItemId(303L)
                .transactionId(101L)
                .build()));
        when(transactionCostDao.findByTransactionItemIdAndCostTypeCode(303L, "TAX"))
                .thenReturn(Optional.of(TransactionItemCost.builder().transactionItemCostId(606L).transactionItemId(303L).costTypeCode("TAX").build()));

        assertThatThrownBy(() -> service.addTransactionItemCost(303L, cost("TAX", "1.00", "tax")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Transaction item cost type already exists for transaction item 303: TAX");
    }

    @Test
    void deleteTransactionItemCostRejectsRemovingOnlyPrice() {
        TransactionItem transactionItem = TransactionItem.builder().transactionItemId(303L).transactionId(101L).build();
        TransactionItemCost price = transactionItemCost(303L, "PRICE", "99.99");
        price.setTransactionItemCostId(606L);
        when(transactionItemDao.findById(303L)).thenReturn(Optional.of(transactionItem));
        when(transactionCostDao.findTransactionItemCostById(606L)).thenReturn(Optional.of(price));
        when(transactionCostDao.findByTransactionItemId(303L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.deleteTransactionItemCost(303L, 606L))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Each transaction item must have exactly one transaction item cost with costTypeCode PRICE");

        verify(transactionCostDao).deleteTransactionItemCost(606L);
    }

    @Test
    void updateInventoryPhysicalPatchesFieldsAndConditionCodes() {
        ItemInventory inventory = new ItemInventory();
        inventory.setItemInventoryId(202);
        inventory.setBoxNumber(1);
        inventory.setDescription("old");
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(conditionDao.findByConditionCode("VG")).thenReturn(Optional.of(Condition.builder().conditionId(3).build()));
        when(conditionDao.findByConditionCode("G")).thenReturn(Optional.of(Condition.builder().conditionId(2).build()));
        when(itemInventoryDao.update(inventory)).thenReturn(inventory);

        ItemInventory result = service.updateInventoryPhysical(202, InventoryPhysicalUpdateRequest.builder()
                .boxNumber(12)
                .description("updated")
                .newOrUsed("U")
                .completeness("C")
                .sealed(false)
                .builtOnce(true)
                .itemConditionCode("VG")
                .boxConditionCode("G")
                .instructionsConditionCode("VG")
                .build());

        assertThat(result).isSameAs(inventory);
        assertThat(inventory.getBoxNumber()).isEqualTo(12);
        assertThat(inventory.getDescription()).isEqualTo("updated");
        assertThat(inventory.getNewOrUsed()).isEqualTo("U");
        assertThat(inventory.getItemConditionId()).isEqualTo(3);
        assertThat(inventory.getBoxConditionId()).isEqualTo(2);
        assertThat(inventory.getInstructionsConditionId()).isEqualTo(3);
    }

    @Test
    void updateInventoryStateValidatesLookupBeforeUpdating() {
        ItemInventory inventory = new ItemInventory();
        inventory.setItemInventoryId(202);
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventoryStateDao.findByInventoryStateCode("SOLD"))
                .thenReturn(Optional.of(ItemInventoryState.builder().inventoryStateCode("SOLD").build()));
        when(itemInventoryDao.updateInventoryState(202, "SOLD", null)).thenReturn(inventory);

        assertThat(service.updateInventoryState(202, InventoryStateUpdateRequest.builder().inventoryStateCode("SOLD").build()))
                .isSameAs(inventory);
    }

    @Test
    void updateSaleIntentAllowsUndecidedForExistingInventory() {
        ItemInventory inventory = new ItemInventory();
        inventory.setItemInventoryId(202);
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventorySaleIntentDao.findBySaleIntentCode("UNDECIDED"))
                .thenReturn(Optional.of(ItemInventorySaleIntent.builder().saleIntentCode("UNDECIDED").build()));
        when(itemInventoryDao.updateSaleIntent(202, "UNDECIDED", null, "legacy row")).thenReturn(inventory);

        assertThat(service.updateSaleIntent(202, SaleIntentUpdateRequest.builder()
                .saleIntentCode("UNDECIDED")
                .saleIntentNote("legacy row")
                .build()))
                .isSameAs(inventory);
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

    private void stubEmptyReadBack(Long transactionId) {
        when(transactionCostDao.findByTransactionId(transactionId)).thenReturn(List.of());
        when(paymentDao.findByTransactionId(transactionId)).thenReturn(List.of());
        when(transactionItemDao.findByTransactionId(transactionId)).thenReturn(List.of());
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
                        .amount(new BigDecimal("112.49000"))
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

    private TransactionCost transactionCost(String costTypeCode, String amount) {
        return TransactionCost.builder()
                .transactionCostId(505L)
                .transactionId(101L)
                .costTypeCode(costTypeCode)
                .amount(Double.valueOf(amount))
                .currencyCode(CurrencyCode.USD)
                .build();
    }

    private TransactionItemCost transactionItemCost(Long transactionItemId, String costTypeCode, String amount) {
        return TransactionItemCost.builder()
                .transactionItemCostId(606L)
                .transactionItemId(transactionItemId)
                .costTypeCode(costTypeCode)
                .amount(Double.valueOf(amount))
                .currencyCode("USD")
                .build();
    }

    private Payment payment(String amount) {
        return Payment.builder()
                .paymentId(404L)
                .transactionId(101L)
                .paymentDate(LocalDate.parse("2026-07-27"))
                .currencyCode("USD")
                .sellerCurrencyCode("USD")
                .exchangeRate(new BigDecimal("1.00000"))
                .amount(new BigDecimal(amount))
                .paymentPlatformId(8)
                .paymentPlatformTransactionId("PAYPAL-123")
                .build();
    }

    private PaymentRequest paymentRequest(String amount) {
        return PaymentRequest.builder()
                .paymentDate(LocalDate.parse("2026-07-27"))
                .currencyCode("USD")
                .sellerCurrencyCode("USD")
                .exchangeRate(new BigDecimal("1.00000"))
                .amount(new BigDecimal(amount))
                .paymentPlatformName("PayPal")
                .paymentPlatformTransactionId("PAYPAL-123")
                .build();
    }
}

package com.vattima.lego.inventory.service.impl;

import com.vattima.lego.inventory.service.api.ItemInventoryService;
import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.AddItemInventoryResponse;
import com.vattima.lego.inventory.service.dto.CostRequest;
import com.vattima.lego.inventory.service.dto.ItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.PaymentRequest;
import com.vattima.lego.inventory.service.dto.TransactionItemInventoryResponse;
import com.vattima.lego.inventory.service.exception.ValidationException;
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
import io.legohunter.data.dto.TransactionPlatform;
import io.legohunter.data.dto.Transactions;
import io.legohunter.data.enums.CurrencyCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;

import static io.legohunter.data.dto.ExternalService.Service.BRICKLINK;

@Component
@RequiredArgsConstructor
@Validated
@Slf4j
public class ItemInventoryServiceImpl implements ItemInventoryService {
    private static final String INVENTORY_STATE_AVAILABLE = "AVAILABLE";
    private static final String SALE_INTENT_KEEP = "KEEP";

    private final ConditionDao conditionDao;
    private final ExternalCatalogItemDao externalCatalogItemDao;
    private final ItemInventoryDao itemInventoryDao;
    private final ItemInventoryExternalCatalogItemDao itemInventoryExternalCatalogItemDao;
    private final PaymentDao paymentDao;
    private final PaymentPlatformDao paymentPlatformDao;
    private final TransactionCostDao transactionCostDao;
    private final TransactionItemDao transactionItemDao;
    private final TransactionPlatformDao transactionPlatformDao;
    private final TransactionsDao transactionsDao;

    @Override
    @Transactional
    public AddItemInventoryResponse addItemInventory(@Valid AddItemInventoryRequest request) {
        log.info("Adding item inventory acquisition transaction");
        validateBusinessRules(request);

        Transactions transaction = insertTransaction(request);
        transactionCostDao.setTransactionCosts(transaction.getTransactionId(), toTransactionCosts(request.getCosts()));
        paymentDao.setTransactionPayments(transaction.getTransactionId(), toPayments(request.getPayments()));

        request.getInventoryItems()
                .forEach(itemRequest -> insertAcquisitionItem(transaction, itemRequest));

        return readBackTransactionTree(transaction.getTransactionId());
    }

    private void validateBusinessRules(AddItemInventoryRequest request) {
        if (!request.isTransactionPlatformProvided()) {
            throw new ValidationException("transactionPlatformName or platformName is required");
        }
        if (!request.isTransactionPlatformNameCompatible()) {
            throw new ValidationException("platformName and transactionPlatformName must match when both are provided");
        }
        if (request.getInventoryItems() == null || request.getInventoryItems().isEmpty()) {
            throw new ValidationException("At least one inventory item is required");
        }
        if (request.getCosts() == null || request.getCosts().isEmpty()) {
            throw new ValidationException("At least one transaction cost is required");
        }
        if (request.getPayments() == null || request.getPayments().isEmpty()) {
            throw new ValidationException("At least one payment is required");
        }
        request.getInventoryItems().forEach(item -> {
            if (item == null) {
                throw new ValidationException("Inventory item entries must not be null");
            }
            if (item.getQuantity() == null || item.getQuantity() != 1) {
                throw new ValidationException("Inventory item quantity must be 1");
            }
            if (Boolean.TRUE.equals(item.getForSale())) {
                throw new ValidationException("New acquisition intake must use forSale=false; sale intent is set to KEEP by the service");
            }
        });
        request.getPayments().forEach(payment -> {
            if (payment == null) {
                throw new ValidationException("Payment entries must not be null");
            }
            if (payment.getExchangeRate() == null && !payment.getCurrencyCode().equals(payment.getSellerCurrencyCode())) {
                throw new ValidationException("exchangeRate is required when currencyCode and sellerCurrencyCode differ");
            }
        });
    }

    private Transactions insertTransaction(AddItemInventoryRequest request) {
        TransactionPlatform transactionPlatform = transactionPlatformDao
                .findTransactionPlatformByName(request.effectiveTransactionPlatformName())
                .orElseThrow(() -> new ValidationException("Transaction platform was not found"));

        Transactions transaction = Transactions.builder()
                .transactionDateTime(request.getTransactionDateTime())
                .fromPartyId(request.getFromPartyId())
                .toPartyId(request.getToPartyId())
                .notes(request.getNotes())
                .transactionPlatformId(transactionPlatform.getTransactionPlatformId())
                .build();
        transactionsDao.insert(transaction);
        return transaction;
    }

    private void insertAcquisitionItem(Transactions transaction, ItemInventoryRequest itemRequest) {
        ExternalCatalogItem catalogItem = externalCatalogItemDao
                .findByExternalServiceIdAndExternalItemKey(BRICKLINK.getExternalServiceId(), itemRequest.getItemNumber())
                .orElseThrow(() -> new ValidationException("BrickLink catalog item was not found for itemNumber " + itemRequest.getItemNumber()));

        ItemInventory inventory = itemInventoryDao.insert(toItemInventory(itemRequest));
        itemInventoryExternalCatalogItemDao.insert(ItemInventoryExternalCatalogItem.builder()
                .itemInventoryId(inventory.getItemInventoryId())
                .externalCatalogItemId(catalogItem.getExternalCatalogItemId())
                .primary(true)
                .build());

        TransactionItem transactionItem = TransactionItem.builder()
                .transactionId(transaction.getTransactionId())
                .transactionTypeCode(itemRequest.getTransactionTypeCode())
                .itemInventoryId(inventory.getItemInventoryId())
                .notes(itemRequest.getDescription())
                .build();
        transactionItemDao.insert(transactionItem);
    }

    private ItemInventory toItemInventory(ItemInventoryRequest itemRequest) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ItemInventory inventory = new ItemInventory();
        inventory.setUuid(UUID.randomUUID().toString());
        inventory.setBoxNumber(itemRequest.getBoxNumber());
        inventory.setDescription(itemRequest.getDescription());
        inventory.setActive(true);

        // New acquisition intake creates private collection inventory only. Listing eligibility is a later workflow.
        inventory.setForSale(false);
        inventory.setInventoryStateCode(INVENTORY_STATE_AVAILABLE);
        inventory.setInventoryStateChangedAt(now);
        inventory.setSaleIntentCode(SALE_INTENT_KEEP);
        inventory.setSaleIntentUpdatedAt(now);
        inventory.setSaleIntentNote("Defaulted by acquisition intake");

        inventory.setNewOrUsed(itemRequest.getNewOrUsed());
        inventory.setCompleteness(itemRequest.getCompleteness());
        inventory.setItemConditionId(conditionId(itemRequest.getItemConditionCode()));
        inventory.setBoxConditionId(conditionId(itemRequest.getBoxConditionCode()));
        inventory.setInstructionsConditionId(conditionId(itemRequest.getInstructionsConditionCode()));
        inventory.setSealed(itemRequest.getSealed());
        inventory.setBuiltOnce(itemRequest.getBuiltOnce());
        return inventory;
    }

    private Integer conditionId(String conditionCode) {
        return conditionDao.findByConditionCode(conditionCode)
                .map(Condition::getConditionId)
                .orElseThrow(() -> new ValidationException("Condition code was not found: " + conditionCode));
    }

    private List<TransactionCost> toTransactionCosts(List<CostRequest> costs) {
        return costs.stream()
                .map(cost -> TransactionCost.builder()
                        .costTypeCode(cost.getCostTypeCode())
                        .currencyCode(CurrencyCode.valueOf(cost.getCurrencyCode()))
                        .amount(cost.getAmount().doubleValue())
                        .notes(cost.getNotes())
                        .build())
                .toList();
    }

    private List<Payment> toPayments(List<PaymentRequest> payments) {
        return payments.stream()
                .map(paymentRequest -> {
                    PaymentPlatform paymentPlatform = paymentPlatformDao
                            .findPaymentPlatformByName(paymentRequest.getPaymentPlatformName())
                            .orElseThrow(() -> new ValidationException("Payment platform was not found: " + paymentRequest.getPaymentPlatformName()));
                    return Payment.builder()
                            .paymentDate(paymentRequest.getPaymentDate())
                            .currencyCode(paymentRequest.getCurrencyCode())
                            .sellerCurrencyCode(paymentRequest.getSellerCurrencyCode())
                            .exchangeRate(resolveExchangeRate(paymentRequest))
                            .amount(paymentRequest.getAmount())
                            .paymentPlatformId(paymentPlatform.getPaymentPlatformId())
                            .paymentPlatformTransactionId(paymentRequest.getPaymentPlatformTransactionId())
                            .build();
                })
                .toList();
    }

    private BigDecimal resolveExchangeRate(PaymentRequest paymentRequest) {
        if (paymentRequest.getExchangeRate() != null) {
            return paymentRequest.getExchangeRate();
        }
        return BigDecimal.ONE.setScale(5);
    }

    private AddItemInventoryResponse readBackTransactionTree(Long transactionId) {
        Transactions transaction = transactionsDao.findById(transactionId).orElseThrow();
        List<TransactionItemInventoryResponse> transactionItems = transactionItemDao.findByTransactionId(transactionId)
                .stream()
                .map(this::toTransactionItemResponse)
                .toList();

        return AddItemInventoryResponse.builder()
                .transaction(transaction)
                .costs(transactionCostDao.findByTransactionId(transactionId))
                .payments(paymentDao.findByTransactionId(transactionId))
                .transactionItems(transactionItems)
                .build();
    }

    private TransactionItemInventoryResponse toTransactionItemResponse(TransactionItem transactionItem) {
        ItemInventory inventory = itemInventoryDao.findByItemInventoryId(transactionItem.getItemInventoryId()).orElseThrow();
        return TransactionItemInventoryResponse.builder()
                .transactionItem(transactionItem)
                .itemInventory(inventory)
                .catalogItems(itemInventoryExternalCatalogItemDao.findByItemInventoryId(inventory.getItemInventoryId()))
                .costs(transactionCostDao.findByTransactionItemId(transactionItem.getTransactionItemId()))
                .build();
    }
}

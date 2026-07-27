package com.vattima.lego.inventory.service.impl;

import com.vattima.lego.inventory.service.api.ItemInventoryService;
import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.AddItemInventoryResponse;
import com.vattima.lego.inventory.service.dto.CostRequest;
import com.vattima.lego.inventory.service.dto.InventoryDetailsUpdateRequest;
import com.vattima.lego.inventory.service.dto.InventorySearchItemResponse;
import com.vattima.lego.inventory.service.dto.InventorySearchResponse;
import com.vattima.lego.inventory.service.dto.InventorySearchTransactionResponse;
import com.vattima.lego.inventory.service.dto.InventoryStateUpdateRequest;
import com.vattima.lego.inventory.service.dto.ItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.PaymentRequest;
import com.vattima.lego.inventory.service.dto.SaleIntentUpdateRequest;
import com.vattima.lego.inventory.service.dto.TransactionHeaderUpdateRequest;
import com.vattima.lego.inventory.service.dto.TransactionItemInventoryResponse;
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
import io.legohunter.data.dto.ItemInventorySearchCriteria;
import io.legohunter.data.dto.Payment;
import io.legohunter.data.dto.PaymentPlatform;
import io.legohunter.data.dto.TransactionCost;
import io.legohunter.data.dto.TransactionItem;
import io.legohunter.data.dto.TransactionItemCost;
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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.legohunter.data.dto.ExternalService.Service.BRICKLINK;

@Component
@RequiredArgsConstructor
@Validated
@Slf4j
public class ItemInventoryServiceImpl implements ItemInventoryService {
    private static final String INVENTORY_STATE_AVAILABLE = "AVAILABLE";
    private static final String SALE_INTENT_KEEP = "KEEP";
    private static final String COST_TYPE_PRICE = "PRICE";

    private final ConditionDao conditionDao;
    private final ExternalCatalogItemDao externalCatalogItemDao;
    private final ItemInventoryDao itemInventoryDao;
    private final ItemInventoryExternalCatalogItemDao itemInventoryExternalCatalogItemDao;
    private final ItemInventorySaleIntentDao itemInventorySaleIntentDao;
    private final ItemInventoryStateDao itemInventoryStateDao;
    private final PaymentDao paymentDao;
    private final PaymentPlatformDao paymentPlatformDao;
    private final TransactionCostDao transactionCostDao;
    private final TransactionItemDao transactionItemDao;
    private final TransactionPlatformDao transactionPlatformDao;
    private final TransactionsDao transactionsDao;
    private final InventoryAcquisitionBusinessValidator inventoryAcquisitionBusinessValidator;

    @Override
    @Transactional
    public AddItemInventoryResponse addItemInventory(@Valid AddItemInventoryRequest request) {
        log.info("Adding item inventory acquisition transaction");
        inventoryAcquisitionBusinessValidator.validate(request);

        Transactions transaction = insertTransaction(request);
        transactionCostDao.setTransactionCosts(transaction.getTransactionId(), toTransactionCosts(request.getCosts()));
        paymentDao.setTransactionPayments(transaction.getTransactionId(), toPayments(request.getPayments()));

        request.getInventoryItems()
                .forEach(itemRequest -> insertAcquisitionItem(transaction, itemRequest));

        return readBackTransactionTree(transaction.getTransactionId());
    }

    @Override
    public AddItemInventoryResponse findTransactionTree(Long transactionId) {
        return readBackTransactionTree(transactionId);
    }

    @Override
    public InventorySearchResponse searchInventory(ItemInventorySearchCriteria criteria) {
        ItemInventorySearchCriteria normalized = normalizeSearchCriteria(criteria);
        Set<InventorySearchItemResponse> items = itemInventoryDao.search(normalized).stream()
                .map(this::toInventorySearchItemResponse)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return InventorySearchResponse.builder()
                .items(items)
                .total(itemInventoryDao.countSearch(normalized))
                .limit(normalized.getLimit())
                .offset(normalized.getOffset())
                .build();
    }

    @Override
    @Transactional
    public AddItemInventoryResponse updateTransactionHeader(Long transactionId, TransactionHeaderUpdateRequest request) {
        Transactions transaction = requireTransaction(transactionId);
        if (request.getTransactionDate() != null) {
            transaction.setTransactionDate(request.getTransactionDate());
        }
        if (request.getFromPartyId() != null) {
            transaction.setFromPartyId(request.getFromPartyId());
        }
        if (request.getToPartyId() != null) {
            transaction.setToPartyId(request.getToPartyId());
        }
        if (request.getTransactionPlatformName() != null) {
            TransactionPlatform transactionPlatform = transactionPlatformDao
                    .findTransactionPlatformByName(request.getTransactionPlatformName())
                    .orElseThrow(() -> new ValidationException("Transaction platform was not found: " + request.getTransactionPlatformName()));
            transaction.setTransactionPlatformId(transactionPlatform.getTransactionPlatformId());
        }
        if (request.getTransactionOrderId() != null) {
            transaction.setTransactionOrderId(request.getTransactionOrderId());
        }
        if (request.getNotes() != null) {
            transaction.setNotes(request.getNotes());
        }
        transactionsDao.update(transaction);
        return readBackTransactionTree(transactionId);
    }

    @Override
    @Transactional
    public AddItemInventoryResponse addTransactionCost(Long transactionId, CostRequest request) {
        requireTransaction(transactionId);
        validateTransactionCostRequest(transactionId, null, request);
        TransactionCost transactionCost = toTransactionCost(request);
        transactionCost.setTransactionId(transactionId);
        transactionCostDao.insert(transactionCost);
        validatePersistedTransactionBalance(transactionId);
        return readBackTransactionTree(transactionId);
    }

    @Override
    @Transactional
    public AddItemInventoryResponse updateTransactionCost(Long transactionId, Long transactionCostId, CostRequest request) {
        requireTransaction(transactionId);
        TransactionCost existing = requireTransactionCost(transactionCostId);
        requireOwnedByTransaction(existing, transactionId);
        validateTransactionCostRequest(transactionId, transactionCostId, request);
        TransactionCost updated = toTransactionCost(request);
        updated.setTransactionId(transactionId);
        updated.setTransactionCostId(transactionCostId);
        transactionCostDao.update(updated);
        validatePersistedTransactionBalance(transactionId);
        return readBackTransactionTree(transactionId);
    }

    @Override
    @Transactional
    public AddItemInventoryResponse deleteTransactionCost(Long transactionId, Long transactionCostId) {
        requireTransaction(transactionId);
        TransactionCost existing = requireTransactionCost(transactionCostId);
        requireOwnedByTransaction(existing, transactionId);
        transactionCostDao.delete(transactionCostId);
        validatePersistedTransactionBalance(transactionId);
        return readBackTransactionTree(transactionId);
    }

    @Override
    @Transactional
    public AddItemInventoryResponse addPayment(Long transactionId, PaymentRequest request) {
        requireTransaction(transactionId);
        Payment payment = toPayment(request);
        payment.setTransactionId(transactionId);
        paymentDao.insert(payment);
        validatePersistedTransactionBalance(transactionId);
        return readBackTransactionTree(transactionId);
    }

    @Override
    @Transactional
    public AddItemInventoryResponse updatePayment(Long transactionId, Long paymentId, PaymentRequest request) {
        requireTransaction(transactionId);
        Payment existing = requirePayment(paymentId);
        requireOwnedByTransaction(existing, transactionId);
        Payment payment = toPayment(request);
        payment.setTransactionId(transactionId);
        payment.setPaymentId(paymentId);
        paymentDao.update(payment);
        validatePersistedTransactionBalance(transactionId);
        return readBackTransactionTree(transactionId);
    }

    @Override
    @Transactional
    public AddItemInventoryResponse deletePayment(Long transactionId, Long paymentId) {
        requireTransaction(transactionId);
        Payment existing = requirePayment(paymentId);
        requireOwnedByTransaction(existing, transactionId);
        paymentDao.delete(paymentId);
        validatePersistedTransactionBalance(transactionId);
        return readBackTransactionTree(transactionId);
    }

    @Override
    @Transactional
    public AddItemInventoryResponse updateTransactionItem(Long transactionItemId, TransactionItemUpdateRequest request) {
        TransactionItem transactionItem = requireTransactionItem(transactionItemId);
        if (request.getTransactionTypeCode() != null) {
            transactionItem.setTransactionTypeCode(request.getTransactionTypeCode());
        }
        if (request.getNotes() != null) {
            transactionItem.setNotes(request.getNotes());
        }
        transactionItemDao.update(transactionItem);
        return readBackTransactionTree(transactionItem.getTransactionId());
    }

    @Override
    @Transactional
    public AddItemInventoryResponse addTransactionItemCost(Long transactionItemId, CostRequest request) {
        TransactionItem transactionItem = requireTransactionItem(transactionItemId);
        validateTransactionItemCostRequest(transactionItemId, null, request);
        TransactionItemCost transactionItemCost = toTransactionItemCost(request);
        transactionItemCost.setTransactionItemId(transactionItemId);
        transactionCostDao.insert(transactionItemCost);
        validateTransactionItemHasPrice(transactionItemId);
        validatePersistedTransactionBalance(transactionItem.getTransactionId());
        return readBackTransactionTree(transactionItem.getTransactionId());
    }

    @Override
    @Transactional
    public AddItemInventoryResponse updateTransactionItemCost(Long transactionItemId, Long transactionItemCostId, CostRequest request) {
        TransactionItem transactionItem = requireTransactionItem(transactionItemId);
        TransactionItemCost existing = requireTransactionItemCost(transactionItemCostId);
        requireOwnedByTransactionItem(existing, transactionItemId);
        validateTransactionItemCostRequest(transactionItemId, transactionItemCostId, request);
        TransactionItemCost transactionItemCost = toTransactionItemCost(request);
        transactionItemCost.setTransactionItemId(transactionItemId);
        transactionItemCost.setTransactionItemCostId(transactionItemCostId);
        transactionCostDao.update(transactionItemCost);
        validateTransactionItemHasPrice(transactionItemId);
        validatePersistedTransactionBalance(transactionItem.getTransactionId());
        return readBackTransactionTree(transactionItem.getTransactionId());
    }

    @Override
    @Transactional
    public AddItemInventoryResponse deleteTransactionItemCost(Long transactionItemId, Long transactionItemCostId) {
        TransactionItem transactionItem = requireTransactionItem(transactionItemId);
        TransactionItemCost existing = requireTransactionItemCost(transactionItemCostId);
        requireOwnedByTransactionItem(existing, transactionItemId);
        transactionCostDao.deleteTransactionItemCost(transactionItemCostId);
        validateTransactionItemHasPrice(transactionItemId);
        validatePersistedTransactionBalance(transactionItem.getTransactionId());
        return readBackTransactionTree(transactionItem.getTransactionId());
    }

    @Override
    @Transactional
    public ItemInventory updateInventoryDetails(Integer itemInventoryId, InventoryDetailsUpdateRequest request) {
        ItemInventory inventory = requireInventory(itemInventoryId);
        if (request.getBoxNumber() != null) {
            inventory.setBoxNumber(request.getBoxNumber());
        }
        if (request.getDescription() != null) {
            inventory.setDescription(request.getDescription());
        }
        if (request.getActive() != null) {
            inventory.setActive(request.getActive());
        }
        if (request.getNewOrUsed() != null) {
            inventory.setNewOrUsed(request.getNewOrUsed());
        }
        if (request.getCompleteness() != null) {
            inventory.setCompleteness(request.getCompleteness());
        }
        if (request.getSealed() != null) {
            inventory.setSealed(request.getSealed());
        }
        if (request.getBuiltOnce() != null) {
            inventory.setBuiltOnce(request.getBuiltOnce());
        }
        if (request.getItemConditionCode() != null) {
            inventory.setItemConditionId(conditionId(request.getItemConditionCode()));
        }
        if (request.getBoxConditionCode() != null) {
            inventory.setBoxConditionId(conditionId(request.getBoxConditionCode()));
        }
        if (request.getInstructionsConditionCode() != null) {
            inventory.setInstructionsConditionId(conditionId(request.getInstructionsConditionCode()));
        }
        return itemInventoryDao.update(inventory);
    }

    @Override
    @Transactional
    public ItemInventory updateInventoryState(Integer itemInventoryId, InventoryStateUpdateRequest request) {
        requireInventory(itemInventoryId);
        if (itemInventoryStateDao.findByInventoryStateCode(request.getInventoryStateCode()).isEmpty()) {
            throw new ValidationException("Inventory state code was not found: " + request.getInventoryStateCode());
        }
        return itemInventoryDao.updateInventoryState(itemInventoryId, request.getInventoryStateCode(), null);
    }

    @Override
    @Transactional
    public ItemInventory updateSaleIntent(Integer itemInventoryId, SaleIntentUpdateRequest request) {
        requireInventory(itemInventoryId);
        if (itemInventorySaleIntentDao.findBySaleIntentCode(request.getSaleIntentCode()).isEmpty()) {
            throw new ValidationException("Sale intent code was not found: " + request.getSaleIntentCode());
        }
        return itemInventoryDao.updateSaleIntent(itemInventoryId, request.getSaleIntentCode(), null, request.getSaleIntentNote());
    }

    private Transactions insertTransaction(AddItemInventoryRequest request) {
        TransactionPlatform transactionPlatform = transactionPlatformDao
                .findTransactionPlatformByName(request.effectiveTransactionPlatformName())
                .orElseThrow(() -> new ValidationException("Transaction platform was not found"));

        Transactions transaction = Transactions.builder()
                .transactionDate(request.getTransactionDate())
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
        transactionCostDao.setTransactionItemCosts(transactionItem.getTransactionItemId(), toTransactionItemCosts(itemRequest.getCosts()));
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

    private List<TransactionCost> toTransactionCosts(Collection<CostRequest> costs) {
        return costs.stream()
                .map(this::toTransactionCost)
                .toList();
    }

    private TransactionCost toTransactionCost(CostRequest cost) {
        return TransactionCost.builder()
                .costTypeCode(cost.getCostTypeCode())
                .currencyCode(CurrencyCode.valueOf(cost.getCurrencyCode()))
                .amount(cost.getAmount().doubleValue())
                .notes(cost.getNotes())
                .build();
    }

    private List<TransactionItemCost> toTransactionItemCosts(Collection<CostRequest> costs) {
        return costs.stream()
                .map(this::toTransactionItemCost)
                .toList();
    }

    private TransactionItemCost toTransactionItemCost(CostRequest cost) {
        return TransactionItemCost.builder()
                .costTypeCode(cost.getCostTypeCode())
                .currencyCode(cost.getCurrencyCode())
                .amount(cost.getAmount().doubleValue())
                .notes(cost.getNotes())
                .build();
    }

    private List<Payment> toPayments(List<PaymentRequest> payments) {
        return payments.stream()
                .map(paymentRequest -> {
                    validatePaymentExchangeRate(paymentRequest);
                    PaymentPlatform paymentPlatform = paymentPlatformDao
                            .findPaymentPlatformByName(paymentRequest.getPaymentPlatformName())
                            .orElseThrow(() -> new ValidationException("Payment platform was not found: " + paymentRequest.getPaymentPlatformName()));
                    return toPayment(paymentRequest, paymentPlatform);
                })
                .toList();
    }

    private Payment toPayment(PaymentRequest paymentRequest) {
        validatePaymentExchangeRate(paymentRequest);
        PaymentPlatform paymentPlatform = paymentPlatformDao
                .findPaymentPlatformByName(paymentRequest.getPaymentPlatformName())
                .orElseThrow(() -> new ValidationException("Payment platform was not found: " + paymentRequest.getPaymentPlatformName()));
        return toPayment(paymentRequest, paymentPlatform);
    }

    private Payment toPayment(PaymentRequest paymentRequest, PaymentPlatform paymentPlatform) {
        return Payment.builder()
                .paymentDate(paymentRequest.getPaymentDate())
                .currencyCode(paymentRequest.getCurrencyCode())
                .sellerCurrencyCode(paymentRequest.getSellerCurrencyCode())
                .exchangeRate(resolveExchangeRate(paymentRequest))
                .amount(paymentRequest.getAmount())
                .paymentPlatformId(paymentPlatform.getPaymentPlatformId())
                .paymentPlatformTransactionId(paymentRequest.getPaymentPlatformTransactionId())
                .build();
    }

    private void validatePaymentExchangeRate(PaymentRequest paymentRequest) {
        if (paymentRequest.getExchangeRate() == null
                && paymentRequest.getCurrencyCode() != null
                && paymentRequest.getSellerCurrencyCode() != null
                && !paymentRequest.getCurrencyCode().equals(paymentRequest.getSellerCurrencyCode())) {
            throw new ValidationException("exchangeRate is required when currencyCode and sellerCurrencyCode differ");
        }
    }

    private BigDecimal resolveExchangeRate(PaymentRequest paymentRequest) {
        if (paymentRequest.getExchangeRate() != null) {
            return paymentRequest.getExchangeRate();
        }
        return BigDecimal.ONE.setScale(5);
    }

    private AddItemInventoryResponse readBackTransactionTree(Long transactionId) {
        Transactions transaction = requireTransaction(transactionId);
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
        ItemInventory inventory = requireInventory(transactionItem.getItemInventoryId());
        return TransactionItemInventoryResponse.builder()
                .transactionItem(transactionItem)
                .itemInventory(inventory)
                .catalogItems(itemInventoryExternalCatalogItemDao.findByItemInventoryId(inventory.getItemInventoryId()))
                .costs(transactionCostDao.findByTransactionItemId(transactionItem.getTransactionItemId()))
                .build();
    }

    private InventorySearchItemResponse toInventorySearchItemResponse(ItemInventory itemInventory) {
        Set<InventorySearchTransactionResponse> transactions = transactionItemDao
                .findByItemInventoryId(itemInventory.getItemInventoryId())
                .stream()
                .map(this::toInventorySearchTransactionResponse)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return InventorySearchItemResponse.builder()
                .itemInventory(itemInventory)
                .transactions(transactions)
                .build();
    }

    private InventorySearchTransactionResponse toInventorySearchTransactionResponse(TransactionItem transactionItem) {
        Long transactionId = transactionItem.getTransactionId();
        return InventorySearchTransactionResponse.builder()
                .transaction(requireTransaction(transactionId))
                .transactionCosts(transactionCostDao.findByTransactionId(transactionId))
                .transactionItem(transactionItem)
                .transactionItemCosts(transactionCostDao.findByTransactionItemId(transactionItem.getTransactionItemId()))
                .build();
    }

    private ItemInventorySearchCriteria normalizeSearchCriteria(ItemInventorySearchCriteria criteria) {
        if (criteria == null) {
            criteria = ItemInventorySearchCriteria.builder().build();
        }
        int limit = criteria.getLimit() == null ? 100 : criteria.getLimit();
        int offset = criteria.getOffset() == null ? 0 : criteria.getOffset();
        if (limit < 1 || limit > 500) {
            throw new ValidationException("Inventory search limit must be between 1 and 500");
        }
        if (offset < 0) {
            throw new ValidationException("Inventory search offset must be greater than or equal to 0");
        }
        criteria.setLimit(limit);
        criteria.setOffset(offset);
        return criteria;
    }

    private Transactions requireTransaction(Long transactionId) {
        return transactionsDao.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction was not found: " + transactionId));
    }

    private TransactionItem requireTransactionItem(Long transactionItemId) {
        return transactionItemDao.findById(transactionItemId)
                .orElseThrow(() -> new NotFoundException("Transaction item was not found: " + transactionItemId));
    }

    private TransactionCost requireTransactionCost(Long transactionCostId) {
        return transactionCostDao.findById(transactionCostId)
                .orElseThrow(() -> new NotFoundException("Transaction cost was not found: " + transactionCostId));
    }

    private TransactionItemCost requireTransactionItemCost(Long transactionItemCostId) {
        return transactionCostDao.findTransactionItemCostById(transactionItemCostId)
                .orElseThrow(() -> new NotFoundException("Transaction item cost was not found: " + transactionItemCostId));
    }

    private Payment requirePayment(Long paymentId) {
        return paymentDao.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment was not found: " + paymentId));
    }

    private ItemInventory requireInventory(Integer itemInventoryId) {
        return itemInventoryDao.findByItemInventoryId(itemInventoryId)
                .orElseThrow(() -> new NotFoundException("Item inventory was not found: " + itemInventoryId));
    }

    private void requireOwnedByTransaction(TransactionCost transactionCost, Long transactionId) {
        if (!Objects.equals(transactionCost.getTransactionId(), transactionId)) {
            throw new NotFoundException("Transaction cost " + transactionCost.getTransactionCostId() + " does not belong to transaction " + transactionId);
        }
    }

    private void requireOwnedByTransaction(Payment payment, Long transactionId) {
        if (!Objects.equals(payment.getTransactionId(), transactionId)) {
            throw new NotFoundException("Payment " + payment.getPaymentId() + " does not belong to transaction " + transactionId);
        }
    }

    private void requireOwnedByTransactionItem(TransactionItemCost transactionItemCost, Long transactionItemId) {
        if (!Objects.equals(transactionItemCost.getTransactionItemId(), transactionItemId)) {
            throw new NotFoundException("Transaction item cost " + transactionItemCost.getTransactionItemCostId()
                    + " does not belong to transaction item " + transactionItemId);
        }
    }

    private void validateTransactionCostRequest(Long transactionId, Long currentTransactionCostId, CostRequest request) {
        if (COST_TYPE_PRICE.equals(request.getCostTypeCode())) {
            throw new ValidationException("Transaction-level costs must not include costTypeCode PRICE; PRICE belongs on each transaction item");
        }
        Optional<TransactionCost> existing = transactionCostDao.findByTransactionIdAndCostTypeCode(transactionId, request.getCostTypeCode());
        if (existing.isPresent() && !Objects.equals(existing.get().getTransactionCostId(), currentTransactionCostId)) {
            throw new ValidationException("Transaction cost type already exists for transaction " + transactionId + ": " + request.getCostTypeCode());
        }
    }

    private void validateTransactionItemCostRequest(Long transactionItemId, Long currentTransactionItemCostId, CostRequest request) {
        Optional<TransactionItemCost> existing = transactionCostDao.findByTransactionItemIdAndCostTypeCode(transactionItemId, request.getCostTypeCode());
        if (existing.isPresent() && !Objects.equals(existing.get().getTransactionItemCostId(), currentTransactionItemCostId)) {
            throw new ValidationException("Transaction item cost type already exists for transaction item "
                    + transactionItemId + ": " + request.getCostTypeCode());
        }
    }

    private void validateTransactionItemHasPrice(Long transactionItemId) {
        long priceCount = transactionCostDao.findByTransactionItemId(transactionItemId).stream()
                .filter(cost -> COST_TYPE_PRICE.equals(cost.getCostTypeCode()))
                .count();
        if (priceCount != 1) {
            throw new ValidationException("Each transaction item must have exactly one transaction item cost with costTypeCode PRICE");
        }
    }

    private void validatePersistedTransactionBalance(Long transactionId) {
        List<TransactionCost> transactionCosts = transactionCostDao.findByTransactionId(transactionId);
        List<TransactionItemCost> itemCosts = transactionItemDao.findByTransactionId(transactionId).stream()
                .flatMap(transactionItem -> transactionCostDao.findByTransactionItemId(transactionItem.getTransactionItemId()).stream())
                .toList();
        List<Payment> payments = paymentDao.findByTransactionId(transactionId);

        // Correction endpoints mutate one row at a time, but every committed mutation must leave the transaction balanced.
        if (payments.isEmpty()) {
            throw new ValidationException("At least one payment is required");
        }
        validateSingleCurrency(transactionCosts, itemCosts, payments);

        BigDecimal totalCosts = Stream.concat(
                        transactionCosts.stream().map(cost -> BigDecimal.valueOf(cost.getAmount())),
                        itemCosts.stream().map(cost -> BigDecimal.valueOf(cost.getAmount()))
                )
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPayments = payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalCosts.compareTo(totalPayments) != 0) {
            throw new ValidationException("Total payments must equal total costs. costs="
                    + totalCosts + ", payments=" + totalPayments);
        }
    }

    private void validateSingleCurrency(List<TransactionCost> transactionCosts, List<TransactionItemCost> itemCosts, List<Payment> payments) {
        List<String> currencies = Stream.of(
                        transactionCosts.stream()
                                .map(TransactionCost::getCurrencyCode)
                                .filter(Objects::nonNull)
                                .map(Enum::name),
                        itemCosts.stream().map(TransactionItemCost::getCurrencyCode),
                        payments.stream().map(Payment::getCurrencyCode)
                )
                .flatMap(Function.identity())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (currencies.size() > 1) {
            throw new ValidationException("All costs and payments in a transaction must use the same currencyCode: " + currencies);
        }
    }
}

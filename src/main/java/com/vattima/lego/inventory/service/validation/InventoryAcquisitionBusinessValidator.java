package com.vattima.lego.inventory.service.validation;

import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.CostRequest;
import com.vattima.lego.inventory.service.dto.ItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.PaymentRequest;
import com.vattima.lego.inventory.service.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class InventoryAcquisitionBusinessValidator {
    private static final String COST_TYPE_PRICE = "PRICE";

    public void validate(AddItemInventoryRequest request) {
        validatePlatform(request);
        validateTransactionCosts(request.getCosts());
        validateInventoryItems(request.getInventoryItems());
        validatePayments(request.getPayments());
        validateSingleCurrency(request);
        validatePaymentTotal(request);
    }

    private void validatePlatform(AddItemInventoryRequest request) {
        if (!request.isTransactionPlatformProvided()) {
            throw new ValidationException("transactionPlatformName or platformName is required");
        }
        if (!request.isTransactionPlatformNameCompatible()) {
            throw new ValidationException("platformName and transactionPlatformName must match when both are provided");
        }
    }

    private void validateTransactionCosts(Collection<CostRequest> transactionCosts) {
        if (transactionCosts == null) {
            throw new ValidationException("Transaction costs must not be null");
        }
        validateNoNullCosts(transactionCosts, "Transaction cost entries must not be null");
        validateUniqueCostTypes(transactionCosts, "Transaction cost types must be unique");
        if (hasCostType(transactionCosts, COST_TYPE_PRICE)) {
            throw new ValidationException("Transaction-level costs must not include costTypeCode PRICE; PRICE belongs on each transaction item");
        }
    }

    private void validateInventoryItems(Collection<ItemInventoryRequest> inventoryItems) {
        if (inventoryItems == null || inventoryItems.isEmpty()) {
            throw new ValidationException("At least one inventory item is required");
        }

        inventoryItems.forEach(item -> {
            if (item == null) {
                throw new ValidationException("Inventory item entries must not be null");
            }
            if (item.getQuantity() == null || item.getQuantity() != 1) {
                throw new ValidationException("Inventory item quantity must be 1");
            }
            if (Boolean.TRUE.equals(item.getForSale())) {
                throw new ValidationException("New acquisition intake must use forSale=false; sale intent is set to KEEP by the service");
            }
            validateItemCosts(item);
        });
    }

    private void validateItemCosts(ItemInventoryRequest item) {
        Collection<CostRequest> itemCosts = item.getCosts();
        if (itemCosts == null || itemCosts.isEmpty()) {
            throw new ValidationException("Each inventory item must include at least one transaction item cost");
        }
        validateNoNullCosts(itemCosts, "Transaction item cost entries must not be null");
        validateUniqueCostTypes(itemCosts, "Transaction item cost types must be unique");
        if (!hasCostType(itemCosts, COST_TYPE_PRICE)) {
            throw new ValidationException("Each inventory item must include a transaction item cost with costTypeCode PRICE");
        }
    }

    private void validatePayments(Collection<PaymentRequest> payments) {
        if (payments == null || payments.isEmpty()) {
            throw new ValidationException("At least one payment is required");
        }
        payments.forEach(payment -> {
            if (payment == null) {
                throw new ValidationException("Payment entries must not be null");
            }
            if (payment.getExchangeRate() == null
                    && payment.getCurrencyCode() != null
                    && payment.getSellerCurrencyCode() != null
                    && !payment.getCurrencyCode().equals(payment.getSellerCurrencyCode())) {
                throw new ValidationException("exchangeRate is required when currencyCode and sellerCurrencyCode differ");
            }
        });
    }

    private void validateNoNullCosts(Collection<CostRequest> costs, String message) {
        if (costs.stream().anyMatch(cost -> cost == null)) {
            throw new ValidationException(message);
        }
    }

    private void validateUniqueCostTypes(Collection<CostRequest> costs, String message) {
        Map<String, Long> counts = costs.stream()
                .map(CostRequest::getCostTypeCode)
                .filter(costTypeCode -> costTypeCode != null)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        boolean hasDuplicate = counts.values().stream().anyMatch(count -> count > 1);
        if (hasDuplicate) {
            throw new ValidationException(message);
        }
    }

    private boolean hasCostType(Collection<CostRequest> costs, String costTypeCode) {
        return costs.stream()
                .map(CostRequest::getCostTypeCode)
                .anyMatch(costTypeCode::equals);
    }

    private void validateSingleCurrency(AddItemInventoryRequest request) {
        if (request.getCosts() == null || request.getInventoryItems() == null || request.getPayments() == null) {
            return;
        }

        List<String> currencies = Stream.of(
                        request.getCosts().stream()
                                .filter(Objects::nonNull)
                                .map(CostRequest::getCurrencyCode),
                        request.getInventoryItems().stream()
                                .filter(Objects::nonNull)
                                .map(ItemInventoryRequest::getCosts)
                                .filter(Objects::nonNull)
                                .flatMap(Collection::stream)
                                .filter(Objects::nonNull)
                                .map(CostRequest::getCurrencyCode),
                        request.getPayments().stream()
                                .filter(Objects::nonNull)
                                .map(PaymentRequest::getCurrencyCode)
                )
                .flatMap(Function.identity())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (currencies.size() > 1) {
            throw new ValidationException("All costs and payments in a transaction must use the same currencyCode: " + currencies);
        }
    }

    private void validatePaymentTotal(AddItemInventoryRequest request) {
        if (request.getCosts() == null || request.getInventoryItems() == null || request.getPayments() == null) {
            return;
        }

        BigDecimal transactionCostTotal = request.getCosts().stream()
                .filter(cost -> cost != null && cost.getAmount() != null)
                .map(CostRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal itemCostTotal = request.getInventoryItems().stream()
                .filter(Objects::nonNull)
                .map(ItemInventoryRequest::getCosts)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(cost -> cost != null && cost.getAmount() != null)
                .map(CostRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paymentTotal = request.getPayments().stream()
                .filter(payment -> payment != null && payment.getAmount() != null)
                .map(PaymentRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal costTotal = transactionCostTotal.add(itemCostTotal);
        if (costTotal.compareTo(paymentTotal) != 0) {
            throw new ValidationException("Total payments must equal total costs. costs="
                    + costTotal + ", payments=" + paymentTotal);
        }
    }
}

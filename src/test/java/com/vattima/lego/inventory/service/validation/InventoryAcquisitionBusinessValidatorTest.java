package com.vattima.lego.inventory.service.validation;

import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.CostRequest;
import com.vattima.lego.inventory.service.dto.ItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.PaymentRequest;
import com.vattima.lego.inventory.service.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryAcquisitionBusinessValidatorTest {
    private final InventoryAcquisitionBusinessValidator validator = new InventoryAcquisitionBusinessValidator();

    @Test
    void rejectsNullTransactionCosts() {
        AddItemInventoryRequest request = validRequest();
        request.setCosts(null);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Transaction costs must not be null");
    }

    @Test
    void rejectsNullTransactionCostEntry() {
        AddItemInventoryRequest request = validRequest();
        List<CostRequest> costs = new ArrayList<>();
        costs.add(null);
        request.setCosts(costs);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Transaction cost entries must not be null");
    }

    @Test
    void rejectsDuplicateTransactionCostTypes() {
        AddItemInventoryRequest request = validRequest();
        request.setCosts(List.of(cost("SHIPPING"), cost("SHIPPING")));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Transaction cost types must be unique");
    }

    @Test
    void rejectsTransactionLevelPrice() {
        AddItemInventoryRequest request = validRequest();
        request.setCosts(List.of(cost("PRICE")));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Transaction-level costs must not include costTypeCode PRICE; PRICE belongs on each transaction item");
    }

    @Test
    void rejectsMissingInventoryItems() {
        AddItemInventoryRequest request = validRequest();
        request.setInventoryItems(List.of());

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("At least one inventory item is required");
    }

    @Test
    void rejectsNullInventoryItems() {
        AddItemInventoryRequest request = validRequest();
        request.setInventoryItems(null);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("At least one inventory item is required");
    }

    @Test
    void rejectsNullInventoryItemQuantity() {
        AddItemInventoryRequest request = validRequest();
        request.getInventoryItems().getFirst().setQuantity(null);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Inventory item quantity must be 1");
    }

    @Test
    void rejectsNullTransactionItemCostEntry() {
        AddItemInventoryRequest request = validRequest();
        List<CostRequest> costs = new ArrayList<>();
        costs.add(null);
        request.getInventoryItems().getFirst().setCosts(costs);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Transaction item cost entries must not be null");
    }

    @Test
    void rejectsDuplicateTransactionItemCostTypes() {
        AddItemInventoryRequest request = validRequest();
        request.getInventoryItems().getFirst().setCosts(List.of(cost("PRICE"), cost("PRICE")));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Transaction item cost types must be unique");
    }

    @Test
    void rejectsMissingItemCosts() {
        AddItemInventoryRequest request = validRequest();
        request.getInventoryItems().getFirst().setCosts(List.of());

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Each inventory item must include at least one transaction item cost");
    }

    @Test
    void rejectsNullItemCosts() {
        AddItemInventoryRequest request = validRequest();
        request.getInventoryItems().getFirst().setCosts(null);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Each inventory item must include at least one transaction item cost");
    }

    @Test
    void rejectsMissingItemPrice() {
        AddItemInventoryRequest request = validRequest();
        request.getInventoryItems().getFirst().setCosts(List.of(cost("FEE")));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Each inventory item must include a transaction item cost with costTypeCode PRICE");
    }

    @Test
    void rejectsMissingCrossCurrencyExchangeRate() {
        AddItemInventoryRequest request = validRequest();
        request.getPayments().getFirst().setSellerCurrencyCode("EUR");
        request.getPayments().getFirst().setExchangeRate(null);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("exchangeRate is required when currencyCode and sellerCurrencyCode differ");
    }

    @Test
    void rejectsMissingPayments() {
        AddItemInventoryRequest request = validRequest();
        request.setPayments(List.of());

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("At least one payment is required");
    }

    @Test
    void rejectsNullPayments() {
        AddItemInventoryRequest request = validRequest();
        request.setPayments(null);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("At least one payment is required");
    }

    @Test
    void allowsPaymentCurrencyNullsForFieldValidationToReport() {
        AddItemInventoryRequest request = validRequest();
        request.getPayments().getFirst().setExchangeRate(null);
        request.getPayments().getFirst().setCurrencyCode(null);

        validator.validate(request);

        request.getPayments().getFirst().setCurrencyCode("USD");
        request.getPayments().getFirst().setSellerCurrencyCode(null);

        validator.validate(request);
    }

    @Test
    void allowsNullCostTypeCodeForFieldValidationToReport() {
        AddItemInventoryRequest request = validRequest();
        request.setCosts(List.of(cost(null)));

        validator.validate(request);
    }

    @Test
    void allowsEmptyTransactionCostsWhenItemsHavePrices() {
        AddItemInventoryRequest request = validRequest();
        request.setCosts(List.of());

        validator.validate(request);
    }

    private AddItemInventoryRequest validRequest() {
        return AddItemInventoryRequest.builder()
                .transactionDate(LocalDate.parse("2026-07-27"))
                .fromPartyId(1L)
                .toPartyId(2L)
                .transactionPlatformName("BrickLink")
                .costs(List.of(cost("SHIPPING")))
                .payments(List.of(PaymentRequest.builder()
                        .paymentDate(LocalDate.parse("2026-07-27"))
                        .currencyCode("USD")
                        .sellerCurrencyCode("USD")
                        .exchangeRate(new BigDecimal("1.00000"))
                        .amount(new BigDecimal("100.00"))
                        .paymentPlatformName("PayPal")
                        .paymentPlatformTransactionId("PAYPAL-123")
                        .build()))
                .inventoryItems(List.of(ItemInventoryRequest.builder()
                        .itemNumber("1234-1")
                        .boxNumber(12)
                        .newOrUsed("N")
                        .completeness("C")
                        .sealed(true)
                        .builtOnce(false)
                        .itemConditionCode("N")
                        .boxConditionCode("G")
                        .instructionsConditionCode("G")
                        .transactionTypeCode("P")
                        .costs(List.of(cost("PRICE")))
                        .forSale(false)
                        .quantity(1)
                        .active(true)
                        .build()))
                .build();
    }

    private CostRequest cost(String costTypeCode) {
        return CostRequest.builder()
                .costTypeCode(costTypeCode)
                .amount(new BigDecimal("1.00"))
                .currencyCode("USD")
                .build();
    }

}

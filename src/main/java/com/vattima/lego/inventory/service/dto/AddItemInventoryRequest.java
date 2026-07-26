package com.vattima.lego.inventory.service.dto;

import com.vattima.lego.inventory.service.validation.UniqueCostTypeCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import io.legohunter.data.validation.PartyExists;
import io.legohunter.data.validation.TransactionPlatformExists;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddItemInventoryRequest {
    @NotNull
    private ZonedDateTime transactionDateTime;

    @NotNull
    @PartyExists
    private Long fromPartyId;

    @NotNull
    @PartyExists
    private Long toPartyId;

    private String notes;

    @TransactionPlatformExists
    private String platformName;

    @TransactionPlatformExists
    private String transactionPlatformName;

    @NotEmpty
    private List<@Valid ItemInventoryRequest> inventoryItems;

    @NotEmpty
    private List<@Valid PaymentRequest> payments;

    @NotEmpty
    @UniqueCostTypeCode
    private List<@Valid CostRequest> costs;

    @AssertTrue(message = "transactionPlatformName or platformName is required")
    public boolean isTransactionPlatformProvided() {
        return hasText(transactionPlatformName) || hasText(platformName);
    }

    @AssertTrue(message = "platformName and transactionPlatformName must match when both are provided")
    public boolean isTransactionPlatformNameCompatible() {
        return !hasText(transactionPlatformName)
                || !hasText(platformName)
                || transactionPlatformName.equals(platformName);
    }

    public String effectiveTransactionPlatformName() {
        return hasText(transactionPlatformName) ? transactionPlatformName : platformName;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

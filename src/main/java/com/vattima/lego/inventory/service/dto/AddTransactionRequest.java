package com.vattima.lego.inventory.service.dto;

import com.vattima.lego.inventory.service.validation.UniqueCostTypeCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import net.lego.data.v2.validation.TransactionPlatformExists;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@Valid
public class AddTransactionRequest {
    @NotNull
    private ZonedDateTime transactionDateTime;

    @NotNull
    private Long fromPartyId;

    @NotNull
    private Long toPartyId;

    @NotBlank(message = "Notes are mandatory")
    private String notes;

    @TransactionPlatformExists
    private String platformName;

    @NotEmpty
    private String orderId;

    @Valid
    private List<TransactionItemRequest> transactionItems;

    @Valid
    private List<PaymentRequest> payments;

    @UniqueCostTypeCode
    private List<@Valid CostRequest> costs;
}

package com.vattima.lego.inventory.service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.vattima.lego.inventory.service.validation.UniqueCostTypeCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import io.legohunter.data.validation.TransactionPlatformExists;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@Valid
public class AddTransactionRequest {
    @NotNull
    @JsonAlias("transactionDateTime")
    private LocalDate transactionDate;

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

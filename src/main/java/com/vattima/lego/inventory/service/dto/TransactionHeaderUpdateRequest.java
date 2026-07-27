package com.vattima.lego.inventory.service.dto;

import io.legohunter.data.validation.PartyExists;
import io.legohunter.data.validation.TransactionPlatformExists;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionHeaderUpdateRequest {
    private LocalDate transactionDate;

    @PartyExists
    private Long fromPartyId;

    @PartyExists
    private Long toPartyId;

    @TransactionPlatformExists
    private String transactionPlatformName;

    private String transactionOrderId;
    private String notes;
}

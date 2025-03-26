package com.vattima.lego.inventory.service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@Builder
public class TransactionRequest {
    private String transactionPlatformIdentifier;
    private String transactionTypeCode;
}

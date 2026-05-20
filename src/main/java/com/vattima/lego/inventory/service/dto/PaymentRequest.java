package com.vattima.lego.inventory.service.dto;

import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Data;
import io.legohunter.data.enums.CurrencyCode;

import java.time.ZonedDateTime;

@Data
@Builder
@Valid
public class PaymentRequest {
    private ZonedDateTime paymentDate;
    private String currencyCode;
    private String sellerCurrencyCode;
    private Float exchangeRate;
    private Double amount;
    private String paymentPlatformName;
    private String paymentPlatformTransactionId;
}

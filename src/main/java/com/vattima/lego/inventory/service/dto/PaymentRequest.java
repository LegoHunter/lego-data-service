package com.vattima.lego.inventory.service.dto;

import com.vattima.lego.inventory.service.validation.ValueOfEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.legohunter.data.enums.CurrencyCode;
import io.legohunter.data.validation.PaymentPlatformExists;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Valid
public class PaymentRequest {
    @NotNull
    private LocalDate paymentDate;

    @NotBlank
    @ValueOfEnum(enumClass = CurrencyCode.class)
    private String currencyCode;

    @NotBlank
    @ValueOfEnum(enumClass = CurrencyCode.class)
    private String sellerCurrencyCode;

    @DecimalMin(value = "0.00001", message = "Must be greater than 0")
    private BigDecimal exchangeRate;

    @NotNull
    @DecimalMin(value = "0.00", message = "Must be greater than or equal to 0")
    private BigDecimal amount;

    @NotBlank
    @PaymentPlatformExists
    private String paymentPlatformName;

    @NotBlank
    private String paymentPlatformTransactionId;
}

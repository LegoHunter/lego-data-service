package com.vattima.lego.inventory.service.dto;

import com.vattima.lego.inventory.service.validation.ValueOfEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import io.legohunter.data.enums.CurrencyCode;
import io.legohunter.data.validation.CostTypeExists;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Valid
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CostRequest {

    @NotBlank
    @CostTypeExists(message = "")
    @EqualsAndHashCode.Include
    private String costTypeCode;

    @NotNull
    @DecimalMin(value = "0.00", message = "Must be greater than or equal to 0")
    private BigDecimal amount;

    @NotBlank
    @ValueOfEnum(enumClass = CurrencyCode.class)
    private String currencyCode;

    private String notes;
}

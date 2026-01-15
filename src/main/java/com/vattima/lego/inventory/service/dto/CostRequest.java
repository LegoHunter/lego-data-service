package com.vattima.lego.inventory.service.dto;

import com.vattima.lego.inventory.service.validation.ValueOfEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Data;
import net.lego.data.v2.enums.CurrencyCode;
import net.lego.data.v2.validation.CostTypeExists;

@Data
@Builder
@Valid
public class CostRequest {

    @CostTypeExists(message = "")
    private String costTypeCode;

    @Min(value = 0, message = "Must be greater than or equal to 0")
    private Double amount;

    @ValueOfEnum(enumClass = CurrencyCode.class)
    private String currencyCode;

    private String notes;
}

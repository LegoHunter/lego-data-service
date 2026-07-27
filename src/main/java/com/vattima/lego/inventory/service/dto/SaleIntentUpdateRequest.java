package com.vattima.lego.inventory.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleIntentUpdateRequest {
    @NotBlank
    private String saleIntentCode;

    private String saleIntentNote;
}

package com.vattima.lego.inventory.service.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceListingSyncRequestCreateRequest {
    private String syncRequestTypeCode;
    private String syncReasonCode;

    @Min(value = 1, message = "Must be greater than or equal to 1")
    private Integer maxAttempts;
}

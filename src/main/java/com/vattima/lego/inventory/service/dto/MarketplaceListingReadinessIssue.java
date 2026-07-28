package com.vattima.lego.inventory.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceListingReadinessIssue {
    private String code;
    private String severity;
    private String message;
}

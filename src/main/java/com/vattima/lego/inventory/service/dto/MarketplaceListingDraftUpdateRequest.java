package com.vattima.lego.inventory.service.dto;

import com.vattima.lego.inventory.service.validation.ValueOfEnum;
import io.legohunter.data.enums.CurrencyCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceListingDraftUpdateRequest {
    private Integer externalCatalogItemId;
    private String listingStatusCode;
    private String title;
    private String description;
    private String privateNotes;

    @DecimalMin(value = "0.01")
    private BigDecimal unitPrice;

    @ValueOfEnum(enumClass = CurrencyCode.class)
    private String currencyCode;

    private Boolean fixedPrice;

    @Valid
    private BricklinkListingDraftRequest bricklink;
}

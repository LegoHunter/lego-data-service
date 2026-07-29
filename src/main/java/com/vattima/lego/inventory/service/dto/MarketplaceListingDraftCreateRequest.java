package com.vattima.lego.inventory.service.dto;

import com.vattima.lego.inventory.service.validation.ValueOfEnum;
import io.legohunter.data.enums.CurrencyCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceListingDraftCreateRequest {
    @NotNull
    private Integer itemInventoryId;

    @NotBlank
    private String marketplaceCode;

    private Integer externalCatalogItemId;
    private Boolean updateSaleIntentToSellable;
    private String saleIntentNote;

    private String title;
    private String description;
    private String privateNotes;

    @DecimalMin(value = "0.01")
    private BigDecimal unitPrice;

    @NotBlank
    @ValueOfEnum(enumClass = CurrencyCode.class)
    private String currencyCode;

    private Boolean fixedPrice;

    @Valid
    private BricklinkListingDraftRequest bricklink;
}

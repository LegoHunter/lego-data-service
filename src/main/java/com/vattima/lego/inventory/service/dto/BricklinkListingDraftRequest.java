package com.vattima.lego.inventory.service.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BricklinkListingDraftRequest {
    @Min(value = 1)
    private Integer colorId;

    private String colorName;

    @Min(value = 1)
    private Integer bulk;

    private Boolean isRetain;
    private Boolean isStockRoom;
    private String stockRoomId;

    @Min(value = 0)
    private Integer saleRate;

    @Min(value = 1)
    private Integer tierQuantity1;
    private BigDecimal tierPrice1;

    @Min(value = 1)
    private Integer tierQuantity2;
    private BigDecimal tierPrice2;

    @Min(value = 1)
    private Integer tierQuantity3;
    private BigDecimal tierPrice3;

    private BigDecimal myWeight;
    private String remarks;
}

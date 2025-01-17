package com.vattima.lego.inventory.service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddItemInventoryRequest {
    private String itemNumber;
    private String description;
    private Integer boxNumber;
    private String newOrUsed;
    private String completeness;
    private Boolean sealed;
    private Boolean builtOnce;
    private String itemConditionCode;
    private String boxConditionCode;
    private String instructionsConditionCode;
    private Boolean forSale;
    private Integer quantity;
    private Boolean active;
}

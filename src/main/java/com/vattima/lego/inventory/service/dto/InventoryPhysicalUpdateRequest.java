package com.vattima.lego.inventory.service.dto;

import com.vattima.lego.inventory.service.validation.ValidUpperCaseCharacter;
import io.legohunter.data.validation.ConditionCodeExists;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryPhysicalUpdateRequest {
    @Min(value = 1)
    private Integer boxNumber;

    private String description;

    private Boolean active;

    @ValidUpperCaseCharacter(allowedChars = {"N", "U"}, message = "Must be N or U")
    private String newOrUsed;

    @ValidUpperCaseCharacter(allowedChars = {"C", "I"}, message = "Must be C or I")
    private String completeness;

    private Boolean sealed;
    private Boolean builtOnce;

    @ConditionCodeExists
    private String itemConditionCode;

    @ConditionCodeExists
    private String boxConditionCode;

    @ConditionCodeExists
    private String instructionsConditionCode;
}

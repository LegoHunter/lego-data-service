package com.vattima.lego.inventory.service.dto;

import com.vattima.lego.inventory.service.validation.ValidUpperCaseCharacter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import net.lego.data.v2.validation.ConditionCodeExists;
import net.lego.data.v2.validation.ItemNumberExists;
import org.hibernate.validator.constraints.Range;

@Data
@Builder
@Valid
public class ItemInventoryRequest {
    @ItemNumberExists(externalService = "BRICKLINK")
    private String itemNumber;

    private String description;

    @Min(value = 1)
    private Integer boxNumber;

    @ValidUpperCaseCharacter(allowedChars = {"N", "U"}, message = "Must be N or U")
    private String newOrUsed;

    @ValidUpperCaseCharacter(allowedChars = {"C", "I"}, message = "Must be C or I")
    private String completeness;

    @NotNull
    private Boolean sealed;

    private Boolean builtOnce;

    @ConditionCodeExists
    private String itemConditionCode;

    @ConditionCodeExists
    private String boxConditionCode;

    @ConditionCodeExists
    private String instructionsConditionCode;

    @NotNull
    private Boolean forSale;

    @Range(min = 1, max = 1, message = "Must be 1")
    private Integer quantity;

    @NotNull
    private Boolean active;
}

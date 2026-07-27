package com.vattima.lego.inventory.service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.vattima.lego.inventory.service.validation.ValidUpperCaseCharacter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.legohunter.data.validation.ConditionCodeExists;
import io.legohunter.data.validation.ItemNumberExists;
import io.legohunter.data.validation.TransactionTypeExists;
import com.vattima.lego.inventory.service.validation.UniqueCostTypeCode;
import org.hibernate.validator.constraints.Range;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Valid
public class ItemInventoryRequest {
    @NotBlank
    @ItemNumberExists(externalService = "BRICKLINK")
    private String itemNumber;

    private String description;

    @NotNull
    @Min(value = 1)
    private Integer boxNumber;

    @NotNull
    @ValidUpperCaseCharacter(allowedChars = {"N", "U"}, message = "Must be N or U")
    private String newOrUsed;

    @NotNull
    @ValidUpperCaseCharacter(allowedChars = {"C", "I"}, message = "Must be C or I")
    private String completeness;

    @NotNull
    private Boolean sealed;

    @NotNull
    private Boolean builtOnce;

    @NotBlank
    @ConditionCodeExists
    private String itemConditionCode;

    @NotBlank
    @ConditionCodeExists
    private String boxConditionCode;

    @NotBlank
    @ConditionCodeExists
    private String instructionsConditionCode;

    @NotBlank
    @TransactionTypeExists
    @JsonAlias("transactionType")
    private String transactionTypeCode;

    @NotEmpty
    @UniqueCostTypeCode
    private Set<@Valid CostRequest> costs;

    @NotNull
    private Boolean forSale;

    @NotNull
    @Range(min = 1, max = 1, message = "Must be 1")
    private Integer quantity;

    @NotNull
    private Boolean active;

    @AssertTrue(message = "New acquisition intake must use forSale=false; sale intent is set to KEEP by the service")
    public boolean isForSaleCompatibleWithNewAcquisition() {
        return !Boolean.TRUE.equals(forSale);
    }
}

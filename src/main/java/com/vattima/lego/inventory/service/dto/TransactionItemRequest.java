package com.vattima.lego.inventory.service.dto;

import com.vattima.lego.inventory.service.validation.UniqueCostTypeCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import io.legohunter.data.validation.TransactionTypeExists;

import java.util.List;

@Data
@Builder
@Valid
public class TransactionItemRequest {

    @NotNull
    @TransactionTypeExists
    private String transactionTypeCode;

    private String notes;

    @NotNull
    @Valid
    private ItemInventoryRequest itemInventory;

    @UniqueCostTypeCode
    private List<@Valid CostRequest> costs;
}

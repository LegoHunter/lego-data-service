package com.vattima.lego.inventory.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import net.lego.data.v2.validation.TransactionTypeExists;

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

    @Valid
    private List<CostRequest> costs;
}

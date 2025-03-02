package com.vattima.lego.inventory.service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
public class AddItemInventoryRequest {
    private ZonedDateTime transactionDateTime;
    private Long fromPartyId;
    private Long toPartyId;
    private String notes;
    private String platformName;
    private List<ItemInventoryRequest> inventoryItems;
    private List<PaymentRequest> payments;
    private List<CostRequest> costs;
}

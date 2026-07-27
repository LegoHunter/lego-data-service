package com.vattima.lego.inventory.service.api;

import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.AddItemInventoryResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

@Validated
public interface ItemInventoryService {
    AddItemInventoryResponse addItemInventory(@Valid AddItemInventoryRequest addItemInventoryRequest);
}

package com.vattima.lego.inventory.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventorySearchResponse {
    private Set<InventorySearchItemResponse> items;
    private int total;
    private int limit;
    private int offset;
}

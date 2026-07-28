package com.vattima.lego.inventory.service.dto;

import io.legohunter.data.dto.BricklinkMarketplaceListing;
import io.legohunter.data.dto.ItemInventory;
import io.legohunter.data.dto.MarketplaceListing;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceListingReadinessResponse {
    private Integer itemInventoryId;
    private String marketplaceCode;
    private boolean readyForMarketplaceSync;
    private ItemInventory itemInventory;
    private MarketplaceListing marketplaceListing;
    private BricklinkMarketplaceListing bricklinkMarketplaceListing;
    private List<MarketplaceListingReadinessIssue> blockers;
    private List<MarketplaceListingReadinessIssue> warnings;
}

package com.vattima.lego.inventory.service.dto;

import io.legohunter.data.dto.BricklinkMarketplaceListing;
import io.legohunter.data.dto.MarketplaceListing;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceListingDraftResponse {
    private MarketplaceListing marketplaceListing;
    private BricklinkMarketplaceListing bricklinkMarketplaceListing;
    private MarketplaceListingReadinessResponse readiness;
}

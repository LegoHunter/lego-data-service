package com.vattima.lego.inventory.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "lego.marketplace-listing-drafts")
public class MarketplaceListingDraftProperties {
    private boolean production;
    private String environmentCode = "local";
    private String nonProdBricklinkStockroomId = "A";
}

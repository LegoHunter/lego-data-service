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
    /**
     * The stockroom every BrickLink draft from this lower environment is stamped with: C for
     * SANDBOX (and local), B for DEV. No default: A is reserved for PROD. Checked at startup by
     * {@link MarketplaceListingDraftStockroomValidator} (HOMELAB-20). Not used in PROD.
     */
    private String nonProdBricklinkStockroomId;
}

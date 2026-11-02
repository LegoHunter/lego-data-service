package com.vattima.lego.inventory.service.config;

import io.legohunter.data.bricklink.BricklinkStockroomPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Refuses to start the application when a lower environment would stamp its BrickLink drafts
 * with the wrong stockroom.
 *
 * <p>The rules are shared with lego-data-ingress and live in lego-data
 * ({@link BricklinkStockroomPolicy}, {@link io.legohunter.data.enums.LegoEnvironment}): the
 * environment code must be a known environment and agree with the production flag, and a lower
 * environment must use its own stockroom (SANDBOX/local C, DEV B; A is PROD's own). The two
 * services must agree, because ingress later checks that the BrickLink lot sits in the stockroom
 * this service recorded on the draft.
 */
@Component
@RequiredArgsConstructor
public class MarketplaceListingDraftStockroomValidator implements InitializingBean {

    static final String PREFIX = "lego.marketplace-listing-drafts";
    static final String STOCKROOM_SETTING = "non-prod-bricklink-stockroom-id";

    private final MarketplaceListingDraftProperties properties;

    @Override
    public void afterPropertiesSet() {
        List<String> violations = violations(properties);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Unsafe marketplace listing draft configuration:\n - "
                    + String.join("\n - ", violations));
        }
    }

    /** Every way the draft settings break the shared stockroom rules, or an empty list. */
    public static List<String> violations(MarketplaceListingDraftProperties properties) {
        return BricklinkStockroomPolicy.violations(
                PREFIX,
                STOCKROOM_SETTING,
                properties.getEnvironmentCode(),
                properties.isProduction(),
                properties.getNonProdBricklinkStockroomId());
    }
}

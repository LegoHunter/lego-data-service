package com.vattima.lego.inventory.service.config;

import io.legohunter.data.bricklink.BricklinkStockroomPolicy;
import io.legohunter.lego.data.environment.LegoEnvironmentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
 *
 * <p>The draft settings carry their own copy of the environment. The profiles set it from
 * {@code lego.environment} (which the lego-data starter checks against the connected database), and
 * it must not be overridden to disagree.
 */
@Component
@RequiredArgsConstructor
public class MarketplaceListingDraftStockroomValidator implements InitializingBean {

    static final String PREFIX = "lego.marketplace-listing-drafts";
    static final String STOCKROOM_SETTING = "non-prod-bricklink-stockroom-id";

    private final MarketplaceListingDraftProperties properties;
    private final LegoEnvironmentProperties environment;

    @Override
    public void afterPropertiesSet() {
        List<String> violations = violations(properties, environment);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Unsafe marketplace listing draft configuration:\n - "
                    + String.join("\n - ", violations));
        }
    }

    /**
     * Every way the draft settings disagree with {@code lego.environment} or break the shared
     * stockroom rules, or an empty list.
     */
    public static List<String> violations(MarketplaceListingDraftProperties properties, LegoEnvironmentProperties environment) {
        List<String> violations = new ArrayList<>();
        if (!normalized(properties.getEnvironmentCode()).equals(normalized(environment.getCode()))) {
            violations.add("%s.environment-code '%s' must equal lego.environment.code '%s'"
                    .formatted(PREFIX, properties.getEnvironmentCode(), environment.getCode()));
        }
        if (properties.isProduction() != environment.isProduction()) {
            violations.add("%s.production (%s) must equal lego.environment.production (%s)"
                    .formatted(PREFIX, properties.isProduction(), environment.isProduction()));
        }
        violations.addAll(BricklinkStockroomPolicy.violations(
                PREFIX,
                STOCKROOM_SETTING,
                properties.getEnvironmentCode(),
                properties.isProduction(),
                properties.getNonProdBricklinkStockroomId()));
        return violations;
    }

    private static String normalized(String code) {
        return code == null ? "" : code.trim().toLowerCase();
    }
}

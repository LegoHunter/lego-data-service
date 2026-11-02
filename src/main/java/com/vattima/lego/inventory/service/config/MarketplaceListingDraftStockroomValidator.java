package com.vattima.lego.inventory.service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Refuses to start the application when a lower environment would stamp its BrickLink drafts
 * with the wrong stockroom (HOMELAB-20).
 *
 * <p>All LCMS environments share one BrickLink account. Every draft a lower environment creates is
 * stockroom-only, in that environment's own stockroom, so buyers never see it and environments
 * never touch each other's lots:
 *
 * <table>
 *   <caption>Stockroom ownership (owner decision, 2026-10-31)</caption>
 *   <tr><th>Stockroom</th><th>Owner</th></tr>
 *   <tr><td>A</td><td>PROD only, for deliberately hiding a real PROD lot from buyers</td></tr>
 *   <tr><td>B</td><td>DEV</td></tr>
 *   <tr><td>C</td><td>SANDBOX (and {@code local})</td></tr>
 * </table>
 *
 * <p>lego-data-ingress enforces the same rules for marketplace sync
 * ({@code BricklinkStockroomPolicy}); the two services must agree, because ingress checks that the
 * remote lot sits in the stockroom this service recorded on the draft.
 */
@Component
@RequiredArgsConstructor
public class MarketplaceListingDraftStockroomValidator implements InitializingBean {

    /** Reserved for PROD. A lower environment must never create a draft in it. */
    public static final String PROD_ONLY_STOCKROOM = "A";

    private static final Set<String> BRICKLINK_STOCKROOMS = Set.of("A", "B", "C");

    private static final Map<String, String> EXPECTED_STOCKROOM_BY_ENVIRONMENT = Map.of(
            "sandbox", "C",
            "local", "C",
            "dev", "B");

    private final MarketplaceListingDraftProperties properties;

    @Override
    public void afterPropertiesSet() {
        List<String> violations = violations(properties);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Unsafe marketplace listing draft configuration (HOMELAB-20):\n - "
                    + String.join("\n - ", violations));
        }
    }

    /** The stockroom the given lower environment must use, if it is a known environment. */
    public static Optional<String> expectedStockroom(String environmentCode) {
        return Optional.ofNullable(EXPECTED_STOCKROOM_BY_ENVIRONMENT.get(normalize(environmentCode)));
    }

    /** Every way the draft settings break the stockroom rules, or an empty list. PROD has none. */
    public static List<String> violations(MarketplaceListingDraftProperties properties) {
        List<String> violations = new ArrayList<>();
        if (properties.isProduction()) {
            return violations;
        }
        String environment = normalize(properties.getEnvironmentCode());
        String stockroom = normalize(properties.getNonProdBricklinkStockroomId()).toUpperCase(Locale.ROOT);
        if (stockroom.isEmpty()) {
            violations.add("lego.marketplace-listing-drafts.non-prod-bricklink-stockroom-id must be set outside PROD (SANDBOX=C, DEV=B)");
            return violations;
        }
        if (!BRICKLINK_STOCKROOMS.contains(stockroom)) {
            violations.add("non-prod-bricklink-stockroom-id '%s' is not a BrickLink stockroom (A, B or C)".formatted(stockroom));
            return violations;
        }
        if (PROD_ONLY_STOCKROOM.equals(stockroom)) {
            violations.add("non-prod-bricklink-stockroom-id must not be A outside PROD: stockroom A is reserved for PROD's own hidden lots (environment '%s')".formatted(environment));
            return violations;
        }
        expectedStockroom(environment)
                .filter(expected -> !expected.equals(stockroom))
                .ifPresent(expected -> violations.add(
                        "non-prod-bricklink-stockroom-id for environment '%s' must be %s, not %s (SANDBOX=C, DEV=B)".formatted(environment, expected, stockroom)));
        return violations;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

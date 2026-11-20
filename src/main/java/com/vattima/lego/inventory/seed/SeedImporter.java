package com.vattima.lego.inventory.seed;

import com.vattima.lego.inventory.seed.SeedManifest.ManifestItem;
import com.vattima.lego.inventory.seed.SeedServiceClient.ListingView;
import com.vattima.lego.inventory.seed.SeedServiceClient.SeedSourceView;
import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.CostRequest;
import com.vattima.lego.inventory.service.dto.ItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftCreateRequest;
import com.vattima.lego.inventory.service.dto.PaymentRequest;
import com.vattima.lego.inventory.service.dto.SeedSourceRequest;
import io.legohunter.data.dto.ExternalService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.legohunter.data.dto.ItemInventorySeedSource.SOURCE_SYSTEM_LEGO_PROD_LEGACY;

/**
 * Creates the manifest's items in a lower environment through the normal lego-data-service API.
 *
 * <p>Per run:
 * <ol>
 *     <li>Read the environment's existing seed sources. Items already seeded are not created again.</li>
 *     <li>Enforce the per-run cap and the per-environment ceiling on the items still to create.</li>
 *     <li>Drop items whose number is missing from the target's BrickLink catalog (intake would
 *     reject the whole request otherwise).</li>
 *     <li>Create the rest in one acquisition transaction (seller party 209, buyer party 0, test box
 *     900), each carrying its seed source.</li>
 *     <li>For every manifest item that is now seeded and has no BrickLink listing yet, create an
 *     unpriced, non-fixed BrickLink draft with sale intent SELLABLE.</li>
 * </ol>
 *
 * <p>It never sends {@code LISTING_CREATE}. The pricing plane gives each draft its first price and
 * creates the lot (owner decision, HOMELAB-40, 2026-11-05). Step 5 also completes a run that
 * stopped after intake, so a re-run of the same manifest is always safe.
 */
public class SeedImporter {

    /** Agreed hard maximum of new items per run. */
    public static final int MAX_ITEMS_PER_RUN = 50;
    /** Default ceiling on seeded items per environment. */
    public static final int DEFAULT_ENVIRONMENT_CEILING = 200;

    static final long SELLER_PARTY_ID = 209L;
    static final long BUYER_PARTY_ID = 0L;
    static final int TEST_BOX_NUMBER = 900;
    static final String TRANSACTION_PLATFORM = "Bricklink";
    static final String TRANSACTION_TYPE_PURCHASE = "P";
    static final String PAYMENT_PLATFORM = "PayPal";
    static final String COST_TYPE_PRICE = "PRICE";
    static final String CURRENCY = "USD";

    private final SeedServiceClient client;
    private final Clock clock;

    public SeedImporter(SeedServiceClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    /**
     * @param maxPerRun          new items allowed in this run, at most {@link #MAX_ITEMS_PER_RUN}
     * @param environmentCeiling seeded items allowed in the environment after this run
     * @param dryRun             report what would happen without writing anything
     */
    public record Options(int maxPerRun, int environmentCeiling, boolean dryRun) {
        public Options {
            if (maxPerRun < 1 || maxPerRun > MAX_ITEMS_PER_RUN) {
                throw new IllegalArgumentException("--max-per-run must be between 1 and " + MAX_ITEMS_PER_RUN);
            }
            if (environmentCeiling < 1) {
                throw new IllegalArgumentException("--environment-ceiling must be at least 1");
            }
        }
    }

    public enum Outcome {
        /** New item created and its draft created. */
        SEEDED,
        /** Seeded by an earlier run that stopped before its draft; the draft was created now. */
        DRAFT_COMPLETED,
        /** Seeded earlier and already has a BrickLink listing; nothing done. */
        ALREADY_SEEDED,
        /** Not in the target's BrickLink catalog; nothing done. */
        NOT_IN_TARGET_CATALOG,
        /** Dry run: would be created with a draft. */
        WOULD_SEED,
        /** Dry run: already seeded, would get its missing draft. */
        WOULD_COMPLETE_DRAFT
    }

    public record Row(String sourceKey, String itemNumber, Integer itemInventoryId, Integer marketplaceListingId,
                      Outcome outcome) {
    }

    public record Report(String seedRunId, String target, boolean dryRun, List<Row> rows) {
        public long count(Outcome outcome) {
            return rows.stream().filter(row -> row.outcome() == outcome).count();
        }
    }

    public Report run(SeedManifest manifest, String seedRunId, String targetCode, Options options) {
        if (!SOURCE_SYSTEM_LEGO_PROD_LEGACY.equals(manifest.sourceSystem())) {
            throw new IllegalArgumentException("Unsupported manifest source system: " + manifest.sourceSystem());
        }
        List<ManifestItem> items = manifest.items() == null ? List.of() : manifest.items();
        requireUniqueSourceKeys(items);

        Map<String, Integer> seeded = seededItemIds();
        List<ManifestItem> pending = new ArrayList<>();
        for (ManifestItem item : items) {
            if (!seeded.containsKey(item.sourceKey())) {
                pending.add(item);
            }
        }

        if (pending.size() > options.maxPerRun()) {
            throw new SeedLimitException(pending.size() + " items still need seeding, more than the per-run cap of "
                    + options.maxPerRun() + ". Export a smaller manifest (--limit) or split it.");
        }
        if (seeded.size() + pending.size() > options.environmentCeiling()) {
            throw new SeedLimitException("Seeding " + pending.size() + " more items would put " + targetCode + " at "
                    + (seeded.size() + pending.size()) + " seeded items, over the ceiling of "
                    + options.environmentCeiling() + ".");
        }

        Set<String> notInCatalog = new HashSet<>();
        for (ManifestItem item : pending) {
            if (!client.catalogItemExists(item.itemNumber())) {
                notInCatalog.add(item.sourceKey());
            }
        }
        List<ManifestItem> toCreate = pending.stream().filter(item -> !notInCatalog.contains(item.sourceKey())).toList();

        if (!options.dryRun() && !toCreate.isEmpty()) {
            client.addItemInventory(acquisition(toCreate, seedRunId));
            seeded = seededItemIds();
        }

        List<Row> rows = new ArrayList<>();
        Set<String> createdNow = new HashSet<>();
        toCreate.forEach(item -> createdNow.add(item.sourceKey()));
        for (ManifestItem item : items) {
            if (notInCatalog.contains(item.sourceKey())) {
                rows.add(new Row(item.sourceKey(), item.itemNumber(), null, null, Outcome.NOT_IN_TARGET_CATALOG));
                continue;
            }
            Integer itemInventoryId = seeded.get(item.sourceKey());
            if (itemInventoryId == null) {
                if (!options.dryRun()) {
                    throw new IllegalStateException("Source item " + item.sourceKey()
                            + " has no seed-source row after intake; the run stopped before its draft.");
                }
                rows.add(new Row(item.sourceKey(), item.itemNumber(), null, null, Outcome.WOULD_SEED));
                continue;
            }
            Integer listingId = bricklinkListingId(itemInventoryId);
            if (listingId != null) {
                rows.add(new Row(item.sourceKey(), item.itemNumber(), itemInventoryId, listingId, Outcome.ALREADY_SEEDED));
            } else if (options.dryRun()) {
                rows.add(new Row(item.sourceKey(), item.itemNumber(), itemInventoryId, null, Outcome.WOULD_COMPLETE_DRAFT));
            } else {
                int newListingId = client.createDraft(draft(itemInventoryId, seedRunId));
                Outcome outcome = createdNow.contains(item.sourceKey()) ? Outcome.SEEDED : Outcome.DRAFT_COMPLETED;
                rows.add(new Row(item.sourceKey(), item.itemNumber(), itemInventoryId, newListingId, outcome));
            }
        }
        return new Report(seedRunId, targetCode, options.dryRun(), List.copyOf(rows));
    }

    AddItemInventoryRequest acquisition(List<ManifestItem> items, String seedRunId) {
        LocalDate today = LocalDate.now(clock);
        BigDecimal total = items.stream().map(ManifestItem::cost).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ItemInventoryRequest> inventoryItems = items.stream().map(item -> ItemInventoryRequest.builder()
                .itemNumber(item.itemNumber())
                .boxNumber(TEST_BOX_NUMBER)
                .newOrUsed(item.newOrUsed())
                .completeness(item.completeness())
                .sealed(item.sealed())
                .builtOnce(item.builtOnce())
                .itemConditionCode(item.itemConditionCode())
                .boxConditionCode(item.boxConditionCode())
                .instructionsConditionCode(item.instructionsConditionCode())
                .transactionTypeCode(TRANSACTION_TYPE_PURCHASE)
                .costs(List.of(CostRequest.builder()
                        .costTypeCode(COST_TYPE_PRICE)
                        .amount(item.cost() == null ? BigDecimal.ZERO : item.cost())
                        .currencyCode(CURRENCY)
                        .notes("Seeded item cost")
                        .build()))
                .forSale(false)
                .quantity(1)
                .active(true)
                .seedSource(SeedSourceRequest.builder()
                        .sourceSystem(SOURCE_SYSTEM_LEGO_PROD_LEGACY)
                        .sourceKey(item.sourceKey())
                        .seedRunId(seedRunId)
                        .build())
                .build()).toList();
        return AddItemInventoryRequest.builder()
                .transactionDate(today)
                .fromPartyId(SELLER_PARTY_ID)
                .toPartyId(BUYER_PARTY_ID)
                .notes("Lower-environment seed run " + seedRunId + ": " + items.size()
                        + " items from " + SOURCE_SYSTEM_LEGO_PROD_LEGACY + ".")
                .transactionPlatformName(TRANSACTION_PLATFORM)
                .inventoryItems(inventoryItems)
                .payments(List.of(PaymentRequest.builder()
                        .paymentDate(today)
                        .currencyCode(CURRENCY)
                        .sellerCurrencyCode(CURRENCY)
                        .exchangeRate(BigDecimal.ONE)
                        .amount(total)
                        .paymentPlatformName(PAYMENT_PLATFORM)
                        .paymentPlatformTransactionId("SEED-" + seedRunId)
                        .build()))
                .costs(List.of())
                .build();
    }

    static MarketplaceListingDraftCreateRequest draft(int itemInventoryId, String seedRunId) {
        return MarketplaceListingDraftCreateRequest.builder()
                .itemInventoryId(itemInventoryId)
                .marketplaceCode(ExternalService.Service.BRICKLINK.getServiceCode())
                .currencyCode(CURRENCY)
                .fixedPrice(false)
                .updateSaleIntentToSellable(true)
                .saleIntentNote("Lower-environment seed run " + seedRunId)
                .build();
    }

    private Map<String, Integer> seededItemIds() {
        Map<String, Integer> result = new HashMap<>();
        for (SeedSourceView view : client.findSeedSources(SOURCE_SYSTEM_LEGO_PROD_LEGACY, null)) {
            result.put(view.sourceKey(), view.itemInventoryId());
        }
        return result;
    }

    private Integer bricklinkListingId(int itemInventoryId) {
        Integer bricklinkServiceId = ExternalService.Service.BRICKLINK.getExternalServiceId();
        return client.findListings(itemInventoryId).stream()
                .filter(listing -> bricklinkServiceId.equals(listing.listingExternalServiceId()))
                .map(ListingView::marketplaceListingId)
                .findFirst()
                .orElse(null);
    }

    private static void requireUniqueSourceKeys(List<ManifestItem> items) {
        Set<String> keys = new HashSet<>();
        for (ManifestItem item : items) {
            if (!keys.add(item.sourceKey())) {
                throw new IllegalArgumentException("The manifest lists source item " + item.sourceKey() + " twice.");
            }
        }
    }

    /** A run refused because it would exceed the per-run cap or the environment ceiling. */
    public static class SeedLimitException extends RuntimeException {
        public SeedLimitException(String message) {
            super(message);
        }
    }
}

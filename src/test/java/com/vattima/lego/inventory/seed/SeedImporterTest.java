package com.vattima.lego.inventory.seed;

import com.vattima.lego.inventory.seed.SeedImporter.Outcome;
import com.vattima.lego.inventory.seed.SeedManifest.ManifestItem;
import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.ItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftCreateRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedImporterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);
    private static final String RUN_1 = "11111111-1111-1111-1111-111111111111";
    private static final String RUN_2 = "22222222-2222-2222-2222-222222222222";
    private static final SeedImporter.Options DEFAULTS = new SeedImporter.Options(50, 200, false);

    private final FakeSeedServiceClient client = new FakeSeedServiceClient();
    private final SeedImporter importer = new SeedImporter(client, CLOCK);

    @Test
    void createsOneAcquisitionAndAnUnpricedDraftPerItem() {
        SeedImporter.Report report = importer.run(manifest(item("101", "6365-1", "45.00"), item("103", "8880-1", "80.00")),
                RUN_1, "sandbox", DEFAULTS);

        assertThat(report.rows()).extracting(SeedImporter.Row::outcome).containsExactly(Outcome.SEEDED, Outcome.SEEDED);
        assertThat(client.acquisitions).hasSize(1);
        AddItemInventoryRequest acquisition = client.acquisitions.get(0);
        assertThat(acquisition.getFromPartyId()).isEqualTo(209L);
        assertThat(acquisition.getToPartyId()).isEqualTo(0L);
        assertThat(acquisition.getTransactionDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(acquisition.getPayments()).singleElement()
                .satisfies(payment -> assertThat(payment.getAmount()).isEqualByComparingTo("125.00"));
        assertThat(acquisition.getPayments().get(0).getPaymentPlatformTransactionId()).hasSizeLessThanOrEqualTo(45);

        ItemInventoryRequest first = acquisition.getInventoryItems().get(0);
        assertThat(first.getBoxNumber()).isEqualTo(900);
        assertThat(first.getForSale()).isFalse();
        assertThat(first.getQuantity()).isEqualTo(1);
        assertThat(first.getDescription()).isNull();
        assertThat(first.getSeedSource().getSourceSystem()).isEqualTo("LEGO_PROD_LEGACY");
        assertThat(first.getSeedSource().getSourceKey()).isEqualTo("101");
        assertThat(first.getSeedSource().getSeedRunId()).isEqualTo(RUN_1);

        assertThat(client.drafts).hasSize(2);
        MarketplaceListingDraftCreateRequest draft = client.drafts.get(0);
        assertThat(draft.getMarketplaceCode()).isEqualTo("BRICKLINK");
        assertThat(draft.getUnitPrice()).isNull();
        assertThat(draft.getFixedPrice()).isFalse();
        assertThat(draft.getUpdateSaleIntentToSellable()).isTrue();
        // Lot placement is the service's job: the tool never names a stockroom or remarks.
        assertThat(draft.getBricklink()).isNull();
    }

    @Test
    void neverRequestsAListingCreate() {
        importer.run(manifest(item("101", "6365-1", "45.00")), RUN_1, "sandbox", DEFAULTS);

        assertThat(client.syncRequests).isEmpty();
    }

    @Test
    void reRunOfTheSameManifestCreatesNothing() {
        SeedManifest manifest = manifest(item("101", "6365-1", "45.00"), item("103", "8880-1", "80.00"));
        importer.run(manifest, RUN_1, "sandbox", DEFAULTS);

        SeedImporter.Report second = importer.run(manifest, RUN_2, "sandbox", DEFAULTS);

        assertThat(second.rows()).extracting(SeedImporter.Row::outcome)
                .containsExactly(Outcome.ALREADY_SEEDED, Outcome.ALREADY_SEEDED);
        assertThat(client.acquisitions).hasSize(1);
        assertThat(client.drafts).hasSize(2);
        assertThat(client.seedSources).hasSize(2);
    }

    @Test
    void reRunCompletesDraftsOfARunThatStoppedAfterIntake() {
        SeedManifest manifest = manifest(item("101", "6365-1", "45.00"), item("103", "8880-1", "80.00"));
        client.failDraftsAfter = 1;
        assertThatThrownBy(() -> importer.run(manifest, RUN_1, "sandbox", DEFAULTS))
                .isInstanceOf(SeedServiceClient.SeedServiceException.class);
        client.failDraftsAfter = Integer.MAX_VALUE;

        SeedImporter.Report second = importer.run(manifest, RUN_2, "sandbox", DEFAULTS);

        assertThat(second.rows()).extracting(SeedImporter.Row::outcome)
                .containsExactly(Outcome.ALREADY_SEEDED, Outcome.DRAFT_COMPLETED);
        assertThat(client.acquisitions).hasSize(1);
        assertThat(client.drafts).hasSize(2);
    }

    @Test
    void reRunWithANewItemSeedsOnlyThatItem() {
        importer.run(manifest(item("101", "6365-1", "45.00")), RUN_1, "sandbox", DEFAULTS);

        importer.run(manifest(item("101", "6365-1", "45.00"), item("106", "8880-1", "70.00")), RUN_2, "sandbox", DEFAULTS);

        assertThat(client.acquisitions).hasSize(2);
        assertThat(client.acquisitions.get(1).getInventoryItems()).extracting(i -> i.getSeedSource().getSourceKey())
                .containsExactly("106");
    }

    @Test
    void refusesMoreNewItemsThanThePerRunCap() {
        SeedManifest manifest = manifest(IntStream.rangeClosed(1, 51)
                .mapToObj(i -> item(String.valueOf(i), "6365-1", "1.00")).toArray(ManifestItem[]::new));

        assertThatThrownBy(() -> importer.run(manifest, RUN_1, "sandbox", DEFAULTS))
                .isInstanceOf(SeedImporter.SeedLimitException.class)
                .hasMessageContaining("per-run cap of 50");
        assertThat(client.acquisitions).isEmpty();
    }

    @Test
    void perRunCapCountsOnlyItemsStillToSeed() {
        List<ManifestItem> first = new ArrayList<>();
        IntStream.rangeClosed(1, 50).forEach(i -> first.add(item(String.valueOf(i), "6365-1", "1.00")));
        importer.run(manifest(first.toArray(ManifestItem[]::new)), RUN_1, "sandbox", DEFAULTS);

        List<ManifestItem> second = new ArrayList<>(first);
        second.add(item("51", "6365-1", "1.00"));
        SeedImporter.Report report = importer.run(manifest(second.toArray(ManifestItem[]::new)), RUN_2, "sandbox", DEFAULTS);

        assertThat(report.count(Outcome.SEEDED)).isEqualTo(1);
    }

    @Test
    void capCannotBeRaisedAboveFifty() {
        assertThatThrownBy(() -> new SeedImporter.Options(51, 200, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesARunThatWouldPassTheEnvironmentCeiling() {
        importer.run(manifest(item("1", "6365-1", "1.00"), item("2", "6365-1", "1.00")), RUN_1, "sandbox", DEFAULTS);

        assertThatThrownBy(() -> importer.run(manifest(item("3", "6365-1", "1.00")), RUN_2, "sandbox",
                new SeedImporter.Options(50, 2, false)))
                .isInstanceOf(SeedImporter.SeedLimitException.class)
                .hasMessageContaining("ceiling of 2");
        assertThat(client.acquisitions).hasSize(1);
    }

    @Test
    void itemsMissingFromTheTargetCatalogAreReportedNotSent() {
        client.missingCatalogItems.add("9999-1");

        SeedImporter.Report report = importer.run(manifest(item("101", "6365-1", "45.00"), item("102", "9999-1", "5.00")),
                RUN_1, "sandbox", DEFAULTS);

        assertThat(report.rows()).extracting(SeedImporter.Row::outcome)
                .containsExactly(Outcome.SEEDED, Outcome.NOT_IN_TARGET_CATALOG);
        assertThat(client.acquisitions.get(0).getInventoryItems()).hasSize(1);
        assertThat(client.acquisitions.get(0).getPayments().get(0).getAmount()).isEqualByComparingTo("45.00");
    }

    @Test
    void dryRunWritesNothing() {
        SeedImporter.Report report = importer.run(manifest(item("101", "6365-1", "45.00")), RUN_1, "sandbox",
                new SeedImporter.Options(50, 200, true));

        assertThat(report.rows()).extracting(SeedImporter.Row::outcome).containsExactly(Outcome.WOULD_SEED);
        assertThat(client.acquisitions).isEmpty();
        assertThat(client.drafts).isEmpty();
    }

    @Test
    void refusesDuplicateSourceKeysInTheManifest() {
        assertThatThrownBy(() -> importer.run(manifest(item("101", "6365-1", "1.00"), item("101", "6365-1", "1.00")),
                RUN_1, "sandbox", DEFAULTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("101 twice");
    }

    @Test
    void refusesAManifestFromAnotherSourceSystem() {
        SeedManifest manifest = new SeedManifest("SOMEWHERE_ELSE", ZonedDateTime.now(CLOCK),
                new ExportFilter(null, null, null, 50), List.of(item("1", "6365-1", "1.00")), List.of());

        assertThatThrownBy(() -> importer.run(manifest, RUN_1, "sandbox", DEFAULTS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SeedManifest manifest(ManifestItem... items) {
        return new SeedManifest("LEGO_PROD_LEGACY", ZonedDateTime.now(CLOCK), new ExportFilter(null, null, null, 50),
                List.of(items), List.of());
    }

    private static ManifestItem item(String sourceKey, String itemNumber, String cost) {
        return new ManifestItem(sourceKey, itemNumber, "name", SeedBucket.USED, "U", "C", false, false, "NA", "VG", "G",
                new BigDecimal(cost));
    }
}

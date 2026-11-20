package com.vattima.lego.inventory.seed;

import com.vattima.lego.inventory.seed.SeedServiceClient.ListingView;
import com.vattima.lego.inventory.seed.SeedServiceClient.SeedSourceView;
import com.vattima.lego.inventory.seed.SeedServiceClient.SyncRequestView;
import com.vattima.lego.inventory.seed.SeedStatusReporter.Stage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeedStatusReporterTest {

    private static final String RUN = "11111111-1111-1111-1111-111111111111";

    private final FakeSeedServiceClient client = new FakeSeedServiceClient();

    @Test
    void classifiesEveryStage() {
        seed(1, null);
        seed(2, new ListingView(12, 2, "DRAFT", null, false, null, "C", "sandbox", "NOT_VERIFIED"));
        seed(3, new ListingView(13, 2, "DRAFT", new BigDecimal("59.99"), false, null, "C", "sandbox", "NOT_VERIFIED"));
        client.syncRequests.put(13, List.of(sync(30, "LISTING_CREATE", "PENDING")));
        seed(4, new ListingView(14, 2, "DRAFT", new BigDecimal("59.99"), false, null, "C", "sandbox", "NOT_VERIFIED"));
        client.syncRequests.put(14, List.of(sync(40, "LISTING_CREATE", "FAILED")));
        seed(5, new ListingView(15, 2, "ACTIVE", new BigDecimal("59.99"), false, 569454187, "C", "sandbox", "VERIFIED"));
        client.syncRequests.put(15, List.of(sync(50, "LISTING_CREATE", "SUCCEEDED"), sync(51, "PRICE_UPDATE", "SUCCEEDED")));
        seed(6, new ListingView(16, 2, "ACTIVE", new BigDecimal("59.99"), false, 569454188, "C", "sandbox", "BLOCKED_STOCKROOM"));

        SeedStatusReporter.Report report = new SeedStatusReporter(client).report(RUN);

        assertThat(report.rows()).extracting(SeedStatusReporter.Row::stage).containsExactly(
                Stage.NO_LISTING, Stage.AWAITING_PRICE, Stage.LOT_PENDING, Stage.SYNC_PROBLEM,
                Stage.LOT_VERIFIED, Stage.LOT_UNVERIFIED);
        SeedStatusReporter.Row verified = report.rows().get(4);
        assertThat(verified.listingCreateSyncRequestId()).isEqualTo(50L);
        assertThat(verified.bricklinkInventoryId()).isEqualTo(569454187);
        assertThat(report.counts().get(Stage.LOT_VERIFIED)).isEqualTo(1L);
    }

    @Test
    void ignoresListingsOnOtherMarketplaces() {
        seed(1, new ListingView(11, 3, "ACTIVE", new BigDecimal("10"), true, null, null, null, null));

        assertThat(new SeedStatusReporter(client).report(RUN).rows())
                .extracting(SeedStatusReporter.Row::stage).containsExactly(Stage.NO_LISTING);
    }

    @Test
    void filtersBySeedRun() {
        seed(1, null);
        client.seedSources.add(new SeedSourceView(2, "LEGO_PROD_LEGACY", "2", "22222222-2222-2222-2222-222222222222"));

        assertThat(new SeedStatusReporter(client).report(RUN).rows()).hasSize(1);
        assertThat(new SeedStatusReporter(client).report(null).rows()).hasSize(2);
    }

    private void seed(int itemId, ListingView listing) {
        client.seedSources.add(new SeedSourceView(itemId, "LEGO_PROD_LEGACY", String.valueOf(itemId), RUN));
        if (listing != null) {
            client.listings.put(itemId, List.of(listing));
        }
    }

    private static SyncRequestView sync(long id, String type, String status) {
        return new SyncRequestView(id, type, status, null, null);
    }
}

package com.vattima.lego.inventory.seed;

import com.vattima.lego.inventory.seed.SeedServiceClient.SeedSourceView;
import com.vattima.lego.inventory.seed.SeedServiceClient.TeardownCheckView;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeedTeardownTest {
    private static final String RUN = "f80effb8-078e-466c-bf05-193d762fb49b";
    private static final String OTHER_RUN = "22222222-2222-2222-2222-222222222222";

    private final FakeSeedServiceClient client = new FakeSeedServiceClient();
    private final MutableClock clock = new MutableClock();
    private final List<Duration> sleeps = new ArrayList<>();
    private Runnable onSleep = () -> { };
    private final SeedTeardown teardown = new SeedTeardown(client, clock, duration -> {
        sleeps.add(duration);
        clock.advance(duration);
        onSleep.run();
    });

    @Test
    void itemsWithoutALotAreDeletedRightAwayAndOnlyTheChosenRunIsTouched() throws Exception {
        seed(960, "7", RUN);
        seed(961, "9", RUN);
        seed(990, "43", OTHER_RUN);

        SeedTeardown.Report report = teardown.run(RUN, options(false));

        assertThat(report.rows()).extracting(SeedTeardown.Row::outcome)
                .containsExactly(SeedTeardown.Outcome.DELETED, SeedTeardown.Outcome.DELETED);
        assertThat(client.prepared).containsExactly(960, 961);
        assertThat(client.deleted).containsExactly(960, 961);
        assertThat(client.seedSources).extracting(SeedSourceView::itemInventoryId).containsExactly(990);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void waitsForTheLotDeleteThenDeletesTheRows() throws Exception {
        seed(960, "7", RUN);
        client.teardown.put(960, check(960, 1, 0));
        // prepare queues the LISTING_DELETE; the ingress job deletes the lot before the next poll.
        client.onPrepare.put(960, current -> check(960, 1, 1));
        onSleep = () -> client.teardown.put(960, check(960, 0, 0));

        SeedTeardown.Report report = teardown.run(RUN, options(false));

        assertThat(report.rows()).singleElement().satisfies(row -> assertThat(row.outcome()).isEqualTo(SeedTeardown.Outcome.DELETED));
        assertThat(client.prepared).containsExactly(960);
        assertThat(client.deleted).containsExactly(960);
        assertThat(sleeps).hasSize(1);
    }

    @Test
    void anItemWithPhotosOrOrdersIsLeftAsItIs() throws Exception {
        seed(960, "7", RUN);
        client.teardown.put(960, new TeardownCheckView(960, true, 0, 0, Map.of("photos", 2)));

        SeedTeardown.Report report = teardown.run(RUN, options(false));

        assertThat(report.rows()).singleElement().satisfies(row -> {
            assertThat(row.outcome()).isEqualTo(SeedTeardown.Outcome.BLOCKED);
            assertThat(row.detail()).contains("photos");
        });
        assertThat(client.deleted).isEmpty();
    }

    @Test
    void aLotThatSurvivesRepeatedDeletesIsReportedStuck() throws Exception {
        seed(960, "7", RUN);
        client.teardown.put(960, check(960, 1, 0));
        // Each LISTING_DELETE is queued, then BLOCKED by ingress before the next poll: the lot stays.
        client.onPrepare.put(960, current -> check(960, 1, 1));
        onSleep = () -> client.teardown.put(960, check(960, 1, 0));

        SeedTeardown.Report report = teardown.run(RUN, options(false));

        assertThat(report.rows()).singleElement().satisfies(row -> {
            assertThat(row.outcome()).isEqualTo(SeedTeardown.Outcome.STUCK);
            assertThat(row.detail()).contains("LISTING_DELETE");
        });
        assertThat(client.prepared).hasSize(SeedTeardown.MAX_PREPARES);
        assertThat(client.deleted).isEmpty();
    }

    @Test
    void stopsWaitingAtTheDeadline() throws Exception {
        seed(960, "7", RUN);
        client.teardown.put(960, check(960, 1, 1));

        SeedTeardown.Report report = teardown.run(RUN, new SeedTeardown.Options(false, Duration.ofMinutes(5), Duration.ofMinutes(1)));

        assertThat(report.rows()).singleElement().satisfies(row -> {
            assertThat(row.outcome()).isEqualTo(SeedTeardown.Outcome.STUCK);
            assertThat(row.detail()).contains("deadline");
        });
        assertThat(sleeps).hasSize(5);
        assertThat(client.deleted).isEmpty();
    }

    @Test
    void dryRunOnlyReports() throws Exception {
        seed(960, "7", RUN);
        seed(961, "9", RUN);
        client.teardown.put(961, check(961, 1, 0));

        SeedTeardown.Report report = teardown.run(RUN, options(true));

        assertThat(report.rows()).extracting(SeedTeardown.Row::outcome)
                .containsExactly(SeedTeardown.Outcome.WOULD_DELETE, SeedTeardown.Outcome.WOULD_DELETE);
        assertThat(report.rows().get(1).detail()).contains("1 lot(s) to delete first");
        assertThat(client.prepared).isEmpty();
        assertThat(client.deleted).isEmpty();
    }

    @Test
    void allMeansEverySeededItem() throws Exception {
        seed(960, "7", RUN);
        seed(990, "43", OTHER_RUN);

        SeedTeardown.Report report = teardown.run(null, options(false));

        assertThat(report.counts()).containsEntry(SeedTeardown.Outcome.DELETED, 2L);
        assertThat(client.seedSources).isEmpty();
    }

    private void seed(int itemInventoryId, String sourceKey, String run) {
        client.seedSources.add(new SeedSourceView(itemInventoryId, "LEGO_PROD_LEGACY", sourceKey, run));
    }

    private static TeardownCheckView check(int itemInventoryId, int lots, int active) {
        return new TeardownCheckView(itemInventoryId, true, lots, active, Map.of());
    }

    private static SeedTeardown.Options options(boolean dryRun) {
        return new SeedTeardown.Options(dryRun, Duration.ofMinutes(60), Duration.ofMinutes(1));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-08T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

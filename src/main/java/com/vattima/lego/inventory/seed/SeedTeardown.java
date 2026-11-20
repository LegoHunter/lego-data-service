package com.vattima.lego.inventory.seed;

import com.vattima.lego.inventory.seed.SeedServiceClient.SeedSourceView;
import com.vattima.lego.inventory.seed.SeedServiceClient.SeedServiceException;
import com.vattima.lego.inventory.seed.SeedServiceClient.TeardownCheckView;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Removes seeded items from a lower environment (HOMELAB-40 phase 3): their BrickLink lots first,
 * through LISTING_DELETE sync requests the ingress marketplace sync job runs, then their local rows.
 * Items that never got a lot lose their local rows straight away.
 *
 * <p>Per item: prepare (cancel pending requests, queue lot deletes, end lot-less listings), wait
 * until the service reports no lot and no active request, then delete the rows. An item that keeps
 * blocking is prepared again, at most {@link #MAX_PREPARES} times. Photos, orders, sales, shipments
 * and eBay listings are never removed: such an item is reported BLOCKED and left as it is.
 */
public class SeedTeardown {
    static final int MAX_PREPARES = 3;

    public enum Outcome {
        /** Dry run: nothing blocks it; teardown would delete it (after its lots, if any). */
        WOULD_DELETE,
        DELETED,
        /** Something teardown never removes; the item is left as it is. */
        BLOCKED,
        /** Its lot or an active sync request was still there at the deadline or after the last prepare. */
        STUCK,
        /** The service no longer has it as a seeded item. */
        ALREADY_GONE
    }

    public record Options(boolean dryRun, Duration maxWait, Duration poll) {
    }

    public record Row(int itemInventoryId, String sourceKey, Outcome outcome, String detail) {
    }

    public record Report(List<Row> rows, Map<Outcome, Long> counts) {
    }

    /** Waits between polls; tests replace it. */
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final SeedServiceClient client;
    private final Clock clock;
    private final Sleeper sleeper;

    public SeedTeardown(SeedServiceClient client, Clock clock, Sleeper sleeper) {
        this.client = client;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /** {@code seedRunId} null means every seeded item in the environment. */
    public Report run(String seedRunId, Options options) throws InterruptedException {
        List<SeedSourceView> sources = client.findSeedSources(null, seedRunId).stream()
                .sorted(Comparator.comparingInt(SeedSourceView::itemInventoryId))
                .toList();
        Map<Integer, Row> rows = new LinkedHashMap<>();
        Map<Integer, String> sourceKeys = new LinkedHashMap<>();
        sources.forEach(source -> sourceKeys.put(source.itemInventoryId(), source.sourceKey()));

        if (options.dryRun()) {
            for (SeedSourceView source : sources) {
                TeardownCheckView check = client.teardownCheck(source.itemInventoryId());
                rows.put(source.itemInventoryId(), dryRunRow(source, check));
            }
            return report(rows);
        }

        Map<Integer, Integer> prepares = new LinkedHashMap<>();
        List<Integer> pending = new ArrayList<>();
        for (SeedSourceView source : sources) {
            TeardownCheckView check = prepare(source.itemInventoryId(), prepares);
            if (check == null) {
                rows.put(source.itemInventoryId(), row(source.itemInventoryId(), sourceKeys, Outcome.ALREADY_GONE, ""));
            } else if (!check.otherBlockers().isEmpty()) {
                rows.put(source.itemInventoryId(), row(source.itemInventoryId(), sourceKeys, Outcome.BLOCKED, check.otherBlockers().toString()));
            } else {
                pending.add(source.itemInventoryId());
            }
        }

        Instant deadline = clock.instant().plus(options.maxWait());
        while (!pending.isEmpty()) {
            List<Integer> stillPending = new ArrayList<>();
            for (int itemInventoryId : pending) {
                TeardownCheckView check = check(itemInventoryId);
                if (check == null) {
                    rows.put(itemInventoryId, row(itemInventoryId, sourceKeys, Outcome.ALREADY_GONE, ""));
                } else if (check.clear()) {
                    Map<String, Integer> deleted = client.deleteSeededItem(itemInventoryId);
                    rows.put(itemInventoryId, row(itemInventoryId, sourceKeys, Outcome.DELETED, deleted.size() + " tables"));
                } else if (!check.otherBlockers().isEmpty()) {
                    rows.put(itemInventoryId, row(itemInventoryId, sourceKeys, Outcome.BLOCKED, check.otherBlockers().toString()));
                } else {
                    stillPending.add(itemInventoryId);
                }
            }
            pending = stillPending;
            if (pending.isEmpty()) {
                break;
            }
            if (!clock.instant().isBefore(deadline)) {
                for (int itemInventoryId : pending) {
                    rows.put(itemInventoryId, row(itemInventoryId, sourceKeys, Outcome.STUCK, waitingFor(check(itemInventoryId))
                            + " at the deadline; run teardown again later"));
                }
                break;
            }
            sleeper.sleep(options.poll());
            // A lot whose LISTING_DELETE was blocked or failed, or a request the pricing plane queued
            // meanwhile: prepare again, a bounded number of times.
            List<Integer> retry = new ArrayList<>();
            for (int itemInventoryId : pending) {
                TeardownCheckView check = check(itemInventoryId);
                if (check != null && check.bricklinkLots() > 0 && check.activeSyncRequests() == 0) {
                    if (prepares.getOrDefault(itemInventoryId, 0) >= MAX_PREPARES) {
                        rows.put(itemInventoryId, row(itemInventoryId, sourceKeys, Outcome.STUCK,
                                "its lot survived " + MAX_PREPARES + " LISTING_DELETE requests; check the request's error"));
                        continue;
                    }
                    prepare(itemInventoryId, prepares);
                } else if (check != null && check.activeSyncRequests() > 0 && check.bricklinkLots() == 0
                        && prepares.getOrDefault(itemInventoryId, 0) < MAX_PREPARES) {
                    // A PENDING request (for example a new LISTING_CREATE) that prepare can cancel.
                    prepare(itemInventoryId, prepares);
                }
                retry.add(itemInventoryId);
            }
            pending = retry;
        }

        for (SeedSourceView source : sources) {
            rows.putIfAbsent(source.itemInventoryId(), row(source.itemInventoryId(), sourceKeys, Outcome.STUCK, "not finished"));
        }
        return report(rows);
    }

    private TeardownCheckView prepare(int itemInventoryId, Map<Integer, Integer> prepares) {
        prepares.merge(itemInventoryId, 1, Integer::sum);
        try {
            return client.prepareTeardown(itemInventoryId);
        } catch (SeedServiceException e) {
            if (e.status() == 400 && !isSeeded(itemInventoryId)) {
                return null;
            }
            throw e;
        }
    }

    /** The current check, or null when the item is no longer a seeded item (already deleted). */
    private TeardownCheckView check(int itemInventoryId) {
        TeardownCheckView check = client.teardownCheck(itemInventoryId);
        return check.seeded() ? check : null;
    }

    private boolean isSeeded(int itemInventoryId) {
        return client.teardownCheck(itemInventoryId).seeded();
    }

    private static Row dryRunRow(SeedSourceView source, TeardownCheckView check) {
        if (!check.seeded()) {
            return new Row(source.itemInventoryId(), source.sourceKey(), Outcome.ALREADY_GONE, "");
        }
        if (!check.otherBlockers().isEmpty()) {
            return new Row(source.itemInventoryId(), source.sourceKey(), Outcome.BLOCKED, check.otherBlockers().toString());
        }
        String detail = check.bricklinkLots() == 0 ? "no lot" : check.bricklinkLots() + " lot(s) to delete first";
        if (check.activeSyncRequests() > 0) {
            detail += ", " + check.activeSyncRequests() + " active sync request(s) to cancel or finish";
        }
        return new Row(source.itemInventoryId(), source.sourceKey(), Outcome.WOULD_DELETE, detail);
    }

    private static String waitingFor(TeardownCheckView check) {
        if (check == null) {
            return "gone";
        }
        return check.bricklinkLots() + " lot(s) and " + check.activeSyncRequests() + " active sync request(s) left";
    }

    private static Row row(int itemInventoryId, Map<Integer, String> sourceKeys, Outcome outcome, String detail) {
        return new Row(itemInventoryId, sourceKeys.get(itemInventoryId), outcome, detail);
    }

    private static Report report(Map<Integer, Row> rows) {
        Map<Outcome, Long> counts = new EnumMap<>(Outcome.class);
        for (Outcome outcome : Outcome.values()) {
            counts.put(outcome, 0L);
        }
        rows.values().forEach(row -> counts.merge(row.outcome(), 1L, Long::sum));
        return new Report(List.copyOf(rows.values()), counts);
    }
}

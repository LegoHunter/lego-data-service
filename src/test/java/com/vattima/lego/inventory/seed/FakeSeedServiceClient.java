package com.vattima.lego.inventory.seed;

import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.ItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftCreateRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An in-memory stand-in for lego-data-service that keeps seed sources, listings and sync requests,
 * and enforces the unique (source system, source key) rule like the real table.
 */
class FakeSeedServiceClient implements SeedServiceClient {

    final List<SeedSourceView> seedSources = new ArrayList<>();
    final Map<Integer, List<ListingView>> listings = new HashMap<>();
    final Map<Integer, List<SyncRequestView>> syncRequests = new HashMap<>();
    final Set<String> missingCatalogItems = new HashSet<>();
    final List<AddItemInventoryRequest> acquisitions = new ArrayList<>();
    final List<MarketplaceListingDraftCreateRequest> drafts = new ArrayList<>();
    int failDraftsAfter = Integer.MAX_VALUE;
    /** Teardown state per seeded item; an item missing here is clear (no lot, nothing active). */
    final Map<Integer, TeardownCheckView> teardown = new HashMap<>();
    /** What prepare does to an item's teardown state (default: nothing changes). */
    final Map<Integer, java.util.function.UnaryOperator<TeardownCheckView>> onPrepare = new HashMap<>();
    final List<Integer> prepared = new ArrayList<>();
    final List<Integer> deleted = new ArrayList<>();

    private int nextItemId = 1000;
    private int nextListingId = 5000;

    @Override
    public List<SeedSourceView> findSeedSources(String sourceSystem, String seedRunId) {
        return seedSources.stream()
                .filter(view -> sourceSystem == null || sourceSystem.equals(view.sourceSystem()))
                .filter(view -> seedRunId == null || seedRunId.equals(view.seedRunId()))
                .toList();
    }

    @Override
    public boolean catalogItemExists(String itemNumber) {
        return !missingCatalogItems.contains(itemNumber);
    }

    @Override
    public void addItemInventory(AddItemInventoryRequest request) {
        for (ItemInventoryRequest item : request.getInventoryItems()) {
            boolean duplicate = seedSources.stream().anyMatch(view ->
                    view.sourceSystem().equals(item.getSeedSource().getSourceSystem())
                            && view.sourceKey().equals(item.getSeedSource().getSourceKey()));
            if (duplicate) {
                throw new SeedServiceException("already seeded", 400);
            }
        }
        acquisitions.add(request);
        for (ItemInventoryRequest item : request.getInventoryItems()) {
            seedSources.add(new SeedSourceView(nextItemId++, item.getSeedSource().getSourceSystem(),
                    item.getSeedSource().getSourceKey(), item.getSeedSource().getSeedRunId()));
        }
    }

    @Override
    public List<ListingView> findListings(int itemInventoryId) {
        return listings.getOrDefault(itemInventoryId, List.of());
    }

    @Override
    public int createDraft(MarketplaceListingDraftCreateRequest request) {
        if (drafts.size() >= failDraftsAfter) {
            throw new SeedServiceException("service unavailable", 503);
        }
        drafts.add(request);
        int id = nextListingId++;
        listings.computeIfAbsent(request.getItemInventoryId(), key -> new ArrayList<>())
                .add(new ListingView(id, 2, "DRAFT", null, false, null, "C", "sandbox", "NOT_VERIFIED"));
        return id;
    }

    @Override
    public List<SyncRequestView> findSyncRequests(int marketplaceListingId) {
        return syncRequests.getOrDefault(marketplaceListingId, List.of());
    }

    @Override
    public TeardownCheckView teardownCheck(int itemInventoryId) {
        if (!isSeeded(itemInventoryId)) {
            return new TeardownCheckView(itemInventoryId, false, 0, 0, Map.of());
        }
        return teardown.getOrDefault(itemInventoryId, new TeardownCheckView(itemInventoryId, true, 0, 0, Map.of()));
    }

    @Override
    public TeardownCheckView prepareTeardown(int itemInventoryId) {
        if (!isSeeded(itemInventoryId)) {
            throw new SeedServiceException("not a seeded item", 400);
        }
        prepared.add(itemInventoryId);
        java.util.function.UnaryOperator<TeardownCheckView> change = onPrepare.get(itemInventoryId);
        if (change != null) {
            teardown.put(itemInventoryId, change.apply(teardownCheck(itemInventoryId)));
        }
        return teardownCheck(itemInventoryId);
    }

    @Override
    public Map<String, Integer> deleteSeededItem(int itemInventoryId) {
        if (!teardownCheck(itemInventoryId).clear()) {
            throw new SeedServiceException("cannot be torn down", 400);
        }
        seedSources.removeIf(view -> view.itemInventoryId() == itemInventoryId);
        deleted.add(itemInventoryId);
        return Map.of("item_inventory", 1, "item_inventory_seed_source", 1);
    }

    private boolean isSeeded(int itemInventoryId) {
        return seedSources.stream().anyMatch(view -> view.itemInventoryId() == itemInventoryId);
    }
}

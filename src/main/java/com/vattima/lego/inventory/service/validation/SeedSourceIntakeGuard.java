package com.vattima.lego.inventory.service.validation;

import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.ItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.SeedSourceRequest;
import com.vattima.lego.inventory.service.exception.ValidationException;
import io.legohunter.data.dao.ItemInventorySeedSourceDao;
import io.legohunter.lego.data.environment.LegoEnvironmentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Checks the seeding fields of an intake request (HOMELAB-40) before anything is written.
 *
 * <ul>
 *   <li>PROD never accepts seeded items: seeding copies PROD data into lower environments only.</li>
 *   <li>A source item can be seeded once per environment, so a re-run cannot duplicate it. The
 *       unique key on {@code item_inventory_seed_source} is the backstop for concurrent runs.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SeedSourceIntakeGuard {
    private final LegoEnvironmentProperties environment;
    private final ItemInventorySeedSourceDao itemInventorySeedSourceDao;

    public void validate(AddItemInventoryRequest request) {
        List<SeedSourceRequest> seedSources = request.getInventoryItems() == null ? List.of() : request.getInventoryItems().stream()
                .filter(Objects::nonNull)
                .map(ItemInventoryRequest::getSeedSource)
                .filter(Objects::nonNull)
                .toList();
        if (seedSources.isEmpty()) {
            return;
        }
        if (environment.isProduction()) {
            throw new ValidationException("Seeded items are refused in PROD: seedSource is only allowed in lower environments");
        }
        Set<String> seen = new HashSet<>();
        for (SeedSourceRequest seedSource : seedSources) {
            String key = seedSource.getSourceSystem() + ":" + seedSource.getSourceKey();
            if (!seen.add(key)) {
                throw new ValidationException("seedSource " + key + " appears more than once in the request");
            }
            itemInventorySeedSourceDao.findBySourceSystemAndSourceKey(seedSource.getSourceSystem(), seedSource.getSourceKey())
                    .ifPresent(existing -> {
                        throw new ValidationException("seedSource " + key + " is already seeded as item " + existing.getItemInventoryId());
                    });
        }
    }
}

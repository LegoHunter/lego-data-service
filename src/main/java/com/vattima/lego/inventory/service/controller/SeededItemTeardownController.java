package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.api.SeededItemTeardownService;
import com.vattima.lego.inventory.service.dto.SeededItemTeardownPrepareResponse;
import com.vattima.lego.inventory.service.logging.LogExecution;
import io.legohunter.data.dto.SeededItemTeardownCheck;
import io.legohunter.data.dto.SeededItemTeardownResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teardown of lower-environment seeded items (HOMELAB-40), used by the seeding CLI's
 * {@code teardown} command. Every endpoint is refused in PROD and for items that were not seeded.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class SeededItemTeardownController {
    private final SeededItemTeardownService seededItemTeardownService;

    @GetMapping("/{itemInventoryId}/seed-teardown")
    public ResponseEntity<SeededItemTeardownCheck> check(@PathVariable Integer itemInventoryId) {
        return ResponseEntity.ok(seededItemTeardownService.check(itemInventoryId));
    }

    @PostMapping("/{itemInventoryId}/seed-teardown/prepare")
    @LogExecution
    public ResponseEntity<SeededItemTeardownPrepareResponse> prepare(@PathVariable Integer itemInventoryId) {
        return ResponseEntity.ok(seededItemTeardownService.prepare(itemInventoryId));
    }

    @DeleteMapping("/{itemInventoryId}/seed-teardown")
    @LogExecution
    public ResponseEntity<SeededItemTeardownResult> deleteLocalRows(@PathVariable Integer itemInventoryId) {
        return ResponseEntity.ok(seededItemTeardownService.deleteLocalRows(itemInventoryId));
    }
}

package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.api.MarketplaceListingService;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftCreateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftResponse;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftUpdateRequest;
import com.vattima.lego.inventory.service.logging.LogExecution;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/marketplace-listings")
@RequiredArgsConstructor
@Validated
public class MarketplaceListingController {
    private final MarketplaceListingService marketplaceListingService;

    @PostMapping
    @LogExecution
    public ResponseEntity<MarketplaceListingDraftResponse> createDraft(
            @Valid @RequestBody MarketplaceListingDraftCreateRequest request
    ) {
        return ResponseEntity.ok(marketplaceListingService.createDraft(request));
    }

    @GetMapping("/{marketplaceListingId}")
    @LogExecution
    public ResponseEntity<MarketplaceListingDraftResponse> findByMarketplaceListingId(
            @PathVariable Integer marketplaceListingId
    ) {
        return ResponseEntity.ok(marketplaceListingService.findByMarketplaceListingId(marketplaceListingId));
    }

    @PatchMapping("/{marketplaceListingId}")
    @LogExecution
    public ResponseEntity<MarketplaceListingDraftResponse> updateDraft(
            @PathVariable Integer marketplaceListingId,
            @Valid @RequestBody MarketplaceListingDraftUpdateRequest request
    ) {
        return ResponseEntity.ok(marketplaceListingService.updateDraft(marketplaceListingId, request));
    }

    @DeleteMapping("/{marketplaceListingId}")
    @LogExecution
    public ResponseEntity<MarketplaceListingDraftResponse> removeDraft(@PathVariable Integer marketplaceListingId) {
        return ResponseEntity.ok(marketplaceListingService.removeDraft(marketplaceListingId));
    }
}

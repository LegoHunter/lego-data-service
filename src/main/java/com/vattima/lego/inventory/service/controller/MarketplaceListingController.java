package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.api.MarketplaceListingService;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftCreateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftResponse;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftUpdateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingSyncRequestCancelRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingSyncRequestCreateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingSyncRequestPreviewResponse;
import com.vattima.lego.inventory.service.logging.LogExecution;
import io.legohunter.data.dto.MarketplaceListingSyncRequest;
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

import java.util.Set;

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

    @GetMapping("/{marketplaceListingId}/sync-request-preview")
    @LogExecution
    public ResponseEntity<MarketplaceListingSyncRequestPreviewResponse> previewListingCreateSyncRequest(
            @PathVariable Integer marketplaceListingId
    ) {
        return ResponseEntity.ok(marketplaceListingService.previewListingCreateSyncRequest(marketplaceListingId));
    }

    @PostMapping("/{marketplaceListingId}/sync-requests")
    @LogExecution
    public ResponseEntity<MarketplaceListingSyncRequestPreviewResponse> createListingCreateSyncRequest(
            @PathVariable Integer marketplaceListingId,
            @Valid @RequestBody(required = false) MarketplaceListingSyncRequestCreateRequest request
    ) {
        return ResponseEntity.ok(marketplaceListingService.createListingCreateSyncRequest(marketplaceListingId, request));
    }

    @GetMapping("/{marketplaceListingId}/sync-requests")
    @LogExecution
    public ResponseEntity<Set<MarketplaceListingSyncRequest>> findSyncRequestsByMarketplaceListingId(
            @PathVariable Integer marketplaceListingId
    ) {
        return ResponseEntity.ok(marketplaceListingService.findSyncRequestsByMarketplaceListingId(marketplaceListingId));
    }

    @GetMapping("/sync-requests/{marketplaceListingSyncRequestId}")
    @LogExecution
    public ResponseEntity<MarketplaceListingSyncRequest> findSyncRequestById(
            @PathVariable Long marketplaceListingSyncRequestId
    ) {
        return ResponseEntity.ok(marketplaceListingService.findSyncRequestById(marketplaceListingSyncRequestId));
    }

    @PatchMapping("/sync-requests/{marketplaceListingSyncRequestId}/cancel")
    @LogExecution
    public ResponseEntity<MarketplaceListingSyncRequest> cancelSyncRequest(
            @PathVariable Long marketplaceListingSyncRequestId,
            @Valid @RequestBody(required = false) MarketplaceListingSyncRequestCancelRequest request
    ) {
        return ResponseEntity.ok(marketplaceListingService.cancelSyncRequest(marketplaceListingSyncRequestId, request));
    }
}

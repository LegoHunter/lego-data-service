package com.vattima.lego.inventory.service.dto;

import io.legohunter.data.dto.MarketplaceListingSyncRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceListingSyncRequestPreviewResponse {
    private Integer marketplaceListingId;
    private String syncRequestTypeCode;
    private boolean readyForSyncRequest;
    private MarketplaceListingSyncRequest syncRequest;
    private MarketplaceListingSyncRequest syncRequestCandidate;
    private MarketplaceListingReadinessResponse readiness;
    private Set<MarketplaceListingSyncRequest> activeSyncRequests;
    private List<MarketplaceListingReadinessIssue> blockers;
    private List<MarketplaceListingReadinessIssue> warnings;
}

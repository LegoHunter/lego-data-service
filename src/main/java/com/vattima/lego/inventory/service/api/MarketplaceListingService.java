package com.vattima.lego.inventory.service.api;

import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftCreateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftResponse;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftUpdateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingReadinessResponse;
import com.vattima.lego.inventory.service.dto.MarketplaceListingSyncRequestCancelRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingSyncRequestCreateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingSyncRequestPreviewResponse;
import io.legohunter.data.dto.MarketplaceListingSyncRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@Validated
public interface MarketplaceListingService {
    MarketplaceListingDraftResponse createDraft(@Valid MarketplaceListingDraftCreateRequest request);

    MarketplaceListingDraftResponse findByMarketplaceListingId(Integer marketplaceListingId);

    Set<MarketplaceListingDraftResponse> findByItemInventoryId(Integer itemInventoryId);

    MarketplaceListingDraftResponse updateDraft(Integer marketplaceListingId, @Valid MarketplaceListingDraftUpdateRequest request);

    MarketplaceListingDraftResponse removeDraft(Integer marketplaceListingId);

    MarketplaceListingReadinessResponse evaluateReadiness(Integer itemInventoryId, String marketplaceCode);

    MarketplaceListingSyncRequestPreviewResponse previewListingCreateSyncRequest(Integer marketplaceListingId);

    MarketplaceListingSyncRequestPreviewResponse createListingCreateSyncRequest(
            Integer marketplaceListingId,
            @Valid MarketplaceListingSyncRequestCreateRequest request
    );

    Set<MarketplaceListingSyncRequest> findSyncRequestsByMarketplaceListingId(Integer marketplaceListingId);

    MarketplaceListingSyncRequest findSyncRequestById(Long marketplaceListingSyncRequestId);

    MarketplaceListingSyncRequest cancelSyncRequest(
            Long marketplaceListingSyncRequestId,
            @Valid MarketplaceListingSyncRequestCancelRequest request
    );
}

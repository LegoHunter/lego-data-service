package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.api.MarketplaceListingService;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftCreateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftResponse;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftUpdateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingSyncRequestCancelRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingSyncRequestCreateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingSyncRequestPreviewResponse;
import io.legohunter.data.dto.MarketplaceListingSyncRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceListingControllerTest {
    @Mock
    private MarketplaceListingService marketplaceListingService;

    @Test
    void createDraftDelegatesToService() {
        MarketplaceListingDraftCreateRequest request = MarketplaceListingDraftCreateRequest.builder().build();
        MarketplaceListingDraftResponse serviceResponse = MarketplaceListingDraftResponse.builder().build();
        when(marketplaceListingService.createDraft(request)).thenReturn(serviceResponse);

        ResponseEntity<MarketplaceListingDraftResponse> response = controller().createDraft(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(serviceResponse);
        verify(marketplaceListingService).createDraft(request);
    }

    @Test
    void findByMarketplaceListingIdDelegatesToService() {
        MarketplaceListingDraftResponse serviceResponse = MarketplaceListingDraftResponse.builder().build();
        when(marketplaceListingService.findByMarketplaceListingId(101)).thenReturn(serviceResponse);

        ResponseEntity<MarketplaceListingDraftResponse> response = controller().findByMarketplaceListingId(101);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(serviceResponse);
        verify(marketplaceListingService).findByMarketplaceListingId(101);
    }

    @Test
    void updateDraftDelegatesToService() {
        MarketplaceListingDraftUpdateRequest request = MarketplaceListingDraftUpdateRequest.builder().build();
        MarketplaceListingDraftResponse serviceResponse = MarketplaceListingDraftResponse.builder().build();
        when(marketplaceListingService.updateDraft(101, request)).thenReturn(serviceResponse);

        ResponseEntity<MarketplaceListingDraftResponse> response = controller().updateDraft(101, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(serviceResponse);
        verify(marketplaceListingService).updateDraft(101, request);
    }

    @Test
    void removeDraftDelegatesToService() {
        MarketplaceListingDraftResponse serviceResponse = MarketplaceListingDraftResponse.builder().build();
        when(marketplaceListingService.removeDraft(101)).thenReturn(serviceResponse);

        ResponseEntity<MarketplaceListingDraftResponse> response = controller().removeDraft(101);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(serviceResponse);
        verify(marketplaceListingService).removeDraft(101);
    }

    @Test
    void previewListingCreateSyncRequestDelegatesToService() {
        MarketplaceListingSyncRequestPreviewResponse serviceResponse = MarketplaceListingSyncRequestPreviewResponse.builder().build();
        when(marketplaceListingService.previewListingCreateSyncRequest(101)).thenReturn(serviceResponse);

        ResponseEntity<MarketplaceListingSyncRequestPreviewResponse> response = controller().previewListingCreateSyncRequest(101);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(serviceResponse);
        verify(marketplaceListingService).previewListingCreateSyncRequest(101);
    }

    @Test
    void createListingCreateSyncRequestDelegatesToService() {
        MarketplaceListingSyncRequestCreateRequest request = MarketplaceListingSyncRequestCreateRequest.builder().build();
        MarketplaceListingSyncRequestPreviewResponse serviceResponse = MarketplaceListingSyncRequestPreviewResponse.builder().build();
        when(marketplaceListingService.createListingCreateSyncRequest(101, request)).thenReturn(serviceResponse);

        ResponseEntity<MarketplaceListingSyncRequestPreviewResponse> response = controller().createListingCreateSyncRequest(101, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(serviceResponse);
        verify(marketplaceListingService).createListingCreateSyncRequest(101, request);
    }

    @Test
    void findSyncRequestsByMarketplaceListingIdDelegatesToService() {
        MarketplaceListingSyncRequest serviceResponse = MarketplaceListingSyncRequest.builder().build();
        when(marketplaceListingService.findSyncRequestsByMarketplaceListingId(101)).thenReturn(Set.of(serviceResponse));

        ResponseEntity<Set<MarketplaceListingSyncRequest>> response = controller().findSyncRequestsByMarketplaceListingId(101);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsExactly(serviceResponse);
        verify(marketplaceListingService).findSyncRequestsByMarketplaceListingId(101);
    }

    @Test
    void findSyncRequestByIdDelegatesToService() {
        MarketplaceListingSyncRequest serviceResponse = MarketplaceListingSyncRequest.builder().build();
        when(marketplaceListingService.findSyncRequestById(900L)).thenReturn(serviceResponse);

        ResponseEntity<MarketplaceListingSyncRequest> response = controller().findSyncRequestById(900L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(serviceResponse);
        verify(marketplaceListingService).findSyncRequestById(900L);
    }

    @Test
    void cancelSyncRequestDelegatesToService() {
        MarketplaceListingSyncRequestCancelRequest request = MarketplaceListingSyncRequestCancelRequest.builder().build();
        MarketplaceListingSyncRequest serviceResponse = MarketplaceListingSyncRequest.builder().build();
        when(marketplaceListingService.cancelSyncRequest(900L, request)).thenReturn(serviceResponse);

        ResponseEntity<MarketplaceListingSyncRequest> response = controller().cancelSyncRequest(900L, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(serviceResponse);
        verify(marketplaceListingService).cancelSyncRequest(900L, request);
    }

    private MarketplaceListingController controller() {
        return new MarketplaceListingController(marketplaceListingService);
    }
}

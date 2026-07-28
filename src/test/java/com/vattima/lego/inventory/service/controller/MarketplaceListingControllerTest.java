package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.api.MarketplaceListingService;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftCreateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftResponse;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

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

    private MarketplaceListingController controller() {
        return new MarketplaceListingController(marketplaceListingService);
    }
}

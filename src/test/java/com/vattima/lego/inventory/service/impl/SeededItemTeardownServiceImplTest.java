package com.vattima.lego.inventory.service.impl;

import com.vattima.lego.inventory.service.config.MarketplaceListingDraftProperties;
import com.vattima.lego.inventory.service.dto.SeededItemTeardownPrepareResponse;
import com.vattima.lego.inventory.service.exception.ValidationException;
import io.legohunter.data.dao.BricklinkMarketplaceListingDao;
import io.legohunter.data.dao.MarketplaceListingDao;
import io.legohunter.data.dao.MarketplaceListingSyncRequestDao;
import io.legohunter.data.dao.SeededItemTeardownDao;
import io.legohunter.data.dto.BricklinkMarketplaceListing;
import io.legohunter.data.dto.MarketplaceListing;
import io.legohunter.data.dto.MarketplaceListingSyncRequest;
import io.legohunter.data.dto.SeededItemTeardownCheck;
import io.legohunter.data.dto.SeededItemTeardownResult;
import io.legohunter.lego.data.environment.LegoEnvironmentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SeededItemTeardownServiceImplTest {
    private final LegoEnvironmentProperties environment = new LegoEnvironmentProperties();
    private final MarketplaceListingDraftProperties listingProperties = new MarketplaceListingDraftProperties();
    private final SeededItemTeardownDao teardownDao = mock(SeededItemTeardownDao.class);
    private final MarketplaceListingDao listingDao = mock(MarketplaceListingDao.class);
    private final BricklinkMarketplaceListingDao bricklinkListingDao = mock(BricklinkMarketplaceListingDao.class);
    private final MarketplaceListingSyncRequestDao syncRequestDao = mock(MarketplaceListingSyncRequestDao.class);
    private final SeededItemTeardownServiceImpl service = new SeededItemTeardownServiceImpl(
            environment, listingProperties, teardownDao, listingDao, bricklinkListingDao, syncRequestDao);

    @BeforeEach
    void sandbox() {
        environment.setCode("sandbox");
        listingProperties.setEnvironmentCode("sandbox");
        listingProperties.setNonProdBricklinkStockroomId("C");
        when(syncRequestDao.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(syncRequestDao.insert(any())).thenAnswer(invocation -> {
            MarketplaceListingSyncRequest request = invocation.getArgument(0);
            request.setMarketplaceListingSyncRequestId(777L);
            return request;
        });
    }

    @Test
    void everyOperationIsRefusedInProd() {
        environment.setCode("prod");
        environment.setProduction(true);

        assertThatThrownBy(() -> service.check(960)).isInstanceOf(ValidationException.class).hasMessageContaining("PROD");
        assertThatThrownBy(() -> service.prepare(960)).isInstanceOf(ValidationException.class).hasMessageContaining("PROD");
        assertThatThrownBy(() -> service.deleteLocalRows(960)).isInstanceOf(ValidationException.class).hasMessageContaining("PROD");
        verifyNoInteractions(teardownDao, listingDao, syncRequestDao);
    }

    @Test
    void prepareRefusesAnItemThatWasNotSeeded() {
        when(teardownDao.check(960)).thenReturn(SeededItemTeardownCheck.builder().itemInventoryId(960).seeded(0).build());

        assertThatThrownBy(() -> service.prepare(960)).isInstanceOf(ValidationException.class).hasMessageContaining("not a seeded item");
        verifyNoInteractions(listingDao, syncRequestDao);
    }

    @Test
    void prepareCancelsPendingWorkQueuesALotDeleteAndEndsLotlessDrafts() {
        when(teardownDao.check(960)).thenReturn(SeededItemTeardownCheck.builder().itemInventoryId(960).seeded(1).build());
        MarketplaceListing withLot = MarketplaceListing.builder().marketplaceListingId(961).itemInventoryId(960)
                .listingExternalServiceId(2).listingStatusCode("ACTIVE").currencyCode("USD").build();
        MarketplaceListing draft = MarketplaceListing.builder().marketplaceListingId(962).itemInventoryId(960)
                .listingExternalServiceId(2).listingStatusCode("DRAFT").build();
        when(listingDao.findByItemInventoryId(960)).thenReturn(Set.of(withLot, draft));
        MarketplaceListingSyncRequest pendingPrice = request(10L, "PRICE_UPDATE", "PENDING");
        MarketplaceListingSyncRequest claimedCreate = request(11L, "LISTING_CREATE", "CLAIMED");
        MarketplaceListingSyncRequest pendingCreate = request(12L, "LISTING_CREATE", "PENDING");
        when(syncRequestDao.findByMarketplaceListingId(961)).thenReturn(Set.of(pendingPrice));
        when(syncRequestDao.findByMarketplaceListingId(962)).thenReturn(Set.of(claimedCreate, pendingCreate));
        when(bricklinkListingDao.findByMarketplaceListingId(961))
                .thenReturn(Optional.of(BricklinkMarketplaceListing.builder().marketplaceListingId(961).bricklinkInventoryId(512345).build()));
        when(bricklinkListingDao.findByMarketplaceListingId(962))
                .thenReturn(Optional.of(BricklinkMarketplaceListing.builder().marketplaceListingId(962).build()));

        SeededItemTeardownPrepareResponse response = service.prepare(960);

        assertThat(response.getCancelledSyncRequestIds()).containsExactlyInAnyOrder(10L, 12L);
        assertThat(response.getInProgressSyncRequestIds()).containsExactly(11L);
        assertThat(response.getLotDeleteSyncRequestIds()).containsExactly(777L);
        assertThat(response.getEndedMarketplaceListingIds()).containsExactly(962);
        assertThat(pendingPrice.getSyncRequestStatusCode()).isEqualTo("CANCELLED");
        assertThat(claimedCreate.getSyncRequestStatusCode()).isEqualTo("CLAIMED");

        ArgumentCaptor<MarketplaceListingSyncRequest> inserted = ArgumentCaptor.forClass(MarketplaceListingSyncRequest.class);
        verify(syncRequestDao).insert(inserted.capture());
        assertThat(inserted.getValue().getSyncRequestTypeCode()).isEqualTo("LISTING_DELETE");
        assertThat(inserted.getValue().getSyncRequestStatusCode()).isEqualTo("PENDING");
        assertThat(inserted.getValue().getMarketplaceListingId()).isEqualTo(961);
        assertThat(inserted.getValue().getRemoteInventoryId()).isEqualTo("512345");
        assertThat(inserted.getValue().getRemoteVisibilityContainerId()).isEqualTo("C");
        assertThat(inserted.getValue().getRemoteIsPubliclyAvailable()).isFalse();
        assertThat(inserted.getValue().getEnvironmentCode()).isEqualTo("sandbox");
        assertThat(draft.getListingStatusCode()).isEqualTo("ENDED");
        assertThat(withLot.getListingStatusCode()).isEqualTo("ACTIVE");
    }

    @Test
    void prepareDoesNotQueueASecondLotDelete() {
        when(teardownDao.check(960)).thenReturn(SeededItemTeardownCheck.builder().itemInventoryId(960).seeded(1).build());
        MarketplaceListing withLot = MarketplaceListing.builder().marketplaceListingId(961).itemInventoryId(960)
                .listingStatusCode("ACTIVE").build();
        when(listingDao.findByItemInventoryId(960)).thenReturn(Set.of(withLot));
        when(syncRequestDao.findByMarketplaceListingId(961)).thenReturn(Set.of(request(20L, "LISTING_DELETE", "PENDING")));
        when(bricklinkListingDao.findByMarketplaceListingId(961))
                .thenReturn(Optional.of(BricklinkMarketplaceListing.builder().marketplaceListingId(961).bricklinkInventoryId(512345).build()));

        SeededItemTeardownPrepareResponse response = service.prepare(960);

        assertThat(response.getLotDeleteSyncRequestIds()).containsExactly(20L);
        assertThat(response.getCancelledSyncRequestIds()).isEmpty();
        verify(syncRequestDao, never()).insert(any());
    }

    @Test
    void deleteLocalRowsReportsWhatWasDeletedAndTurnsBlockersIntoValidationErrors() {
        when(teardownDao.teardown(960)).thenReturn(SeededItemTeardownResult.builder()
                .itemInventoryId(960).deletedRows(Map.of("item_inventory", 1)).build());
        when(teardownDao.teardown(961)).thenThrow(new IllegalStateException("Item inventory 961 cannot be torn down: 1 BrickLink lot(s)"));

        assertThat(service.deleteLocalRows(960).getDeletedRows()).containsEntry("item_inventory", 1);
        assertThatThrownBy(() -> service.deleteLocalRows(961))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("BrickLink lot");
    }

    private MarketplaceListingSyncRequest request(Long id, String type, String status) {
        return MarketplaceListingSyncRequest.builder()
                .marketplaceListingSyncRequestId(id)
                .syncRequestTypeCode(type)
                .syncRequestStatusCode(status)
                .build();
    }
}

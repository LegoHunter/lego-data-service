package com.vattima.lego.inventory.service.impl;

import com.vattima.lego.inventory.service.config.MarketplaceListingDraftProperties;
import com.vattima.lego.inventory.service.dto.BricklinkListingDraftRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftCreateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftResponse;
import com.vattima.lego.inventory.service.dto.MarketplaceListingDraftUpdateRequest;
import com.vattima.lego.inventory.service.dto.MarketplaceListingReadinessResponse;
import com.vattima.lego.inventory.service.exception.NotFoundException;
import com.vattima.lego.inventory.service.exception.ValidationException;
import com.vattima.lego.inventory.service.validation.MarketplaceListingDraftBusinessValidator;
import io.legohunter.data.dao.BricklinkMarketplaceListingDao;
import io.legohunter.data.dao.ExternalServiceDao;
import io.legohunter.data.dao.ItemInventoryDao;
import io.legohunter.data.dao.ItemInventoryExternalCatalogItemDao;
import io.legohunter.data.dao.ItemInventoryPhotoDao;
import io.legohunter.data.dao.MarketplaceListingDao;
import io.legohunter.data.dto.BricklinkMarketplaceListing;
import io.legohunter.data.dto.ExternalCatalogItem;
import io.legohunter.data.dto.ExternalService;
import io.legohunter.data.dto.ItemInventory;
import io.legohunter.data.dto.ItemInventoryExternalCatalogItem;
import io.legohunter.data.dto.ItemInventoryPhoto;
import io.legohunter.data.dto.MarketplaceListing;
import io.legohunter.data.enums.PhotoStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceListingServiceImplTest {
    @Mock private BricklinkMarketplaceListingDao bricklinkMarketplaceListingDao;
    @Mock private ExternalServiceDao externalServiceDao;
    @Mock private ItemInventoryDao itemInventoryDao;
    @Mock private ItemInventoryExternalCatalogItemDao itemInventoryExternalCatalogItemDao;
    @Mock private ItemInventoryPhotoDao itemInventoryPhotoDao;
    @Mock private MarketplaceListingDao marketplaceListingDao;

    private MarketplaceListingDraftProperties properties;
    private MarketplaceListingServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new MarketplaceListingDraftProperties();
        properties.setEnvironmentCode("sandbox");
        properties.setNonProdBricklinkStockroomId("A");
        service = new MarketplaceListingServiceImpl(
                bricklinkMarketplaceListingDao,
                externalServiceDao,
                itemInventoryDao,
                itemInventoryExternalCatalogItemDao,
                itemInventoryPhotoDao,
                marketplaceListingDao,
                new MarketplaceListingDraftBusinessValidator(),
                properties
        );
    }

    @Test
    void createDraftPersistsLocalMarketplaceAndBricklinkRowsWithNonProdStockroomSafetyAndPhotoWarning() {
        ItemInventory inventory = inventory("KEEP", "AVAILABLE", true);
        MarketplaceListing persistedListing = listing(101, inventory.getItemInventoryId(), new BigDecimal("125.00"), "DRAFT");
        BricklinkMarketplaceListing persistedBricklink = BricklinkMarketplaceListing.builder()
                .marketplaceListingId(101)
                .isStockRoom(true)
                .stockRoomId("A")
                .environmentCode("sandbox")
                .build();
        stubCreateInputs(inventory, Set.of(), persistedListing, persistedBricklink);

        MarketplaceListingDraftResponse response = service.createDraft(MarketplaceListingDraftCreateRequest.builder()
                .itemInventoryId(202)
                .marketplaceCode("bricklink")
                .updateSaleIntentToSellable(true)
                .saleIntentNote("ready to list")
                .unitPrice(new BigDecimal("125.00"))
                .currencyCode("USD")
                .fixedPrice(true)
                .bricklink(BricklinkListingDraftRequest.builder()
                        .isStockRoom(false)
                        .stockRoomId("B")
                        .remarks("seller notes")
                        .build())
                .build());

        assertThat(response.getMarketplaceListing()).isSameAs(persistedListing);
        assertThat(response.getBricklinkMarketplaceListing()).isSameAs(persistedBricklink);
        assertThat(response.getReadiness().isReadyForMarketplaceSync()).isTrue();
        assertThat(response.getReadiness().getWarnings()).singleElement()
                .satisfies(warning -> assertThat(warning.getCode()).isEqualTo("MISSING_ITEM_INVENTORY_PHOTOS"));
        verify(itemInventoryDao).updateSaleIntent(202, "SELLABLE", null, "ready to list");

        ArgumentCaptor<BricklinkMarketplaceListing> bricklinkCaptor = ArgumentCaptor.forClass(BricklinkMarketplaceListing.class);
        verify(bricklinkMarketplaceListingDao).insert(bricklinkCaptor.capture());
        assertThat(bricklinkCaptor.getValue().getIsStockRoom()).isTrue();
        assertThat(bricklinkCaptor.getValue().getStockRoomId()).isEqualTo("A");
        assertThat(bricklinkCaptor.getValue().getEnvironmentCode()).isEqualTo("sandbox");
        assertThat(bricklinkCaptor.getValue().getLastRemoteSafetyStatusCode()).isEqualTo("NOT_VERIFIED");
    }

    @Test
    void createDraftRejectsDuplicateOpenListingForSameMarketplace() {
        ItemInventory inventory = inventory("SELLABLE", "AVAILABLE", true);
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(externalServiceDao.findByServiceCode("BRICKLINK")).thenReturn(Optional.of(bricklinkService()));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of(primaryBricklinkCatalogLink()));
        when(marketplaceListingDao.findByItemInventoryId(202)).thenReturn(Set.of(listing(101, 202, new BigDecimal("10.00"), "DRAFT")));

        assertThatThrownBy(() -> service.createDraft(MarketplaceListingDraftCreateRequest.builder()
                .itemInventoryId(202)
                .marketplaceCode("BRICKLINK")
                .unitPrice(new BigDecimal("12.00"))
                .currencyCode("USD")
                .build()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Inventory item already has an open marketplace listing for BRICKLINK");
    }

    @Test
    void createDraftDoesNotForceStockroomOrUpdateSaleIntentWhenProductionAndAlreadySellable() {
        properties.setProduction(true);
        ItemInventory inventory = inventory("SELLABLE", "AVAILABLE", true);
        MarketplaceListing persistedListing = listing(101, inventory.getItemInventoryId(), new BigDecimal("125.00"), "DRAFT");
        BricklinkMarketplaceListing persistedBricklink = BricklinkMarketplaceListing.builder()
                .marketplaceListingId(101)
                .build();
        stubCreateInputs(inventory, Set.of(), persistedListing, persistedBricklink);

        service.createDraft(MarketplaceListingDraftCreateRequest.builder()
                .itemInventoryId(202)
                .marketplaceCode("BRICKLINK")
                .updateSaleIntentToSellable(true)
                .unitPrice(new BigDecimal("125.00"))
                .currencyCode("USD")
                .build());

        ArgumentCaptor<BricklinkMarketplaceListing> bricklinkCaptor = ArgumentCaptor.forClass(BricklinkMarketplaceListing.class);
        verify(bricklinkMarketplaceListingDao).insert(bricklinkCaptor.capture());
        assertThat(bricklinkCaptor.getValue().getIsStockRoom()).isNull();
        assertThat(bricklinkCaptor.getValue().getStockRoomId()).isNull();
        assertThat(bricklinkCaptor.getValue().getEnvironmentCode()).isNull();
        verify(itemInventoryDao, never()).updateSaleIntent(any(), any(), any(), any());
    }

    @Test
    void createDraftRejectsMissingInventory() {
        when(externalServiceDao.findByServiceCode("BRICKLINK")).thenReturn(Optional.of(bricklinkService()));
        when(itemInventoryDao.findByItemInventoryId(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDraft(MarketplaceListingDraftCreateRequest.builder()
                .itemInventoryId(404)
                .marketplaceCode("BRICKLINK")
                .unitPrice(new BigDecimal("12.00"))
                .currencyCode("USD")
                .build()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Item inventory was not found: 404");
    }

    @Test
    void createDraftRejectsMissingBricklinkCatalogLink() {
        ItemInventory inventory = inventory("SELLABLE", "AVAILABLE", true);
        when(externalServiceDao.findByServiceCode("BRICKLINK")).thenReturn(Optional.of(bricklinkService()));
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of());

        assertThatThrownBy(() -> service.createDraft(MarketplaceListingDraftCreateRequest.builder()
                .itemInventoryId(202)
                .marketplaceCode("BRICKLINK")
                .unitPrice(new BigDecimal("12.00"))
                .currencyCode("USD")
                .build()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Inventory item must have a BrickLink catalog link for the requested draft");
    }

    @Test
    void createDraftRejectsMissingMarketplaceExternalService() {
        when(externalServiceDao.findByServiceCode("BRICKLINK")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDraft(MarketplaceListingDraftCreateRequest.builder()
                .itemInventoryId(202)
                .marketplaceCode("BRICKLINK")
                .unitPrice(new BigDecimal("12.00"))
                .currencyCode("USD")
                .build()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Marketplace external service was not found: BRICKLINK");
    }

    @Test
    void createDraftRejectsInactiveInventory() {
        ItemInventory inventory = inventory("SELLABLE", "AVAILABLE", false);
        when(externalServiceDao.findByServiceCode("BRICKLINK")).thenReturn(Optional.of(bricklinkService()));
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of(primaryBricklinkCatalogLink()));

        assertThatThrownBy(() -> service.createDraft(MarketplaceListingDraftCreateRequest.builder()
                .itemInventoryId(202)
                .marketplaceCode("BRICKLINK")
                .unitPrice(new BigDecimal("12.00"))
                .currencyCode("USD")
                .build()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Inventory item must be active before creating a marketplace listing draft");
    }

    @Test
    void createDraftRejectsNonSellableInventoryWhenNotUpdatingSaleIntent() {
        ItemInventory inventory = inventory("KEEP", "AVAILABLE", true);
        when(externalServiceDao.findByServiceCode("BRICKLINK")).thenReturn(Optional.of(bricklinkService()));
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of(primaryBricklinkCatalogLink()));

        assertThatThrownBy(() -> service.createDraft(MarketplaceListingDraftCreateRequest.builder()
                .itemInventoryId(202)
                .marketplaceCode("BRICKLINK")
                .unitPrice(new BigDecimal("12.00"))
                .currencyCode("USD")
                .build()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Inventory item must have saleIntentCode SELLABLE before creating a marketplace listing draft");

        verify(itemInventoryDao, never()).updateSaleIntent(any(), any(), any(), any());
    }

    @Test
    void createDraftRejectsUnavailableInventory() {
        ItemInventory inventory = inventory("SELLABLE", "RESERVED_FOR_ORDER", true);
        when(externalServiceDao.findByServiceCode("BRICKLINK")).thenReturn(Optional.of(bricklinkService()));
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of(primaryBricklinkCatalogLink()));

        assertThatThrownBy(() -> service.createDraft(MarketplaceListingDraftCreateRequest.builder()
                .itemInventoryId(202)
                .marketplaceCode("BRICKLINK")
                .unitPrice(new BigDecimal("12.00"))
                .currencyCode("USD")
                .build()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Inventory item must have inventoryStateCode AVAILABLE before creating a marketplace listing draft");
    }

    @Test
    void evaluateReadinessReturnsBlockersAndDoesNotWarnForKeepInventoryWithoutPhotos() {
        ItemInventory inventory = inventory("KEEP", "RESERVED_FOR_ORDER", false);
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of());
        when(marketplaceListingDao.findByItemInventoryId(202)).thenReturn(Set.of());
        when(itemInventoryPhotoDao.findByItemInventoryId(202)).thenReturn(Set.of());

        MarketplaceListingReadinessResponse response = service.evaluateReadiness(202, null);

        assertThat(response.isReadyForMarketplaceSync()).isFalse();
        assertThat(response.getBlockers()).extracting("code")
                .contains("INVENTORY_INACTIVE", "INVENTORY_NOT_SELLABLE", "INVENTORY_NOT_AVAILABLE",
                        "MISSING_PRIMARY_BRICKLINK_CATALOG_LINK", "MISSING_MARKETPLACE_LISTING_DRAFT");
        assertThat(response.getWarnings()).isEmpty();
    }

    @Test
    void evaluateReadinessDoesNotWarnWhenSellableInventoryHasPhotos() {
        ItemInventory inventory = inventory("SELLABLE", "AVAILABLE", true);
        MarketplaceListing listing = listing(101, 202, new BigDecimal("12.00"), "DRAFT");
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of(primaryBricklinkCatalogLink()));
        when(marketplaceListingDao.findByItemInventoryId(202)).thenReturn(Set.of(listing));
        when(bricklinkMarketplaceListingDao.findByMarketplaceListingId(101))
                .thenReturn(Optional.of(BricklinkMarketplaceListing.builder()
                        .marketplaceListingId(101)
                        .isStockRoom(true)
                        .stockRoomId("A")
                        .build()));
        when(itemInventoryPhotoDao.findByItemInventoryId(202)).thenReturn(Set.of(ItemInventoryPhoto.builder()
                .itemInventoryId(202)
                .md5("photo-md5")
                .status(PhotoStatus.PROCESSED)
                .build()));

        MarketplaceListingReadinessResponse response = service.evaluateReadiness(202, "BRICKLINK");

        assertThat(response.isReadyForMarketplaceSync()).isTrue();
        assertThat(response.getWarnings()).isEmpty();
    }

    @Test
    void findByMarketplaceListingIdReturnsListingAndReadiness() {
        ItemInventory inventory = inventory("SELLABLE", "AVAILABLE", true);
        MarketplaceListing listing = listing(101, 202, new BigDecimal("12.00"), "DRAFT");
        BricklinkMarketplaceListing bricklink = BricklinkMarketplaceListing.builder()
                .marketplaceListingId(101)
                .isStockRoom(true)
                .stockRoomId("A")
                .build();
        when(marketplaceListingDao.findByMarketplaceListingId(101)).thenReturn(Optional.of(listing));
        when(bricklinkMarketplaceListingDao.findByMarketplaceListingId(101)).thenReturn(Optional.of(bricklink), Optional.of(bricklink));
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of(primaryBricklinkCatalogLink()));
        when(marketplaceListingDao.findByItemInventoryId(202)).thenReturn(Set.of(listing));
        when(itemInventoryPhotoDao.findByItemInventoryId(202)).thenReturn(Set.of());

        MarketplaceListingDraftResponse response = service.findByMarketplaceListingId(101);

        assertThat(response.getMarketplaceListing()).isSameAs(listing);
        assertThat(response.getBricklinkMarketplaceListing()).isSameAs(bricklink);
        assertThat(response.getReadiness().isReadyForMarketplaceSync()).isTrue();
    }

    @Test
    void findByMarketplaceListingIdHandlesUnsupportedMarketplaceAsNotReady() {
        ItemInventory inventory = inventory("SELLABLE", "AVAILABLE", true);
        MarketplaceListing ebayListing = listing(101, 202, new BigDecimal("12.00"), "DRAFT");
        ebayListing.setListingExternalServiceId(3);
        when(marketplaceListingDao.findByMarketplaceListingId(101)).thenReturn(Optional.of(ebayListing));
        when(bricklinkMarketplaceListingDao.findByMarketplaceListingId(101)).thenReturn(Optional.empty());
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of(primaryBricklinkCatalogLink()));
        when(marketplaceListingDao.findByItemInventoryId(202)).thenReturn(Set.of(ebayListing));
        when(itemInventoryPhotoDao.findByItemInventoryId(202)).thenReturn(Set.of());

        MarketplaceListingDraftResponse response = service.findByMarketplaceListingId(101);

        assertThat(response.getReadiness().isReadyForMarketplaceSync()).isFalse();
        assertThat(response.getReadiness().getMarketplaceCode()).isEqualTo("3");
        assertThat(response.getReadiness().getBlockers()).singleElement()
                .satisfies(blocker -> assertThat(blocker.getCode()).isEqualTo("UNSUPPORTED_MARKETPLACE"));
    }

    @Test
    void findByItemInventoryIdRejectsMissingInventory() {
        when(itemInventoryDao.findByItemInventoryId(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByItemInventoryId(404))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Item inventory was not found: 404");
    }

    @Test
    void findByItemInventoryIdReturnsAllDraftsForInventory() {
        ItemInventory inventory = inventory("SELLABLE", "AVAILABLE", true);
        MarketplaceListing listing = listing(101, 202, new BigDecimal("12.00"), "DRAFT");
        BricklinkMarketplaceListing bricklink = BricklinkMarketplaceListing.builder()
                .marketplaceListingId(101)
                .isStockRoom(true)
                .stockRoomId("A")
                .build();
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(marketplaceListingDao.findByItemInventoryId(202)).thenReturn(Set.of(listing), Set.of(listing));
        when(bricklinkMarketplaceListingDao.findByMarketplaceListingId(101)).thenReturn(Optional.of(bricklink), Optional.of(bricklink));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of(primaryBricklinkCatalogLink()));
        when(itemInventoryPhotoDao.findByItemInventoryId(202)).thenReturn(Set.of());

        Set<MarketplaceListingDraftResponse> response = service.findByItemInventoryId(202);

        assertThat(response).singleElement()
                .satisfies(draft -> assertThat(draft.getMarketplaceListing()).isSameAs(listing));
    }

    @Test
    void updateDraftPatchesMarketplaceAndBricklinkRows() {
        ItemInventory inventory = inventory("SELLABLE", "AVAILABLE", true);
        MarketplaceListing existing = listing(101, 202, new BigDecimal("12.00"), "DRAFT");
        MarketplaceListing updated = listing(101, 202, new BigDecimal("15.00"), "DRAFT");
        BricklinkMarketplaceListing existingBricklink = BricklinkMarketplaceListing.builder().marketplaceListingId(101).build();
        BricklinkMarketplaceListing updatedBricklink = BricklinkMarketplaceListing.builder()
                .marketplaceListingId(101)
                .isStockRoom(true)
                .stockRoomId("A")
                .environmentCode("sandbox")
                .build();
        when(marketplaceListingDao.findByMarketplaceListingId(101)).thenReturn(Optional.of(existing));
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of(primaryBricklinkCatalogLink()));
        when(externalServiceDao.findByServiceCode("BRICKLINK")).thenReturn(Optional.of(bricklinkService()));
        when(marketplaceListingDao.findByItemInventoryId(202)).thenReturn(Set.of(existing), Set.of(updated));
        when(marketplaceListingDao.update(existing)).thenReturn(updated);
        when(bricklinkMarketplaceListingDao.findByMarketplaceListingId(101)).thenReturn(Optional.of(existingBricklink), Optional.of(updatedBricklink));
        when(bricklinkMarketplaceListingDao.update(existingBricklink)).thenReturn(updatedBricklink);
        when(itemInventoryPhotoDao.findByItemInventoryId(202)).thenReturn(Set.of());

        MarketplaceListingDraftResponse response = service.updateDraft(101, MarketplaceListingDraftUpdateRequest.builder()
                .unitPrice(new BigDecimal("15.00"))
                .title("Updated title")
                .bricklink(BricklinkListingDraftRequest.builder().bulk(2).build())
                .build());

        assertThat(response.getMarketplaceListing()).isSameAs(updated);
        assertThat(existing.getUnitPrice()).isEqualByComparingTo("15.00");
        assertThat(existing.getTitle()).isEqualTo("Updated title");
        assertThat(existingBricklink.getBulk()).isEqualTo(2);
        assertThat(existingBricklink.getIsStockRoom()).isTrue();
    }

    @Test
    void updateDraftCanCreateMissingBricklinkDetails() {
        ItemInventory inventory = inventory("SELLABLE", "AVAILABLE", true);
        MarketplaceListing existing = listing(101, 202, new BigDecimal("12.00"), "DRAFT");
        MarketplaceListing updated = listing(101, 202, new BigDecimal("12.00"), "DRAFT");
        BricklinkMarketplaceListing insertedBricklink = BricklinkMarketplaceListing.builder()
                .marketplaceListingId(101)
                .isStockRoom(true)
                .stockRoomId("A")
                .environmentCode("sandbox")
                .build();
        when(marketplaceListingDao.findByMarketplaceListingId(101)).thenReturn(Optional.of(existing));
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of(primaryBricklinkCatalogLink()));
        when(externalServiceDao.findByServiceCode("BRICKLINK")).thenReturn(Optional.of(bricklinkService()));
        when(marketplaceListingDao.findByItemInventoryId(202)).thenReturn(Set.of(existing), Set.of(updated));
        when(marketplaceListingDao.update(existing)).thenReturn(updated);
        when(bricklinkMarketplaceListingDao.findByMarketplaceListingId(101)).thenReturn(Optional.empty(), Optional.of(insertedBricklink));
        when(bricklinkMarketplaceListingDao.insert(any(BricklinkMarketplaceListing.class))).thenReturn(insertedBricklink);
        when(itemInventoryPhotoDao.findByItemInventoryId(202)).thenReturn(Set.of());

        MarketplaceListingDraftResponse response = service.updateDraft(101, MarketplaceListingDraftUpdateRequest.builder()
                .externalCatalogItemId(303)
                .listingStatusCode("DRAFT")
                .description("updated")
                .privateNotes("private")
                .currencyCode("USD")
                .fixedPrice(true)
                .build());

        assertThat(response.getBricklinkMarketplaceListing()).isSameAs(insertedBricklink);
        verify(bricklinkMarketplaceListingDao).insert(any(BricklinkMarketplaceListing.class));
        assertThat(existing.getDescription()).isEqualTo("updated");
        assertThat(existing.getPrivateNotes()).isEqualTo("private");
        assertThat(existing.getFixedPrice()).isTrue();
    }

    @Test
    void updateDraftRejectsRequestedCatalogLinkThatDoesNotBelongToBricklink() {
        ItemInventory inventory = inventory("SELLABLE", "AVAILABLE", true);
        MarketplaceListing existing = listing(101, 202, new BigDecimal("12.00"), "DRAFT");
        when(marketplaceListingDao.findByMarketplaceListingId(101)).thenReturn(Optional.of(existing));
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of());

        assertThatThrownBy(() -> service.updateDraft(101, MarketplaceListingDraftUpdateRequest.builder()
                .externalCatalogItemId(999)
                .build()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Inventory item must have the requested BrickLink catalog link");
    }

    @Test
    void removeDraftMarksListingRemoved() {
        ItemInventory inventory = inventory("SELLABLE", "AVAILABLE", true);
        MarketplaceListing existing = listing(101, 202, new BigDecimal("12.00"), "DRAFT");
        MarketplaceListing removed = listing(101, 202, new BigDecimal("12.00"), "REMOVED");
        removed.setEndedAt(java.time.ZonedDateTime.now());
        when(marketplaceListingDao.findByMarketplaceListingId(101)).thenReturn(Optional.of(existing));
        when(marketplaceListingDao.update(existing)).thenReturn(removed);
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of(primaryBricklinkCatalogLink()));
        when(marketplaceListingDao.findByItemInventoryId(202)).thenReturn(Set.of(removed));
        when(itemInventoryPhotoDao.findByItemInventoryId(202)).thenReturn(Set.of());
        when(bricklinkMarketplaceListingDao.findByMarketplaceListingId(101)).thenReturn(Optional.empty());

        MarketplaceListingDraftResponse response = service.removeDraft(101);

        assertThat(existing.getListingStatusCode()).isEqualTo("REMOVED");
        assertThat(existing.getEndedAt()).isNotNull();
        assertThat(response.getMarketplaceListing()).isSameAs(removed);
    }

    @Test
    void findByMarketplaceListingIdThrowsNotFoundWhenMissing() {
        when(marketplaceListingDao.findByMarketplaceListingId(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByMarketplaceListingId(404))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Marketplace listing was not found: 404");
    }

    @Test
    void createDraftRejectsUnsupportedMarketplace() {
        assertThatThrownBy(() -> service.createDraft(MarketplaceListingDraftCreateRequest.builder()
                .itemInventoryId(202)
                .marketplaceCode("EBAY")
                .unitPrice(new BigDecimal("12.00"))
                .currencyCode("USD")
                .build()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Only BRICKLINK marketplace listing drafts are supported in Phase 3");
    }

    private void stubCreateInputs(
            ItemInventory inventory,
            Set<ItemInventoryPhoto> photos,
            MarketplaceListing persistedListing,
            BricklinkMarketplaceListing persistedBricklink
    ) {
        when(itemInventoryDao.findByItemInventoryId(202)).thenReturn(Optional.of(inventory));
        when(externalServiceDao.findByServiceCode("BRICKLINK")).thenReturn(Optional.of(bricklinkService()));
        when(itemInventoryExternalCatalogItemDao.findByItemInventoryId(202)).thenReturn(Set.of(primaryBricklinkCatalogLink()));
        when(marketplaceListingDao.findByItemInventoryId(202)).thenReturn(Set.of(), Set.of(persistedListing));
        when(marketplaceListingDao.insert(any(MarketplaceListing.class))).thenReturn(persistedListing);
        when(bricklinkMarketplaceListingDao.insert(any(BricklinkMarketplaceListing.class))).thenReturn(persistedBricklink);
        when(bricklinkMarketplaceListingDao.findByMarketplaceListingId(101)).thenReturn(Optional.of(persistedBricklink));
        when(itemInventoryPhotoDao.findByItemInventoryId(202)).thenReturn(photos);
    }

    private ItemInventory inventory(String saleIntentCode, String inventoryStateCode, boolean active) {
        ItemInventory inventory = new ItemInventory();
        inventory.setItemInventoryId(202);
        inventory.setSaleIntentCode(saleIntentCode);
        inventory.setInventoryStateCode(inventoryStateCode);
        inventory.setActive(active);
        return inventory;
    }

    private ExternalService bricklinkService() {
        ExternalService service = new ExternalService();
        service.setExternalServiceId(2);
        service.setServiceCode("BRICKLINK");
        return service;
    }

    private MarketplaceListing listing(Integer marketplaceListingId, Integer itemInventoryId, BigDecimal unitPrice, String status) {
        return MarketplaceListing.builder()
                .marketplaceListingId(marketplaceListingId)
                .itemInventoryId(itemInventoryId)
                .listingExternalServiceId(2)
                .externalCatalogItemId(303)
                .listingStatusCode(status)
                .unitPrice(unitPrice)
                .currencyCode("USD")
                .build();
    }

    private ItemInventoryExternalCatalogItem primaryBricklinkCatalogLink() {
        return ItemInventoryExternalCatalogItem.builder()
                .itemInventoryId(202)
                .externalCatalogItemId(303)
                .primary(true)
                .externalCatalogItem(ExternalCatalogItem.builder()
                        .externalCatalogItemId(303)
                        .externalServiceId(2)
                        .externalItemKey("6390-1")
                        .build())
                .build();
    }
}

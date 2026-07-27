package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.api.ItemInventoryService;
import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.AddItemInventoryResponse;
import com.vattima.lego.inventory.service.dto.InventoryPhysicalUpdateRequest;
import com.vattima.lego.inventory.service.dto.InventorySearchResponse;
import com.vattima.lego.inventory.service.dto.InventoryStateUpdateRequest;
import com.vattima.lego.inventory.service.dto.SaleIntentUpdateRequest;
import io.legohunter.data.dao.ItemInventoryDao;
import io.legohunter.data.dto.ItemInventory;
import io.legohunter.data.dto.ItemInventorySearchCriteria;
import io.legohunter.data.dto.Transactions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemInventoryControllerTest {
    @Mock
    private ItemInventoryService itemInventoryService;

    @Mock
    private ItemInventoryDao itemInventoryDao;

    @Test
    void findAllReturnsInventoryRows() {
        ItemInventory itemInventory = new ItemInventory();
        itemInventory.setItemInventoryId(1);
        when(itemInventoryDao.findAll()).thenReturn(Set.of(itemInventory));

        ResponseEntity<Set<ItemInventory>> response = controller().findAll();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsExactly(itemInventory);
    }

    @Test
    void findByUuidReturnsInventoryWhenPresent() {
        ItemInventory itemInventory = new ItemInventory();
        itemInventory.setUuid("inventory-uuid");
        when(itemInventoryDao.findByUuid("inventory-uuid")).thenReturn(Optional.of(itemInventory));

        ResponseEntity<ItemInventory> response = controller().findByUuid("inventory-uuid");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(itemInventory);
    }

    @Test
    void findByUuidReturnsNotFoundWhenMissing() {
        when(itemInventoryDao.findByUuid("missing")).thenReturn(Optional.empty());

        ResponseEntity<ItemInventory> response = controller().findByUuid("missing");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void findByItemInventoryIdReturnsInventoryWhenPresent() {
        ItemInventory itemInventory = new ItemInventory();
        itemInventory.setItemInventoryId(1);
        when(itemInventoryDao.findByItemInventoryId(1)).thenReturn(Optional.of(itemInventory));

        ResponseEntity<ItemInventory> response = controller().findByUuid(1);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(itemInventory);
    }

    @Test
    void findByItemInventoryIdReturnsNotFoundWhenMissing() {
        when(itemInventoryDao.findByItemInventoryId(99)).thenReturn(Optional.empty());

        ResponseEntity<ItemInventory> response = controller().findByUuid(99);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void addItemInventoryDelegatesToService() {
        AddItemInventoryRequest request = AddItemInventoryRequest.builder().build();
        AddItemInventoryResponse serviceResponse = AddItemInventoryResponse.builder()
                .transaction(Transactions.builder().transactionId(101L).build())
                .build();
        when(itemInventoryService.addItemInventory(request)).thenReturn(serviceResponse);

        ResponseEntity<AddItemInventoryResponse> response = controller().addItemInventory(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(serviceResponse);
        verify(itemInventoryService).addItemInventory(request);
    }

    @Test
    void searchBuildsCriteriaAndDelegatesToService() {
        InventorySearchResponse serviceResponse = InventorySearchResponse.builder().total(1).limit(25).offset(5).build();
        when(itemInventoryService.searchInventory(any(ItemInventorySearchCriteria.class))).thenReturn(serviceResponse);

        ResponseEntity<InventorySearchResponse> response = controller().search(
                "6390-1",
                "Main Street",
                12,
                "AVAILABLE",
                "KEEP",
                true,
                "U",
                "C",
                "VG",
                "G",
                "E",
                java.time.LocalDate.parse("2026-01-01"),
                java.time.LocalDate.parse("2026-12-31"),
                25,
                5);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(serviceResponse);
        org.mockito.ArgumentCaptor<ItemInventorySearchCriteria> criteriaCaptor = org.mockito.ArgumentCaptor.forClass(ItemInventorySearchCriteria.class);
        verify(itemInventoryService).searchInventory(criteriaCaptor.capture());
        assertThat(criteriaCaptor.getValue().getItemNumber()).isEqualTo("6390-1");
        assertThat(criteriaCaptor.getValue().getDescription()).isEqualTo("Main Street");
        assertThat(criteriaCaptor.getValue().getBoxNumber()).isEqualTo(12);
        assertThat(criteriaCaptor.getValue().getInventoryStateCode()).isEqualTo("AVAILABLE");
        assertThat(criteriaCaptor.getValue().getSaleIntentCode()).isEqualTo("KEEP");
        assertThat(criteriaCaptor.getValue().getLimit()).isEqualTo(25);
        assertThat(criteriaCaptor.getValue().getOffset()).isEqualTo(5);
    }

    @Test
    void updatePhysicalDelegatesToService() {
        InventoryPhysicalUpdateRequest request = InventoryPhysicalUpdateRequest.builder().boxNumber(12).build();
        ItemInventory inventory = new ItemInventory();
        when(itemInventoryService.updateInventoryPhysical(202, request)).thenReturn(inventory);

        ResponseEntity<ItemInventory> response = controller().updatePhysical(202, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(inventory);
    }

    @Test
    void updateStateDelegatesToService() {
        InventoryStateUpdateRequest request = InventoryStateUpdateRequest.builder().inventoryStateCode("SOLD").build();
        ItemInventory inventory = new ItemInventory();
        when(itemInventoryService.updateInventoryState(202, request)).thenReturn(inventory);

        ResponseEntity<ItemInventory> response = controller().updateState(202, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(inventory);
    }

    @Test
    void updateSaleIntentDelegatesToService() {
        SaleIntentUpdateRequest request = SaleIntentUpdateRequest.builder().saleIntentCode("UNDECIDED").build();
        ItemInventory inventory = new ItemInventory();
        when(itemInventoryService.updateSaleIntent(202, request)).thenReturn(inventory);

        ResponseEntity<ItemInventory> response = controller().updateSaleIntent(202, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(inventory);
    }

    private ItemInventoryController controller() {
        return new ItemInventoryController(itemInventoryService, itemInventoryDao);
    }
}

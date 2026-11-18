package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.api.PartyService;
import com.vattima.lego.inventory.service.dto.PartyCreateRequest;
import com.vattima.lego.inventory.service.dto.PartyResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyControllerTest {
    @Mock
    private PartyService partyService;

    @Test
    void findByIdDelegatesToThePartyService() {
        PartyResponse party = PartyResponse.builder().partyId(12L).build();
        when(partyService.findById(12L)).thenReturn(party);

        ResponseEntity<PartyResponse> response = controller().findById(12L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(party);
    }

    @Test
    void searchPassesEveryCriterionToThePartyService() {
        List<PartyResponse> parties = List.of(PartyResponse.builder().partyId(12L).build());
        when(partyService.search("smith", "a@example.com", "Bricklink", "brickuser", 5)).thenReturn(parties);

        ResponseEntity<List<PartyResponse>> response =
                controller().search("smith", "a@example.com", "Bricklink", "brickuser", 5);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(parties);
    }

    @Test
    void createAnswersCreatedWithTheNewParty() {
        PartyCreateRequest request = PartyCreateRequest.builder().partyType("P").partyFirstName("Ann").build();
        PartyResponse party = PartyResponse.builder().partyId(215L).build();
        when(partyService.create(request, true)).thenReturn(party);

        ResponseEntity<PartyResponse> response = controller().create(request, true);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isSameAs(party);
    }

    private PartyController controller() {
        return new PartyController(partyService);
    }
}

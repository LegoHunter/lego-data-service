package com.vattima.lego.inventory.service.impl;

import com.vattima.lego.inventory.service.dto.ApiValidationError;
import com.vattima.lego.inventory.service.dto.PartyCreateRequest;
import com.vattima.lego.inventory.service.dto.PartyExternalIdentityRequest;
import com.vattima.lego.inventory.service.dto.PartyResponse;
import com.vattima.lego.inventory.service.dto.PartyUpdateRequest;
import com.vattima.lego.inventory.service.exception.DuplicatePartyException;
import com.vattima.lego.inventory.service.exception.NotFoundException;
import com.vattima.lego.inventory.service.exception.ValidationException;
import io.legohunter.data.dao.PartyDao;
import io.legohunter.data.dao.PartyExternalIdentityDao;
import io.legohunter.data.dao.TransactionPlatformDao;
import io.legohunter.data.dto.Party;
import io.legohunter.data.dto.PartyExternalIdentity;
import io.legohunter.data.dto.TransactionPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartyServiceImplTest {
    private static final TransactionPlatform BRICKLINK = platform(1, "Bricklink");
    private static final TransactionPlatform EBAY = platform(2, "eBay");

    @Mock
    private PartyDao partyDao;

    @Mock
    private PartyExternalIdentityDao partyExternalIdentityDao;

    @Mock
    private TransactionPlatformDao transactionPlatformDao;

    private PartyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PartyServiceImpl(partyDao, partyExternalIdentityDao, transactionPlatformDao);
        when(transactionPlatformDao.findAll()).thenReturn(List.of(BRICKLINK, EBAY));
        when(transactionPlatformDao.findTransactionPlatformByName("Bricklink")).thenReturn(Optional.of(BRICKLINK));
        when(transactionPlatformDao.findTransactionPlatformByName("eBay")).thenReturn(Optional.of(EBAY));
        doAnswer(invocation -> {
            invocation.<Party>getArgument(0).setPartyId(215L);
            return null;
        }).when(partyDao).insert(any(Party.class));
    }

    @Test
    void findByIdReturnsThePartyWithItsExternalIdentities() {
        when(partyDao.findPartyById(12L)).thenReturn(Optional.of(Party.builder()
                .partyId(12L)
                .partyType("P")
                .partyFirstName("Ann")
                .partyLastName("Smith")
                .partyActivationDate(LocalDateTime.parse("2020-03-04T00:00:00"))
                .build()));
        when(partyExternalIdentityDao.findByPartyId(12L)).thenReturn(List.of(PartyExternalIdentity.builder()
                .partyExternalIdentityId(7L).partyId(12L).transactionPlatformId(1).externalPartyId("annbricks").build()));

        PartyResponse party = service.findById(12L);

        assertThat(party.getPartyId()).isEqualTo(12L);
        assertThat(party.getPartyLastName()).isEqualTo("Smith");
        assertThat(party.getPartyActivationDate()).isEqualTo(LocalDate.parse("2020-03-04"));
        assertThat(party.getExternalIdentities()).singleElement().satisfies(identity -> {
            assertThat(identity.getTransactionPlatformName()).isEqualTo("Bricklink");
            assertThat(identity.getExternalPartyId()).isEqualTo("annbricks");
        });
    }

    @Test
    void findByIdRejectsAnUnknownParty() {
        when(partyDao.findPartyById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Party [99] was not found");
    }

    @Test
    void searchResolvesThePlatformNameAndUsesTheDefaultLimit() {
        when(partyDao.search("smith", null, 1, "annbricks", 50))
                .thenReturn(List.of(Party.builder().partyId(12L).build()));
        when(partyExternalIdentityDao.findByPartyId(12L)).thenReturn(List.of());

        assertThat(service.search("smith", null, " Bricklink ", "annbricks", null))
                .extracting(PartyResponse::getPartyId).containsExactly(12L);
    }

    @Test
    void searchWithoutAPlatformDoesNotFilterByPlatform() {
        when(partyDao.search(null, "ann@", null, null, 5)).thenReturn(List.of());

        assertThat(service.search(null, "ann@", " ", null, 5)).isEmpty();

        verify(partyDao).search(null, "ann@", null, null, 5);
    }

    @Test
    void searchRejectsAnUnknownPlatformAndALimitOutOfRange() {
        when(transactionPlatformDao.findTransactionPlatformByName("Etsy")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.search(null, null, "Etsy", null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Transaction Platform [Etsy] is invalid. Must be one of [Bricklink, eBay]");
        assertThatThrownBy(() -> service.search(null, null, null, null, 0))
                .isInstanceOf(ValidationException.class)
                .hasMessage("limit must be between 1 and 200");
        assertThatThrownBy(() -> service.search(null, null, null, null, 201))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createSavesATrimmedPartyActiveFromTodayWithItsExternalIdentities() {
        noDuplicates();
        PartyCreateRequest request = PartyCreateRequest.builder()
                .partyType("P")
                .partyFirstName(" Ann ")
                .partyLastName("Smith")
                .partyCity("  ")
                .partyEmail(" ann@example.com ")
                .externalIdentities(List.of(
                        identity("Bricklink", " annbricks "),
                        identity("eBay", "ann-on-ebay")))
                .build();

        PartyResponse created = service.create(request, false);

        ArgumentCaptor<Party> saved = ArgumentCaptor.forClass(Party.class);
        verify(partyDao).insert(saved.capture());
        assertThat(saved.getValue().getPartyFirstName()).isEqualTo("Ann");
        assertThat(saved.getValue().getPartyCity()).isNull();
        assertThat(saved.getValue().getPartyEmail()).isEqualTo("ann@example.com");
        assertThat(saved.getValue().getPartyActivationDate().toLocalDate())
                .isBetween(LocalDate.now().minusDays(1), LocalDate.now());
        ArgumentCaptor<PartyExternalIdentity> identities = ArgumentCaptor.forClass(PartyExternalIdentity.class);
        verify(partyExternalIdentityDao, times(2)).insert(identities.capture());
        assertThat(identities.getAllValues())
                .extracting(
                        PartyExternalIdentity::getPartyId,
                        PartyExternalIdentity::getTransactionPlatformId,
                        PartyExternalIdentity::getExternalPartyId)
                .containsExactly(tuple(215L, 1, "annbricks"), tuple(215L, 2, "ann-on-ebay"));
        assertThat(created.getPartyId()).isEqualTo(215L);
    }

    @Test
    void createRejectsAPartyWhoseEmailNameOrIdentityAlreadyExists() {
        when(partyDao.findByEmail("ann@example.com")).thenReturn(List.of(Party.builder().partyId(12L).build()));
        when(partyDao.findByName("Ann", "Smith")).thenReturn(List.of(
                Party.builder().partyId(12L).build(),
                Party.builder().partyId(40L).build()));
        when(partyExternalIdentityDao.findByPlatformAndExternalPartyId(1, "annbricks"))
                .thenReturn(Optional.of(PartyExternalIdentity.builder().partyId(13L).build()));
        PartyCreateRequest request = PartyCreateRequest.builder()
                .partyType("P")
                .partyFirstName("Ann")
                .partyLastName("Smith")
                .partyEmail("ann@example.com")
                .externalIdentities(List.of(identity("Bricklink", "annbricks")))
                .build();

        assertThatThrownBy(() -> service.create(request, false))
                .isInstanceOfSatisfying(DuplicatePartyException.class, duplicate -> {
                    assertThat(duplicate.getMatches()).extracting(ApiValidationError::getCode)
                            .containsOnly("DUPLICATE_PARTY");
                    assertThat(duplicate.getMatches())
                            .extracting(ApiValidationError::getField, ApiValidationError::getMessage)
                            .containsExactly(
                                    tuple("partyEmail", "Party [12] already has this email"),
                                    tuple("externalIdentities",
                                            "Party [13] already has the external identity [annbricks]"),
                                    tuple("partyName",
                                            "Party [12] already has this name; send allowDuplicateName=true to allow it"),
                                    tuple("partyName",
                                            "Party [40] already has this name; send allowDuplicateName=true to allow it"));
                });

        verify(partyDao, never()).insert(any(Party.class));
        verify(partyExternalIdentityDao, never()).insert(any(PartyExternalIdentity.class));
    }

    @Test
    void createAllowsASecondPartyWithTheSameNameWhenAsked() {
        noDuplicates();
        when(partyDao.findByName("Ann", "Smith")).thenReturn(List.of(Party.builder().partyId(12L).build()));
        PartyCreateRequest request = PartyCreateRequest.builder()
                .partyType("P").partyFirstName("Ann").partyLastName("Smith").build();

        assertThat(service.create(request, true).getPartyId()).isEqualTo(215L);

        verify(partyDao, never()).findByName(any(), any());
    }

    @Test
    void allowingADuplicateNameDoesNotAllowADuplicateEmail() {
        noDuplicates();
        when(partyDao.findByEmail("ann@example.com")).thenReturn(List.of(Party.builder().partyId(12L).build()));
        PartyCreateRequest request = PartyCreateRequest.builder()
                .partyType("P").partyFirstName("Ann").partyEmail("ann@example.com").build();

        assertThatThrownBy(() -> service.create(request, true)).isInstanceOf(DuplicatePartyException.class);

        verify(partyDao, never()).insert(any(Party.class));
    }

    @Test
    void createRejectsAnUnknownPlatformARepeatedIdentityAndAMissingName() {
        noDuplicates();
        when(transactionPlatformDao.findTransactionPlatformByName("Etsy")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(PartyCreateRequest.builder()
                .partyType("C").partyFirstName("Brick Shop")
                .externalIdentities(List.of(identity("Etsy", "shop"))).build(), false))
                .isInstanceOf(ValidationException.class)
                .hasMessageStartingWith("Transaction Platform [Etsy] is invalid");
        assertThatThrownBy(() -> service.create(PartyCreateRequest.builder()
                .partyType("C").partyFirstName("Brick Shop")
                .externalIdentities(List.of(identity("eBay", "Shop"), identity("eBay", "shop"))).build(), false))
                .isInstanceOf(ValidationException.class)
                .hasMessage("External identity [shop] on [eBay] is listed more than once");
        assertThatThrownBy(() -> service.create(PartyCreateRequest.builder()
                .partyType("C").partyFirstName(" ").build(), false))
                .isInstanceOf(ValidationException.class)
                .hasMessage("partyFirstName or partyLastName is required");

        verify(partyDao, never()).insert(any(Party.class));
    }

    @Test
    void updateReplacesTheFieldsAndKeepsTheTypeTheActivationDateAndTheIdentitiesWhenLeftOut() {
        noDuplicates();
        when(partyDao.findPartyById(12L)).thenReturn(Optional.of(existingParty()));

        PartyResponse updated = service.update(12L, PartyUpdateRequest.builder()
                .partyFirstName("Ann")
                .partyLastName("Smith")
                .partyCity(" Raleigh ")
                .build(), false);

        ArgumentCaptor<Party> saved = ArgumentCaptor.forClass(Party.class);
        verify(partyDao).update(saved.capture());
        assertThat(saved.getValue().getPartyId()).isEqualTo(12L);
        assertThat(saved.getValue().getPartyType()).isEqualTo("P");
        assertThat(saved.getValue().getPartyCity()).isEqualTo("Raleigh");
        assertThat(saved.getValue().getPartyEmail()).isNull();
        assertThat(saved.getValue().getPartyActivationDate()).isEqualTo(LocalDateTime.parse("2020-03-04T00:00:00"));
        verify(partyDao, never()).findByName(any(), any());
        verify(partyExternalIdentityDao, never()).delete(any());
        verify(partyExternalIdentityDao, never()).insert(any(PartyExternalIdentity.class));
        assertThat(updated.getPartyId()).isEqualTo(12L);
    }

    @Test
    void updateRejectsAnUnknownParty() {
        when(partyDao.findPartyById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, PartyUpdateRequest.builder().partyFirstName("Ann").build(), false))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Party [99] was not found");
    }

    @Test
    void updateMakesTheIdentitiesExactlyTheListedOnes() {
        noDuplicates();
        when(partyDao.findPartyById(12L)).thenReturn(Optional.of(existingParty()));
        PartyExternalIdentity kept = PartyExternalIdentity.builder()
                .partyExternalIdentityId(7L).partyId(12L).transactionPlatformId(1).externalPartyId("AnnBricks").build();
        PartyExternalIdentity removed = PartyExternalIdentity.builder()
                .partyExternalIdentityId(8L).partyId(12L).transactionPlatformId(2).externalPartyId("old-ebay").build();
        when(partyExternalIdentityDao.findByPartyId(12L)).thenReturn(List.of(kept, removed));
        when(partyExternalIdentityDao.findByPlatformAndExternalPartyId(1, "annbricks")).thenReturn(Optional.of(kept));

        service.update(12L, PartyUpdateRequest.builder()
                .partyType("C")
                .partyFirstName("Ann")
                .partyLastName("Smith")
                .externalIdentities(List.of(identity("Bricklink", "annbricks"), identity("eBay", "new-ebay")))
                .build(), false);

        verify(partyExternalIdentityDao).delete(8L);
        verify(partyExternalIdentityDao, never()).delete(7L);
        ArgumentCaptor<PartyExternalIdentity> added = ArgumentCaptor.forClass(PartyExternalIdentity.class);
        verify(partyExternalIdentityDao).insert(added.capture());
        assertThat(added.getValue().getPartyId()).isEqualTo(12L);
        assertThat(added.getValue().getTransactionPlatformId()).isEqualTo(2);
        assertThat(added.getValue().getExternalPartyId()).isEqualTo("new-ebay");
        ArgumentCaptor<Party> saved = ArgumentCaptor.forClass(Party.class);
        verify(partyDao).update(saved.capture());
        assertThat(saved.getValue().getPartyType()).isEqualTo("C");
    }

    @Test
    void updateWithAnEmptyIdentityListRemovesThemAll() {
        noDuplicates();
        when(partyDao.findPartyById(12L)).thenReturn(Optional.of(existingParty()));
        when(partyExternalIdentityDao.findByPartyId(12L)).thenReturn(List.of(PartyExternalIdentity.builder()
                .partyExternalIdentityId(7L).partyId(12L).transactionPlatformId(1).externalPartyId("annbricks").build()));

        service.update(12L, PartyUpdateRequest.builder()
                .partyFirstName("Ann").partyLastName("Smith").externalIdentities(List.of()).build(), false);

        verify(partyExternalIdentityDao).delete(7L);
    }

    @Test
    void updateIgnoresThePartyItselfWhenLookingForDuplicates() {
        noDuplicates();
        when(partyDao.findPartyById(12L)).thenReturn(Optional.of(existingParty()));
        when(partyDao.findByEmail("ann@example.com")).thenReturn(List.of(Party.builder().partyId(12L).build()));

        service.update(12L, PartyUpdateRequest.builder()
                .partyFirstName("Ann").partyLastName("Smith").partyEmail("ann@example.com").build(), false);

        verify(partyDao).update(any(Party.class));
    }

    @Test
    void updateRejectsAnEmailIdentityOrNewNameThatAnotherPartyHas() {
        when(partyDao.findPartyById(12L)).thenReturn(Optional.of(existingParty()));
        when(partyDao.findByEmail("bob@example.com")).thenReturn(List.of(Party.builder().partyId(40L).build()));
        when(partyDao.findByName("Bob", "Jones")).thenReturn(List.of(
                Party.builder().partyId(12L).build(),
                Party.builder().partyId(41L).build()));
        when(partyExternalIdentityDao.findByPlatformAndExternalPartyId(1, "bobbricks"))
                .thenReturn(Optional.of(PartyExternalIdentity.builder().partyId(42L).build()));
        PartyUpdateRequest request = PartyUpdateRequest.builder()
                .partyFirstName("Bob")
                .partyLastName("Jones")
                .partyEmail("bob@example.com")
                .externalIdentities(List.of(identity("Bricklink", "bobbricks")))
                .build();

        assertThatThrownBy(() -> service.update(12L, request, false))
                .isInstanceOfSatisfying(DuplicatePartyException.class, duplicate -> assertThat(duplicate.getMatches())
                        .extracting(ApiValidationError::getField, ApiValidationError::getMessage)
                        .containsExactly(
                                tuple("partyEmail", "Party [40] already has this email"),
                                tuple("externalIdentities", "Party [42] already has the external identity [bobbricks]"),
                                tuple("partyName",
                                        "Party [41] already has this name; send allowDuplicateName=true to allow it")));

        verify(partyDao, never()).update(any(Party.class));
        verify(partyExternalIdentityDao, never()).delete(any());
    }

    @Test
    void updateAllowsANewNameAnotherPartyHasWhenAsked() {
        noDuplicates();
        when(partyDao.findPartyById(12L)).thenReturn(Optional.of(existingParty()));
        when(partyDao.findByName("Bob", "Jones")).thenReturn(List.of(Party.builder().partyId(41L).build()));

        service.update(12L, PartyUpdateRequest.builder().partyFirstName("Bob").partyLastName("Jones").build(), true);

        verify(partyDao).update(any(Party.class));
    }

    @Test
    void updateRejectsAMissingName() {
        when(partyDao.findPartyById(12L)).thenReturn(Optional.of(existingParty()));

        assertThatThrownBy(() -> service.update(12L, PartyUpdateRequest.builder().partyFirstName(" ").build(), false))
                .isInstanceOf(ValidationException.class)
                .hasMessage("partyFirstName or partyLastName is required");
    }

    private static Party existingParty() {
        return Party.builder()
                .partyId(12L)
                .partyType("P")
                .partyFirstName("ann")
                .partyLastName("Smith ")
                .partyEmail("old@example.com")
                .partyActivationDate(LocalDateTime.parse("2020-03-04T00:00:00"))
                .build();
    }

    private void noDuplicates() {
        when(partyDao.findByEmail(any())).thenReturn(List.of());
        when(partyDao.findByName(any(), any())).thenReturn(List.of());
        when(partyExternalIdentityDao.findByPlatformAndExternalPartyId(any(), any())).thenReturn(Optional.empty());
        when(partyExternalIdentityDao.findByPartyId(any())).thenReturn(List.of());
    }

    private static PartyExternalIdentityRequest identity(String platformName, String externalPartyId) {
        return PartyExternalIdentityRequest.builder()
                .transactionPlatformName(platformName).externalPartyId(externalPartyId).build();
    }

    private static TransactionPlatform platform(int id, String name) {
        return TransactionPlatform.builder().transactionPlatformId(id).transactionPlatformName(name).build();
    }
}

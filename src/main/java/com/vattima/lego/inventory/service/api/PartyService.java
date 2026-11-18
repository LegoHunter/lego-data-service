package com.vattima.lego.inventory.service.api;

import com.vattima.lego.inventory.service.dto.PartyCreateRequest;
import com.vattima.lego.inventory.service.dto.PartyResponse;
import com.vattima.lego.inventory.service.dto.PartyUpdateRequest;

import java.util.List;

/** Finds and adds parties: the people and companies on the other side of a transaction. */
public interface PartyService {
    PartyResponse findById(Long partyId);

    /**
     * Finds parties by any mix of part of the name, part of the email, and an identity on a transaction
     * platform. With no criterion, the first parties by id are returned.
     */
    List<PartyResponse> search(String name, String email, String transactionPlatformName, String externalPartyId, Integer limit);

    /**
     * Adds a party. An existing party with the same email or the same platform identity always blocks the
     * add; one with the same name blocks it unless {@code allowDuplicateName} is true.
     */
    PartyResponse create(PartyCreateRequest request, boolean allowDuplicateName);

    /**
     * Replaces an existing party's fields and, when the request lists them, its external identities. The
     * duplicate rules of {@link #create} apply against the other parties; the name is only checked when it
     * changes.
     */
    PartyResponse update(Long partyId, PartyUpdateRequest request, boolean allowDuplicateName);
}

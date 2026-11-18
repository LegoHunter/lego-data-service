package com.vattima.lego.inventory.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartyResponse {
    private Long partyId;
    private String partyType;
    private String partyFirstName;
    private String partyMiddleInitial;
    private String partyLastName;
    private String partyAddress1;
    private String partyAddress2;
    private String partyCity;
    private String partyState;
    private String partyPostalCode;
    private String partyCountryCode;
    private String partyCountry;
    private String partyPhone;
    private String partyEmail;
    private LocalDate partyActivationDate;
    private List<PartyExternalIdentityResponse> externalIdentities;
}

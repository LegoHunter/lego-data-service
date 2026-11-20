package com.vattima.lego.inventory.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The new values of an existing party. Every party field is replaced: a field left out is cleared. Two
 * things are kept when left out: the party type, and the external identities.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartyUpdateRequest {
    /** {@code P} for a person, {@code C} for a company. Left out, the party keeps its type. */
    @Pattern(regexp = "[PC]", message = "must be P (person) or C (company)")
    private String partyType;

    /** A person's first name, or the whole name of a company. */
    @Size(max = 50)
    private String partyFirstName;

    @Size(max = 1)
    private String partyMiddleInitial;

    @Size(max = 50)
    private String partyLastName;

    @Size(max = 50)
    private String partyAddress1;

    @Size(max = 50)
    private String partyAddress2;

    @Size(max = 50)
    private String partyCity;

    @Size(max = 50)
    private String partyState;

    @Size(max = 50)
    private String partyPostalCode;

    @Size(max = 50)
    private String partyCountryCode;

    @Size(max = 50)
    private String partyCountry;

    @Size(max = 50)
    private String partyPhone;

    @Email
    @Size(max = 50)
    private String partyEmail;

    /**
     * The full set of external identities the party should have afterwards: ones not listed are removed.
     * Left out (null), the identities are not touched; an empty list removes them all.
     */
    private List<@Valid @NotNull PartyExternalIdentityRequest> externalIdentities;

    @AssertTrue(message = "partyFirstName or partyLastName is required")
    public boolean isNameProvided() {
        return hasText(partyFirstName) || hasText(partyLastName);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

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
 * A new party: a person or company on the other side of a transaction, such as the seller of an item.
 * The field sizes are those of the {@code party} table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartyCreateRequest {
    /** {@code P} for a person, {@code C} for a company. */
    @NotNull
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

    private List<@Valid @NotNull PartyExternalIdentityRequest> externalIdentities;

    @AssertTrue(message = "partyFirstName or partyLastName is required")
    public boolean isNameProvided() {
        return hasText(partyFirstName) || hasText(partyLastName);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

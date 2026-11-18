package com.vattima.lego.inventory.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A party's identity on a transaction platform, for example a BrickLink user name. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartyExternalIdentityRequest {
    /** A name from the {@code transaction_platform} table, for example {@code Bricklink} or {@code eBay}. */
    @NotBlank
    private String transactionPlatformName;

    /** The party's id or user name on that platform. */
    @NotBlank
    @Size(max = 255)
    private String externalPartyId;
}

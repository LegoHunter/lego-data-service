package com.vattima.lego.inventory.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartyExternalIdentityResponse {
    private Long partyExternalIdentityId;
    private Integer transactionPlatformId;
    private String transactionPlatformName;
    private String externalPartyId;
}

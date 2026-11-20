package com.vattima.lego.inventory.service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BadRequestResponse {
    private String validationMessage;
}

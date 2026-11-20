package com.vattima.lego.inventory.service.exception;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public class NewOrUsedNotFoundException extends ValidationException {
    @Getter
    private final String newOrUsed;

    public NewOrUsedNotFoundException(String newOrUsed) {
        this.newOrUsed = newOrUsed;
    }

    @Override
    public String getMessage() {
        return "New or Used %s was not found".formatted(Optional.ofNullable(StringUtils.trimToNull(getNewOrUsed())).orElse("[null]"));
    }
}

package com.vattima.lego.inventory.service.exception;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public class ItemNumberNotFoundException extends ValidationException {
    @Getter
    private final String itemNumber;

    public ItemNumberNotFoundException(String itemNumber) {
        this.itemNumber = itemNumber;
    }

    @Override
    public String getMessage() {
        return "Item Number %s was not found".formatted(Optional.ofNullable(StringUtils.trimToNull(getItemNumber())).orElse("[null]"));
    }
}

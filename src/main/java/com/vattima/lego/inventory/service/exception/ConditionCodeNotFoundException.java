package com.vattima.lego.inventory.service.exception;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public class ConditionCodeNotFoundException extends ValidationException {
    @Getter
    private final String conditionCode;

    public ConditionCodeNotFoundException(String conditionCode) {
        this.conditionCode = conditionCode;
    }

    @Override
    public String getMessage() {
        return "Condition Code %s was not found".formatted(Optional.ofNullable(StringUtils.trimToNull(getConditionCode())).orElse("[null]"));
    }
}

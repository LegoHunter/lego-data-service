package com.vattima.lego.inventory.service.exception;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public class CompletenessNotFoundException extends ValidationException {
    @Getter
    private final String completeness;

    public CompletenessNotFoundException(String completeness) {
        this.completeness = completeness;
    }

    @Override
    public String getMessage() {
        return "Condition Code %s was not found".formatted(Optional.ofNullable(StringUtils.trimToNull(getCompleteness())).orElse("[null]"));
    }
}

package com.vattima.lego.inventory.service.exception;

import com.vattima.lego.inventory.service.dto.ApiValidationError;
import lombok.Getter;

import java.util.List;

/** A new party matches one or more existing parties. Each match is one entry of {@link #getMatches()}. */
@Getter
public class DuplicatePartyException extends LegoDataServiceException {
    private final List<ApiValidationError> matches;

    public DuplicatePartyException(List<ApiValidationError> matches) {
        super("A matching party already exists");
        this.matches = List.copyOf(matches);
    }
}

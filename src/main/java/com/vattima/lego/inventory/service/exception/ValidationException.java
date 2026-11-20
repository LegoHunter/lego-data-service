package com.vattima.lego.inventory.service.exception;

public class ValidationException extends LegoDataServiceException {
    public ValidationException() {
        super();
    }

    public ValidationException(String message) {
        super(message);
    }
}

package com.vattima.lego.inventory.service.advice;

import com.vattima.lego.inventory.service.dto.BadRequestResponse;
import com.vattima.lego.inventory.service.exception.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class ItemInventoryControllerAdvice {
    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<BadRequestResponse> handleNoHandlerFound(ValidationException e, WebRequest request) {
        return ResponseEntity.badRequest().body(BadRequestResponse.builder().validationMessage(e.getMessage()).build());
    }
}

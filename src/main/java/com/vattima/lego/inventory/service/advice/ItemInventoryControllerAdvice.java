package com.vattima.lego.inventory.service.advice;

import com.vattima.lego.inventory.service.dto.ApiErrorResponse;
import com.vattima.lego.inventory.service.dto.ApiValidationError;
import com.vattima.lego.inventory.service.exception.NotFoundException;
import com.vattima.lego.inventory.service.exception.ValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.Set;

@ControllerAdvice
public class ItemInventoryControllerAdvice {
    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiErrorResponse> validationExceptionHandler(ValidationException e, WebRequest request) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.builder()
                .message("Business rule validation failed")
                .errors(List.of(ApiValidationError.builder()
                        .message(e.getMessage())
                        .code("BUSINESS_RULE")
                        .build()))
                .build());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ApiErrorResponse> notFoundExceptionHandler(NotFoundException e, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.builder()
                .message("Resource not found")
                .errors(List.of(ApiValidationError.builder()
                        .message(e.getMessage())
                        .code("NOT_FOUND")
                        .build()))
                .build());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        List<ApiValidationError> errors = ex.getBindingResult().getAllErrors().stream()
                .map(this::toValidationError)
                .toList();
        return new ResponseEntity<>(ApiErrorResponse.builder()
                .message("Request validation failed")
                .errors(errors)
                .build(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        List<ApiValidationError> errors;
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        errors = violations.stream()
                .map(violation -> ApiValidationError.builder()
                        .field(violation.getPropertyPath().toString())
                        .message(violation.getMessage())
                        .code("CONSTRAINT_VIOLATION")
                        .build())
                .toList();
        return new ResponseEntity<>(ApiErrorResponse.builder()
                .message("Request validation failed")
                .errors(errors)
                .build(), HttpStatus.BAD_REQUEST);
    }

    private ApiValidationError toValidationError(ObjectError error) {
        String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
        return ApiValidationError.builder()
                .field(field)
                .message(error.getDefaultMessage())
                .code(error.getCode())
                .build();
    }
}

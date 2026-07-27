package com.vattima.lego.inventory.service.advice;

import com.vattima.lego.inventory.service.dto.ApiErrorResponse;
import com.vattima.lego.inventory.service.exception.ValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemInventoryControllerAdviceTest {
    private final ItemInventoryControllerAdvice advice = new ItemInventoryControllerAdvice();

    @Test
    void validationExceptionReturnsStructuredBusinessRuleError() {
        ResponseEntity<ApiErrorResponse> response = advice.validationExceptionHandler(new ValidationException("Nope"), null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Business rule validation failed");
        assertThat(response.getBody().getErrors()).singleElement()
                .satisfies(error -> {
                    assertThat(error.getCode()).isEqualTo("BUSINESS_RULE");
                    assertThat(error.getMessage()).isEqualTo("Nope");
                });
    }

    @Test
    void methodArgumentNotValidReturnsFieldAndObjectErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "inventoryItems[0].itemNumber", "Required"));
        bindingResult.addError(new ObjectError("request", "Platform mismatch"));

        ResponseEntity<ApiErrorResponse> response = advice.handleValidationExceptions(
                new MethodArgumentNotValidException(methodParameter(), bindingResult));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Request validation failed");
        assertThat(response.getBody().getErrors())
                .extracting(error -> error.getField() + ":" + error.getMessage())
                .containsExactly("inventoryItems[0].itemNumber:Required", "request:Platform mismatch");
    }

    @Test
    void constraintViolationReturnsStructuredErrors() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("addItemInventory.arg0.payments");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be empty");

        ResponseEntity<ApiErrorResponse> response = advice.handleConstraintViolationException(new ConstraintViolationException(Set.of(violation)));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrors()).singleElement()
                .satisfies(error -> {
                    assertThat(error.getField()).isEqualTo("addItemInventory.arg0.payments");
                    assertThat(error.getMessage()).isEqualTo("must not be empty");
                    assertThat(error.getCode()).isEqualTo("CONSTRAINT_VIOLATION");
                });
    }

    private MethodParameter methodParameter() throws Exception {
        Method method = getClass().getDeclaredMethod("handler", Object.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void handler(Object request) {
    }
}

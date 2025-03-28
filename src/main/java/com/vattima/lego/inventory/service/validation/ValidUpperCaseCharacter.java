package com.vattima.lego.inventory.service.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = UpperCaseCharacterValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidUpperCaseCharacter {
    String message() default "Invalid character. Must be one of the specified upper-case characters.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String[] allowedChars(); // Define an attribute to specify the valid characters
}

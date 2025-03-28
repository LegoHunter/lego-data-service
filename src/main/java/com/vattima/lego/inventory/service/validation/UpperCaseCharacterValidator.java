package com.vattima.lego.inventory.service.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UpperCaseCharacterValidator implements ConstraintValidator<ValidUpperCaseCharacter, String> {

    private Set<String> validCharacters;

    @Override
    public void initialize(ValidUpperCaseCharacter constraintAnnotation) {
        validCharacters = Stream.of(constraintAnnotation.allowedChars())
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.length() != 1) {
            return false;
        }
        return validCharacters.contains(value);
    }
}
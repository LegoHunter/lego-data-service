package com.vattima.lego.inventory.service.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UpperCaseCharacterValidator implements ConstraintValidator<ValidUpperCaseCharacter, String> {

    private List<String> validCharacters;

    @Override
    public void initialize(ValidUpperCaseCharacter constraintAnnotation) {
        validCharacters = Stream.of(constraintAnnotation.allowedChars())
                .collect(Collectors.toList());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value != null && value.length() == 1 && validCharacters.contains(value)) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(String.format("Value [%s] is invalid. Must be one of %s", value, validCharacters))
                .addConstraintViolation();
        return false;
    }
}

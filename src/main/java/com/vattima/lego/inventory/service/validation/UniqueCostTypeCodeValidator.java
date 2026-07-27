package com.vattima.lego.inventory.service.validation;

import com.vattima.lego.inventory.service.dto.CostRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UniqueCostTypeCodeValidator implements ConstraintValidator<UniqueCostTypeCode, Collection<CostRequest>> {
    @Override
    public boolean isValid(Collection<CostRequest> costRequests, ConstraintValidatorContext context) {
        if (costRequests == null || costRequests.isEmpty()) {
            return true;
        }

        Map<String, Long> duplicatesMap = costRequests.stream()
                .filter(costRequest -> costRequest != null && costRequest.getCostTypeCode() != null)
                .map(CostRequest::getCostTypeCode)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(e -> e.getValue() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (duplicatesMap.isEmpty()) {
            return true;
        } else {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(String.format("Cost types must be unique in the collection. The following duplicates are not allowed [%s]",
                            duplicatesMap.entrySet()
                                    .stream()
                                    .map(e -> String.format("%s: %s", e.getKey(), e.getValue()))
                                    .collect(Collectors.joining())
                            ))
                    .addConstraintViolation();
            return false;
        }
    }

    @Override
    public void initialize(UniqueCostTypeCode constraintAnnotation) {

    }
}

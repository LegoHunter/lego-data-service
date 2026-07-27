package com.vattima.lego.inventory.service.validation;

import com.vattima.lego.inventory.service.dto.CostRequest;
import io.legohunter.data.enums.CurrencyCode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void uniqueCostTypeCodeAllowsNullOrUniqueCostTypes() {
        CostListHolder holder = new CostListHolder();

        holder.setCosts(null);
        assertThat(validator.validate(holder)).isEmpty();

        holder.setCosts(List.of(cost("ITEM"), cost("SHIPPING")));
        assertThat(validator.validate(holder)).isEmpty();
    }

    @Test
    void uniqueCostTypeCodeRejectsDuplicates() {
        CostListHolder holder = new CostListHolder();
        holder.setCosts(List.of(cost("ITEM"), cost("ITEM")));

        assertThat(validator.validate(holder)).singleElement()
                .satisfies(violation -> assertThat(violation.getMessage())
                        .isEqualTo("Cost types must be unique in the collection. The following duplicates are not allowed [ITEM: 2]"));
    }

    @Test
    void valueOfEnumAllowsNullAndKnownEnumName() {
        CurrencyHolder holder = new CurrencyHolder();

        holder.setCurrencyCode(null);
        assertThat(validator.validate(holder)).isEmpty();

        holder.setCurrencyCode("USD");
        assertThat(validator.validate(holder)).isEmpty();
    }

    @Test
    void valueOfEnumRejectsUnknownEnumName() {
        CurrencyHolder holder = new CurrencyHolder();
        holder.setCurrencyCode("US");

        assertThat(validator.validate(holder)).singleElement()
                .satisfies(violation -> assertThat(violation.getMessage())
                        .startsWith("Value [US] is invalid. Must be one of"));
    }

    @Test
    void upperCaseCharacterAllowsConfiguredSingleCharacterOnly() {
        CharacterHolder holder = new CharacterHolder();

        holder.setValue("N");
        assertThat(validator.validate(holder)).isEmpty();

        holder.setValue("NEW");
        assertThat(validator.validate(holder)).singleElement()
                .satisfies(violation -> assertThat(violation.getMessage())
                        .isEqualTo("Value [NEW] is invalid. Must be one of [N, U]"));

        holder.setValue(null);
        assertThat(validator.validate(holder)).singleElement()
                .satisfies(violation -> assertThat(violation.getMessage())
                        .isEqualTo("Value [null] is invalid. Must be one of [N, U]"));
    }

    private CostRequest cost(String costTypeCode) {
        return CostRequest.builder()
                .costTypeCode(costTypeCode)
                .amount(BigDecimal.ONE)
                .currencyCode("USD")
                .build();
    }

    @Getter
    @Setter
    private static class CostListHolder {
        @UniqueCostTypeCode
        private List<CostRequest> costs;
    }

    @Getter
    @Setter
    private static class CurrencyHolder {
        @ValueOfEnum(enumClass = CurrencyCode.class)
        private String currencyCode;
    }

    @Getter
    @Setter
    private static class CharacterHolder {
        @ValidUpperCaseCharacter(allowedChars = {"N", "U"})
        private String value;
    }
}

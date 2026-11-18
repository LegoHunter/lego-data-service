package com.vattima.lego.inventory.service.validation;

import com.vattima.lego.inventory.service.dto.PartyCreateRequest;
import com.vattima.lego.inventory.service.dto.PartyExternalIdentityRequest;
import com.vattima.lego.inventory.service.dto.PartyUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PartyCreateRequestValidationTest {
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
    void aPersonWithOnlyALastNameAndACompanyWithOnlyAFirstNameAreValid() {
        assertThat(validator.validate(PartyCreateRequest.builder().partyType("P").partyLastName("Smith").build()))
                .isEmpty();
        assertThat(validator.validate(PartyCreateRequest.builder().partyType("C").partyFirstName("Brick Shop").build()))
                .isEmpty();
    }

    @Test
    void aFullRequestWithExternalIdentitiesIsValid() {
        PartyCreateRequest request = PartyCreateRequest.builder()
                .partyType("P")
                .partyFirstName("Ann")
                .partyMiddleInitial("Q")
                .partyLastName("Smith")
                .partyEmail("ann@example.com")
                .externalIdentities(List.of(PartyExternalIdentityRequest.builder()
                        .transactionPlatformName("Bricklink").externalPartyId("annbricks").build()))
                .build();

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void theTypeMustBePersonOrCompany() {
        assertThat(fields(PartyCreateRequest.builder().partyFirstName("Ann").build())).containsExactly("partyType");
        assertThat(fields(PartyCreateRequest.builder().partyType("PERSON").partyFirstName("Ann").build()))
                .containsExactly("partyType");
    }

    @Test
    void aNameIsRequired() {
        assertThat(validator.validate(PartyCreateRequest.builder().partyType("P").partyFirstName(" ").build()))
                .singleElement()
                .satisfies(violation -> assertThat(violation.getMessage())
                        .isEqualTo("partyFirstName or partyLastName is required"));
    }

    @Test
    void valuesLongerThanTheirColumnAndABadEmailAreRejected() {
        PartyCreateRequest request = PartyCreateRequest.builder()
                .partyType("P")
                .partyFirstName("A".repeat(51))
                .partyMiddleInitial("QQ")
                .partyEmail("not-an-email")
                .build();

        assertThat(fields(request)).containsExactlyInAnyOrder("partyFirstName", "partyMiddleInitial", "partyEmail");
    }

    @Test
    void anExternalIdentityNeedsAPlatformAndAnId() {
        PartyCreateRequest request = PartyCreateRequest.builder()
                .partyType("P")
                .partyFirstName("Ann")
                .externalIdentities(List.of(PartyExternalIdentityRequest.builder().externalPartyId(" ").build()))
                .build();

        assertThat(fields(request)).containsExactlyInAnyOrder(
                "externalIdentities[0].transactionPlatformName",
                "externalIdentities[0].externalPartyId");
    }

    @Test
    void anUpdateMayLeaveTheTypeOutButNotSendAnUnknownOne() {
        assertThat(validator.validate(PartyUpdateRequest.builder().partyLastName("Smith").build())).isEmpty();
        assertThat(validator.validate(PartyUpdateRequest.builder().partyType("X").partyLastName("Smith").build()))
                .singleElement()
                .satisfies(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("partyType"));
        assertThat(validator.validate(PartyUpdateRequest.builder().partyType("C").build()))
                .singleElement()
                .satisfies(violation -> assertThat(violation.getMessage())
                        .isEqualTo("partyFirstName or partyLastName is required"));
    }

    private static List<String> fields(PartyCreateRequest request) {
        Set<ConstraintViolation<PartyCreateRequest>> violations = validator.validate(request);
        return violations.stream().map(violation -> violation.getPropertyPath().toString()).toList();
    }
}

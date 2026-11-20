package com.vattima.lego.inventory.service.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CostRequestTest {

    @Test
    void equalityUsesCostTypeCodeOnly() {
        CostRequest price = CostRequest.builder()
                .costTypeCode("PRICE")
                .amount(new BigDecimal("10.00"))
                .currencyCode("USD")
                .notes("first")
                .build();
        CostRequest adjustedPrice = CostRequest.builder()
                .costTypeCode("PRICE")
                .amount(new BigDecimal("12.00"))
                .currencyCode("USD")
                .notes("second")
                .build();
        CostRequest shipping = CostRequest.builder()
                .costTypeCode("SHIPPING")
                .amount(new BigDecimal("10.00"))
                .currencyCode("USD")
                .notes("first")
                .build();

        assertThat(price)
                .isEqualTo(adjustedPrice)
                .isNotEqualTo(shipping);
        assertThat(price.hashCode()).isEqualTo(adjustedPrice.hashCode());
    }
}

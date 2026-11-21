package com.vattima.lego.inventory.service.validation;

import com.vattima.lego.inventory.service.exception.ValidationException;
import com.vattima.lego.inventory.service.validation.TransactionCurrencyRules.CostLine;
import com.vattima.lego.inventory.service.validation.TransactionCurrencyRules.PaymentLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionCurrencyRulesTest {

    @Test
    void acceptsOneCurrencyAtRateOne() {
        assertThatCode(() -> TransactionCurrencyRules.validate(
                List.of(cost("USD", "100.00"), cost("USD", "12.49")),
                List.of(payment("USD", "USD", "1.00000", "112.49"))))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsOneCurrencyThatIsNotUsd() {
        assertThatCode(() -> TransactionCurrencyRules.validate(
                List.of(cost("CAD", "50.00")),
                List.of(payment("CAD", "CAD", "1", "50.00"))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsARateOtherThanOneWithOneCurrency() {
        assertThatThrownBy(() -> TransactionCurrencyRules.validate(
                List.of(cost("USD", "100.00")),
                List.of(payment("USD", "USD", "1.3606", "100.00"))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("exchangeRate must be 1 when the transaction uses one currency [USD]: 1.3606");
    }

    @Test
    void rejectsAMissingRate() {
        assertThatThrownBy(() -> TransactionCurrencyRules.validate(
                List.of(cost("USD", "100.00")),
                List.of(payment("USD", "USD", null, "100.00"))))
                .isInstanceOf(ValidationException.class)
                .hasMessageStartingWith("exchangeRate is required on every payment");
    }

    @Test
    void rejectsARateOfZero() {
        assertThatThrownBy(() -> TransactionCurrencyRules.validate(
                List.of(cost("USD", "100.00")),
                List.of(payment("USD", "CAD", "0", "100.00"))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("exchangeRate must be greater than 0: 0");
    }

    @Test
    void keepsTheExactTotalsCheckForOneCurrency() {
        assertThatThrownBy(() -> TransactionCurrencyRules.validate(
                List.of(cost("USD", "2.00")),
                List.of(payment("USD", "USD", "1", "1.99"))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Total payments must equal total costs. costs=2.00, payments=1.99");
    }

    /** Set 138-1: 900 USD + 4.99 USD PayPal fee + shipping in CAD, paid 987.27 USD at 1 USD = 1.3606 CAD. */
    @Test
    void acceptsThePurchaseFromCanadaWhicheverWayTheShippingCentRounds() {
        for (String shippingCad : List.of("111.95", "111.96")) {
            assertThatCode(() -> TransactionCurrencyRules.validate(
                    List.of(cost("USD", "900.00"), cost("USD", "4.99"), cost("CAD", shippingCad)),
                    List.of(payment("USD", "CAD", "1.3606", "987.27"))))
                    .as("shipping %s CAD", shippingCad)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void acceptsAPaymentMadeInTheOtherCurrency() {
        // 100 USD price + 20 CAD shipping = 156.06 CAD at 1.3606 per USD.
        assertThatCode(() -> TransactionCurrencyRules.validate(
                List.of(cost("USD", "100.00"), cost("CAD", "20.00")),
                List.of(payment("CAD", "CAD", "1.3606", "156.06"))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTwoCurrenciesThatAreMoreThanACentPerConvertedLineApart() {
        // The 40 CAD shipping first quoted for 138-1 leaves the 987.27 USD payment about 52.88 USD over.
        assertThatThrownBy(() -> TransactionCurrencyRules.validate(
                List.of(cost("USD", "900.00"), cost("USD", "4.99"), cost("CAD", "40.00")),
                List.of(payment("USD", "CAD", "1.3606", "987.27"))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Total payments must equal total costs in USD at 1.3606 per USD. costs=934.3888, "
                        + "payments=987.2700");
    }

    @Test
    void rejectsAThirdCurrency() {
        assertThatThrownBy(() -> TransactionCurrencyRules.validate(
                List.of(cost("USD", "100.00"), cost("EUR", "10.00")),
                List.of(payment("USD", "CAD", "1.3606", "110.00"))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("A transaction may use at most two currencies, USD and the other party's: [USD, EUR, CAD]");
    }

    @Test
    void rejectsTwoCurrenciesWithoutUsd() {
        assertThatThrownBy(() -> TransactionCurrencyRules.validate(
                List.of(cost("EUR", "100.00")),
                List.of(payment("EUR", "CAD", "1.5", "100.00"))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("A transaction with two currencies must have USD as one of them: [EUR, CAD]");
    }

    @Test
    void rejectsPaymentsWithDifferentRates() {
        assertThatThrownBy(() -> TransactionCurrencyRules.validate(
                List.of(cost("USD", "100.00"), cost("CAD", "20.00")),
                List.of(payment("USD", "CAD", "1.3606", "50.00"), payment("USD", "CAD", "1.36060", "30.00"),
                        payment("USD", "CAD", "1.35", "34.70"))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("All payments in a transaction with two currencies must use the same exchangeRate: "
                        + "[1.3606, 1.35]");
    }

    @Test
    void rejectsNoPayments() {
        assertThatThrownBy(() -> TransactionCurrencyRules.validate(List.of(cost("USD", "1.00")), List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("At least one payment is required");
    }

    private static CostLine cost(String currency, String amount) {
        return new CostLine(currency, new BigDecimal(amount));
    }

    private static PaymentLine payment(String currency, String sellerCurrency, String rate, String amount) {
        return new PaymentLine(currency, sellerCurrency, rate == null ? null : new BigDecimal(rate), new BigDecimal(amount));
    }
}

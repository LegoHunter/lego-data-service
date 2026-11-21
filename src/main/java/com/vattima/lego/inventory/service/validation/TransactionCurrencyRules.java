package com.vattima.lego.inventory.service.validation;

import com.vattima.lego.inventory.service.exception.ValidationException;
import io.legohunter.data.enums.CurrencyCode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The currency rules every transaction must meet, both when it is created and after each correction (HOMELAB-130).
 *
 * <ul>
 *   <li>A transaction uses one currency, or two: USD and the other party's currency. Every cost's and payment's
 *       {@code currencyCode} and every payment's {@code sellerCurrencyCode} count.</li>
 *   <li>Every payment carries an {@code exchangeRate}. With one currency it is 1. With two it is the number of
 *       units of the other currency per 1 USD (PayPal's form: "1 USD = 1.3606 CAD"), and all payments share it.</li>
 *   <li>Payments equal costs. With two currencies both sides are converted to USD first, and the totals may differ
 *       by at most one cent per converted line, because each converted line can be a rounding cent away.</li>
 * </ul>
 */
public final class TransactionCurrencyRules {
    public static final String HOME_CURRENCY = CurrencyCode.USD.name();

    private static final MathContext CONVERSION = new MathContext(20, RoundingMode.HALF_EVEN);
    private static final BigDecimal CENT = new BigDecimal("0.01");

    /** One cost: its currency and amount. */
    public record CostLine(String currencyCode, BigDecimal amount) {
    }

    /** One payment: the currency paid, the other party's currency, the rate and the amount. */
    public record PaymentLine(String currencyCode, String sellerCurrencyCode, BigDecimal exchangeRate, BigDecimal amount) {
    }

    private TransactionCurrencyRules() {
    }

    /** Applies every rule above; throws {@link ValidationException} on the first one broken. */
    public static void validate(Collection<CostLine> costs, Collection<PaymentLine> payments) {
        List<CostLine> costLines = costs.stream().filter(Objects::nonNull).toList();
        List<PaymentLine> paymentLines = payments.stream().filter(Objects::nonNull).toList();
        if (paymentLines.isEmpty()) {
            throw new ValidationException("At least one payment is required");
        }

        Set<String> currencies = currencies(costLines, paymentLines);
        if (currencies.size() > 2) {
            throw new ValidationException("A transaction may use at most two currencies, USD and the other party's: "
                    + currencies);
        }
        if (currencies.size() == 2 && !currencies.contains(HOME_CURRENCY)) {
            throw new ValidationException("A transaction with two currencies must have USD as one of them: " + currencies);
        }

        paymentLines.forEach(payment -> {
            if (payment.exchangeRate() == null) {
                throw new ValidationException("exchangeRate is required on every payment: 1 when the transaction uses "
                        + "one currency, otherwise the units of the other currency per 1 USD");
            }
            if (payment.exchangeRate().signum() <= 0) {
                throw new ValidationException("exchangeRate must be greater than 0: " + payment.exchangeRate());
            }
        });

        if (currencies.size() <= 1) {
            paymentLines.forEach(payment -> {
                if (payment.exchangeRate().compareTo(BigDecimal.ONE) != 0) {
                    throw new ValidationException("exchangeRate must be 1 when the transaction uses one currency "
                            + currencies + ": " + payment.exchangeRate());
                }
            });
            validateTotals(sum(costLines.stream().map(CostLine::amount)), sum(paymentLines.stream().map(PaymentLine::amount)),
                    BigDecimal.ZERO, "");
            return;
        }

        Set<String> rates = new LinkedHashSet<>();
        paymentLines.forEach(payment -> rates.add(payment.exchangeRate().stripTrailingZeros().toPlainString()));
        if (rates.size() > 1) {
            throw new ValidationException("All payments in a transaction with two currencies must use the same "
                    + "exchangeRate: " + rates);
        }
        BigDecimal rate = paymentLines.getFirst().exchangeRate();

        long convertedLines = Stream.concat(
                        costLines.stream().map(CostLine::currencyCode),
                        paymentLines.stream().map(PaymentLine::currencyCode))
                .filter(currency -> !HOME_CURRENCY.equals(currency))
                .count();
        BigDecimal costTotal = sum(costLines.stream().map(cost -> toUsd(cost.currencyCode(), cost.amount(), rate)));
        BigDecimal paymentTotal = sum(paymentLines.stream()
                .map(payment -> toUsd(payment.currencyCode(), payment.amount(), rate)));
        validateTotals(costTotal, paymentTotal, CENT.multiply(BigDecimal.valueOf(convertedLines)),
                " in USD at " + rate.stripTrailingZeros().toPlainString() + " per USD");
    }

    private static Set<String> currencies(List<CostLine> costs, List<PaymentLine> payments) {
        Set<String> currencies = new LinkedHashSet<>();
        costs.stream().map(CostLine::currencyCode).filter(Objects::nonNull).forEach(currencies::add);
        payments.forEach(payment -> {
            if (payment.currencyCode() != null) {
                currencies.add(payment.currencyCode());
            }
            if (payment.sellerCurrencyCode() != null) {
                currencies.add(payment.sellerCurrencyCode());
            }
        });
        return currencies;
    }

    private static BigDecimal toUsd(String currencyCode, BigDecimal amount, BigDecimal rate) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return HOME_CURRENCY.equals(currencyCode) ? amount : amount.divide(rate, CONVERSION);
    }

    private static BigDecimal sum(Stream<BigDecimal> amounts) {
        return amounts.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void validateTotals(BigDecimal costTotal, BigDecimal paymentTotal, BigDecimal tolerance, String basis) {
        if (costTotal.subtract(paymentTotal).abs().compareTo(tolerance) > 0) {
            throw new ValidationException("Total payments must equal total costs" + basis + ". costs="
                    + display(costTotal, tolerance) + ", payments=" + display(paymentTotal, tolerance));
        }
    }

    private static String display(BigDecimal amount, BigDecimal tolerance) {
        return tolerance.signum() == 0 ? amount.toPlainString() : amount.setScale(4, RoundingMode.HALF_EVEN).toPlainString();
    }
}

package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Way's own fee on a Park'N Fly Canada stay.
 *
 * <p>Charged on top of the vendor's amounts, so every screen must include it or the quoted price
 * is not the billed one. Callers map their own representation into {@link Rule}; turning a
 * percentage into money, and where it rounds, lives here once.
 */
public final class ParkNflyCanadaWayFee {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private ParkNflyCanadaWayFee() {
    }

    /**
     * One of Way's fee rules, reduced to the two fields that decide the money.
     *
     * @param type  {@code PERCENT}, {@code PERCENTAGE} or {@code %} for a share of the amount;
     *              {@code AMOUNT}, {@code FLAT} or {@code $} for a fixed charge
     * @param value the percentage or the fixed amount, depending on {@code type}
     */
    public record Rule(String type, BigDecimal value) {
    }

    /**
     * Way's fee on {@code amount}, to the cent.
     *
     * <p>Charged on the vendor's base - what the customer is actually buying - and never on the
     * schedule price, which this vendor does not charge, nor on the vendor's own surcharge and tax,
     * which are not Way's to take a percentage of.
     */
    public static BigDecimal on(BigDecimal amount, List<Rule> rules) {
        if (amount == null || rules == null || rules.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Rule rule : rules) {
            total = total.add(feeFor(amount, rule));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal feeFor(BigDecimal amount, Rule rule) {
        if (rule == null || rule.type() == null || rule.value() == null
                || rule.value().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        String type = rule.type().trim();
        if ("PERCENT".equalsIgnoreCase(type) || "PERCENTAGE".equalsIgnoreCase(type) || "%".equals(type)) {
            return amount.multiply(rule.value()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }
        if ("AMOUNT".equalsIgnoreCase(type) || "FLAT".equalsIgnoreCase(type) || "$".equals(type)) {
            return rule.value().setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }
}

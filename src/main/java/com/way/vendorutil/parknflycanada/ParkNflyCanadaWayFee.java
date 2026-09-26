package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Way's own fee on a Park'N Fly Canada stay.
 *
 * <p>Way charges its fee on top of what the vendor charges, and every screen the customer sees has
 * to include it or the price they are quoted is not the price they are billed. That had gone wrong
 * twice: search showed the vendor total alone and jumped by Way's fee at the payment step, and the
 * lot detail page showed the parking base with neither the vendor's surcharge and tax nor this.
 *
 * <p>Each service holds these rules in its own shape - a packed string in search, DTOs in orders -
 * so this takes the two fields that actually matter and nothing else. The caller maps its own
 * representation into {@link Rule}; how a percentage becomes money, and where it rounds, lives here
 * once.
 *
 * <p>Only rules Way charges belong here. The same table also carries the vendor's own tax rules,
 * and for this vendor those are not used: the surcharge and sales tax come from the rate card and
 * the lot's own settings, so counting them again would tax the stay twice.
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
     *
     * <p>Zero for no rules, unreadable rules or a missing amount. A lot with no Way fee is
     * ordinary, and inventing one would overstate the bill as surely as omitting a real one
     * understates it.
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
        // An unknown type is not guessed at. Charging on a rule nobody has defined is worse than
        // charging nothing, and the total still reconciles against the vendor invoice either way.
        return BigDecimal.ZERO;
    }
}

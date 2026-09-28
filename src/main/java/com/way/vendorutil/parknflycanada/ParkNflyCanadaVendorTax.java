package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Park'N Fly Canada's own surcharge and sales tax, read from the listing's vendor tax rules.
 */
public record ParkNflyCanadaVendorTax(BigDecimal fuelSurcharge, BigDecimal taxRate) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     */
    public static final ParkNflyCanadaVendorTax NONE = new ParkNflyCanadaVendorTax(null, null);

    /**
     * Reads the vendor rules into a surcharge and a tax rate.
     *
     * <p>Several rules of the same kind add up, which is how a location with two separate levies
     * is expressed without inventing anything. Anything unreadable is skipped rather than failing
     * the price: one malformed row must not take a lot off sale.
     *
     * @param rules the listing's {@code VENDOR} rules, already parsed into type and value
     * @return the amounts found, or {@link #NONE} when the listing carries no vendor rules
     */
    public static ParkNflyCanadaVendorTax from(List<ParkNflyCanadaWayFee.Rule> rules) {
        if (rules == null || rules.isEmpty()) {
            return NONE;
        }
        BigDecimal surcharge = null;
        BigDecimal percent = null;
        for (ParkNflyCanadaWayFee.Rule rule : rules) {
            if (rule == null || rule.type() == null || rule.value() == null) {
                continue;
            }
            String type = rule.type().trim();
            if ("PERCENT".equalsIgnoreCase(type) || "PERCENTAGE".equalsIgnoreCase(type)
                    || "%".equals(type)) {
                percent = percent == null ? rule.value() : percent.add(rule.value());
            } else if ("AMOUNT".equalsIgnoreCase(type) || "FLAT".equalsIgnoreCase(type)
                    || "$".equals(type)) {
                surcharge = surcharge == null ? rule.value() : surcharge.add(rule.value());
            }
        }
        return surcharge == null && percent == null ? NONE
                : new ParkNflyCanadaVendorTax(surcharge, percent);
    }

    /**
     */
    public boolean isEmpty() {
        return fuelSurcharge == null && taxRate == null;
    }

    /**
     * The tax rule for this lot.
     * @param provinceRule
     */
    public ParkNflyCanadaTaxRule applyTo(ParkNflyCanadaTaxRule provinceRule, String province) {
        if (provinceRule == null && isEmpty()) {
            return null;
        }
        if (provinceRule == null && (province == null || province.trim().isEmpty())) {
            return null;
        }
        BigDecimal rate = taxRate != null
                ? taxRate.divide(HUNDRED, 6, RoundingMode.HALF_UP)
                : provinceRule == null ? null : provinceRule.hstRate();
        BigDecimal fuel = fuelSurcharge != null ? fuelSurcharge
                : provinceRule == null ? null : provinceRule.fuelSurcharge();
        if (rate == null || fuel == null) {
            return null;
        }
        return new ParkNflyCanadaTaxRule(
                provinceRule != null ? provinceRule.province() : province, rate, fuel);
    }
}

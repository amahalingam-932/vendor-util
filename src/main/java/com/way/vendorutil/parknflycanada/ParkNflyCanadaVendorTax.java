package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Park'N Fly Canada's own surcharge and sales tax, read from the listing's vendor tax rules.
 *
 * <p>These amounts are not in the vendor's API. Park'N Fly tell Way the fuel surcharge and the
 * sales tax for each location as it opens, and Ops enter them so a new lot can be sold without an
 * engineering release.
 *
 * <p>They are held the way every other vendor's are: rows in {@code tbl_listing_tax_rule} with a
 * tax type of {@code VENDOR}, each carrying a small JSON rule in {@code LTA_CommissionTaxClob}.
 * This vendor briefly had three listing attributes of its own instead, which meant a second place
 * to configure a tax, a second screen for Ops and a migration to create the keys. Reading the
 * rules every other vendor already uses removes all three.
 *
 * <ul>
 *   <li>{@code {"type":"AMOUNT","value":3.96}} - the flat fuel surcharge, once per reservation</li>
 *   <li>{@code {"type":"PERCENT","value":13}} - the sales tax, as a percentage of the base</li>
 *   <li>{@code {"type":"TAX_ON_FUEL","value":1}} - whether the surcharge is inside the taxable
 *       amount; 1 for yes, 0 or absent for no</li>
 * </ul>
 *
 * <h2>The tax basis</h2>
 *
 * <p>By default the tax applies to the base alone. That is what Way's shared evaluator does for
 * every other vendor, and it matches what Park'N Fly themselves return: a 12.99 base comes back
 * with 1.69 of tax, which is thirteen per cent of 12.99 and not of 16.95. Their IT director has
 * stated the opposite in writing, so Ops can override it per lot from the WayPanel screen, and
 * {@code TAX_ON_FUEL} carries that choice.
 *
 * <p>The switch is per listing on purpose. A service-wide flag was tried and removed: it forced one
 * answer onto every Canadian lot, when the disagreement is with a particular location's billing.
 */
public record ParkNflyCanadaVendorTax(BigDecimal fuelSurcharge, BigDecimal taxRate, Boolean taxOnFuel) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Nothing configured on the listing; the caller falls back to its province default. */
    public static final ParkNflyCanadaVendorTax NONE = new ParkNflyCanadaVendorTax(null, null, null);

    /** Kept so callers that never cared about the tax basis still compile and behave as before. */
    public ParkNflyCanadaVendorTax(BigDecimal fuelSurcharge, BigDecimal taxRate) {
        this(fuelSurcharge, taxRate, null);
    }

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
        Boolean onFuel = null;
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
            } else if ("TAX_ON_FUEL".equalsIgnoreCase(type) || "HST_ON_FUEL".equalsIgnoreCase(type)) {
                // A flag, not a levy, so the last row wins rather than the rows adding up - two
                // of these summing to 2 would mean nothing.
                onFuel = rule.value().signum() != 0;
            }
        }
        return surcharge == null && percent == null && onFuel == null ? NONE
                : new ParkNflyCanadaVendorTax(surcharge, percent, onFuel);
    }

    /** True when the listing configured nothing, so the province default should stand. */
    public boolean isEmpty() {
        return fuelSurcharge == null && taxRate == null && taxOnFuel == null;
    }

    /**
     * Whether the fuel surcharge sits inside the taxable amount for this lot.
     *
     * <p>False unless the listing says otherwise, which keeps every lot configured before this
     * existed on the basis the vendor's own {@code confirmationGet} demonstrates.
     */
    public boolean taxIncludesFuel() {
        return Boolean.TRUE.equals(taxOnFuel);
    }

    /**
     * The province rule with whatever this listing overrides applied on top.
     *
     * <p>Each amount is considered on its own: a lot may set its surcharge and inherit the
     * province's tax rate, or the other way round. A blank stays blank and inherits.
     */
    public ParkNflyCanadaTaxRule applyTo(ParkNflyCanadaTaxRule provinceRule, String province) {
        if (provinceRule == null && isEmpty()) {
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

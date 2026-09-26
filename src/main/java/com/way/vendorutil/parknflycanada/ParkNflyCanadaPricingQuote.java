package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;

/**
 * Stay quote for Park'N Fly Canada checkout and vendor statement math.
 *
 * <p>{@link #customerTotal()} is what the customer pays Way ({@code base + fuelSurcharge + hst}).
 * {@link #wayCommission()} and {@link #vendorNet()} describe the vendor statement only and are
 * never subtracted from what the customer is charged.
 *
 * <p>The quote carries the same stay twice. The first set of amounts is in the currency Way
 * charges the customer in and is what every screen, payment and refund uses. The {@code vendor}
 * set is the same stay in the currency Park'N Fly Canada quote and invoice in, and exists for one
 * reason: the fee reconciliation compares Way's figure against their {@code total_estimated_fee},
 * which is a CAD amount. Comparing a converted number against it would either flag every booking
 * or, with a tolerance wide enough to absorb the exchange rate, hide a genuine mismatch.
 *
 * <p>When no conversion applies the two sets are identical and {@link #conversionRate()} is one,
 * which is exactly the behaviour before currency was handled at all.
 */
public record ParkNflyCanadaPricingQuote(
        int billableDays,
        String province,
        BigDecimal base,
        BigDecimal fuelSurcharge,
        BigDecimal hst,
        BigDecimal customerTotal,
        BigDecimal wayCommission,
        BigDecimal vendorNet,
        BigDecimal vendorBase,
        BigDecimal vendorFuelSurcharge,
        BigDecimal vendorHst,
        BigDecimal vendorTotal,
        BigDecimal conversionRate) {

    /**
     * A quote in a single currency, where the vendor's amounts are charged as they arrive.
     *
     * <p>Kept so existing callers and tests read unchanged; it is the same thing as applying
     * {@link ParkNflyCanadaCurrencyConversion#NONE}.
     */
    public ParkNflyCanadaPricingQuote(int billableDays, String province, BigDecimal base,
            BigDecimal fuelSurcharge, BigDecimal hst, BigDecimal customerTotal, BigDecimal wayCommission,
            BigDecimal vendorNet) {
        this(billableDays, province, base, fuelSurcharge, hst, customerTotal, wayCommission, vendorNet,
                base, fuelSurcharge, hst, customerTotal, BigDecimal.ONE);
    }

    /** Fuel surcharge plus tax, i.e. everything the customer pays on top of the parking base. */
    public BigDecimal feesAndTaxes() {
        return fuelSurcharge.add(hst);
    }

    /** The same figure in the vendor's currency, for reconciliation rather than display. */
    public BigDecimal vendorFeesAndTaxes() {
        return vendorFuelSurcharge.add(vendorHst);
    }

    /** True when the customer is charged in a different currency from the vendor's invoice. */
    public boolean currencyConverted() {
        return conversionRate != null && BigDecimal.ONE.compareTo(conversionRate) != 0;
    }
}

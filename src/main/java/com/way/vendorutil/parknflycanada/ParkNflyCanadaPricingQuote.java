package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;

/**
 * Stay quote for Park'N Fly Canada checkout and vendor statement math.
 *
 * <p>{@link #customerTotal()} is what the customer pays Way ({@code base + fuelSurcharge + hst}).
 * {@link #wayCommission()} and {@link #vendorNet()} describe the vendor statement only and are
 * never subtracted from what the customer is charged.
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
     */
    public ParkNflyCanadaPricingQuote(int billableDays, String province, BigDecimal base,
            BigDecimal fuelSurcharge, BigDecimal hst, BigDecimal customerTotal, BigDecimal wayCommission,
            BigDecimal vendorNet) {
        this(billableDays, province, base, fuelSurcharge, hst, customerTotal, wayCommission, vendorNet,
                base, fuelSurcharge, hst, customerTotal, BigDecimal.ONE);
    }

    /**
     */
    public BigDecimal feesAndTaxes() {
        return fuelSurcharge.add(hst);
    }

    /**
     */
    public boolean currencyConverted() {
        return conversionRate != null && BigDecimal.ONE.compareTo(conversionRate) != 0;
    }
}

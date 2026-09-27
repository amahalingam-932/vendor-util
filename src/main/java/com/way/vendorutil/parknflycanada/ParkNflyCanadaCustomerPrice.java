package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;

/**
 * The one price a customer is shown and charged for a Park'N Fly Canada stay.
 *
 * @param base          parking for the stay, at the vendor's banded rate
 * @param fuelSurcharge the vendor's flat per-reservation surcharge
 * @param vendorTax     the vendor's sales tax
 * @param wayFee        Way's own fee, charged on the base
 * @param billableDays  whole days charged
 */
public record ParkNflyCanadaCustomerPrice(
        BigDecimal base,
        BigDecimal fuelSurcharge,
        BigDecimal vendorTax,
        BigDecimal wayFee,
        int billableDays) {

    /**
     * Assembles the customer price from the vendor quote and Way's fee.
     *
     * @param wayFee may be null or zero; a lot with no Way fee pays the vendor total and nothing
     *               invented on top
     */
    public static ParkNflyCanadaCustomerPrice of(ParkNflyCanadaPricingQuote quote, BigDecimal wayFee) {
        return new ParkNflyCanadaCustomerPrice(
                quote.base(),
                quote.fuelSurcharge(),
                quote.hst(),
                wayFee == null ? BigDecimal.ZERO : wayFee,
                quote.billableDays());
    }

    /**
     */
    public BigDecimal total() {
        return base.add(fuelSurcharge).add(vendorTax).add(wayFee);
    }

    /**
     * Everything charged on top of the parking itself.
     */
    public BigDecimal taxesAndFees() {
        return vendorTax.add(wayFee);
    }
}

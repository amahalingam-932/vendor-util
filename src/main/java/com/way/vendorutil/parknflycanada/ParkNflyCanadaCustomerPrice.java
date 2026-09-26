package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;

/**
 * The one price a customer is shown and charged for a Park'N Fly Canada stay.
 *
 * <p>Three services work this out independently - search, the lot detail page and checkout - and
 * each had been given a different part of it. Search left out Way's fee and advertised a total ten
 * dollars below the bill. The detail page showed the parking base alone, missing the surcharge, the
 * tax and the fee. Each was found by opening the page and reading the number, which is not a way to
 * be sure about money.
 *
 * <p>So the assembly lives here. {@link ParkNflyCanadaPricingCalculator} already produces the
 * vendor's side identically everywhere; this adds Way's fee to it and hands back both the total and
 * the lines that explain it, so a screen cannot show a figure that disagrees with the one next to
 * the pay button.
 *
 * @param base         parking for the stay, at the vendor's banded rate
 * @param fuelSurcharge the vendor's flat per-reservation surcharge
 * @param vendorTax    the vendor's sales tax
 * @param wayFee       Way's own fee, charged on the base
 * @param billableDays whole days charged, which is what makes a banded rate legible
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

    /** What the customer pays. Every screen showing a total shows this one. */
    public BigDecimal total() {
        return base.add(fuelSurcharge).add(vendorTax).add(wayFee);
    }

    /**
     * Everything charged on top of the parking itself.
     *
     * <p>The vendor's tax and Way's fee together, which is how the checkout summary already groups
     * them - the customer is not being asked to care which of the two organisations levies what.
     * The fuel surcharge stays on its own line because the vendor bills it as a distinct item.
     */
    public BigDecimal taxesAndFees() {
        return vendorTax.add(wayFee);
    }
}

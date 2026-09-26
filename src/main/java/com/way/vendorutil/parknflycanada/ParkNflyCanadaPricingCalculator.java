package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Customer checkout and vendor-statement math for Park'N Fly Canada.
 *
 * <p>The customer pays {@code base + fuel + tax}. Way's commission is a share of that total on the
 * vendor statement, not a deduction from what the customer pays, so it never reduces
 * {@link ParkNflyCanadaPricingQuote#customerTotal()}.
 *
 * <p>The sales tax applies to the parking base alone unless the listing says otherwise. That
 * default is what Way's shared tax evaluator does for every other vendor, and it is what Park'N
 * Fly themselves return - a 12.99 base comes back with 1.69 of tax, which is thirteen per cent of
 * 12.99 and not of 16.95.
 *
 * <p>Park'N Fly's IT director has stated the opposite in writing, so the basis is settable per lot
 * through the listing's {@code TAX_ON_FUEL} vendor tax rule, which Ops enter in WayPanel. Per lot
 * rather than per service: a service-wide flag was tried and removed because it forced one answer
 * onto every Canadian location when the disagreement concerns particular ones. The lot's surcharge
 * and tax rate come from the same ordinary {@code VENDOR} tax rules.
 */
public final class ParkNflyCanadaPricingCalculator {

    private static final RoundingMode MONEY = RoundingMode.HALF_UP;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ParkNflyCanadaPricingCalculator() {
    }

    /**
     * @param billableDays         whole parking days, as charged by the vendor's day rate
     * @param dayRate              {@code day_rate} from {@code locationListGet}
     * @param weekRate             {@code week_rate} from {@code locationListGet}
     * @param taxRule              province fuel surcharge and tax rate
     * @param wayCommissionPercent Way's statement commission as a percent of base (e.g. 15)
     */
    public static ParkNflyCanadaPricingQuote quote(int billableDays, BigDecimal dayRate, BigDecimal weekRate,
            ParkNflyCanadaTaxRule taxRule, BigDecimal wayCommissionPercent) {
        return quote(billableDays, dayRate, weekRate, taxRule, wayCommissionPercent,
                ParkNflyCanadaCurrencyConversion.NONE, false);
    }

    /** As above, with the tax basis this lot is configured for. */
    public static ParkNflyCanadaPricingQuote quote(int billableDays, BigDecimal dayRate, BigDecimal weekRate,
            ParkNflyCanadaTaxRule taxRule, BigDecimal wayCommissionPercent, boolean taxIncludesFuel) {
        return quote(billableDays, dayRate, weekRate, taxRule, wayCommissionPercent,
                ParkNflyCanadaCurrencyConversion.NONE, taxIncludesFuel);
    }

    /**
     * As above, converting the customer-facing amounts into the currency Way charges in.
     *
     * <p>The stay is priced in the vendor's currency first and only then converted, so the tax
     * stays a percentage of the base the vendor actually charges. Pricing from converted unit
     * rates instead would give an HST figure that could never match their
     * {@code total_estimated_fee}, and the fee reconciliation compares against precisely that.
     *
     * <p>Way's commission is taken on the converted base, because it is Way's revenue in Way's own
     * currency rather than a share of the vendor's invoice.
     *
     * @param conversion vendor-to-checkout currency conversion, or
     *                   {@link ParkNflyCanadaCurrencyConversion#NONE} to charge the vendor's
     *                   amounts unchanged
     */
    public static ParkNflyCanadaPricingQuote quote(int billableDays, BigDecimal dayRate, BigDecimal weekRate,
            ParkNflyCanadaTaxRule taxRule, BigDecimal wayCommissionPercent,
            ParkNflyCanadaCurrencyConversion conversion) {
        return quote(billableDays, dayRate, weekRate, taxRule, wayCommissionPercent, conversion, false);
    }

    /**
     * As above, with the tax basis this lot is configured for.
     *
     * @param taxIncludesFuel true when the fuel surcharge sits inside the taxable amount, which is
     *                        the per-listing {@code TAX_ON_FUEL} vendor rule. False - the default -
     *                        taxes the parking base alone.
     */
    public static ParkNflyCanadaPricingQuote quote(int billableDays, BigDecimal dayRate, BigDecimal weekRate,
            ParkNflyCanadaTaxRule taxRule, BigDecimal wayCommissionPercent,
            ParkNflyCanadaCurrencyConversion conversion, boolean taxIncludesFuel) {
        if (taxRule == null) {
            throw new IllegalArgumentException("taxRule is required");
        }
        if (wayCommissionPercent == null) {
            throw new IllegalArgumentException("wayCommissionPercent is required");
        }
        ParkNflyCanadaCurrencyConversion fx =
                conversion == null ? ParkNflyCanadaCurrencyConversion.NONE : conversion;

        // Priced in the vendor's currency: this is what Park'N Fly Canada will invoice.
        BigDecimal vendorBase = ParkNflyCanadaStayBaseCalculator.calculateBase(billableDays, dayRate, weekRate);
        BigDecimal vendorFuel = scaleMoney(taxRule.fuelSurcharge());
        // The base alone by default: that is what Way's shared tax evaluator does for every other
        // vendor, and it matches what Park'N Fly return - a 12.99 base comes back with 1.69 of tax,
        // which is thirteen per cent of 12.99 and not of 16.95. A lot whose TAX_ON_FUEL rule says
        // otherwise taxes base plus surcharge instead, following the vendor's written policy for
        // that location. One lot's answer, never a service-wide one.
        BigDecimal taxableAmount = taxIncludesFuel ? vendorBase.add(vendorFuel) : vendorBase;
        BigDecimal vendorHst = scaleMoney(taxableAmount.multiply(taxRule.hstRate()));
        BigDecimal vendorTotal = vendorBase.add(vendorFuel).add(vendorHst);

        // Converted for the customer. Each line is converted so the breakdown still sums to the
        // total on screen, rather than converting the total and leaving the lines not adding up.
        BigDecimal base = fx.convert(vendorBase);
        BigDecimal fuel = fx.convert(vendorFuel);
        BigDecimal hst = fx.convert(vendorHst);
        BigDecimal customerTotal = base.add(fuel).add(hst);

        BigDecimal wayCommission = scaleMoney(base.multiply(wayCommissionPercent)
                .divide(HUNDRED, MONEY_SCALE + 2, MONEY));
        BigDecimal vendorNet = customerTotal.subtract(wayCommission);
        return new ParkNflyCanadaPricingQuote(billableDays, taxRule.province(), base, fuel, hst, customerTotal,
                wayCommission, vendorNet, vendorBase, vendorFuel, vendorHst, vendorTotal, fx.rate());
    }

    private static BigDecimal scaleMoney(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, MONEY);
    }
}

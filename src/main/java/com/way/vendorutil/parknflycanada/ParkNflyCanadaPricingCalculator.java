package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Customer checkout and vendor-statement math for Park'N Fly Canada.
 *
 * <p>The customer pays {@code base + fuel + tax}. Way's commission is a share of that total on the
 * vendor statement, not a deduction from it, so it never reduces
 * {@link ParkNflyCanadaPricingQuote#customerTotal()}.
 */
public final class ParkNflyCanadaPricingCalculator {

    private static final RoundingMode MONEY = RoundingMode.HALF_UP;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ParkNflyCanadaPricingCalculator() {
    }

    /**
     *
     * @param billableDays         whole parking days, as charged by the vendor's day rate
     * @param dayRate              {@code day_rate} from {@code locationListGet}
     * @param weekRate             {@code week_rate} from {@code locationListGet}
     * @param taxRule              province fuel surcharge and tax rate
     * @param wayCommissionPercent Way's statement commission as a percent of base (e.g. 15)
     */
    public static ParkNflyCanadaPricingQuote quote(int billableDays, BigDecimal dayRate, BigDecimal weekRate,
            ParkNflyCanadaTaxRule taxRule, BigDecimal wayCommissionPercent) {
        return quote(billableDays, dayRate, weekRate, taxRule, wayCommissionPercent,
                ParkNflyCanadaCurrencyConversion.NONE);
    }

    /**
     * As above, converting the customer-facing amounts into the currency Way charges in.
     *
     * @param conversion vendor-to-checkout currency conversion
     */
    public static ParkNflyCanadaPricingQuote quote(int billableDays, BigDecimal dayRate, BigDecimal weekRate,
            ParkNflyCanadaTaxRule taxRule, BigDecimal wayCommissionPercent,
            ParkNflyCanadaCurrencyConversion conversion) {
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
        // The parking base alone, matching what Park'N Fly return: a 12.99 base comes back with
        // 1.69 of tax, which is thirteen per cent of 12.99 and not of 16.95.
        BigDecimal vendorHst = scaleMoney(vendorBase.multiply(taxRule.hstRate()));
        BigDecimal vendorTotal = vendorBase.add(vendorFuel).add(vendorHst);

        // Converted for the customer. Each line is converted so the breakdown still sums to the
        // total on screen, rather than converting the total and leaving the lines not adding up.
        BigDecimal base = fx.convert(vendorBase);
        BigDecimal fuel = fx.convert(vendorFuel);
        BigDecimal hst = fx.convert(vendorHst);
        BigDecimal customerTotal = base.add(fuel).add(hst);

        BigDecimal wayCommission = commissionOn(base, wayCommissionPercent);
        BigDecimal vendorNet = customerTotal.subtract(wayCommission);
        return new ParkNflyCanadaPricingQuote(billableDays, taxRule.province(), base, fuel, hst, customerTotal,
                wayCommission, vendorNet, vendorBase, vendorFuel, vendorHst, vendorTotal, fx.rate());
    }

    /**
     * Way's commission on a stay's base, as money.
     *
     * <p>Taken on the base alone - never on the fuel surcharge or the sales tax, which are the
     * vendor's and the government's respectively - and on the base in the currency Way charges in,
     * because this is Way's revenue rather than a share of the vendor's invoice.
     *
     * @return the commission, or null when either input is missing
     */
    public static BigDecimal commissionOn(BigDecimal base, BigDecimal wayCommissionPercent) {
        if (base == null || wayCommissionPercent == null) {
            return null;
        }
        return scaleMoney(base.multiply(wayCommissionPercent)
                .divide(HUNDRED, MONEY_SCALE + 2, MONEY));
    }

    private static BigDecimal scaleMoney(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, MONEY);
    }
}

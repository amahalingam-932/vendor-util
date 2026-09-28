package com.way.vendorutil.parknflycanada;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParkNflyCanadaPricingCalculatorTest {

    private static final BigDecimal DAY = new BigDecimal("12.99");
    private static final BigDecimal WEEK = new BigDecimal("59.99");
    private static final BigDecimal COMMISSION = new BigDecimal("15");

    private static final ParkNflyCanadaTaxRule ONTARIO =
            new ParkNflyCanadaTaxRule("ON", new BigDecimal("0.13"), new BigDecimal("3.96"));

    /**
     * The reference case: Park'N Fly Canada's own {@code confirmationGet} on 27 Aug 2026 for a
     * 15-18 Sep stay at location 132 returned base 51.96 (4 x 12.99), fuel 3.96, HST 6.75 and
     * {@code total_estimated_fee} 62.67. 6.75 is 13% of the base alone, which is what pins
     * tax on the parking base alone.
     */
    @Test
    void matchesLiveVendorConfirmationForFourDays() {
        ParkNflyCanadaPricingQuote quote =
                ParkNflyCanadaPricingCalculator.quote(4, DAY, WEEK, ONTARIO, COMMISSION);

        assertEquals(new BigDecimal("51.96"), quote.base());
        assertEquals(new BigDecimal("3.96"), quote.fuelSurcharge());
        assertEquals(new BigDecimal("6.75"), quote.hst());
        assertEquals(new BigDecimal("62.67"), quote.customerTotal());
    }

    /** Manu's OGI60296958 statement example: 1 day at 12.99 gives HST 1.69 and commission 1.95. */
    @Test
    void oneDayStatementExample() {
        ParkNflyCanadaPricingQuote quote =
                ParkNflyCanadaPricingCalculator.quote(1, DAY, WEEK, ONTARIO, COMMISSION);

        assertEquals(1, quote.billableDays());
        assertEquals("ON", quote.province());
        assertEquals(new BigDecimal("12.99"), quote.base());
        assertEquals(new BigDecimal("1.69"), quote.hst());
        assertEquals(new BigDecimal("18.64"), quote.customerTotal());
        assertEquals(new BigDecimal("1.95"), quote.wayCommission());
        assertEquals(new BigDecimal("16.69"), quote.vendorNet());
    }

    /** Frank's rate sheet: an 8-day stay is the weekly flat plus one daily, 59.99 + 12.99. */
    @Test
    void eightDayStayUsesWeeklyPlusExtraDaily() {
        ParkNflyCanadaPricingQuote quote =
                ParkNflyCanadaPricingCalculator.quote(8, DAY, WEEK, ONTARIO, COMMISSION);

        assertEquals(new BigDecimal("72.98"), quote.base());
        assertEquals(new BigDecimal("9.49"), quote.hst());
        assertEquals(new BigDecimal("86.43"), quote.customerTotal());
        assertEquals(new BigDecimal("10.95"), quote.wayCommission());
        assertEquals(new BigDecimal("75.48"), quote.vendorNet());
    }

    /**
     * Sales tax applies to the parking base alone, never to base plus the fuel surcharge.
     *
     * <p>Two things agree on this and they are the reason there is no longer a switch. Way's
     * shared tax evaluator computes every percentage rule against the item price for every other
     * vendor. And Park'N Fly themselves do the same: a 12.99 base comes back from
     * {@code confirmationGet} with 1.69 of tax, which is thirteen per cent of 12.99 and not of
     * the 16.95 it would be with the surcharge included.
     *
     * <p>On a 50.00 base that is 6.50 of tax and 60.46 in total. An earlier reading of the
     * vendor's email had it at 7.01 and 60.97; this test exists so that figure is not quietly
     * reintroduced.
     */
    @Test
    void taxAppliesToTheBaseAndNotTheFuelSurcharge() {
        ParkNflyCanadaPricingQuote quote = ParkNflyCanadaPricingCalculator.quote(
                1, new BigDecimal("50.00"), WEEK, ONTARIO, COMMISSION);

        assertEquals(new BigDecimal("50.00"), quote.base());
        assertEquals(new BigDecimal("6.50"), quote.hst());
        assertEquals(new BigDecimal("60.46"), quote.customerTotal());
    }

    /** Way's commission is a statement figure and never reduces what the customer pays. */
    @Test
    void commissionDoesNotReduceCustomerTotal() {
        ParkNflyCanadaPricingQuote quote =
                ParkNflyCanadaPricingCalculator.quote(1, DAY, WEEK, ONTARIO, COMMISSION);

        assertEquals(quote.base().add(quote.feesAndTaxes()), quote.customerTotal());
        assertEquals(quote.customerTotal().subtract(quote.wayCommission()), quote.vendorNet());
    }
}

package com.way.vendorutil.parknflycanada;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParkNflyCanadaCurrencyConversionTest {

    private static final BigDecimal CAD_TO_USD = new BigDecimal("0.7299");
    private static final BigDecimal DAY = new BigDecimal("12.99");
    private static final BigDecimal WEEK = new BigDecimal("59.99");

    private static ParkNflyCanadaTaxRule ontario() {
        return new ParkNflyCanadaTaxRule("ON", new BigDecimal("0.13"), new BigDecimal("3.96"));
    }

    private static ParkNflyCanadaCurrencyConversion cadToUsd() {
        return ParkNflyCanadaCurrencyConversion.of("CAD", "USD", CAD_TO_USD);
    }

    @Test
    void matchingCurrenciesNeedNoConversion() {
        assertSame(ParkNflyCanadaCurrencyConversion.NONE,
                ParkNflyCanadaCurrencyConversion.of("CAD", "cad", CAD_TO_USD));
        assertFalse(ParkNflyCanadaCurrencyConversion.NONE.isRequired());
    }

    @Test
    void unconfiguredCurrencyKeepsPreviousBehaviourRatherThanFailing() {
        assertSame(ParkNflyCanadaCurrencyConversion.NONE,
                ParkNflyCanadaCurrencyConversion.of(null, "USD", CAD_TO_USD));
        assertSame(ParkNflyCanadaCurrencyConversion.NONE,
                ParkNflyCanadaCurrencyConversion.of("CAD", "  ", CAD_TO_USD));
    }

    @Test
    void differentCurrenciesWithoutARateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ParkNflyCanadaCurrencyConversion.of("CAD", "USD", null));
        assertThrows(IllegalArgumentException.class,
                () -> ParkNflyCanadaCurrencyConversion.of("CAD", "USD", BigDecimal.ZERO));
    }

    @Test
    void convertsToTheCent() {
        ParkNflyCanadaCurrencyConversion fx = cadToUsd();
        assertTrue(fx.isRequired());
        // 59.99 x 0.7299 = 43.7860... -> 43.79
        assertEquals(new BigDecimal("43.79"), fx.convert(new BigDecimal("59.99")));
        // 3.96 x 0.7299 = 2.8904... -> 2.89
        assertEquals(new BigDecimal("2.89"), fx.convert(new BigDecimal("3.96")));
    }

    @Test
    void quoteWithoutConversionIsUnchangedAndReportsNoConversion() {
        ParkNflyCanadaPricingQuote quote = ParkNflyCanadaPricingCalculator.quote(
                7, DAY, WEEK, ontario(), new BigDecimal("10"));

        assertEquals(new BigDecimal("59.99"), quote.base());
        assertEquals(new BigDecimal("7.80"), quote.hst());
        assertEquals(new BigDecimal("71.75"), quote.customerTotal());
        // The vendor view mirrors the customer view when nothing is converted.
        assertEquals(quote.base(), quote.vendorBase());
        assertEquals(quote.customerTotal(), quote.vendorTotal());
        assertFalse(quote.currencyConverted());
    }

    @Test
    void taxIsAPercentageOfTheVendorBaseNotTheConvertedBase() {
        ParkNflyCanadaPricingQuote quote = ParkNflyCanadaPricingCalculator.quote(
                7, DAY, WEEK, ontario(), new BigDecimal("10"), cadToUsd());

        // Reconciliation compares against total_estimated_fee, which is CAD: 59.99 + 3.96 + 7.80.
        assertEquals(new BigDecimal("59.99"), quote.vendorBase());
        assertEquals(new BigDecimal("7.80"), quote.vendorHst());
        assertEquals(new BigDecimal("71.75"), quote.vendorTotal());
        assertTrue(quote.currencyConverted());
    }

    @Test
    void customerAmountsAreConvertedAndTheBreakdownStillSumsToTheTotal() {
        ParkNflyCanadaPricingQuote quote = ParkNflyCanadaPricingCalculator.quote(
                7, DAY, WEEK, ontario(), new BigDecimal("10"), cadToUsd());

        assertEquals(new BigDecimal("43.79"), quote.base());   // 59.99 CAD
        assertEquals(new BigDecimal("2.89"), quote.fuelSurcharge()); // 3.96 CAD
        assertEquals(new BigDecimal("5.69"), quote.hst());     // 7.80 CAD
        // What the customer sees has to add up, so the total is the sum of the converted lines.
        assertEquals(new BigDecimal("52.37"), quote.customerTotal());
        assertEquals(quote.base().add(quote.feesAndTaxes()), quote.customerTotal());
    }

    @Test
    void wayCommissionIsTakenOnTheConvertedBase() {
        ParkNflyCanadaPricingQuote quote = ParkNflyCanadaPricingCalculator.quote(
                7, DAY, WEEK, ontario(), new BigDecimal("10"), cadToUsd());

        // Way's fee is its own revenue in its own currency: 10% of 43.79, not of 59.99.
        assertEquals(new BigDecimal("4.38"), quote.wayCommission());
    }

    @Test
    void aNullConversionIsTreatedAsNoConversion() {
        ParkNflyCanadaPricingQuote quote = ParkNflyCanadaPricingCalculator.quote(
                7, DAY, WEEK, ontario(), new BigDecimal("10"), null);

        assertEquals(new BigDecimal("71.75"), quote.customerTotal());
        assertFalse(quote.currencyConverted());
    }
}

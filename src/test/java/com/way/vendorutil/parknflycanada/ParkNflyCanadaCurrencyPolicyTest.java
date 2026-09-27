package com.way.vendorutil.parknflycanada;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one rule four services share, so it is worth pinning down here rather than four times.
 *
 * <p>What matters is not the arithmetic - that belongs to the conversion itself - but which
 * configurations are accepted and which are refused, because a refusal withholds the lot and an
 * acceptance charges the customer.
 */
class ParkNflyCanadaCurrencyPolicyTest {

    /**
     * The band the rate has to sit in. Configured rather than compiled in, because what counts as
     * a plausible rate is a judgement the business makes, not a constant of arithmetic.
     */
    private static final BigDecimal MIN = new BigDecimal("0.50");
    private static final BigDecimal MAX = new BigDecimal("1.00");

    private static ParkNflyCanadaCurrencyConversion resolve(boolean enabled, String currency,
            BigDecimal rate) {
        return ParkNflyCanadaCurrencyPolicy.resolve(enabled, currency, rate, MIN, MAX);
    }

    @Test
    void switchedOffIsTheIdentityConversion() {
        assertEquals(ParkNflyCanadaCurrencyConversion.NONE, resolve(false, "USD", null));
    }

    /** Off means off: a rate that would be refused is simply never looked at. */
    @Test
    void switchedOffIgnoresEvenARateThatWouldBeRefused() {
        assertEquals(ParkNflyCanadaCurrencyConversion.NONE,
                resolve(false, "USD", new BigDecimal("1.40")));
    }

    @Test
    void switchedOnWithAPlausibleRateConverts() {
        ParkNflyCanadaCurrencyConversion conversion =
                resolve(true, "USD", new BigDecimal("0.72"));

        assertTrue(conversion.isRequired());
        assertEquals(new BigDecimal("9.35"), conversion.convert(new BigDecimal("12.99")));
    }

    /**
     * Switched on with no rate is refused rather than falling back.
     *
     * <p>A fallback would present Canadian dollars as though they were US ones, which is precisely
     * the fault this exists to prevent.
     */
    @Test
    void switchedOnWithNoRateIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> resolve(true, "USD", null));
    }

    /**
     * The mistake this band exists for: a rate quoted as "the dollar is at 1.40" is USD per CAD,
     * and the figure wanted is its reciprocal. Entering 1.40 unchanged would inflate every
     * Canadian price by about ninety per cent and charge customers for it.
     */
    @Test
    void theRateEnteredTheWrongWayRoundIsRefused() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> resolve(true, "USD", new BigDecimal("1.40")));

        assertTrue(refused.getMessage().contains(ParkNflyCanadaCurrencyPolicy.RATE_SOURCE));
    }

    @Test
    void anAbsurdlyLowRateIsRefusedToo() {
        assertThrows(IllegalArgumentException.class,
                () -> resolve(true, "USD", new BigDecimal("0.0072")));
    }

    /** Both edges of the band are inside it, so an ordinary market move never trips the check. */
    @Test
    void theBandAdmitsItsOwnEdges() {
        assertTrue(resolve(true, "USD", MAX).isRequired());
        assertTrue(resolve(true, "USD", MIN).isRequired());
    }

    /** Charging in the vendor's own currency means there is nothing to convert. */
    @Test
    void chargingInTheVendorsOwnCurrencyNeedsNoRate() {
        assertFalse(resolve(true, "CAD", null).isRequired());
    }

    @Test
    void describesBothStatesForTheStartupLog() {
        assertTrue(ParkNflyCanadaCurrencyPolicy.describe(false, "USD", null).contains("off"));
        assertTrue(ParkNflyCanadaCurrencyPolicy.describe(true, "USD", new BigDecimal("0.72"))
                .contains("0.72"));
    }

    /**
     * Conversion switched on with no configured band is refused.
     *
     * <p>Without the band there is nothing to catch a rate entered the wrong way round, and that
     * mistake charges every Canadian customer about ninety per cent too much. Refusing is the only
     * safe answer: the lot is withheld and somebody notices.
     */
    @Test
    void conversionWithNoConfiguredBandIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ParkNflyCanadaCurrencyPolicy
                .resolve(true, "USD", new BigDecimal("0.72"), null, MAX));
        assertThrows(IllegalArgumentException.class, () -> ParkNflyCanadaCurrencyPolicy
                .resolve(true, "USD", new BigDecimal("0.72"), MIN, null));
    }

    /** With conversion off the band is never consulted, so a missing one changes nothing. */
    @Test
    void aMissingBandDoesNotMatterWhileConversionIsOff() {
        assertEquals(ParkNflyCanadaCurrencyConversion.NONE,
                ParkNflyCanadaCurrencyPolicy.resolve(false, "USD", null, null, null));
    }
}

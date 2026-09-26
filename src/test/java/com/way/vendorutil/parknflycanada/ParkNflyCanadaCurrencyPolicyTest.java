package com.way.vendorutil.parknflycanada;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * The one rule four services share, so it is worth pinning down here rather than four times.
 *
 * <p>What matters is not the arithmetic - that belongs to the conversion itself - but which
 * configurations are accepted and which are refused, because a refusal withholds the lot and an
 * acceptance charges the customer.
 */
class ParkNflyCanadaCurrencyPolicyTest {

    @Test
    void switchedOffIsTheIdentityConversion() {
        // Every committed profile is in this state, so this is the behaviour in production today.
        assertSame(ParkNflyCanadaCurrencyConversion.NONE,
                ParkNflyCanadaCurrencyPolicy.resolve(false, "USD", null));
    }

    @Test
    void switchedOffIgnoresEvenARateThatWouldBeRefused() {
        // The switch is the switch. A leftover rate in a property file must not start converting.
        assertSame(ParkNflyCanadaCurrencyConversion.NONE,
                ParkNflyCanadaCurrencyPolicy.resolve(false, "USD", new BigDecimal("1.40")));
    }

    @Test
    void switchedOnWithAPlausibleRateConverts() {
        ParkNflyCanadaCurrencyConversion conversion =
                ParkNflyCanadaCurrencyPolicy.resolve(true, "USD", new BigDecimal("0.72"));

        assertTrue(conversion.isRequired());
        assertEquals("CAD", conversion.vendorCurrency());
        assertEquals("USD", conversion.checkoutCurrency());
        assertEquals(new BigDecimal("7.20"), conversion.convert(new BigDecimal("10.00")));
    }

    @Test
    void switchedOnWithNoRateIsRefused() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> ParkNflyCanadaCurrencyPolicy.resolve(true, "USD", null));

        assertTrue(refused.getMessage().contains("rate is required"));
    }

    @Test
    void theRateEnteredTheWrongWayRoundIsRefused() {
        // 1.40 is USD/CAD, the figure a person quotes out loud. Taken literally it would inflate
        // every Canadian price by about ninety-five per cent.
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> ParkNflyCanadaCurrencyPolicy.resolve(true, "USD", new BigDecimal("1.40")));

        assertTrue(refused.getMessage().contains(ParkNflyCanadaCurrencyPolicy.RATE_PROPERTY));
        assertTrue(refused.getMessage().contains("0.7143"));
    }

    @Test
    void anAbsurdlyLowRateIsRefusedToo() {
        // A rate entered in cents, or a stale feed returning zero-ish, would undercharge instead.
        assertThrows(IllegalArgumentException.class,
                () -> ParkNflyCanadaCurrencyPolicy.resolve(true, "USD", new BigDecimal("0.0072")));
    }

    @Test
    void theBandAdmitsItsOwnEdges() {
        // Parity is a real possibility and must not be treated as a typo.
        assertTrue(ParkNflyCanadaCurrencyPolicy.resolve(true, "USD",
                ParkNflyCanadaCurrencyPolicy.MAX_PLAUSIBLE_RATE).isRequired());
        assertTrue(ParkNflyCanadaCurrencyPolicy.resolve(true, "USD",
                ParkNflyCanadaCurrencyPolicy.MIN_PLAUSIBLE_RATE).isRequired());
    }

    @Test
    void chargingInTheVendorsOwnCurrencyNeedsNoRate() {
        // Should Way ever settle with the customer in CAD, conversion on with checkoutCurrency=CAD
        // is not a misconfiguration - there is simply nothing to convert.
        assertFalse(ParkNflyCanadaCurrencyPolicy.resolve(true, "CAD", null).isRequired());
    }

    @Test
    void describesBothStatesForTheStartupLog() {
        assertTrue(ParkNflyCanadaCurrencyPolicy.describe(false, "USD", null).contains("off"));
        assertTrue(ParkNflyCanadaCurrencyPolicy.describe(true, "USD", new BigDecimal("0.72"))
                .contains("0.72"));
    }
}

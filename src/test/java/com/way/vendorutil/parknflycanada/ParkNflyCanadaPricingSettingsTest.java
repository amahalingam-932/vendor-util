package com.way.vendorutil.parknflycanada;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Everything the price depends on is configured, and nothing has a default.
 *
 * <p>These pin down what happens when a row is missing, because that is the case a compiled-in
 * default used to hide: the configuration was absent and the service carried on against a figure
 * nobody had chosen. Missing now means either "switched off" or "cannot be priced", never a guess.
 */
class ParkNflyCanadaPricingSettingsTest {

    private final Map<String, String> configuration = new HashMap<>();

    private ParkNflyCanadaPricingSettings settings(BigDecimal usdToCadRate) {
        return ParkNflyCanadaPricingSettings.fromConfiguration(configuration::get, usdToCadRate);
    }

    private void set(String key, String value) {
        configuration.put(key, value);
    }

    private void fullyConfigured() {
        set(ParkNflyCanadaPricingSettings.CHECKOUT_CURRENCY_KEY, "USD");
        set(ParkNflyCanadaPricingSettings.CONVERSION_ENABLED_KEY, "true");
        set(ParkNflyCanadaPricingSettings.GRACE_MINUTES_KEY, "15");
        set(ParkNflyCanadaPricingSettings.MIN_PLAUSIBLE_RATE_KEY, "0.50");
        set(ParkNflyCanadaPricingSettings.MAX_PLAUSIBLE_RATE_KEY, "1.00");
    }

    /** Nothing configured is nothing assumed. */
    @Test
    void anEmptyConfigurationCarriesNoFigures() {
        ParkNflyCanadaPricingSettings settings = settings(null);

        assertNull(settings.getCheckoutCurrency());
        assertNull(settings.getCadToUsdRate());
        assertNull(settings.getGraceMinutes());
        assertNull(settings.getMinPlausibleRate());
        assertNull(settings.getMaxPlausibleRate());
        assertFalse(settings.isCurrencyConversionEnabled());
    }

    /**
     * The stored rate is inverted exactly once, here.
     *
     * <p>{@code tbl_convertion_factor} holds USD to CAD 1.37, so one Canadian dollar is
     * 1 / 1.37 = 0.729927 US dollars. Using 1.37 unchanged would multiply the price instead of
     * dividing it and charge a customer about 88% more.
     */
    @Test
    void theStoredUsdToCadRateIsInvertedForPricing() {
        fullyConfigured();

        ParkNflyCanadaPricingSettings settings = settings(new BigDecimal("1.37"));

        assertEquals(new BigDecimal("0.729927"), settings.getCadToUsdRate());
        assertEquals(new BigDecimal("9.48"),
                settings.currencyConversion().convert(new BigDecimal("12.99")));
    }

    /** No row, or a nonsense row, leaves the rate absent rather than guessed. */
    @Test
    void aMissingOrUselessRateStaysAbsent() {
        fullyConfigured();

        assertNull(settings(null).getCadToUsdRate());
        assertNull(settings(BigDecimal.ZERO).getCadToUsdRate());
        assertNull(settings(new BigDecimal("-1.37")).getCadToUsdRate());
    }

    /** Conversion off charges the vendor's amounts unchanged, and consults nothing else. */
    @Test
    void conversionOffLeavesTheVendorsAmountsAlone() {
        ParkNflyCanadaCurrencyConversion conversion = settings(null).currencyConversion();

        assertNotNull(conversion);
        assertEquals(new BigDecimal("12.99"), conversion.convert(new BigDecimal("12.99")));
    }

    /** A missing or unreadable switch is off, which is the absence of a feature, not of a figure. */
    @Test
    void aMissingSwitchIsOff() {
        assertFalse(settings(new BigDecimal("1.37")).isCurrencyConversionEnabled());

        set(ParkNflyCanadaPricingSettings.CONVERSION_ENABLED_KEY, "yes please");
        assertFalse(settings(new BigDecimal("1.37")).isCurrencyConversionEnabled());

        set(ParkNflyCanadaPricingSettings.CONVERSION_ENABLED_KEY, " TRUE ");
        assertTrue(settings(new BigDecimal("1.37")).isCurrencyConversionEnabled());
    }

    /**
     * Switched on with no rate is refused rather than quietly charging CAD as USD.
     */
    @Test
    void conversionOnWithNoRateIsRefused() {
        fullyConfigured();

        assertThrows(IllegalArgumentException.class, () -> settings(null).currencyConversion());
    }

    /**
     * A rate the wrong way round is refused, and so is switching conversion on without the band
     * that catches it. The band is a judgement the business makes, so it is configured too.
     */
    @Test
    void aRateOutsideTheConfiguredBandIsRefused() {
        fullyConfigured();

        assertThrows(IllegalArgumentException.class,
                () -> settings(new BigDecimal("0.7299")).currencyConversion());
    }

    @Test
    void conversionOnWithNoConfiguredBandIsRefused() {
        set(ParkNflyCanadaPricingSettings.CONVERSION_ENABLED_KEY, "true");
        set(ParkNflyCanadaPricingSettings.GRACE_MINUTES_KEY, "15");

        assertThrows(IllegalArgumentException.class,
                () -> settings(new BigDecimal("1.37")).currencyConversion());
    }

    /**
     * Conversion switched on with no charging currency is refused, not silently ignored.
     *
     * <p>The regression this guards: the conversion value type treats a blank charging currency as
     * "not configured" and returns the identity conversion. That was right while the currency had a
     * compiled-in default of USD. With no default, blank means the row is missing - and a deployment
     * that believes conversion is on while every Canadian customer is charged unconverted Canadian
     * dollars is the exact fault the feature exists to prevent.
     */
    @Test
    void conversionOnWithNoChargingCurrencyIsRefused() {
        set(ParkNflyCanadaPricingSettings.CONVERSION_ENABLED_KEY, "true");
        set(ParkNflyCanadaPricingSettings.MIN_PLAUSIBLE_RATE_KEY, "0.50");
        set(ParkNflyCanadaPricingSettings.MAX_PLAUSIBLE_RATE_KEY, "1.00");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> settings(new BigDecimal("1.37")).currencyConversion());

        assertTrue(refused.getMessage()
                .contains(ParkNflyCanadaPricingSettings.CHECKOUT_CURRENCY_KEY));
    }

    /** With every row present it converts, which is the case the one above used to hide. */
    @Test
    void conversionOnWithEverythingConfiguredActuallyConverts() {
        fullyConfigured();

        assertEquals(new BigDecimal("9.48"),
                settings(new BigDecimal("1.37")).currencyConversion().convert(new BigDecimal("12.99")));
    }

    /** The grace comes from configuration; it is the vendor's rule and the vendor can change it. */
    @Test
    void theGracePeriodIsRead() {
        set(ParkNflyCanadaPricingSettings.GRACE_MINUTES_KEY, "15");

        assertEquals(15, settings(null).getGraceMinutes());
    }

    /** An unreadable number is a missing one, not a zero. */
    @Test
    void anUnreadableNumberIsTreatedAsAbsent() {
        set(ParkNflyCanadaPricingSettings.GRACE_MINUTES_KEY, "fifteen");
        set(ParkNflyCanadaPricingSettings.MIN_PLAUSIBLE_RATE_KEY, "half");

        assertNull(settings(null).getGraceMinutes());
        assertNull(settings(null).getMinPlausibleRate());
    }

    /**
     * The two reconciliation allowances default to zero when unset - the strictest reading.
     *
     * <p>Zero refuses a mismatch rather than accepting one on a tolerance nobody chose. It is the
     * one place a missing value can safely mean something, because being stricter cannot overcharge
     * anybody.
     */
    @Test
    void theReconciliationAllowancesAreStrictWhenUnset() {
        assertEquals(BigDecimal.ZERO, settings(null).getFeeMismatchTolerance());
        assertEquals(BigDecimal.ZERO, settings(null).getConversionRoundingAllowance());

        set(ParkNflyCanadaPricingSettings.FEE_MISMATCH_TOLERANCE_KEY, "0.05");
        set(ParkNflyCanadaPricingSettings.CONVERSION_ROUNDING_ALLOWANCE_KEY, "0.02");

        assertEquals(new BigDecimal("0.05"), settings(null).getFeeMismatchTolerance());
        assertEquals(new BigDecimal("0.02"), settings(null).getConversionRoundingAllowance());
    }

    @Test
    void theBootDescriptionNamesTheCurrency() {
        assertTrue(settings(null).describeCurrency().contains("CAD"));
    }

    /** A service with no configuration at all must not blow up building its settings. */
    @Test
    void aNullLookupIsTreatedAsAnEmptyConfiguration() {
        assertNull(ParkNflyCanadaPricingSettings.fromConfiguration(null, null).getGraceMinutes());
    }
}

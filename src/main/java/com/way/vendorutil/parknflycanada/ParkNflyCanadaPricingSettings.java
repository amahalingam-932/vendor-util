package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;
import java.util.function.UnaryOperator;

/**
 * Every configured number a Park'N Fly Canada stay is priced with, read from the database.
 */
public final class ParkNflyCanadaPricingSettings {

    /**
     */
    public static final String CHECKOUT_CURRENCY_KEY = "PNF_CA_CHECKOUT_CURRENCY";

    /**
     */
    public static final String CONVERSION_ENABLED_KEY = "PNF_CA_CURRENCY_CONVERSION_ENABLED";

    /**
     * Minutes past a whole day that Park'N Fly Canada do not charge for.
     */
    public static final String GRACE_MINUTES_KEY = "PNF_CA_GRACE_MINUTES";

    /**
     */
    public static final String MIN_PLAUSIBLE_RATE_KEY = "PNF_CA_MIN_PLAUSIBLE_RATE";

    /**
     */
    public static final String MAX_PLAUSIBLE_RATE_KEY = "PNF_CA_MAX_PLAUSIBLE_RATE";

    /**
     * How far ahead Way will sell on the strength of one rate refresh, in days.
     *
     */
    public static final String SELLABLE_HORIZON_DAYS_KEY = "PNF_CA_SELLABLE_HORIZON_DAYS";


    public static final String FEE_MISMATCH_TOLERANCE_KEY = "PNF_CA_FEE_MISMATCH_TOLERANCE";

    public static final String CONVERSION_ROUNDING_ALLOWANCE_KEY =
            "PNF_CA_CONVERSION_ROUNDING_ALLOWANCE";

    private final String checkoutCurrency;
    private final boolean currencyConversionEnabled;
    private final BigDecimal cadToUsdRate;
    private final Integer graceMinutes;
    private final BigDecimal minPlausibleRate;
    private final BigDecimal maxPlausibleRate;
    private final BigDecimal feeMismatchTolerance;
    private final BigDecimal conversionRoundingAllowance;
    private final Integer sellableHorizonDays;

    private ParkNflyCanadaPricingSettings(String checkoutCurrency, boolean currencyConversionEnabled,
            BigDecimal cadToUsdRate, Integer graceMinutes, BigDecimal minPlausibleRate,
            BigDecimal maxPlausibleRate, BigDecimal feeMismatchTolerance,
            BigDecimal conversionRoundingAllowance, Integer sellableHorizonDays) {
        this.checkoutCurrency = checkoutCurrency;
        this.currencyConversionEnabled = currencyConversionEnabled;
        this.cadToUsdRate = cadToUsdRate;
        this.graceMinutes = graceMinutes;
        this.minPlausibleRate = minPlausibleRate;
        this.maxPlausibleRate = maxPlausibleRate;
        this.feeMismatchTolerance = feeMismatchTolerance;
        this.conversionRoundingAllowance = conversionRoundingAllowance;
        this.sellableHorizonDays = sellableHorizonDays;
    }

    /**
     * Builds the settings from configuration.
     *
     * @param lookup       reads a configuration value by key, returning null when there is no row
     * @param usdToCadRate the stored rate, or null when there is no row
     */
    public static ParkNflyCanadaPricingSettings fromConfiguration(UnaryOperator<String> lookup,
            BigDecimal usdToCadRate) {
        UnaryOperator<String> safe = lookup == null ? key -> null : lookup;
        return new ParkNflyCanadaPricingSettings(
                text(safe.apply(CHECKOUT_CURRENCY_KEY)),
                Boolean.parseBoolean(text(safe.apply(CONVERSION_ENABLED_KEY))),
                cadToUsd(usdToCadRate),
                integer(safe.apply(GRACE_MINUTES_KEY)),
                decimal(safe.apply(MIN_PLAUSIBLE_RATE_KEY)),
                decimal(safe.apply(MAX_PLAUSIBLE_RATE_KEY)),
                decimal(safe.apply(FEE_MISMATCH_TOLERANCE_KEY)),
                decimal(safe.apply(CONVERSION_ROUNDING_ALLOWANCE_KEY)),
                integer(safe.apply(SELLABLE_HORIZON_DAYS_KEY)));
    }

    /**
     * Settings stated outright, for a test or for a caller that holds the values already.
     */
    public static ParkNflyCanadaPricingSettings of(String checkoutCurrency, boolean conversionEnabled,
            BigDecimal cadToUsdRate, Integer graceMinutes, BigDecimal minPlausibleRate,
            BigDecimal maxPlausibleRate, BigDecimal feeMismatchTolerance,
            BigDecimal conversionRoundingAllowance) {
        return new ParkNflyCanadaPricingSettings(checkoutCurrency, conversionEnabled, cadToUsdRate,
                graceMinutes, minPlausibleRate, maxPlausibleRate, feeMismatchTolerance,
                conversionRoundingAllowance, null);
    }

    public String getCheckoutCurrency() {
        return checkoutCurrency;
    }

    public boolean isCurrencyConversionEnabled() {
        return currencyConversionEnabled;
    }

    public BigDecimal getCadToUsdRate() {
        return cadToUsdRate;
    }

    /**
     */
    public Integer getGraceMinutes() {
        return graceMinutes;
    }

    public BigDecimal getMinPlausibleRate() {
        return minPlausibleRate;
    }

    public BigDecimal getMaxPlausibleRate() {
        return maxPlausibleRate;
    }

    /**
     */
    public Integer getSellableHorizonDays() {
        return sellableHorizonDays;
    }

    /**
     */
    public BigDecimal getFeeMismatchTolerance() {
        return feeMismatchTolerance == null ? BigDecimal.ZERO : feeMismatchTolerance;
    }

    /**
     */
    public BigDecimal getConversionRoundingAllowance() {
        return conversionRoundingAllowance == null ? BigDecimal.ZERO : conversionRoundingAllowance;
    }

    /**
     * The conversion to apply, or the identity conversion when it is switched off.
     *
     * @throws IllegalArgumentException when conversion is on and the rate is missing, or the band
     *         that would sanity-check it is missing, or the rate falls outside that band. The
     *         caller withholds the lot: quoting the vendor's Canadian amounts as though they were
     *         Way's charging currency shows a price roughly a third out.
     */
    public ParkNflyCanadaCurrencyConversion currencyConversion() {
        return ParkNflyCanadaCurrencyPolicy.resolve(currencyConversionEnabled, checkoutCurrency,
                cadToUsdRate, minPlausibleRate, maxPlausibleRate);
    }

    /**
     * One line naming what this node will quote in, for a service to log at boot.
     */
    public String describeCurrency() {
        return ParkNflyCanadaCurrencyPolicy.describe(currencyConversionEnabled, checkoutCurrency,
                cadToUsdRate);
    }

    /**
     */
    private static BigDecimal cadToUsd(BigDecimal usdToCadRate) {
        if (usdToCadRate == null || usdToCadRate.signum() <= 0) {
            return null;
        }
        return BigDecimal.ONE.divide(usdToCadRate, 6, java.math.RoundingMode.HALF_UP);
    }

    private static String text(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /**
     */
    private static BigDecimal decimal(String value) {
        String trimmed = text(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static Integer integer(String value) {
        String trimmed = text(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
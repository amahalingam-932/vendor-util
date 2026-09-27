package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;

/**
 * One place that decides what currency a Park'N Fly Canada price is charged in.
 *
 */
public final class ParkNflyCanadaCurrencyPolicy {

    public static final String VENDOR_CURRENCY = "CAD";

    public static final String RATE_SOURCE = "tbl_convertion_factor (USD to CAD, inverted)";

    private ParkNflyCanadaCurrencyPolicy() {
    }

    public static ParkNflyCanadaCurrencyConversion resolve(boolean enabled, String checkoutCurrency,
            BigDecimal cadToUsdRate, BigDecimal minPlausibleRate, BigDecimal maxPlausibleRate) {
        if (!enabled) {
            return ParkNflyCanadaCurrencyConversion.NONE;
        }
        if (checkoutCurrency == null || checkoutCurrency.trim().isEmpty()) {
            throw new IllegalArgumentException("currency conversion is on but "
                    + ParkNflyCanadaPricingSettings.CHECKOUT_CURRENCY_KEY
                    + " is not configured, so there is no currency to convert into");
        }
        if (minPlausibleRate == null || maxPlausibleRate == null) {
            throw new IllegalArgumentException("currency conversion is on but "
                    + ParkNflyCanadaPricingSettings.MIN_PLAUSIBLE_RATE_KEY + " and "
                    + ParkNflyCanadaPricingSettings.MAX_PLAUSIBLE_RATE_KEY
                    + " are not configured, so the rate cannot be sanity checked");
        }
        if (cadToUsdRate != null && (cadToUsdRate.compareTo(minPlausibleRate) < 0
                || cadToUsdRate.compareTo(maxPlausibleRate) > 0)) {
            throw new IllegalArgumentException(RATE_SOURCE + " gives " + cadToUsdRate
                    + " CAD to USD, outside the configured plausible range " + minPlausibleRate
                    + " to " + maxPlausibleRate + "; a USD/CAD figure such as 1.40 has to be stored"
                    + " as USD to CAD and is inverted on read");
        }
        return ParkNflyCanadaCurrencyConversion.of(VENDOR_CURRENCY, checkoutCurrency, cadToUsdRate);
    }

    public static String describe(boolean enabled, String checkoutCurrency, BigDecimal cadToUsdRate) {
        if (!enabled) {
            return "currency conversion off; the vendor's " + VENDOR_CURRENCY
                    + " amounts are charged unchanged";
        }
        return "currency conversion on; " + VENDOR_CURRENCY + " to " + checkoutCurrency + " at "
                + cadToUsdRate;
    }
}

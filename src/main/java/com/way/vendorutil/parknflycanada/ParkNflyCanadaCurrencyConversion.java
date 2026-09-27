package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts Park'N Fly Canada's amounts into the currency Way charges in.
 *
 * @param vendorCurrency   currency the vendor quotes and invoices in, e.g. {@code CAD}
 * @param checkoutCurrency currency Way charges the customer in, e.g. {@code USD}
 * @param rate             how many units of {@code checkoutCurrency} one unit of
 *                         {@code vendorCurrency} buys; must be greater than zero
 */
public record ParkNflyCanadaCurrencyConversion(
        String vendorCurrency,
        String checkoutCurrency,
        BigDecimal rate) {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY = RoundingMode.HALF_UP;

    /**
     */
    public static final ParkNflyCanadaCurrencyConversion NONE =
            new ParkNflyCanadaCurrencyConversion("CAD", "CAD", BigDecimal.ONE);

    public ParkNflyCanadaCurrencyConversion {
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("conversion rate must be greater than zero");
        }
    }

    /**
     * Builds a conversion, falling back to {@link #NONE} when the two currencies match.
     */
    public static ParkNflyCanadaCurrencyConversion of(String vendorCurrency, String checkoutCurrency,
            BigDecimal rate) {
        if (isBlank(vendorCurrency) || isBlank(checkoutCurrency)) {
            return NONE;
        }
        if (vendorCurrency.trim().equalsIgnoreCase(checkoutCurrency.trim())) {
            return NONE;
        }
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException(
                    "a conversion rate is required to charge " + checkoutCurrency + " for a "
                            + vendorCurrency + " booking");
        }
        return new ParkNflyCanadaCurrencyConversion(vendorCurrency.trim(), checkoutCurrency.trim(), rate);
    }

    /**
     */
    public boolean isRequired() {
        return !vendorCurrency.equalsIgnoreCase(checkoutCurrency);
    }

    /**
     * Converts one amount, to the cent.
     */
    public BigDecimal convert(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        if (!isRequired()) {
            return amount.setScale(MONEY_SCALE, MONEY);
        }
        return amount.multiply(rate).setScale(MONEY_SCALE, MONEY);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts Park'N Fly Canada's amounts into the currency Way charges in.
 *
 * <p>Park'N Fly Canada quote and invoice in Canadian dollars - confirmed by Marjorie Pease, and
 * unavoidable for a Canadian operator remitting HST - while Way settles with the customer in US
 * dollars. Nothing in the WSTravelAgent response says so: there is no currency field on any
 * endpoint, so the figures arrive as bare numbers and were previously charged as though they were
 * already USD.
 *
 * <p>The conversion deliberately happens after the stay has been priced, not before. Pricing the
 * stay from converted unit rates would produce an HST figure that is 13% of a converted base,
 * which could never equal the tax Park'N Fly actually remit, and the fee reconciliation compares
 * against exactly that. So the quote is calculated in the vendor's currency first, reconciled in
 * it, and only the customer-facing amounts are converted.
 *
 * <p>{@link #NONE} is the identity conversion and reproduces the previous behaviour exactly: the
 * vendor's numbers are used as-is. That is the default until the currency question is settled, so
 * both versions can be exercised without a code change.
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

    /** Treat the vendor's amounts as already being in the charging currency. */
    public static final ParkNflyCanadaCurrencyConversion NONE =
            new ParkNflyCanadaCurrencyConversion("CAD", "CAD", BigDecimal.ONE);

    public ParkNflyCanadaCurrencyConversion {
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("conversion rate must be greater than zero");
        }
    }

    /**
     * Builds a conversion, falling back to {@link #NONE} when the two currencies match.
     *
     * <p>A blank currency is treated as "not configured" rather than as an error, so a deployment
     * that has not been told about currencies keeps its previous behaviour instead of failing to
     * price.
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

    /** True when the vendor's currency differs from the one Way charges in. */
    public boolean isRequired() {
        return !vendorCurrency.equalsIgnoreCase(checkoutCurrency);
    }

    /**
     * Converts one amount, to the cent.
     *
     * <p>Components are converted individually rather than deriving them from a converted total,
     * so the lines the customer sees still add up to the total they are charged. The trade-off is
     * that the sum can differ by a cent from converting the total directly, which matters less
     * than a checkout whose breakdown does not reconcile on screen.
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

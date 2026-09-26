package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;

/**
 * One place that decides what currency a Park'N Fly Canada price is charged in.
 *
 * <p>Four services touch this vendor's money - search, the lot detail page, the cart and the fee
 * reconciliation - and they must agree to the cent. Before this class each of them carried its own
 * copy of the same three settings, the same plausibility bounds and its own wording of the same
 * error, which is three places for the rule to drift and two chances to fix a bug in only some of
 * them. A customer who is quoted one figure on the search page and charged another at checkout is
 * the single worst outcome this integration can produce, so the rule lives once and every service
 * asks this class rather than reimplementing it.
 *
 * <p>It is deliberately a plain static helper with no Spring in it: art-util is on the classpath of
 * every service, but they configure themselves differently - svc-orders binds
 * {@code @ConfigurationProperties}, search and the consumer use {@code @Value} - so the shared part
 * is the decision, not the plumbing that reads the properties.
 *
 * <h2>The settings</h2>
 *
 * <ul>
 *   <li>{@code way.pnfcanada.currencyConversionEnabled} - off in every committed profile, which
 *       charges the vendor's amounts exactly as they arrive.</li>
 *   <li>{@code way.pnfcanada.checkoutCurrency} - what Way charges the customer in, {@code USD}.</li>
 *   <li>{@code way.pnfcanada.cadToUsdRate} - how many US dollars one Canadian dollar buys.</li>
 * </ul>
 */
public final class ParkNflyCanadaCurrencyPolicy {

    /**
     * Park'N Fly Canada quote, invoice and are remitted in Canadian dollars, and have confirmed
     * they will not offer any other currency. It is a constant rather than a setting for that
     * reason: it is a fact about the vendor, not a choice Way gets to make.
     */
    public static final String VENDOR_CURRENCY = "CAD";

    /** Property that switches conversion on, named here so every log and error agrees. */
    public static final String RATE_PROPERTY = "way.pnfcanada.cadToUsdRate";

    /**
     * The narrowest band the CAD-to-USD rate has plausibly sat in, and wide enough that an ordinary
     * market move never trips it.
     *
     * <p>The bound exists for one specific mistake. A rate is quoted out loud as "the dollar is at
     * 1.40", which is USD per CAD inverted - the figure wanted here is its reciprocal, about
     * 0.7143. Entering 1.40 would not fail; it would silently inflate every Canadian price by
     * ninety-five per cent and charge customers for it. Anything outside this band is far more
     * likely to be that error than a real rate.
     */
    public static final BigDecimal MIN_PLAUSIBLE_RATE = new BigDecimal("0.50");

    /** Upper end of the plausible band; see {@link #MIN_PLAUSIBLE_RATE}. */
    public static final BigDecimal MAX_PLAUSIBLE_RATE = new BigDecimal("1.00");

    private ParkNflyCanadaCurrencyPolicy() {
    }

    /**
     * The conversion to apply, or {@link ParkNflyCanadaCurrencyConversion#NONE} when conversion is
     * switched off.
     *
     * <p>Throws rather than falling back when conversion is on but the rate is missing or
     * implausible. A fallback here would present Canadian dollars as though they were US ones,
     * which is precisely the fault this exists to prevent, so each caller catches it and withholds
     * the lot instead - showing nothing is recoverable, charging the wrong currency is not.
     *
     * @param enabled          value of {@code way.pnfcanada.currencyConversionEnabled}
     * @param checkoutCurrency value of {@code way.pnfcanada.checkoutCurrency}
     * @param cadToUsdRate     value of {@code way.pnfcanada.cadToUsdRate}, may be null when off
     * @throws IllegalArgumentException when conversion is on and the rate cannot be trusted
     */
    public static ParkNflyCanadaCurrencyConversion resolve(boolean enabled, String checkoutCurrency,
            BigDecimal cadToUsdRate) {
        if (!enabled) {
            return ParkNflyCanadaCurrencyConversion.NONE;
        }
        if (cadToUsdRate != null && (cadToUsdRate.compareTo(MIN_PLAUSIBLE_RATE) < 0
                || cadToUsdRate.compareTo(MAX_PLAUSIBLE_RATE) > 0)) {
            throw new IllegalArgumentException(RATE_PROPERTY + "=" + cadToUsdRate
                    + " is outside the plausible range " + MIN_PLAUSIBLE_RATE + " to "
                    + MAX_PLAUSIBLE_RATE + "; this rate is CAD to USD, so a USD/CAD figure such as"
                    + " 1.40 must be entered as its inverse (about 0.7143)");
        }
        // A missing rate is caught here, by the same factory the value type uses, so the message a
        // service logs is the same whichever way the configuration is wrong.
        return ParkNflyCanadaCurrencyConversion.of(VENDOR_CURRENCY, checkoutCurrency, cadToUsdRate);
    }

    /**
     * One line describing the configuration, for a service to log as it starts.
     *
     * <p>Worth the few characters at boot: with conversion off the amounts on screen are Canadian
     * dollars with a dollar sign in front of them, and the only way to tell from the outside is to
     * know what this setting was. Recording it once per start makes an incident answerable from the
     * logs rather than from somebody's memory of a property file.
     */
    public static String describe(boolean enabled, String checkoutCurrency, BigDecimal cadToUsdRate) {
        if (!enabled) {
            return "currency conversion off; the vendor's " + VENDOR_CURRENCY
                    + " amounts are charged unchanged";
        }
        return "currency conversion on; " + VENDOR_CURRENCY + " to " + checkoutCurrency + " at "
                + cadToUsdRate;
    }
}

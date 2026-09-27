package com.way.vendorutil.parknflycanada;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ParkNflyCanadaStayPricingTest {

    /** Toronto Payless, location 132, as the vendor returned it on 21 Aug 2026. */
    private static final String CARD_JSON =
            "[{\"from\":\"2026-07-22\",\"to\":\"2027-01-01\","
                    + "\"hour\":4.99,\"day\":12.99,\"week\":59.99}]";

    /**
     * The lot's own settings as Ops entered them: {@code PNF_CA_FUEL_SURCHARGE = 3.96},
     * {@code PNF_CA_HST_PERCENT = 13}, {@code PNF_CA_HST_ON_FUEL = No}. Both money values are
     * mandatory on the listing, so this is what every priced stay looks like.
     */
    private static final ParkNflyCanadaVendorTax LOT_TAX =
            new ParkNflyCanadaVendorTax(new BigDecimal("3.96"), new BigDecimal("13"), Boolean.FALSE);

    /** {@code CON_WayCommission} for listing 6291560, from its contract. */
    private static final BigDecimal COMMISSION = new BigDecimal("20");

    /** The configured band a rate must sit in, and the vendor's fifteen minute grace. */
    private static final BigDecimal MIN_RATE = new BigDecimal("0.50");
    private static final BigDecimal MAX_RATE = new BigDecimal("1.00");
    private static final Integer GRACE = 15;

    private static ParkNflyCanadaPricingSettings settings() {
        return ParkNflyCanadaPricingSettings.of("USD", false, null, GRACE, MIN_RATE, MAX_RATE,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static ParkNflyCanadaPricingSettings converting(BigDecimal cadToUsd) {
        return ParkNflyCanadaPricingSettings.of("USD", true, cadToUsd, GRACE, MIN_RATE, MAX_RATE,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static ParkNflyCanadaStayPricing.Request stay(String checkIn, String checkOut) {
        return ParkNflyCanadaStayPricing.Request.of(CARD_JSON, "ON", LOT_TAX, COMMISSION,
                checkIn, checkOut);
    }

    /**
     * The reference case, end to end through the shared sequence.
     *
     * <p>Park'N Fly Canada's own {@code confirmationGet} for a four day stay at location 132
     * returned base 51.96, fuel 3.96, HST 6.75 and {@code total_estimated_fee} 62.67. All three
     * services reach that figure by this route, so it is asserted here as well as on the
     * calculator.
     */
    @Test
    void pricesTheVendorsOwnFourDayConfirmation() {
        ParkNflyCanadaStayPricing.Result result = ParkNflyCanadaStayPricing.price(
                stay("2026-09-15 10:00:00", "2026-09-19 10:00:00"), settings());

        assertTrue(result.isPriced());
        assertEquals(4, result.quote().billableDays());
        assertEquals(new BigDecimal("51.96"), result.quote().base());
        assertEquals(new BigDecimal("3.96"), result.quote().fuelSurcharge());
        assertEquals(new BigDecimal("6.75"), result.quote().hst());
        assertEquals(new BigDecimal("62.67"), result.quote().customerTotal());
    }

    /**
     * The commission comes from the lot's contract and only moves the statement figures.
     */
    @Test
    void theContractsCommissionDrivesTheStatementAndNotThePrice() {
        ParkNflyCanadaStayPricing.Result atTwenty = ParkNflyCanadaStayPricing.price(
                stay("2026-09-15 10:00:00", "2026-09-18 10:00:00"), settings());
        ParkNflyCanadaStayPricing.Result atFifteen = ParkNflyCanadaStayPricing.price(
                ParkNflyCanadaStayPricing.Request.of(CARD_JSON, "ON", LOT_TAX,
                        new BigDecimal("15"), "2026-09-15 10:00:00", "2026-09-18 10:00:00"),
                settings());

        assertEquals(atFifteen.quote().customerTotal(), atTwenty.quote().customerTotal());
        // 20% and 15% of the 38.97 base.
        assertEquals(new BigDecimal("7.79"), atTwenty.quote().wayCommission());
        assertEquals(new BigDecimal("5.85"), atFifteen.quote().wayCommission());
    }

    /**
     * The banding, which is the thing a per-day price cannot express: three days are charged per
     * day, six days reach the weekly rate and cost less than five would at the daily rate, and
     * eight are a week plus a day.
     */
    @Test
    void theWeeklyBandAppliesThroughTheSharedSequence() {
        assertEquals(new BigDecimal("38.97"), ParkNflyCanadaStayPricing
                .price(stay("2026-09-15 10:00:00", "2026-09-18 10:00:00"), settings())
                .quote().base());
        assertEquals(new BigDecimal("59.99"), ParkNflyCanadaStayPricing
                .price(stay("2026-09-15 10:00:00", "2026-09-21 10:00:00"), settings())
                .quote().base());
        assertEquals(new BigDecimal("72.98"), ParkNflyCanadaStayPricing
                .price(stay("2026-09-15 10:00:00", "2026-09-23 10:00:00"), settings())
                .quote().base());
    }

    /** The vendor's fifteen minute grace survives the move into the shared sequence. */
    @Test
    void theVendorsGraceIsStillForgiven() {
        assertEquals(3, ParkNflyCanadaStayPricing
                .price(stay("2026-09-15 00:00:00", "2026-09-18 00:15:00"), settings())
                .quote().billableDays());
    }

    @Test
    void aStayNoCardCoversIsWithheld() {
        ParkNflyCanadaStayPricing.Result result = ParkNflyCanadaStayPricing.price(
                stay("2027-06-01 10:00:00", "2027-06-04 10:00:00"), settings());

        assertFalse(result.isPriced());
        assertEquals(ParkNflyCanadaStayPricing.Withheld.NO_RATE_CARD, result.withheld());
        assertNull(result.quote());
    }

    /**
     * A stay crossing a rate change is withheld rather than priced from the window it starts in.
     * The vendor's rule for such a stay is not documented, and quoting the old year's rates for a
     * stay mostly in the new one is a figure Way could not honour.
     */
    @Test
    void aStayCrossingARateChangeIsWithheld() {
        assertEquals(ParkNflyCanadaStayPricing.Withheld.NO_RATE_CARD,
                ParkNflyCanadaStayPricing
                        .price(stay("2026-12-30 10:00:00", "2027-01-06 10:00:00"), settings())
                        .withheld());
    }

    /**
     * A lot whose sales tax rate or fuel surcharge is missing is withheld, with nothing borrowed.
     *
     * <p>Both fields are mandatory when the listing is created, so this should not happen - but if
     * it does, there is no province table and no compiled-in rate to fall back to. Charging a
     * customer Ontario's 13% on a lot in another province is worse than not selling.
     */
    @Test
    void aLotMissingItsTaxOrSurchargeIsWithheld() {
        ParkNflyCanadaVendorTax surchargeOnly =
                new ParkNflyCanadaVendorTax(new BigDecimal("3.96"), null, null);
        ParkNflyCanadaVendorTax taxOnly =
                new ParkNflyCanadaVendorTax(null, new BigDecimal("13"), null);

        for (ParkNflyCanadaVendorTax incomplete :
                new ParkNflyCanadaVendorTax[] {surchargeOnly, taxOnly, ParkNflyCanadaVendorTax.NONE, null}) {
            ParkNflyCanadaStayPricing.Result result = ParkNflyCanadaStayPricing.price(
                    ParkNflyCanadaStayPricing.Request.of(CARD_JSON, "ON", incomplete, COMMISSION,
                            "2026-09-15 10:00:00", "2026-09-18 10:00:00"),
                    settings());

            assertEquals(ParkNflyCanadaStayPricing.Withheld.NO_TAX_RULE, result.withheld());
        }
    }

    /**
     * A listing with no province is withheld even though its own rate and surcharge are complete.
     *
     * <p>The money would be right - the lot carries both figures - but the tax line on the vendor
     * statement would have no province against it, and an unattributable tax charge is somebody
     * reconciling by hand later.
     */
    @Test
    void aLotWithNoProvinceIsWithheld() {
        for (String noProvince : new String[] {null, "", "   "}) {
            ParkNflyCanadaStayPricing.Result result = ParkNflyCanadaStayPricing.price(
                    ParkNflyCanadaStayPricing.Request.of(CARD_JSON, noProvince, LOT_TAX, COMMISSION,
                            "2026-09-15 10:00:00", "2026-09-18 10:00:00"),
                    settings());

            assertEquals(ParkNflyCanadaStayPricing.Withheld.NO_TAX_RULE, result.withheld());
        }
    }

    /**
     * A stay whose dates cannot be read is withheld, not priced at a minimum.
     *
     * <p>Matches what TPS does with the same problem - its parse throws and the caller ends up
     * with no price rather than a made-up one. Billing a day for a stay nobody can describe is
     * quieter than billing eleven, but it is still charging for something we do not understand.
     */
    @Test
    void aStayWhoseDatesCannotBeReadIsWithheld() {
        for (String[] dates : new String[][] {
                {"2026-09-15 10:00:00", "rubbish"},
                {"rubbish", "2026-09-18 10:00:00"},
                {"2026-09-15 10:00:00", null},
                {"2026-09-18 10:00:00", "2026-09-15 10:00:00"}}) {

            ParkNflyCanadaStayPricing.Result result = ParkNflyCanadaStayPricing.price(
                    ParkNflyCanadaStayPricing.Request.of(CARD_JSON, "ON", LOT_TAX, COMMISSION,
                            dates[0], dates[1]),
                    settings());

            assertFalse(result.isPriced());
            assertNull(result.quote());
        }
    }

    /** A lot whose contract carries no commission is withheld: the booking could not be settled. */
    @Test
    void aLotWithNoContractCommissionIsWithheld() {
        ParkNflyCanadaStayPricing.Result result = ParkNflyCanadaStayPricing.price(
                ParkNflyCanadaStayPricing.Request.of(CARD_JSON, "ON", LOT_TAX, null,
                        "2026-09-15 10:00:00", "2026-09-18 10:00:00"),
                settings());

        assertEquals(ParkNflyCanadaStayPricing.Withheld.NO_COMMISSION, result.withheld());
    }

    /**
     * A bad exchange rate comes back as a result, not as an exception.
     *
     * <p>One caller is a search over many rows. Letting this escape would fail the customer's
     * whole search because one configuration row is wrong, when the right answer is to withhold
     * that lot and serve everything else.
     */
    @Test
    void aMisconfiguredRateWithholdsTheLotInsteadOfThrowing() {
        ParkNflyCanadaPricingSettings misconfigured =
                converting(null);

        assertEquals(ParkNflyCanadaStayPricing.Withheld.CURRENCY_MISCONFIGURED,
                ParkNflyCanadaStayPricing
                        .price(stay("2026-09-15 10:00:00", "2026-09-18 10:00:00"), misconfigured)
                        .withheld());
    }

    @Test
    void conversionIsAppliedToEveryAmountWhenItIsOn() {
        ParkNflyCanadaPricingSettings converting =
                converting(new BigDecimal("0.7300"));

        ParkNflyCanadaStayPricing.Result result = ParkNflyCanadaStayPricing.price(
                stay("2026-09-15 10:00:00", "2026-09-18 10:00:00"), converting);

        // 38.97 CAD at 0.73 is 28.45, and the surcharge converts with it rather than staying CAD.
        assertEquals(new BigDecimal("28.45"), result.quote().base());
        assertEquals(new BigDecimal("2.89"), result.quote().fuelSurcharge());
    }



    /** A lot that says its surcharge is taxable is taxed on base plus surcharge. */
    @Test
    void aLotCanPutTheSurchargeInsideTheTaxableAmount() {
        ParkNflyCanadaVendorTax taxOnFuel =
                new ParkNflyCanadaVendorTax(new BigDecimal("3.96"), new BigDecimal("13"), Boolean.TRUE);

        ParkNflyCanadaStayPricing.Result result = ParkNflyCanadaStayPricing.price(
                ParkNflyCanadaStayPricing.Request.of(CARD_JSON, "ON", taxOnFuel, COMMISSION,
                        "2026-09-15 10:00:00", "2026-09-18 10:00:00"),
                settings());

        // 13% of 38.97 + 3.96 rather than of 38.97 alone.
        assertEquals(new BigDecimal("5.58"), result.quote().hst());
    }

    /**
     * A lookup that throws withholds the stay instead of escaping.
     *
     * <p>At checkout this is the difference between refusing a sale and taking payment at Way's
     * generic schedule price - a per-day figure this vendor does not charge - because the caller's
     * own catch would otherwise swallow the failure and leave the cart as it was.
     */
    @Test
    void aFailedLookupWithholdsTheStay() {
        ParkNflyCanadaStayPricing.Request request = ParkNflyCanadaStayPricing.Request.of(
                CARD_JSON,
                () -> { throw new IllegalStateException("database is down"); },
                () -> LOT_TAX,
                () -> COMMISSION,
                "2026-09-15 10:00:00", "2026-09-18 10:00:00");

        ParkNflyCanadaStayPricing.Result result =
                ParkNflyCanadaStayPricing.price(request, settings());

        assertEquals(ParkNflyCanadaStayPricing.Withheld.LOOKUP_FAILED, result.withheld());
        assertTrue(result.reason().contains("database is down"));
    }

    /** A lot with no usable card costs no lookups at all. */
    @Test
    void theLookupsAreSkippedWhenNoCardCoversTheStay() {
        AtomicInteger lookups = new AtomicInteger();
        ParkNflyCanadaStayPricing.Request request = ParkNflyCanadaStayPricing.Request.of(
                CARD_JSON,
                () -> { lookups.incrementAndGet(); return "ON"; },
                () -> { lookups.incrementAndGet(); return LOT_TAX; },
                () -> { lookups.incrementAndGet(); return COMMISSION; },
                "2027-06-01 10:00:00", "2027-06-04 10:00:00");

        ParkNflyCanadaStayPricing.price(request, settings());

        assertEquals(0, lookups.get());
    }

    @Test
    void perDayIsTheBaseSpreadOverTheBillableDays() {
        // Six days on the weekly band: 59.99 over six days, not the 12.99 daily rate.
        assertEquals(new BigDecimal("10.00"), ParkNflyCanadaStayPricing
                .price(stay("2026-09-15 10:00:00", "2026-09-21 10:00:00"), settings()).perDay());
    }

    @Test
    void aStayDateIsTheDatePartOfTheTimestamp() {
        assertEquals(LocalDate.of(2026, 9, 15),
                ParkNflyCanadaStayPricing.toStayDate("2026-09-15 10:00:00"));
        assertNull(ParkNflyCanadaStayPricing.toStayDate(null));
        assertNull(ParkNflyCanadaStayPricing.toStayDate("15/09/2026"));
        assertNull(ParkNflyCanadaStayPricing.toStayDate("2026-09"));
    }

    @Test
    void aMissingStayOrMissingSettingsIsWithheldRatherThanThrown() {
        assertFalse(ParkNflyCanadaStayPricing.price(null, settings()).isPriced());
        assertFalse(ParkNflyCanadaStayPricing
                .price(stay("2026-09-15 10:00:00", "2026-09-18 10:00:00"), null).isPriced());
    }

    /**
     * A stay cannot be priced without the vendor's grace period.
     *
     * <p>Treating a missing value as zero would bill a whole extra day for the few minutes
     * Park'N Fly Canada forgive - a real overcharge, and the exact bug this grace was added to
     * fix. Refusing until somebody sets it is the only answer that cannot take money wrongly.
     */
    @Test
    void aMissingGracePeriodWithholdsTheStay() {
        ParkNflyCanadaPricingSettings noGrace = ParkNflyCanadaPricingSettings.of(
                "USD", false, null, null, MIN_RATE, MAX_RATE, BigDecimal.ZERO, BigDecimal.ZERO);

        ParkNflyCanadaStayPricing.Result result = ParkNflyCanadaStayPricing.price(
                stay("2026-09-15 10:00:00", "2026-09-18 10:00:00"), noGrace);

        assertEquals(ParkNflyCanadaStayPricing.Withheld.MISSING_CONFIGURATION, result.withheld());
    }

    /** The grace is the vendor's to change, so a different configured value is honoured. */
    @Test
    void theConfiguredGraceIsWhatIsForgiven() {
        ParkNflyCanadaPricingSettings noForgiveness = ParkNflyCanadaPricingSettings.of(
                "USD", false, null, 0, MIN_RATE, MAX_RATE, BigDecimal.ZERO, BigDecimal.ZERO);

        // Fifteen minutes past three days: forgiven at 15, a fourth day at 0.
        assertEquals(3, ParkNflyCanadaStayPricing
                .price(stay("2026-09-15 00:00:00", "2026-09-18 00:15:00"), settings())
                .quote().billableDays());
        assertEquals(4, ParkNflyCanadaStayPricing
                .price(stay("2026-09-15 00:00:00", "2026-09-18 00:15:00"), noForgiveness)
                .quote().billableDays());
    }

    // ---- sellable horizon: how far ahead one refresh lets Way sell ----

    private static final int HORIZON = 90;

    private final Map<String, String> configuration = new HashMap<>();

    /** A card carrying the vendor's real window, extended into 2027. */
    private static final String CARD = ParkNflyCanadaRateCards.toJson(
            List.of(new ParkNflyCanadaRateCard(
                    LocalDate.now().minusDays(60), LocalDate.now().plusDays(460),
                    new BigDecimal("4.99"), new BigDecimal("12.99"), new BigDecimal("59.99"))));

    private ParkNflyCanadaStayPricing.Result price(long stayInDays, String syncedAt) {
        LocalDate checkIn = LocalDate.now().plusDays(stayInDays);
        return ParkNflyCanadaStayPricing.price(
                ParkNflyCanadaStayPricing.Request
                        .of(CARD, "ON",
                                new ParkNflyCanadaVendorTax(new BigDecimal("3.96"), new BigDecimal("13"), false),
                                new BigDecimal("20"),
                                checkIn + " 00:00:00", checkIn.plusDays(3) + " 00:00:00")
                        .withSyncedAt(syncedAt),
                ParkNflyCanadaPricingSettings.fromConfiguration(configuration::get, null));
    }

    private static String syncedDaysAgo(long days) {
        return Instant.now().minus(days, ChronoUnit.DAYS).toString();
    }

    private void horizonConfigured() {
        configuration.put(ParkNflyCanadaPricingSettings.GRACE_MINUTES_KEY, "15");
        configuration.put(ParkNflyCanadaPricingSettings.SELLABLE_HORIZON_DAYS_KEY,
                String.valueOf(HORIZON));
    }

    // ---------------------------------------------------------------------------------------------
    // Off until somebody turns it on
    // ---------------------------------------------------------------------------------------------

    /**
     * No configured horizon means no check.
     *
     * <p>Must stay true: how far ahead Way will sell on one refresh is a commercial judgement, and
     * turning the check on has to be a decision rather than a side effect of a deployment.
     */
    @Test
    void withNoConfiguredHorizonAnAncientCardStillPricesAFarFutureStay() {
        configuration.put(ParkNflyCanadaPricingSettings.GRACE_MINUTES_KEY, "15");

        assertTrue(price(400, syncedDaysAgo(365)).isPriced());
    }

    // ---------------------------------------------------------------------------------------------
    // The defining property: the horizon shrinks as the sync ages
    // ---------------------------------------------------------------------------------------------

    /** Freshly synced, the full horizon is available. */
    @Test
    void aFreshSyncSellsTheWholeHorizon() {
        horizonConfigured();

        assertTrue(price(1, syncedDaysAgo(0)).isPriced());
        assertTrue(price(89, syncedDaysAgo(0)).isPriced());
    }

    /**
     * The point of the whole design: a stale sync does not withhold everything at once.
     *
     * <p>Sixty days without a refresh leaves thirty days of horizon. Next week's stay still sells,
     * because those rates are almost certainly still right; a stay in three months does not. An age
     * check would have withheld both at the same instant.
     */
    @Test
    void aStaleSyncStillSellsNearTermStaysAndStopsSellingDistantOnes() {
        horizonConfigured();

        assertTrue(price(7, syncedDaysAgo(60)).isPriced(),
                "a stay next week must still sell from a card refreshed sixty days ago");
        assertEquals(ParkNflyCanadaStayPricing.Withheld.BEYOND_SELLABLE_HORIZON,
                price(60, syncedDaysAgo(60)).withheld(),
                "a stay two months out is beyond what that refresh supports");
    }

    /**
     * Once the sync has been dead longer than the horizon, nothing sells at all.
     *
     * <p>This is the TPS end state: the horizon stopped moving, the calendar did not, and coverage
     * has run out entirely.
     */
    @Test
    void aSyncDeadLongerThanTheHorizonSellsNothing() {
        horizonConfigured();

        assertEquals(ParkNflyCanadaStayPricing.Withheld.BEYOND_SELLABLE_HORIZON,
                price(1, syncedDaysAgo(HORIZON + 1)).withheld());
    }

    /** The card still covers these stays, which is exactly why the horizon was needed. */
    @Test
    void theCardItselfStillCoversTheWithheldStay() {
        horizonConfigured();

        ParkNflyCanadaStayPricing.Result priced = price(200, syncedDaysAgo(0));

        assertEquals(ParkNflyCanadaStayPricing.Withheld.BEYOND_SELLABLE_HORIZON, priced.withheld(),
                "the vendor's window reaches this stay; only the horizon stops it");
        assertTrue(priced.reason().contains("90"), priced.reason());
    }

    // ---------------------------------------------------------------------------------------------
    // Boundaries and edges
    // ---------------------------------------------------------------------------------------------

    /** The horizon is inclusive: a stay starting exactly on it still sells. */
    @Test
    void theHorizonIsInclusive() {
        LocalDate today = LocalDate.now();
        assertNull(ParkNflyCanadaSellableHorizonProbe.beyond(today.plusDays(HORIZON), syncedDaysAgo(0), HORIZON));
        assertNotNull(ParkNflyCanadaSellableHorizonProbe.beyond(today.plusDays(HORIZON + 1), syncedDaysAgo(0), HORIZON));
    }

    /** A horizon of zero or less is no horizon, rather than one that withholds everything. */
    @Test
    void anUnusableHorizonIsNoHorizon() {
        LocalDate far = LocalDate.now().plusDays(400);
        assertNull(ParkNflyCanadaSellableHorizonProbe.beyond(far, syncedDaysAgo(365), 0));
        assertNull(ParkNflyCanadaSellableHorizonProbe.beyond(far, syncedDaysAgo(365), -90));
        assertNull(ParkNflyCanadaSellableHorizonProbe.beyond(far, syncedDaysAgo(365), null));
    }

    /**
     * An unknown refresh time withholds once a horizon is set.
     *
     * <p>There is no horizon to measure without it, and treating "we do not know when this was
     * refreshed" as current would exempt exactly the lots whose sync is most likely broken.
     */
    @Test
    void anUnknownRefreshTimeWithholds() {
        horizonConfigured();

        for (String unknown : new String[] {null, "", "   ", "last Tuesday"}) {
            assertEquals(ParkNflyCanadaStayPricing.Withheld.BEYOND_SELLABLE_HORIZON,
                    price(1, unknown).withheld(),
                    "a refresh time of '" + unknown + "' must not count as current");
        }
    }


    /**
     * The horizon only ever narrows the vendor's window; it never widens it.
     *
     * <p>A stay past the card's own validity is still {@code NO_RATE_CARD}, not something the
     * horizon lets through because the sync happens to be recent.
     */
    @Test
    void theHorizonNeverWidensTheVendorsOwnWindow() {
        configuration.put(ParkNflyCanadaPricingSettings.GRACE_MINUTES_KEY, "15");
        configuration.put(ParkNflyCanadaPricingSettings.SELLABLE_HORIZON_DAYS_KEY, "900");

        assertEquals(ParkNflyCanadaStayPricing.Withheld.NO_RATE_CARD,
                price(500, syncedDaysAgo(0)).withheld());
    }

    /** Reaches the package-private helper without duplicating its signature in every test. */
    private static final class ParkNflyCanadaSellableHorizonProbe {
        static String beyond(LocalDate checkIn, String syncedAt, Integer horizonDays) {
            return ParkNflyCanadaStayPricing.beyondSellableHorizon(checkIn, syncedAt, horizonDays);
        }
    }
}

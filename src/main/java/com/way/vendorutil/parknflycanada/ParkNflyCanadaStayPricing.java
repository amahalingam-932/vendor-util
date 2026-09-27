package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Prices one Park'N Fly Canada stay, for every screen that has to agree on the figure.
 */
public final class ParkNflyCanadaStayPricing {

    private ParkNflyCanadaStayPricing() {
    }

    /**
     */
    public enum Withheld {

        /**
         */
        NO_RATE_CARD,

        /**
         * The stay is further ahead than Way will sell on the strength of the last refresh.
         */
        BEYOND_SELLABLE_HORIZON,

        /**
         * The lot has no sales tax rate or no fuel surcharge configured.
         */
        NO_TAX_RULE,

        /**
         * The lot's contract carries no Way commission.
         */
        NO_COMMISSION,

        /**
         */
        CURRENCY_MISCONFIGURED,

        /**
         * A configured value the price depends on has no row.
         */
        MISSING_CONFIGURATION,

        /**
         * The stay's own dates cannot be read, or run backwards.
         */
        UNREADABLE_STAY_DATES,

        /**
         * The province or the lot's tax settings could not be read at all.
         */
        LOOKUP_FAILED
    }

    /**
     * One stay to price.
     *
     * @param rateCardJson the card stored on the listing, as the attribute holds it
     * @param province     the listing's province, which labels the tax rule
     * @param vendorTax    the lot's own fuel surcharge and sales tax rate, from its listing
     *                     attributes. Both are mandatory; neither has a fallback.
     * @param wayCommissionPercent Way's commission for this lot, from its contract - 20 means 20%.
     *                     Per listing because it is negotiated per lot, so there is no global rate
     * @param checkInDateTime  start of the stay, {@code yyyy-MM-dd HH:mm:ss}
     * @param checkOutDateTime end of the stay, same format
     */
    public record Request(String rateCardJson,
            Supplier<String> province, Supplier<ParkNflyCanadaVendorTax> vendorTax,
            Supplier<BigDecimal> wayCommissionPercent,
            String checkInDateTime, String checkOutDateTime, String rateCardSyncedAt) {

        /**
         */
        public static Request of(String rateCardJson, String province,
                ParkNflyCanadaVendorTax vendorTax, BigDecimal wayCommissionPercent,
                String checkInDateTime, String checkOutDateTime) {
            return of(rateCardJson, () -> province, () -> vendorTax, () -> wayCommissionPercent,
                    checkInDateTime, checkOutDateTime);
        }

        /**
         */
        public static Request of(String rateCardJson, Supplier<String> province,
                Supplier<ParkNflyCanadaVendorTax> vendorTax,
                Supplier<BigDecimal> wayCommissionPercent, String checkInDateTime,
                String checkOutDateTime) {
            return new Request(rateCardJson, province, vendorTax, wayCommissionPercent,
                    checkInDateTime, checkOutDateTime, null);
        }


        /**
         * The same stay, told when the stored card was last refreshed.
         *
         * <p>Stored as its own listing attribute. Absent is treated as never synced - see
         * {@link Withheld#RATE_CARD_STALE}.
         */
        public Request withSyncedAt(String syncedAt) {
            return new Request(rateCardJson, province, vendorTax, wayCommissionPercent,
                    checkInDateTime, checkOutDateTime, syncedAt);
        }
    }

    /**
     * What the stay costs, or which step stopped it being priced.
     *
     * @param card     the card the price came from, for a caller that shows the vendor's own
     *                 daily rate as a strike-off
     * @param quote    the vendor's side of the price: base, fuel surcharge, tax and total
     * @param perDay   the base spread over the billable days. Display only - the base need not
     *                 divide evenly, so multiplying this back can land a cent out. The quote is
     *                 the authoritative figure.
     * @param withheld null when the stay was priced
     * @param reason   a sentence for the log, naming the values involved; null when priced
     */
    public record Result(ParkNflyCanadaRateCard card, ParkNflyCanadaPricingQuote quote,
            BigDecimal perDay, Withheld withheld, String reason) {

        public boolean isPriced() {
            return withheld == null;
        }
    }

    /**
     * Runs the whole sequence.
     *
     * <p>Never throws: a search over many rows must withhold one bad lot, not fail entirely.
     *
     * @return a priced result, or one naming the step that stopped it
     */
    public static Result price(Request request, ParkNflyCanadaPricingSettings settings) {
        if (request == null || settings == null) {
            return withheld(Withheld.NO_RATE_CARD, "no stay or no settings supplied");
        }

        LocalDate checkInDate = toStayDate(request.checkInDateTime());
        LocalDate checkOutDate = toStayDate(request.checkOutDateTime());

        ParkNflyCanadaRateCard card = checkInDate == null ? null
                : ParkNflyCanadaRateCards.cardFor(request.rateCardJson(), checkInDate, checkOutDate);
        if (card == null || !card.isPriceable()) {
            return withheld(Withheld.NO_RATE_CARD,
                    "no rate card covers " + request.checkInDateTime() + " to "
                            + request.checkOutDateTime());
        }

        // Covering the dates is not the same as being current. Checked here, immediately after the
        // card is found and before anything is queried, because a stay Way will not sell is not
        // worth reading a contract for.
        String outsideHorizon = beyondSellableHorizon(checkInDate, request.rateCardSyncedAt(),
                settings.getSellableHorizonDays());
        if (outsideHorizon != null) {
            return withheld(Withheld.BEYOND_SELLABLE_HORIZON, outsideHorizon);
        }

        // Only now are the province and the lot's own settings looked up - see the note on
        // Request for why they are not resolved before the card.
        //
        // A lookup that fails withholds the stay. It used to be swallowed by the caller's own
        // catch, which left a cart sitting on Way's generic schedule price and let the customer
        // pay it: payment happens before Park'N Fly Canada are ever contacted, so nothing further
        // down would have noticed. Not knowing the province is not a reason to guess a price.
        String province;
        ParkNflyCanadaVendorTax lotSettings;
        BigDecimal wayCommissionPercent;
        try {
            province = request.province() == null ? null : request.province().get();
            lotSettings = request.vendorTax() == null ? null : request.vendorTax().get();
            wayCommissionPercent = request.wayCommissionPercent() == null ? null
                    : request.wayCommissionPercent().get();
        } catch (RuntimeException lookupFailed) {
            return withheld(Withheld.LOOKUP_FAILED,
                    "could not read the province or the lot's tax settings: "
                            + lookupFailed.getMessage());
        }

        // The lot's own sales tax rate and fuel surcharge, and nothing else. Both are mandatory
        // on the listing, so there is no province table to fall back to and no rate compiled in.
        // A lot missing either is withheld rather than charged a figure borrowed from elsewhere.
        ParkNflyCanadaVendorTax vendorTax =
                lotSettings == null ? ParkNflyCanadaVendorTax.NONE : lotSettings;
        ParkNflyCanadaTaxRule taxRule = vendorTax.applyTo(null, province);
        if (taxRule == null) {
            return withheld(Withheld.NO_TAX_RULE,
                    "listing has no sales tax rate or no fuel surcharge configured, province "
                            + province);
        }

        if (wayCommissionPercent == null) {
            return withheld(Withheld.NO_COMMISSION,
                    "the lot's contract carries no Way commission");
        }

        ParkNflyCanadaCurrencyConversion conversion;
        try {
            conversion = settings.currencyConversion();
        } catch (IllegalArgumentException misconfigured) {
            return withheld(Withheld.CURRENCY_MISCONFIGURED, misconfigured.getMessage());
        }

        if (settings.getGraceMinutes() == null) {
            return withheld(Withheld.MISSING_CONFIGURATION,
                    ParkNflyCanadaPricingSettings.GRACE_MINUTES_KEY + " is not configured, so the "
                            + "billable days cannot be worked out");
        }
        java.util.OptionalInt stayDays = ParkNflyCanadaStayDuration.billableDays(
                request.checkInDateTime(), request.checkOutDateTime(), settings.getGraceMinutes());
        if (stayDays.isEmpty()) {
            return withheld(Withheld.UNREADABLE_STAY_DATES,
                    "the stay " + request.checkInDateTime() + " to " + request.checkOutDateTime()
                            + " cannot be read");
        }
        int billableDays = stayDays.getAsInt();

        ParkNflyCanadaPricingQuote quote = ParkNflyCanadaPricingCalculator.quote(billableDays,
                card.dayRate(), card.weekRate(), taxRule, wayCommissionPercent,
                conversion, vendorTax.taxIncludesFuel());

        return new Result(card, quote, perDay(quote), null, null);
    }

    /**
     * Why this stay sits beyond what the last refresh supports, or null when it is inside it.
     */
    static String beyondSellableHorizon(LocalDate checkInDate, String syncedAt, Integer horizonDays) {
        if (horizonDays == null || horizonDays <= 0 || checkInDate == null) {
            return null;
        }
        java.time.Instant synced = toInstant(syncedAt);
        if (synced == null) {
            return "the rate card carries no readable "
                    + ParkNflyCanadaRateCards.RATE_SYNCED_AT_ATTRIBUTE
                    + ", so there is no refresh to measure a " + horizonDays + " day horizon from";
        }
        LocalDate horizon = synced.atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(horizonDays);
        if (!checkInDate.isAfter(horizon)) {
            return null;
        }
        return "the stay starts " + checkInDate + ", beyond the " + horizonDays
                + " day horizon from the last refresh at " + syncedAt + " which reaches " + horizon;
    }

    /**
     */
    private static java.time.Instant toInstant(String syncedAt) {
        if (syncedAt == null || syncedAt.isBlank()) {
            return null;
        }
        try {
            return java.time.Instant.parse(syncedAt.trim());
        } catch (Exception notAnInstant) {
            return null;
        }
    }

    /**
     * The date part of a stay timestamp, or null when it cannot be read.
     */
    public static LocalDate toStayDate(String dateTime) {
        if (dateTime == null || dateTime.trim().length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(dateTime.trim().substring(0, 10));
        } catch (Exception notADate) {
            return null;
        }
    }

    /**
     * The vendor's base spread over the billable days, for a headline per-day figure.
     */
    public static BigDecimal perDay(ParkNflyCanadaPricingQuote quote) {
        if (quote == null || quote.base() == null) {
            return null;
        }
        if (quote.billableDays() <= 0) {
            return quote.base();
        }
        return quote.base().divide(BigDecimal.valueOf(quote.billableDays()), 2, RoundingMode.HALF_UP);
    }

    private static Result withheld(Withheld withheld, String reason) {
        return new Result(null, null, null, withheld, reason);
    }
}
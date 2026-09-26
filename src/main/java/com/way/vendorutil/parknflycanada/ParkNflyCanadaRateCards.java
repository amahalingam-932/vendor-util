package com.way.vendorutil.parknflycanada;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads and writes the stored Park'N Fly Canada rate cards.
 *
 * <p>Cards are held as a JSON array on the listing attribute {@value #RATE_CARD_ATTRIBUTE}, so
 * both search and checkout can read them from a table they already query and no schema change is
 * needed. An array rather than one attribute per rate, because a booking horizon can straddle two
 * cards and an attribute holds a single value per key.
 *
 * <pre>
 * [{"from":"2026-07-22","to":"2027-01-01","hour":4.99,"day":12.99,"week":59.99}]
 * </pre>
 *
 * <p>Money is read as {@link BigDecimal} rather than a floating point type, so a rate never
 * arrives a fraction of a cent off what the vendor published.
 *
 * <p>Reads are lenient and writes are strict: unparseable stored JSON yields an empty list rather
 * than an exception, because the caller's correct response to "no usable card" is to withhold the
 * lot, and that is the same response as "no card at all". A malformed attribute must not take a
 * search page down.
 */
public final class ParkNflyCanadaRateCards {

    private static final Logger logger = LoggerFactory.getLogger(ParkNflyCanadaRateCards.class);

    /**
     * Listing attribute holding the JSON array of cards.
     *
     * <p>Both this and {@link #RATE_SYNCED_AT_ATTRIBUTE} need a row in {@code tbl_service_key}
     * before anything can be written against them - an environment without those two rows refuses
     * every store, logs "required service keys are missing" and withholds every Canadian lot.
     * Staging already has them, proven by the cards stored there. Any environment that has not had
     * them created by hand needs them before this vendor is switched on.
     */
    public static final String RATE_CARD_ATTRIBUTE = "PNF_CA_RATE_CARD";

    /** Listing attribute holding the ISO instant of the last successful sync. */
    public static final String RATE_SYNCED_AT_ATTRIBUTE = "PNF_CA_RATE_SYNCED_AT";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private ParkNflyCanadaRateCards() {
    }

    /**
     * Parses the stored attribute value.
     *
     * @return the cards, oldest window first; empty when the value is blank, malformed, or
     *         contains no card with a usable window
     */
    public static List<ParkNflyCanadaRateCard> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<StoredCard> stored = OBJECT_MAPPER.readValue(json, new TypeReference<>() { });
            List<ParkNflyCanadaRateCard> cards = new ArrayList<>();
            for (StoredCard card : stored) {
                ParkNflyCanadaRateCard parsed = card == null ? null : card.toRateCard();
                if (parsed != null) {
                    cards.add(parsed);
                }
            }
            cards.sort(Comparator.comparing(ParkNflyCanadaRateCard::validFrom));
            return List.copyOf(cards);
        } catch (Exception ex) {
            logger.error("ParkNfly Canada rate card attribute is not readable; treating as absent", ex);
            return List.of();
        }
    }

    /**
     * Serialises cards for storage.
     *
     * @throws IllegalArgumentException if the cards cannot be written, because a sync job that
     *                                  cannot serialise its result must fail loudly rather than
     *                                  overwrite a good attribute with nothing
     */
    public static String toJson(List<ParkNflyCanadaRateCard> cards) {
        List<StoredCard> stored = new ArrayList<>();
        for (ParkNflyCanadaRateCard card : cards == null ? List.<ParkNflyCanadaRateCard>of() : cards) {
            if (card != null) {
                stored.add(StoredCard.of(card));
            }
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(stored);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to serialise ParkNfly Canada rate cards", ex);
        }
    }

    /**
     * Selects the card that prices a stay starting on {@code checkInDate}.
     *
     * @return the covering, priceable card, or {@code null} when none applies - the lot is then
     *         not sellable for those dates and must be withheld rather than priced from a guess
     */
    public static ParkNflyCanadaRateCard cardFor(List<ParkNflyCanadaRateCard> cards, LocalDate checkInDate) {
        return cardFor(cards, checkInDate, null);
    }

    /**
     * Selects the card that prices a stay, requiring one window to cover the whole of it.
     *
     * <p>Checking only the start date prices the entire stay from the window the customer arrives
     * in. A stay from 30 December into 6 January would then be charged at the old year's rates
     * even though most of it falls after the vendor's rate change - and the customer sees and
     * accepts that price before the booking reconciles against what Park'N Fly Canada actually
     * charge. Requiring both ends to sit in one window withholds the lot instead.
     *
     * <p>Splitting the stay across windows would be the richer answer, but it needs Park'N Fly
     * Canada's own rule for a stay that crosses a rate change, and that is neither documented nor
     * confirmed. Withholding is the honest behaviour until it is: the customer is told rates are
     * unavailable for those dates rather than quoted a figure Way cannot honour.
     *
     * @param checkOutDate end of the stay, or {@code null} to consider the start date alone
     * @return the covering, priceable card, or {@code null} when none applies - the lot is then
     *         not sellable for those dates and must be withheld rather than priced from a guess
     */
    public static ParkNflyCanadaRateCard cardFor(List<ParkNflyCanadaRateCard> cards,
            LocalDate checkInDate, LocalDate checkOutDate) {
        if (cards == null || checkInDate == null) {
            return null;
        }
        return cards.stream()
                .filter(card -> card.covers(checkInDate))
                .filter(card -> checkOutDate == null || card.covers(checkOutDate))
                .filter(ParkNflyCanadaRateCard::isPriceable)
                .findFirst()
                .orElse(null);
    }

    /** Convenience for callers holding the raw attribute value. */
    public static ParkNflyCanadaRateCard cardFor(String json, LocalDate checkInDate) {
        return cardFor(fromJson(json), checkInDate, null);
    }

    /** Convenience for callers holding the raw attribute value and both ends of the stay. */
    public static ParkNflyCanadaRateCard cardFor(String json, LocalDate checkInDate,
            LocalDate checkOutDate) {
        return cardFor(fromJson(json), checkInDate, checkOutDate);
    }

    /**
     * The last date any stored card can price, so a sync job knows whether the booking horizon is
     * still covered.
     *
     * @return the latest {@code validTo}, or {@code null} when there are no usable cards
     */
    public static LocalDate coveredUntil(List<ParkNflyCanadaRateCard> cards) {
        if (cards == null) {
            return null;
        }
        return cards.stream()
                .filter(ParkNflyCanadaRateCard::isPriceable)
                .map(ParkNflyCanadaRateCard::validTo)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    /** Wire shape. Dates are strings so the module needs no Jackson date module. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StoredCard(
            @JsonProperty("from") String from,
            @JsonProperty("to") String to,
            @JsonProperty("hour") BigDecimal hour,
            @JsonProperty("day") BigDecimal day,
            @JsonProperty("week") BigDecimal week) {

        static StoredCard of(ParkNflyCanadaRateCard card) {
            return new StoredCard(
                    card.validFrom() == null ? null : card.validFrom().toString(),
                    card.validTo() == null ? null : card.validTo().toString(),
                    card.hourRate(), card.dayRate(), card.weekRate());
        }

        ParkNflyCanadaRateCard toRateCard() {
            LocalDate validFrom = parseDate(from);
            LocalDate validTo = parseDate(to);
            if (validFrom == null || validTo == null || validTo.isBefore(validFrom)) {
                logger.error("ParkNfly Canada rate card skipped; unusable window from={} to={}", from, to);
                return null;
            }
            return new ParkNflyCanadaRateCard(validFrom, validTo, hour, day, week);
        }

        private static LocalDate parseDate(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                // The vendor sends "2026-07-22T00:00:00"; storage keeps the date part only.
                return LocalDate.parse(value.trim().substring(0, 10));
            } catch (Exception ex) {
                return null;
            }
        }
    }
}

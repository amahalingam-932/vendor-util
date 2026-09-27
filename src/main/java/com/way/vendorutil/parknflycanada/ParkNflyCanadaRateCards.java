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
 */
public final class ParkNflyCanadaRateCards {

    private static final Logger logger = LoggerFactory.getLogger(ParkNflyCanadaRateCards.class);

    /**
     * Listing attribute holding the JSON array of cards.
     */
    public static final String RATE_CARD_ATTRIBUTE = "PNF_CA_RATE_CARD";

    /**
     */
    public static final String RATE_SYNCED_AT_ATTRIBUTE = "PNF_CA_RATE_SYNCED_AT";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private ParkNflyCanadaRateCards() {
    }

    /**
     * Parses the stored attribute value.
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
     */
    public static ParkNflyCanadaRateCard cardFor(List<ParkNflyCanadaRateCard> cards, LocalDate checkInDate) {
        return cardFor(cards, checkInDate, null);
    }

    /**
     * Selects the card that prices a stay, requiring one window to cover the whole of it.
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

    /**
     */
    public static ParkNflyCanadaRateCard cardFor(String json, LocalDate checkInDate) {
        return cardFor(fromJson(json), checkInDate, null);
    }

    /**
     */
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

    /**
     */
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

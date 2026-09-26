package com.way.vendorutil.parknflycanada;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParkNflyCanadaRateCardsTest {

    /** The card Park'N Fly Canada actually returned for Toronto Payless on 21 Aug 2026. */
    private static final String PAYLESS_YYZ =
            "[{\"from\":\"2026-07-22\",\"to\":\"2027-01-01\","
                    + "\"hour\":4.99,\"day\":12.99,\"week\":59.99}]";

    @Test
    void aStaySpanningTwoWindowsIsNotPricedFromTheFirst() {
        // W1 ends 2027-01-01, W2 starts the next day at a higher rate. A new-year stay begins in
        // W1 and ends in W2, so neither window can price the whole of it.
        List<ParkNflyCanadaRateCard> twoWindows = ParkNflyCanadaRateCards.fromJson(
                "[{\"from\":\"2026-07-22\",\"to\":\"2027-01-01\",\"hour\":4.99,\"day\":12.99,\"week\":59.99},"
                + "{\"from\":\"2027-01-02\",\"to\":\"2027-06-30\",\"hour\":5.99,\"day\":14.99,\"week\":69.99}]");

        assertNull(ParkNflyCanadaRateCards.cardFor(
                twoWindows, LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 6)));

        // Wholly inside one window still prices, from that window.
        assertEquals(new java.math.BigDecimal("12.99"), ParkNflyCanadaRateCards.cardFor(
                twoWindows, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 27)).dayRate());
        assertEquals(new java.math.BigDecimal("14.99"), ParkNflyCanadaRateCards.cardFor(
                twoWindows, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 8)).dayRate());
    }

    @Test
    void anAbsentCheckOutDateConsidersTheStartAlone() {
        assertNotNull(ParkNflyCanadaRateCards.cardFor(PAYLESS_YYZ, LocalDate.of(2026, 7, 22), null));
    }

    @Test
    void readsTheStoredCard() {
        List<ParkNflyCanadaRateCard> cards = ParkNflyCanadaRateCards.fromJson(PAYLESS_YYZ);

        assertEquals(1, cards.size());
        ParkNflyCanadaRateCard card = cards.get(0);
        assertEquals(LocalDate.of(2026, 7, 22), card.validFrom());
        assertEquals(LocalDate.of(2027, 1, 1), card.validTo());
        assertEquals(new BigDecimal("12.99"), card.dayRate());
        assertEquals(new BigDecimal("59.99"), card.weekRate());
        assertTrue(card.isPriceable());
    }

    /** Rates must survive the round trip exactly; a cent lost here is a cent mischarged. */
    @Test
    void roundTripsWithoutLosingPrecision() {
        List<ParkNflyCanadaRateCard> original = ParkNflyCanadaRateCards.fromJson(PAYLESS_YYZ);

        List<ParkNflyCanadaRateCard> reparsed =
                ParkNflyCanadaRateCards.fromJson(ParkNflyCanadaRateCards.toJson(original));

        assertEquals(original, reparsed);
        assertEquals(new BigDecimal("12.99"), reparsed.get(0).dayRate());
    }

    @Test
    void selectsTheWindowCoveringCheckIn() {
        String twoWindows =
                "[{\"from\":\"2026-07-22\",\"to\":\"2026-12-31\",\"hour\":4.99,\"day\":12.99,\"week\":59.99},"
                        + "{\"from\":\"2027-01-01\",\"to\":\"2027-06-30\",\"hour\":5.49,\"day\":14.99,\"week\":69.99}]";

        assertEquals(new BigDecimal("12.99"),
                ParkNflyCanadaRateCards.cardFor(twoWindows, LocalDate.of(2026, 9, 15)).dayRate());
        assertEquals(new BigDecimal("14.99"),
                ParkNflyCanadaRateCards.cardFor(twoWindows, LocalDate.of(2027, 2, 1)).dayRate());
    }

    @Test
    void windowBoundsAreInclusive() {
        assertNotNull(ParkNflyCanadaRateCards.cardFor(PAYLESS_YYZ, LocalDate.of(2026, 7, 22)));
        assertNotNull(ParkNflyCanadaRateCards.cardFor(PAYLESS_YYZ, LocalDate.of(2027, 1, 1)));
        assertNull(ParkNflyCanadaRateCards.cardFor(PAYLESS_YYZ, LocalDate.of(2026, 7, 21)));
        assertNull(ParkNflyCanadaRateCards.cardFor(PAYLESS_YYZ, LocalDate.of(2027, 1, 2)));
    }

    /**
     * A stay past the last synced window has no defensible price. Returning null is what makes
     * the caller withhold the lot instead of pricing it from a stale card.
     */
    @Test
    void returnsNothingBeyondTheSyncedHorizon() {
        assertNull(ParkNflyCanadaRateCards.cardFor(PAYLESS_YYZ, LocalDate.of(2027, 3, 1)));
    }

    /** A card missing a day or week rate cannot drive the stay bands, so it is not selectable. */
    @Test
    void ignoresCardsThatCannotPriceAStay() {
        String noWeekRate = "[{\"from\":\"2026-07-22\",\"to\":\"2027-01-01\",\"hour\":4.99,\"day\":12.99}]";

        assertNull(ParkNflyCanadaRateCards.cardFor(noWeekRate, LocalDate.of(2026, 9, 15)));
    }

    /** A malformed attribute must not take a search page down. */
    @Test
    void treatsUnreadableStorageAsAbsent() {
        assertTrue(ParkNflyCanadaRateCards.fromJson("not json").isEmpty());
        assertTrue(ParkNflyCanadaRateCards.fromJson("").isEmpty());
        assertTrue(ParkNflyCanadaRateCards.fromJson(null).isEmpty());
        assertNull(ParkNflyCanadaRateCards.cardFor("not json", LocalDate.of(2026, 9, 15)));
    }

    @Test
    void skipsEntriesWithAnUnusableWindow() {
        String mixed =
                "[{\"from\":\"2026-12-31\",\"to\":\"2026-01-01\",\"day\":9.99,\"week\":49.99},"
                        + "{\"from\":\"2026-07-22\",\"to\":\"2027-01-01\",\"day\":12.99,\"week\":59.99}]";

        List<ParkNflyCanadaRateCard> cards = ParkNflyCanadaRateCards.fromJson(mixed);

        assertEquals(1, cards.size());
        assertEquals(new BigDecimal("12.99"), cards.get(0).dayRate());
    }

    /** The vendor sends full timestamps; storage keeps the date part. */
    @Test
    void acceptsTheVendorsTimestampForm() {
        String withTime = "[{\"from\":\"2026-07-22T00:00:00\",\"to\":\"2027-01-01T00:00:00\","
                + "\"day\":12.99,\"week\":59.99}]";

        assertEquals(LocalDate.of(2026, 7, 22),
                ParkNflyCanadaRateCards.fromJson(withTime).get(0).validFrom());
    }

    @Test
    void reportsHowFarTheSyncedHorizonReaches() {
        String twoWindows =
                "[{\"from\":\"2026-07-22\",\"to\":\"2026-12-31\",\"day\":12.99,\"week\":59.99},"
                        + "{\"from\":\"2027-01-01\",\"to\":\"2027-06-30\",\"day\":14.99,\"week\":69.99}]";

        assertEquals(LocalDate.of(2027, 6, 30),
                ParkNflyCanadaRateCards.coveredUntil(ParkNflyCanadaRateCards.fromJson(twoWindows)));
        assertNull(ParkNflyCanadaRateCards.coveredUntil(List.of()));
    }

    @Test
    void writesAnEmptyArrayForNothingToStore() {
        assertEquals("[]", ParkNflyCanadaRateCards.toJson(List.of()));
        assertEquals("[]", ParkNflyCanadaRateCards.toJson(null));
    }

    /**
     * A card with no window can be written, but never reads back as usable. Storage stays
     * write-tolerant while selection stays strict, so a half-built sync result cannot quietly
     * become a price.
     */
    @Test
    void aCardWithoutAWindowNeverBecomesAPrice() {
        String written = ParkNflyCanadaRateCards.toJson(
                List.of(new ParkNflyCanadaRateCard(null, null, null,
                        new BigDecimal("12.99"), new BigDecimal("59.99"))));

        assertTrue(ParkNflyCanadaRateCards.fromJson(written).isEmpty());
        assertNull(ParkNflyCanadaRateCards.cardFor(written, LocalDate.of(2026, 9, 15)));
    }
}

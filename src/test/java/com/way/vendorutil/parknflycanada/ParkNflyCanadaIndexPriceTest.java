package com.way.vendorutil.parknflycanada;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The indexed per-night figure follows the vendor's banding, and refuses to be invented.
 */
class ParkNflyCanadaIndexPriceTest {

    private static final ParkNflyCanadaRateCard CARD = new ParkNflyCanadaRateCard(
            LocalDate.of(2026, 7, 22), LocalDate.of(2027, 1, 1),
            new BigDecimal("4.99"), new BigDecimal("12.99"), new BigDecimal("59.99"));

    /**
     * The figure is the banded base spread over the stay, not the day rate.
     *
     * <p>These are the numbers the live search returned for the same stay lengths, so a change to
     * the banding rule shows up here rather than as a lot sitting in the wrong price filter.
     */
    @Test
    void summarisesTheBandedPriceAtTheConfiguredStayLength() {
        assertEquals(new BigDecimal("12.99"), ParkNflyCanadaIndexPrice.perNight(CARD, 1));
        assertEquals(new BigDecimal("12.99"), ParkNflyCanadaIndexPrice.perNight(CARD, 4));
        // 59.99 flat across five to seven days, so the per-night figure falls as the stay lengthens.
        assertEquals(new BigDecimal("12.00"), ParkNflyCanadaIndexPrice.perNight(CARD, 5));
        assertEquals(new BigDecimal("8.57"), ParkNflyCanadaIndexPrice.perNight(CARD, 7));
        assertEquals(new BigDecimal("9.90"), ParkNflyCanadaIndexPrice.perNight(CARD, 10));
        assertEquals(new BigDecimal("8.57"), ParkNflyCanadaIndexPrice.perNight(CARD, 21));
    }

    /**
     * No configured stay length means no stored price.
     *
     * <p>The alternative - picking one here - would put a figure nobody chose into the column that
     * decides which price filter the lot falls into, and it would look authoritative.
     */
    @Test
    void refusesToGuessTheStayLength() {
        assertNull(ParkNflyCanadaIndexPrice.perNight(CARD, null));
        assertNull(ParkNflyCanadaIndexPrice.perNight(CARD, 0));
        assertNull(ParkNflyCanadaIndexPrice.perNight(CARD, -7));
    }

    /** An unusable card leaves the stored price alone. */
    @Test
    void refusesAnUnusableRateCard() {
        assertNull(ParkNflyCanadaIndexPrice.perNight(null, 3));
        assertNull(ParkNflyCanadaIndexPrice.perNight(new ParkNflyCanadaRateCard(
                LocalDate.of(2026, 7, 22), LocalDate.of(2027, 1, 1), null, null, null), 3));
    }
}

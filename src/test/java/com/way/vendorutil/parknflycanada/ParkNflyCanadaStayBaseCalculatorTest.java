package com.way.vendorutil.parknflycanada;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParkNflyCanadaStayBaseCalculatorTest {

    private static final BigDecimal DAY = new BigDecimal("12.99");
    private static final BigDecimal WEEK = new BigDecimal("59.99");

    @Test
    void linearDailyForOneThroughFourDays() {
        assertEquals(new BigDecimal("12.99"), ParkNflyCanadaStayBaseCalculator.calculateBase(1, DAY, WEEK));
        assertEquals(new BigDecimal("51.96"), ParkNflyCanadaStayBaseCalculator.calculateBase(4, DAY, WEEK));
    }

    @Test
    void flatWeeklyForFiveThroughSevenDays() {
        assertEquals(new BigDecimal("59.99"), ParkNflyCanadaStayBaseCalculator.calculateBase(5, DAY, WEEK));
        assertEquals(new BigDecimal("59.99"), ParkNflyCanadaStayBaseCalculator.calculateBase(7, DAY, WEEK));
    }

    @Test
    void weeklyPlusExtraDailiesFromDayEight() {
        assertEquals(new BigDecimal("72.98"), ParkNflyCanadaStayBaseCalculator.calculateBase(8, DAY, WEEK));
        assertEquals(new BigDecimal("98.96"), ParkNflyCanadaStayBaseCalculator.calculateBase(10, DAY, WEEK));
    }

    /**
     * The weekly rate repeats; it is not charged once and then topped up with dailies for ever.
     *
     * <p>Pinned against a real reservation. On 24 September 2026 a fifteen-day stay at Toronto
     * location 132 was booked and cancelled, and the vendor's own rate lines came back as
     * {@code WEEK RATE 59.99 x 2} plus {@code DAY RATE 12.99 x 1} - a base of 132.97. The previous
     * rule gave 163.91, so Way would have charged 34.96 more than Park'N Fly invoiced.
     */
    @Test
    void theWeeklyRateRepeatsForEveryWholeWeek() {
        // The reservation that proved it.
        assertEquals(new BigDecimal("132.97"), ParkNflyCanadaStayBaseCalculator.calculateBase(15, DAY, WEEK));

        // Two and three whole weeks, no remainder.
        assertEquals(new BigDecimal("119.98"), ParkNflyCanadaStayBaseCalculator.calculateBase(14, DAY, WEEK));
        assertEquals(new BigDecimal("179.97"), ParkNflyCanadaStayBaseCalculator.calculateBase(21, DAY, WEEK));
    }

    /**
     * The days left over after the whole weeks cost whichever is less: those days at the daily
     * rate, or one more week.
     *
     * <p>Twelve days is one week and five days over. Five dailies come to 64.95 and another week
     * to 59.99, so the customer is charged 119.98 rather than 124.94. Thirteen days is the same
     * with six days over.
     *
     * <p>Confirmed by the vendor, not inferred. A twelve-day reservation on 24 September 2026
     * (confirmation 990004021607) came back billed {@code WEEK RATE 59.99 x 2} with <em>no daily
     * line at all</em> - the five days left over were charged as a second whole week because that
     * costs less. Its {@code total_estimated_fee} of 139.54 equals what this calculator produces
     * once the surcharge and tax are added, to the cent.
     */
    @Test
    void aRemainderCostsWhicheverIsLower() {
        // One week plus five days: the extra week wins at 59.99 against 64.95 of dailies.
        assertEquals(new BigDecimal("119.98"), ParkNflyCanadaStayBaseCalculator.calculateBase(12, DAY, WEEK));
        assertEquals(new BigDecimal("119.98"), ParkNflyCanadaStayBaseCalculator.calculateBase(13, DAY, WEEK));

        // And the other way round: four days over is cheaper billed daily than as a whole week.
        assertEquals(new BigDecimal("111.95"), ParkNflyCanadaStayBaseCalculator.calculateBase(11, DAY, WEEK));
    }

    /**
     * The crossover follows the rates rather than a fixed day count.
     *
     * <p>With a cheap daily and an expensive week, six days billed daily is the lower of the two,
     * so that is what the customer pays. A hard-coded "five days or more means a week" would have
     * charged them 90.00 instead of 30.00.
     */
    @Test
    void theCheaperOptionFollowsTheRatesNotTheDayCount() {
        BigDecimal cheapDay = new BigDecimal("5.00");
        BigDecimal dearWeek = new BigDecimal("90.00");

        assertEquals(new BigDecimal("30.00"),
                ParkNflyCanadaStayBaseCalculator.calculateBase(6, cheapDay, dearWeek));
    }

    @Test
    void rejectsMissingRates() {
        assertThrows(IllegalArgumentException.class,
                () -> ParkNflyCanadaStayBaseCalculator.calculateBase(1, null, WEEK));
    }
}

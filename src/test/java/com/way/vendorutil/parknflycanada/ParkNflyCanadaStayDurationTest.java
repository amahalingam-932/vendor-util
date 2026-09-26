package com.way.vendorutil.parknflycanada;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * How many days Park'N Fly Canada actually bill for.
 *
 * <p>Way used to round any remainder up to a whole day. The vendor does not: reservation
 * 990004016038, three days and exactly fifteen minutes, was invoiced at 48.00 - the three day
 * total - while Way charged four days at 62.67. These pin the boundary that settles.
 */
class ParkNflyCanadaStayDurationTest {

    @Test
    void awholeNumberOfDaysIsThatManyDays() {
        assertEquals(3, ParkNflyCanadaStayDuration.billableDays(
                "2026-09-25 00:00:00", "2026-09-28 00:00:00"));
    }

    @Test
    void fifteenMinutesPastAWholeDayIsForgiven() {
        // The exact case the vendor invoiced at three days. Way billed four.
        assertEquals(3, ParkNflyCanadaStayDuration.billableDays(
                "2026-09-25 00:00:00", "2026-09-28 00:15:00"));
    }

    @Test
    void theGraceBoundaryIsInclusive() {
        // Exactly fifteen minutes is forgiven; one second past it is not.
        assertEquals(3, ParkNflyCanadaStayDuration.billableDays(
                "2026-09-25 00:00:00", "2026-09-28 00:15:00"));
        assertEquals(4, ParkNflyCanadaStayDuration.billableDays(
                "2026-09-25 00:00:00", "2026-09-28 00:15:01"));
    }

    @Test
    void anOverstayBeyondTheGraceIsAWholeFurtherDay() {
        // Sixteen minutes, and an hour: both a fourth day. The vendor bands by the day, so there
        // is nothing between three days and four.
        assertEquals(4, ParkNflyCanadaStayDuration.billableDays(
                "2026-09-25 00:00:00", "2026-09-28 00:16:00"));
        assertEquals(4, ParkNflyCanadaStayDuration.billableDays(
                "2026-09-25 00:00:00", "2026-09-28 01:00:00"));
    }

    @Test
    void aStayShorterThanADayStillBillsOne() {
        assertEquals(1, ParkNflyCanadaStayDuration.billableDays(
                "2026-09-25 08:00:00", "2026-09-25 11:00:00"));
        assertEquals(1, ParkNflyCanadaStayDuration.billableDays(
                "2026-09-25 08:00:00", "2026-09-25 08:05:00"));
    }

    @Test
    void theGraceIsTheVendorsToChange() {
        // Passed in rather than assumed, so a change to their rating is one value here.
        assertEquals(4, ParkNflyCanadaStayDuration.billableDays(
                "2026-09-25 00:00:00", "2026-09-28 00:15:00", 0));
        assertEquals(3, ParkNflyCanadaStayDuration.billableDays(
                "2026-09-25 00:00:00", "2026-09-28 00:45:00", 60));
    }

    @Test
    void anUnreadableStayBillsTheMinimum() {
        assertEquals(1, ParkNflyCanadaStayDuration.billableDays("not a date", "nor this"));
        assertEquals(1, ParkNflyCanadaStayDuration.billableDays(null, null));
    }
}

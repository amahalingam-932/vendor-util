package com.way.vendorutil.parknflycanada;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.TimeZone;

/**
 * How many days Park'N Fly Canada actually bill for.
 */
class ParkNflyCanadaStayDurationTest {

    /**
     * The vendor forgives fifteen minutes. It is configured in production - it is their
     * rating rule and they can change it - so the tests state it rather than rely on a
     * default, because there no longer is one.
     */
    private static final int GRACE = 15;

    @Test
    void awholeNumberOfDaysIsThatManyDays() {
        assertEquals(3, ParkNflyCanadaStayDuration.billableDays("2026-09-25 00:00:00", "2026-09-28 00:00:00", GRACE).getAsInt());
    }

    @Test
    void fifteenMinutesPastAWholeDayIsForgiven() {
        // The exact case the vendor invoiced at three days. Way billed four.
        assertEquals(3, ParkNflyCanadaStayDuration.billableDays("2026-09-25 00:00:00", "2026-09-28 00:15:00", GRACE).getAsInt());
    }

    @Test
    void theGraceBoundaryIsInclusive() {
        // Exactly fifteen minutes is forgiven; one second past it is not.
        assertEquals(3, ParkNflyCanadaStayDuration.billableDays("2026-09-25 00:00:00", "2026-09-28 00:15:00", GRACE).getAsInt());
        assertEquals(4, ParkNflyCanadaStayDuration.billableDays("2026-09-25 00:00:00", "2026-09-28 00:15:01", GRACE).getAsInt());
    }

    @Test
    void anOverstayBeyondTheGraceIsAWholeFurtherDay() {
        // Sixteen minutes, and an hour: both a fourth day. The vendor bands by the day, so there
        // is nothing between three days and four.
        assertEquals(4, ParkNflyCanadaStayDuration.billableDays("2026-09-25 00:00:00", "2026-09-28 00:16:00", GRACE).getAsInt());
        assertEquals(4, ParkNflyCanadaStayDuration.billableDays("2026-09-25 00:00:00", "2026-09-28 01:00:00", GRACE).getAsInt());
    }

    @Test
    void aStayShorterThanADayStillBillsOne() {
        assertEquals(1, ParkNflyCanadaStayDuration.billableDays("2026-09-25 08:00:00", "2026-09-25 11:00:00", GRACE).getAsInt());
        assertEquals(1, ParkNflyCanadaStayDuration.billableDays("2026-09-25 08:00:00", "2026-09-25 08:05:00", GRACE).getAsInt());
    }

    @Test
    void theGraceIsTheVendorsToChange() {
        // Passed in rather than assumed, so a change to their rating is one value here.
        assertEquals(4, ParkNflyCanadaStayDuration.billableDays(
                "2026-09-25 00:00:00", "2026-09-28 00:15:00", 0).getAsInt());
        assertEquals(3, ParkNflyCanadaStayDuration.billableDays(
                "2026-09-25 00:00:00", "2026-09-28 00:45:00", 60).getAsInt());
    }

    @Test
    void anUnreadableStayRefusesToAnswer() {
        assertTrue(ParkNflyCanadaStayDuration.billableDays("not a date", "nor this", GRACE).isEmpty());
        assertTrue(ParkNflyCanadaStayDuration.billableDays(null, null, GRACE).isEmpty());
    }

    /**
     * A stay across the clock change is billed by the calendar, not by elapsed hours.
     *
     * <p>Toronto's clocks go back on 1 November 2026, so a three night stay spanning it is
     * seventy-three hours of elapsed time. Measured in milliseconds that repeated hour is a
     * remainder past the grace, and the customer was billed a fourth day - about thirteen dollars
     * plus tax, once a year, on every stay that spanned the change.
     *
     * <p>The timezone is set explicitly because that was the other half of the fault: the old
     * arithmetic gave three days on a UTC node and four on a Toronto one, so the bill depended on
     * how the server happened to be started.
     */
    @Test
    void aStayAcrossTheClockChangeIsBilledByTheCalendar() {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zone : new String[] {"UTC", "America/Toronto", "Asia/Kolkata"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));

                assertEquals(3, ParkNflyCanadaStayDuration.billableDays(
                        "2026-10-31 10:00:00", "2026-11-03 10:00:00", GRACE).getAsInt(), zone);
                assertEquals(3, ParkNflyCanadaStayDuration.billableDays(
                        "2026-03-07 10:00:00", "2026-03-10 10:00:00", GRACE).getAsInt(), zone);
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    /**
     * One unreadable date bills the minimum, not the days since the other one.
     */
    @Test
    void oneUnreadableDateRefusesToAnswer() {
        assertTrue(ParkNflyCanadaStayDuration.billableDays(
                "2026-09-15 10:00:00", "rubbish", GRACE).isEmpty());
        assertTrue(ParkNflyCanadaStayDuration.billableDays(
                "rubbish", "2026-09-18 10:00:00", GRACE).isEmpty());
        assertTrue(ParkNflyCanadaStayDuration.billableDays(
                "2026-09-15 10:00:00", "", GRACE).isEmpty());
        assertTrue(ParkNflyCanadaStayDuration.billableDays(null, null, GRACE).isEmpty());
    }

    /** A check-out before check-in is a data fault, and bills the minimum rather than nothing. */
    @Test
    void aBackwardsStayRefusesToAnswer() {
        assertTrue(ParkNflyCanadaStayDuration.billableDays(
                "2026-09-18 10:00:00", "2026-09-15 10:00:00", GRACE).isEmpty());
    }

    /**
     * The grace boundary is exact to the second.
     *
     * <p>Comparing whole minutes would round fifteen minutes and one second back to fifteen and
     * forgive a day the vendor charges for.
     */
    @Test
    void theGraceBoundaryIsExactToTheSecond() {
        assertEquals(3, ParkNflyCanadaStayDuration.billableDays(
                "2026-09-15 00:00:00", "2026-09-18 00:15:00", GRACE).getAsInt());
        assertEquals(4, ParkNflyCanadaStayDuration.billableDays(
                "2026-09-15 00:00:00", "2026-09-18 00:15:01", GRACE).getAsInt());
    }

    // ---- timestamp formats: every shape Way and the vendor exchange ----

private int days(String from, String to) {
        OptionalInt billable = ParkNflyCanadaStayDuration.billableDays(from, to, GRACE);
        assertTrue(billable.isPresent(), "expected " + from + " to " + to + " to be readable");
        return billable.getAsInt();
    }

    /** Way's own storage format, which must keep working exactly as it did. */
    @Test
    void readsTheFormatWayStores() {
        assertEquals(3, days("2026-10-10 00:00:00", "2026-10-13 00:00:00"));
    }

    /**
     * The vendor's own confirmation format, which used to be unreadable.
     *
     * <p>This is the 15-18 September stay from the live transcript: four billable days.
     */
    @Test
    void readsTheFormatTheVendorReturns() {
        assertEquals(4, days("2026-09-15T08:00:00Z", "2026-09-18T18:00:00Z"));
    }

    /** Their swagger examples drop the seconds. */
    @Test
    void readsATimestampWithoutSeconds() {
        assertEquals(3, days("2026-10-10 00:00", "2026-10-13 00:00"));
        assertEquals(3, days("2026-10-10T00:00", "2026-10-13T00:00"));
    }

    /**
     * Every shape of the same stay gives the same answer.
     *
     * <p>The point of widening the parser is that punctuation must not change a price.
     */
    @Test
    void everyShapeOfTheSameStayBillsTheSame() {
        int stored = days("2026-10-10 00:00:00", "2026-10-13 00:15:00");
        int isoZulu = days("2026-10-10T00:00:00Z", "2026-10-13T00:15:00Z");
        int isoOffset = days("2026-10-10T00:00:00-04:00", "2026-10-13T00:15:00-04:00");
        int isoPlain = days("2026-10-10T00:00:00", "2026-10-13T00:15:00");

        // Three days and exactly the grace period: three billable days, as reservation
        // OGI53602359 confirmed end to end.
        assertEquals(3, stored);
        assertEquals(stored, isoZulu);
        assertEquals(stored, isoOffset);
        assertEquals(stored, isoPlain);
    }

    /**
     * An offset is read and discarded rather than converted.
     *
     * <p>These are wall-clock times at the car park - the vendor asked for the facility's local
     * time - so shifting a marked instant into another zone would bill a different stay. Two
     * timestamps written with different offsets describe the same wall-clock stay here.
     */
    @Test
    void anOffsetDoesNotShiftTheStay() {
        assertEquals(days("2026-10-10T00:00:00-04:00", "2026-10-13T00:00:00-04:00"),
                days("2026-10-10T00:00:00+09:00", "2026-10-13T00:00:00+09:00"));
    }

    /** Genuine rubbish is still unreadable, and still withholds the lot. */
    @Test
    void stillRefusesWhatIsNotATimestamp() {
        assertTrue(ParkNflyCanadaStayDuration.billableDays("next Tuesday", "the Tuesday after", GRACE)
                .isEmpty());
        assertTrue(ParkNflyCanadaStayDuration.billableDays("2026-13-45 99:99:99", "2026-10-13 00:00:00", GRACE)
                .isEmpty());
        assertTrue(ParkNflyCanadaStayDuration.billableDays(null, "2026-10-13 00:00:00", GRACE).isEmpty());
        assertTrue(ParkNflyCanadaStayDuration.billableDays("2026-10-13 00:00:00", "  ", GRACE).isEmpty());
    }

    /** A stay that runs backwards is refused whatever shape it is written in. */
    @Test
    void stillRefusesAStayThatRunsBackwards() {
        assertTrue(ParkNflyCanadaStayDuration.billableDays(
                "2026-10-13T00:00:00Z", "2026-10-10T00:00:00Z", GRACE).isEmpty());
    }
}

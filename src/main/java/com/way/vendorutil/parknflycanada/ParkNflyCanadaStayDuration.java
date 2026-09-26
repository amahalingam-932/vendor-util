package com.way.vendorutil.parknflycanada;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Billable parking days for Park'N Fly Canada pricing.
 *
 * <p>Park'N Fly Canada forgive a short overstay: a stay that runs a few minutes past a whole day is
 * billed as that whole day, not the next one. Way used to round any remainder up, matching the
 * generic {@code CartManagementServiceImpl#calculateDurationInDays}, and so charged a whole extra
 * day for a few minutes the vendor never billed for.
 *
 * <p>That was confirmed against a real invoice rather than inferred. Reservation 990004016038, a
 * stay of three days and exactly fifteen minutes, came back from the vendor with
 * {@code total_estimated_fee = 48.00} - the three day total - while Way charged four days at
 * 62.67. An earlier booking, 990004006326, had already lost 15.98 the same way.
 *
 * <p>Fifteen minutes is therefore forgiven, and the boundary is inclusive: exactly fifteen minutes
 * past three days is three days, which is the case the invoice above settles. A second past that
 * is a fourth day, because the vendor's own grace has run out.
 */
public final class ParkNflyCanadaStayDuration {

    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    /**
     * The overstay the vendor forgives, confirmed by Park'N Fly Canada and by their invoice for
     * reservation 990004016038.
     */
    public static final int DEFAULT_GRACE_MINUTES = 15;

    private ParkNflyCanadaStayDuration() {
    }

    /** Billable days with the vendor's own fifteen minute grace. */
    public static int billableDays(String startDateTime, String endDateTime) {
        return billableDays(startDateTime, endDateTime, DEFAULT_GRACE_MINUTES);
    }

    /**
     * Billable days, forgiving an overstay of up to {@code graceMinutes}.
     *
     * <p>The grace is a term of the vendor's rating, not a Way policy, so it is a parameter rather
     * than a constant buried in the arithmetic - if Park'N Fly Canada change it, one value changes
     * and every screen follows.
     *
     * @param graceMinutes minutes past a whole day that are not charged; zero or less bills any
     *                     remainder as a further day
     * @return at least one day; an unreadable date pair also counts as one, which is what the
     *         caller then prices and what the vendor would invoice at minimum
     */
    public static int billableDays(String startDateTime, String endDateTime, int graceMinutes) {
        try {
            Calendar from = toCalendar(startDateTime);
            Calendar to = toCalendar(endDateTime);
            long diff = to.getTimeInMillis() - from.getTimeInMillis();

            long days = diff / MILLIS_PER_DAY;
            long remainder = diff % MILLIS_PER_DAY;
            long grace = Math.max(0L, (long) graceMinutes) * 60L * 1000L;
            if (remainder > grace) {
                days++;
            }
            return (int) Math.max(1L, days);
        } catch (Exception ex) {
            return 1;
        }
    }

    /**
     * The same parse {@code com.way.util.dateutil.DateUtils#getCalendar(String)} performs, kept
     * here so this artifact does not depend on way-util - and so on Spring, Hazelcast and JPA -
     * for four lines of date handling.
     *
     * <p>Behaviour is deliberately identical to that method, including the swallowed
     * {@link ParseException} that leaves the calendar on the current time. Changing it belongs in
     * its own ticket, not in a move: a stay whose dates do not parse is currently billed as one
     * day by the caller's own catch, and quietly altering that here would change what a customer
     * pays as a side effect of splitting an artifact.
     */
    private static Calendar toCalendar(String dateInString) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar calendar = Calendar.getInstance();
        try {
            calendar.setTime(sdf.parse(dateInString));
        } catch (ParseException unparseable) {
            // Intentionally ignored; see the note above.
        }
        return calendar;
    }
}

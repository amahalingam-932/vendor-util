package com.way.vendorutil.parknflycanada;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.OptionalInt;

/**
 * Billable parking days for Park'N Fly Canada pricing.
 */
public final class ParkNflyCanadaStayDuration {

    /**
     * Timestamp shapes a stay may arrive in, tried in order: Way's own, then the vendor's.
     * A zone or offset is parsed and dropped - these are wall-clock times at the car park.
     */
    private static final DateTimeFormatter[] ACCEPTED_FORMATS = {
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss[.SSS][XXX][X]"),
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm[XXX][X]"),
    };

    /**
     * A stay that can be read is at least one day. A floor, not a rate, so it stays a constant.
     * Never a fallback for a stay that cannot be read - that case has no answer and says so.
     */
    private static final int MINIMUM_BILLABLE_DAYS = 1;

    private ParkNflyCanadaStayDuration() {
    }

    /**
     * Billable days, forgiving an overstay of up to {@code graceMinutes}.
     *
     * @param graceMinutes minutes past a whole day that are not charged; zero or less bills any
     *                     remainder as a further day
     * @return the billable days, or empty when the stay cannot be read - dates that do not parse,
     *         or a check-out before its check-in. Empty rather than a number, because there is no
     *         safe number to return for a stay nobody can describe.
     */
    public static OptionalInt billableDays(String startDateTime, String endDateTime,
            int graceMinutes) {
        LocalDateTime from = parse(startDateTime);
        LocalDateTime to = parse(endDateTime);
        if (from == null || to == null) {
            return OptionalInt.empty();
        }

        Duration stay = Duration.between(from, to);
        if (stay.isNegative()) {
            return OptionalInt.empty();
        }

        long days = stay.toDays();
        Duration remainder = stay.minusDays(days);
        // Compared as durations rather than whole minutes on purpose. Truncating the remainder to
        // minutes would make fifteen minutes and one second look like fifteen, and forgive a day
        // the vendor charges for.
        Duration grace = Duration.ofMinutes(Math.max(0, graceMinutes));
        if (remainder.compareTo(grace) > 0) {
            days++;
        }
        return OptionalInt.of((int) Math.max(MINIMUM_BILLABLE_DAYS, days));
    }

    /**
     * Reads a stay timestamp in any of the shapes Way and the vendor exchange.
     */
    private static LocalDateTime parse(String dateTime) {
        if (dateTime == null || dateTime.trim().isEmpty()) {
            return null;
        }
        String trimmed = dateTime.trim();
        for (DateTimeFormatter format : ACCEPTED_FORMATS) {
            try {
                return LocalDateTime.parse(trimmed, format);
            } catch (DateTimeParseException notThisShape) {
            }
        }
        return null;
    }
}

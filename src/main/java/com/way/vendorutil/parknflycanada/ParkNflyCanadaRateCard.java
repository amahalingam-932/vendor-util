package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One Park'N Fly Canada rate card: the unit rates for a lot, and the dates they apply between.
 *
 * @param validFrom first date the rates apply, inclusive
 * @param validTo   last date the rates apply, inclusive
 * @param hourRate  {@code hour_rate}; not used by the current stay bands but stored as returned
 * @param dayRate   {@code day_rate}
 * @param weekRate  {@code week_rate}
 */
public record ParkNflyCanadaRateCard(
        LocalDate validFrom,
        LocalDate validTo,
        BigDecimal hourRate,
        BigDecimal dayRate,
        BigDecimal weekRate) {

    /**
     */
    public boolean covers(LocalDate date) {
        if (date == null || validFrom == null || validTo == null) {
            return false;
        }
        return !date.isBefore(validFrom) && !date.isAfter(validTo);
    }

    /**
     * True when this card can price a stay. The bands need a daily and a weekly rate; a card
     * missing either cannot be used, and the caller should withhold the lot rather than guess.
     */
    public boolean isPriceable() {
        return dayRate != null && weekRate != null
                && dayRate.signum() > 0 && weekRate.signum() > 0;
    }
}

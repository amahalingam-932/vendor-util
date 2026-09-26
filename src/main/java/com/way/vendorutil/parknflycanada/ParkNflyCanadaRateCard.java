package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One Park'N Fly Canada rate card: the unit rates for a lot, and the dates they apply between.
 *
 * <p>{@code locationListGet} takes a single {@code start_date} but answers with a card stamped
 * {@code date_from}/{@code date_to} - for Toronto Payless, a request for 21 Aug 2026 returned
 * rates valid from 22 Jul 2026 to 1 Jan 2027. The rates are therefore not per-date, and a booking
 * horizon of several months is usually covered by one card. Keeping the window is what lets a
 * sync job fetch once instead of once per searchable date.
 *
 * <p>Rates are base amounts only. The fuel surcharge and sales tax are province-specific, are not
 * returned by the vendor, and live in configuration - see {@link ParkNflyCanadaTaxRule}.
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

    /** True when {@code date} falls inside this card's validity window, both ends inclusive. */
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

package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The single per-night figure Way stores for a Park'N Fly Canada lot, so the database and the
 * search index can filter and sort on it.
 */
public final class ParkNflyCanadaIndexPrice {

    private ParkNflyCanadaIndexPrice() {
    }

    /**
     * The per-night price of a {@code representativeDays}-long stay at this card's rates.
     *
     * @param card the rate card in force, or null
     * @param representativeDays the configured stay length to summarise at
     * @return the per-night figure, or null when it cannot be worked out - in which case the caller
     *         leaves the stored price alone rather than writing a guess
     */
    public static BigDecimal perNight(ParkNflyCanadaRateCard card, Integer representativeDays) {
        if (card == null || !card.isPriceable()) {
            return null;
        }
        if (representativeDays == null || representativeDays <= 0) {
            return null;
        }
        BigDecimal base = ParkNflyCanadaStayBaseCalculator.calculateBase(
                representativeDays, card.dayRate(), card.weekRate());
        return base.divide(BigDecimal.valueOf(representativeDays), 2, RoundingMode.HALF_UP);
    }
}

package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Base parking amount from Park'N Fly Canada's unit rates and the length of the stay.
 *
 * <p>The rule, taken from the vendor's own {@code rate_lines} on a real reservation:
 *
 * <ul>
 *   <li>every whole week is charged at the weekly rate</li>
 *   <li>whatever is left over is charged as a short stay in its own right</li>
 *   <li>a short stay is charged per day up to four days, and at the flat weekly rate from five to
 *       seven, because seven dailies cost more than a week</li>
 * </ul>
 *
 * <p>A fifteen-day stay therefore comes to two weeks plus one day. The vendor confirmed exactly
 * that on 24 September 2026, billing {@code WEEK RATE 59.99 x 2} and {@code DAY RATE 12.99 x 1}
 * for a base of 132.97.
 *
 * <h2>What this replaced, and why it mattered</h2>
 *
 * <p>Until that reservation was made, this class applied the weekly rate <strong>once</strong> and
 * billed every day past the seventh at the daily rate. A comment recorded it as confirmed with
 * Ops. It was wrong, and because no test went beyond ten days nothing caught it: a fifteen-day
 * stay was priced at 163.91 against the vendor's 132.97, so Way would have charged a customer
 * 34.96 more than Park'N Fly invoiced, and the gap widened with every extra day.
 *
 * <p>Both shapes of remainder have been checked against real reservations, so none of this rests
 * on inference:
 *
 * <ul>
 *   <li>fifteen days, one day over - billed {@code WEEK RATE x 2} plus {@code DAY RATE x 1},
 *       base 132.97 (confirmation 990004021606)</li>
 *   <li>twelve days, five days over - billed {@code WEEK RATE x 2} with no daily line at all,
 *       base 119.98 (confirmation 990004021607)</li>
 * </ul>
 *
 * <p>The second is the telling one: the vendor charged a whole extra week rather than five
 * separate days, because 59.99 is less than five dailies at 12.99. Both reservations were
 * cancelled once their fee had been read.
 */
public final class ParkNflyCanadaStayBaseCalculator {

    private static final int DAYS_PER_WEEK = 7;

    private ParkNflyCanadaStayBaseCalculator() {
    }

    public static BigDecimal calculateBase(int billableDays, BigDecimal dayRate, BigDecimal weekRate) {
        if (billableDays <= 0) {
            throw new IllegalArgumentException("billableDays must be positive");
        }
        if (dayRate == null || weekRate == null) {
            throw new IllegalArgumentException("dayRate and weekRate are required");
        }

        int wholeWeeks = billableDays / DAYS_PER_WEEK;
        int remainingDays = billableDays % DAYS_PER_WEEK;

        BigDecimal total = weekRate.multiply(BigDecimal.valueOf(wholeWeeks));
        if (remainingDays > 0) {
            total = total.add(shortStay(remainingDays, dayRate, weekRate));
        }
        return scaleMoney(total);
    }

    /**
     * A stay of one to six days, or the days left over after the whole weeks: whichever of the two
     * ways of charging it comes to less.
     *
     * <p>Expressed as a comparison rather than a day count on purpose. With today's rates the
     * crossover falls after the fourth day - five dailies at 12.99 come to 64.95 against a 59.99
     * week - but that is a consequence of those two numbers, not a rule. Should Park'N Fly change
     * either rate, a hard-coded threshold would quietly start charging the dearer of the two;
     * comparing them cannot.
     *
     * <p>Seven is never passed in: that is a whole week and is counted as one.
     */
    private static BigDecimal shortStay(int days, BigDecimal dayRate, BigDecimal weekRate) {
        BigDecimal daily = dayRate.multiply(BigDecimal.valueOf(days));
        return daily.min(weekRate);
    }

    private static BigDecimal scaleMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}

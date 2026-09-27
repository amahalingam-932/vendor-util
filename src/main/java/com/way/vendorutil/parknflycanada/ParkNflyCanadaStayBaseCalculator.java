package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Base parking amount from Park'N Fly Canada's unit rates and the length of the stay.
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
     */
    private static BigDecimal shortStay(int days, BigDecimal dayRate, BigDecimal weekRate) {
        BigDecimal daily = dayRate.multiply(BigDecimal.valueOf(days));
        return daily.min(weekRate);
    }

    private static BigDecimal scaleMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}

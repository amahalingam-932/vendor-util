package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;

/**
 * Province-scoped surcharge and sales-tax rule for Park'N Fly Canada.
 *
 * @param province      two-letter province code, upper case (e.g. {@code ON})
 * @param hstRate       sales-tax rate as a decimal fraction (e.g. {@code 0.13} for Ontario HST)
 * @param fuelSurcharge flat per-reservation fuel surcharge in vendor currency
 */
public record ParkNflyCanadaTaxRule(String province, BigDecimal hstRate, BigDecimal fuelSurcharge) {
}

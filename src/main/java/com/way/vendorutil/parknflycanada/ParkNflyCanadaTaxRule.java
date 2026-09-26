package com.way.vendorutil.parknflycanada;

import java.math.BigDecimal;

/**
 * Province-scoped surcharge and sales-tax rule for Park'N Fly Canada.
 *
 * <p>Park'N Fly Canada's IT director (Marjorie Pease, 3 Sep 2026) confirmed that the fuel
 * surcharge amount and the sales-tax rate are set by the province the lot operates in, and that
 * the fuel surcharge amount itself is subject to change. Neither value is returned by
 * {@code locationListGet}, so Way holds them in configuration
 * ({@code way.pnfcanada.provinceTaxRules}) and resolves them from the listing's province.
 *
 * @param province      two-letter province code, upper case (e.g. {@code ON})
 * @param hstRate       sales-tax rate as a decimal fraction (e.g. {@code 0.13} for Ontario HST)
 * @param fuelSurcharge flat per-reservation fuel surcharge in vendor currency
 */
public record ParkNflyCanadaTaxRule(String province, BigDecimal hstRate, BigDecimal fuelSurcharge) {
}

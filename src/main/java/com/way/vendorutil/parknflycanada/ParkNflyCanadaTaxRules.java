package com.way.vendorutil.parknflycanada;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses and resolves the configured Park'N Fly Canada province tax rules.
 *
 * <p>Config format follows the comma-separated convention already used for PARC lists in
 * {@code ParcSystemUtil}: one {@code province:hstRate:fuelSurcharge} triple per province.
 *
 * <pre>
 * way.pnfcanada.provinceTaxRules=ON:0.13:3.96
 * </pre>
 *
 * <p>Resolution is deliberately fail-closed. A province with no configured rule returns
 * {@code null} and the caller blocks checkout, because the alternative - silently applying
 * Ontario's 13% to a British Columbia lot - would charge the customer the wrong tax and would
 * not match the fee the vendor bills on {@code confirmationGet}.
 */
public final class ParkNflyCanadaTaxRules {

    private static final Logger logger = LoggerFactory.getLogger(ParkNflyCanadaTaxRules.class);

    private static final int RULE_PARTS = 3;

    private ParkNflyCanadaTaxRules() {
    }

    /**
     * @param configValue comma-separated {@code province:hstRate:fuelSurcharge} triples; blank
     *                    yields an empty map
     * @return province code (upper case) to rule, preserving configuration order
     */
    public static Map<String, ParkNflyCanadaTaxRule> parse(String configValue) {
        Map<String, ParkNflyCanadaTaxRule> rules = new LinkedHashMap<>();
        if (StringUtils.isBlank(configValue)) {
            return rules;
        }
        for (String entry : configValue.split(",")) {
            if (StringUtils.isBlank(entry)) {
                continue;
            }
            String[] parts = entry.trim().split(":");
            if (parts.length != RULE_PARTS) {
                logger.error("ParkNfly Canada province tax rule ignored; expected province:hstRate:fuel entry={}",
                        entry.trim());
                continue;
            }
            String province = normalizeProvince(parts[0]);
            if (province == null) {
                logger.error("ParkNfly Canada province tax rule ignored; blank province entry={}", entry.trim());
                continue;
            }
            try {
                BigDecimal hstRate = new BigDecimal(parts[1].trim());
                BigDecimal fuelSurcharge = new BigDecimal(parts[2].trim());
                if (hstRate.signum() < 0 || fuelSurcharge.signum() < 0) {
                    logger.error("ParkNfly Canada province tax rule ignored; negative amount province={}", province);
                    continue;
                }
                rules.put(province, new ParkNflyCanadaTaxRule(province, hstRate, fuelSurcharge));
            } catch (NumberFormatException ex) {
                logger.error("ParkNfly Canada province tax rule ignored; unparseable number entry={}", entry.trim());
            }
        }
        return rules;
    }

    /**
     * Resolves the rule for a listing.
     *
     * @param rules           parsed rules, keyed by upper-case province code
     * @param listingProvince province code from the listing address; may be blank
     * @param defaultProvince fallback province code from config; may be blank, in which case a
     *                        listing without a province resolves to {@code null}
     * @return the matching rule, or {@code null} when the province is unknown or unconfigured
     */
    public static ParkNflyCanadaTaxRule resolve(Map<String, ParkNflyCanadaTaxRule> rules, String listingProvince,
            String defaultProvince) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        String province = normalizeProvince(listingProvince);
        if (province == null) {
            province = normalizeProvince(defaultProvince);
            if (province != null) {
                logger.warn("ParkNfly Canada listing has no province; falling back to configured default={}",
                        province);
            }
        }
        return province == null ? null : rules.get(province);
    }

    private static String normalizeProvince(String province) {
        return StringUtils.isBlank(province) ? null : province.trim().toUpperCase();
    }
}

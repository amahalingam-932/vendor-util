package com.way.vendorutil.parknflycanada;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParkNflyCanadaTaxRulesTest {

    @Test
    void parsesMultipleProvinces() {
        Map<String, ParkNflyCanadaTaxRule> rules =
                ParkNflyCanadaTaxRules.parse("ON:0.13:3.96, bc:0.12:4.25");

        assertEquals(2, rules.size());
        assertEquals(new BigDecimal("0.13"), rules.get("ON").hstRate());
        assertEquals(new BigDecimal("3.96"), rules.get("ON").fuelSurcharge());
        assertEquals(new BigDecimal("4.25"), rules.get("BC").fuelSurcharge());
    }

    @Test
    void skipsMalformedEntriesWithoutLosingValidOnes() {
        Map<String, ParkNflyCanadaTaxRule> rules =
                ParkNflyCanadaTaxRules.parse("ON:0.13:3.96,QC:notanumber:1.00,AB:0.05");

        assertEquals(1, rules.size());
        assertTrue(rules.containsKey("ON"));
    }

    @Test
    void blankConfigParsesToEmpty() {
        assertTrue(ParkNflyCanadaTaxRules.parse("   ").isEmpty());
        assertTrue(ParkNflyCanadaTaxRules.parse(null).isEmpty());
    }

    @Test
    void resolvesListingProvinceCaseInsensitively() {
        Map<String, ParkNflyCanadaTaxRule> rules = ParkNflyCanadaTaxRules.parse("ON:0.13:3.96");

        assertEquals("ON", ParkNflyCanadaTaxRules.resolve(rules, "on", "").province());
    }

    /** An unconfigured province must not silently borrow another province's rate. */
    @Test
    void returnsNullForUnconfiguredProvince() {
        Map<String, ParkNflyCanadaTaxRule> rules = ParkNflyCanadaTaxRules.parse("ON:0.13:3.96");

        assertNull(ParkNflyCanadaTaxRules.resolve(rules, "BC", ""));
        assertNull(ParkNflyCanadaTaxRules.resolve(rules, "BC", "ON"));
    }

    @Test
    void fallsBackToDefaultOnlyWhenListingHasNoProvince() {
        Map<String, ParkNflyCanadaTaxRule> rules = ParkNflyCanadaTaxRules.parse("ON:0.13:3.96");

        assertEquals("ON", ParkNflyCanadaTaxRules.resolve(rules, null, "ON").province());
        assertNull(ParkNflyCanadaTaxRules.resolve(rules, null, ""));
    }
}

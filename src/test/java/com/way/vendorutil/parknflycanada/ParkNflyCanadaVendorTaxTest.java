package com.way.vendorutil.parknflycanada;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The listing's own surcharge, sales tax and tax basis, read from its {@code VENDOR} tax rules.
 *
 * <p>These rows are entered by Ops in WayPanel and are the only per-lot pricing configuration this
 * vendor has, so a misreading here is charged to a customer.
 */
class ParkNflyCanadaVendorTaxTest {

    private static ParkNflyCanadaWayFee.Rule rule(String type, String value) {
        return new ParkNflyCanadaWayFee.Rule(type, new BigDecimal(value));
    }

    @Test
    void readsSurchargeAndTaxRate() {
        ParkNflyCanadaVendorTax tax = ParkNflyCanadaVendorTax.from(
                List.of(rule("AMOUNT", "3.96"), rule("PERCENT", "13")));

        assertEquals(new BigDecimal("3.96"), tax.fuelSurcharge());
        assertEquals(new BigDecimal("13"), tax.taxRate());
    }

    /** Two levies of the same kind are a real configuration, so they add rather than overwrite. */
    @Test
    void rulesOfTheSameKindAddUp() {
        ParkNflyCanadaVendorTax tax = ParkNflyCanadaVendorTax.from(
                List.of(rule("AMOUNT", "3.96"), rule("AMOUNT", "1.00"),
                        rule("PERCENT", "8"), rule("PERCENT", "5")));

        assertEquals(new BigDecimal("4.96"), tax.fuelSurcharge());
        assertEquals(new BigDecimal("13"), tax.taxRate());
    }

    /** Lots configured before the flag was dropped still carry the row; it must be ignored. */
    @Test
    void taxOnFuelRowsAreIgnored() {
        ParkNflyCanadaVendorTax tax = ParkNflyCanadaVendorTax.from(
                List.of(rule("PERCENT", "13"), rule("TAX_ON_FUEL", "1")));

        assertEquals(new BigDecimal("13"), tax.taxRate());
        assertNull(tax.fuelSurcharge());
    }

    /** A lot carrying only the dropped flag has nothing configured. */
    @Test
    void taxOnFuelAloneReadsAsNone() {
        assertSame(ParkNflyCanadaVendorTax.NONE,
                ParkNflyCanadaVendorTax.from(List.of(rule("TAX_ON_FUEL", "1"))));
    }

    @Test
    void noRulesFallsBackToTheProvinceDefault() {
        assertSame(ParkNflyCanadaVendorTax.NONE, ParkNflyCanadaVendorTax.from(List.of()));
        assertSame(ParkNflyCanadaVendorTax.NONE, ParkNflyCanadaVendorTax.from(null));
        assertTrue(ParkNflyCanadaVendorTax.NONE.isEmpty());
    }

    /** An unreadable or unknown row is skipped: one bad row must not take a lot off sale. */
    @Test
    void unknownAndNullRulesAreIgnored() {
        ParkNflyCanadaVendorTax tax = ParkNflyCanadaVendorTax.from(
                java.util.Arrays.asList(rule("SOMETHING_ELSE", "9"), null, rule("PERCENT", "13")));

        assertEquals(new BigDecimal("13"), tax.taxRate());
        assertNull(tax.fuelSurcharge());
    }

}

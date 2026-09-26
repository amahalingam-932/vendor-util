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
        assertFalse(tax.taxIncludesFuel());
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

    @Test
    void taxOnFuelIsReadFromItsOwnRule() {
        ParkNflyCanadaVendorTax on = ParkNflyCanadaVendorTax.from(
                List.of(rule("PERCENT", "13"), rule("TAX_ON_FUEL", "1")));
        ParkNflyCanadaVendorTax off = ParkNflyCanadaVendorTax.from(
                List.of(rule("PERCENT", "13"), rule("TAX_ON_FUEL", "0")));

        assertTrue(on.taxIncludesFuel());
        assertFalse(off.taxIncludesFuel());
    }

    /**
     * A flag is not a levy. Two of these must not add up to 2 and mean something new, so the last
     * row read decides - which is also what happens if a lot is somehow configured twice.
     */
    @Test
    void repeatedTaxOnFuelRulesDoNotAccumulate() {
        ParkNflyCanadaVendorTax tax = ParkNflyCanadaVendorTax.from(
                List.of(rule("TAX_ON_FUEL", "1"), rule("TAX_ON_FUEL", "0")));

        assertFalse(tax.taxIncludesFuel());
    }

    /** A lot that predates this field taxes the base alone, as it did before the field existed. */
    @Test
    void absentTaxOnFuelMeansBaseOnly() {
        ParkNflyCanadaVendorTax tax = ParkNflyCanadaVendorTax.from(List.of(rule("PERCENT", "13")));

        assertNull(tax.taxOnFuel());
        assertFalse(tax.taxIncludesFuel());
    }

    /** A lot carrying only the basis flag is still configured, so it must not read as NONE. */
    @Test
    void taxOnFuelAloneIsNotEmpty() {
        ParkNflyCanadaVendorTax tax = ParkNflyCanadaVendorTax.from(List.of(rule("TAX_ON_FUEL", "1")));

        assertFalse(tax.isEmpty());
        assertTrue(tax.taxIncludesFuel());
    }

    @Test
    void noRulesFallsBackToTheProvinceDefault() {
        assertSame(ParkNflyCanadaVendorTax.NONE, ParkNflyCanadaVendorTax.from(List.of()));
        assertSame(ParkNflyCanadaVendorTax.NONE, ParkNflyCanadaVendorTax.from(null));
        assertTrue(ParkNflyCanadaVendorTax.NONE.isEmpty());
        assertFalse(ParkNflyCanadaVendorTax.NONE.taxIncludesFuel());
    }

    /** An unreadable or unknown row is skipped: one bad row must not take a lot off sale. */
    @Test
    void unknownAndNullRulesAreIgnored() {
        ParkNflyCanadaVendorTax tax = ParkNflyCanadaVendorTax.from(
                java.util.Arrays.asList(rule("SOMETHING_ELSE", "9"), null, rule("PERCENT", "13")));

        assertEquals(new BigDecimal("13"), tax.taxRate());
        assertNull(tax.fuelSurcharge());
    }

    /** The two-argument form is what older callers use; it must keep meaning "base only". */
    @Test
    void legacyConstructorDefaultsToBaseOnly() {
        ParkNflyCanadaVendorTax tax =
                new ParkNflyCanadaVendorTax(new BigDecimal("3.96"), new BigDecimal("13"));

        assertNull(tax.taxOnFuel());
        assertFalse(tax.taxIncludesFuel());
    }
}

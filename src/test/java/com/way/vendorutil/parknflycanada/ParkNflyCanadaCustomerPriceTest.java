package com.way.vendorutil.parknflycanada;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The one number every screen must agree on.
 *
 * <p>These pin the arithmetic that search, the lot detail page and checkout had each implemented
 * differently, each one missing a different part and each one shipping a price the customer would
 * not have been charged.
 */
class ParkNflyCanadaCustomerPriceTest {

    /** Ontario: 13% sales tax on the base, 3.96 fuel surcharge, taxed on the base alone. */
    private static final ParkNflyCanadaTaxRule ONTARIO =
            new ParkNflyCanadaTaxRule("ON", new BigDecimal("0.13"), new BigDecimal("3.96"));

    private static ParkNflyCanadaPricingQuote fourDayQuote() {
        return ParkNflyCanadaPricingCalculator.quote(4, new BigDecimal("12.99"), new BigDecimal("59.99"),
                ONTARIO, new BigDecimal("15"));
    }

    @Test
    void totalIsTheVendorStayPlusWaysFee() {
        // 4 x 12.99 = 51.96 base, 3.96 fuel, 13% of base = 6.75 tax, and a 10% Way fee on the base.
        BigDecimal wayFee = ParkNflyCanadaWayFee.on(new BigDecimal("51.96"),
                List.of(new ParkNflyCanadaWayFee.Rule("PERCENTAGE", new BigDecimal("10"))));

        ParkNflyCanadaCustomerPrice price = ParkNflyCanadaCustomerPrice.of(fourDayQuote(), wayFee);

        assertEquals(new BigDecimal("51.96"), price.base());
        assertEquals(new BigDecimal("3.96"), price.fuelSurcharge());
        assertEquals(new BigDecimal("6.75"), price.vendorTax());
        assertEquals(new BigDecimal("5.20"), price.wayFee());
        assertEquals(new BigDecimal("67.87"), price.total());
    }

    @Test
    void theLinesAlwaysAddUpToTheTotal() {
        ParkNflyCanadaCustomerPrice price = ParkNflyCanadaCustomerPrice.of(
                fourDayQuote(), new BigDecimal("10.15"));

        assertEquals(price.total(),
                price.base().add(price.fuelSurcharge()).add(price.taxesAndFees()));
    }

    @Test
    void aLotWithNoWayFeePaysTheVendorTotal() {
        ParkNflyCanadaCustomerPrice price = ParkNflyCanadaCustomerPrice.of(fourDayQuote(), null);

        // 51.96 + 3.96 + 6.75, and nothing invented on top.
        assertEquals(new BigDecimal("62.67"), price.total());
        assertEquals(BigDecimal.ZERO, price.wayFee());
    }

    @Test
    void aPercentageFeeIsTakenOnTheBaseOnly() {
        // Not on the surcharge or the vendor's tax - those are not Way's to take a share of.
        BigDecimal fee = ParkNflyCanadaWayFee.on(new BigDecimal("51.96"),
                List.of(new ParkNflyCanadaWayFee.Rule("PERCENT", new BigDecimal("10"))));

        assertEquals(new BigDecimal("5.20"), fee);
    }

    @Test
    void aFlatFeeIsChargedAsItStands() {
        BigDecimal fee = ParkNflyCanadaWayFee.on(new BigDecimal("51.96"),
                List.of(new ParkNflyCanadaWayFee.Rule("FLAT", new BigDecimal("2.50"))));

        assertEquals(new BigDecimal("2.50"), fee);
    }

    @Test
    void severalRulesAddTogether() {
        BigDecimal fee = ParkNflyCanadaWayFee.on(new BigDecimal("100.00"),
                List.of(new ParkNflyCanadaWayFee.Rule("PERCENTAGE", new BigDecimal("10")),
                        new ParkNflyCanadaWayFee.Rule("FLAT", new BigDecimal("1.25"))));

        assertEquals(new BigDecimal("11.25"), fee);
    }

    @Test
    void nothingIsChargedForRulesThatCannotBeRead() {
        // A type nobody has defined is not guessed at, and a zero or negative value is not a fee.
        assertEquals(BigDecimal.ZERO, ParkNflyCanadaWayFee.on(new BigDecimal("51.96"), null));
        assertEquals(BigDecimal.ZERO, ParkNflyCanadaWayFee.on(new BigDecimal("51.96"), List.of()));
        assertEquals(new BigDecimal("0.00"), ParkNflyCanadaWayFee.on(new BigDecimal("51.96"),
                List.of(new ParkNflyCanadaWayFee.Rule("SOMETHING_ELSE", new BigDecimal("10")))));
        assertEquals(new BigDecimal("0.00"), ParkNflyCanadaWayFee.on(new BigDecimal("51.96"),
                List.of(new ParkNflyCanadaWayFee.Rule("PERCENTAGE", BigDecimal.ZERO))));
    }
}

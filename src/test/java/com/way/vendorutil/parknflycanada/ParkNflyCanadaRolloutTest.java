package com.way.vendorutil.parknflycanada;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The two rollout lists are compared the same way from either service. */
class ParkNflyCanadaRolloutTest {

    @Test
    void readsAListIgnoringSpacingCaseAndBlanks() {
        assertEquals(Set.of("YYZ", "YVR"),
                ParkNflyCanadaRollout.airportCodes(" yyz , YVR ,, "));
    }

    @Test
    void anAbsentOrEmptyListMeansNotOnSale() {
        assertTrue(ParkNflyCanadaRollout.airportCodes(null).isEmpty());
        assertTrue(ParkNflyCanadaRollout.airportCodes("   ").isEmpty());
    }

    /** Order and spelling are not differences; membership is. */
    @Test
    void listsAgreeRegardlessOfOrderAndSpacing() {
        assertTrue(ParkNflyCanadaRollout.agree("YYZ,YVR", " yvr , yyz "));
        assertFalse(ParkNflyCanadaRollout.agree("YYZ,YVR", "YYZ"));
    }

    /** Both switched off is a valid state, and says so plainly rather than warning. */
    @Test
    void bothEmptyIsNotAMisconfiguration() {
        String described = ParkNflyCanadaRollout.describeAgreement("", null);

        assertTrue(described.contains("not on sale anywhere"), described);
        assertFalse(described.contains("MISCONFIGURED"), described);
    }

    @Test
    void matchingListsReportTheAirportsTheyAgreeOn() {
        String described = ParkNflyCanadaRollout.describeAgreement("YYZ", "yyz");

        assertTrue(described.contains("YYZ"), described);
        assertTrue(described.contains("agree"), described);
    }

    /**
     * A mismatch names both keys, both lists and the airport that will misprice.
     *
     * <p>Whoever finds this line is reading one service's log and needs to be told about the
     * other one, so the message has to carry both sides.
     */
    @Test
    void aMismatchNamesBothKeysAndTheOffendingAirport() {
        String described = ParkNflyCanadaRollout.describeAgreement("YYZ,YVR", "YYZ");

        assertTrue(described.startsWith("MISCONFIGURED"), described);
        assertTrue(described.contains(ParkNflyCanadaRollout.SEARCH_AIRPORTS_KEY), described);
        assertTrue(described.contains(ParkNflyCanadaRollout.DETAIL_AIRPORTS_KEY), described);
        assertTrue(described.contains("YVR"), described);
    }

    /** One side switched off entirely is the most likely mistake, and is reported as one. */
    @Test
    void oneSideSwitchedOffIsReportedAsAMismatch() {
        String described = ParkNflyCanadaRollout.describeAgreement("YYZ", "");

        assertTrue(described.startsWith("MISCONFIGURED"), described);
        assertTrue(described.contains("YYZ"), described);
    }
}

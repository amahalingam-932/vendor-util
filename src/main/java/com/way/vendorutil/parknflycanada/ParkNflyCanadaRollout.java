package com.way.vendorutil.parknflycanada;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The two configuration rows that decide where Park'N Fly Canada is on sale, and the check that
 * they agree.
 */
public final class ParkNflyCanadaRollout {

    /**
     */
    public static final String SEARCH_AIRPORTS_KEY = "PNF_CA_SEARCH_AIRPORT_CODES";

    /**
     */
    public static final String DETAIL_AIRPORTS_KEY = "PNF_CA_DETAIL_AIRPORT_CODES";

    private ParkNflyCanadaRollout() {
    }

    /**
     * The airport codes in a configured list, upper-cased and de-duplicated.
     *
     * @return never null; an empty set means this vendor is not on sale
     */
    public static Set<String> airportCodes(String configuredList) {
        if (configuredList == null || configuredList.isBlank()) {
            return Set.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        Arrays.stream(configuredList.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(code -> code.toUpperCase(java.util.Locale.ROOT))
                .forEach(codes::add);
        return codes;
    }

    /**
     */
    public static boolean agree(String searchAirports, String detailAirports) {
        return airportCodes(searchAirports).equals(airportCodes(detailAirports));
    }

    /**
     * One line for a service to print at boot, saying whether the two lists agree and naming the
     * airports that differ when they do not.
     */
    public static String describeAgreement(String searchAirports, String detailAirports) {
        Set<String> search = airportCodes(searchAirports);
        Set<String> detail = airportCodes(detailAirports);
        if (search.equals(detail)) {
            return search.isEmpty()
                    ? "not on sale anywhere; both " + SEARCH_AIRPORTS_KEY + " and "
                            + DETAIL_AIRPORTS_KEY + " are empty"
                    : "on sale at " + search + "; search and detail agree";
        }

        Set<String> searchOnly = new LinkedHashSet<>(search);
        searchOnly.removeAll(detail);
        Set<String> detailOnly = new LinkedHashSet<>(detail);
        detailOnly.removeAll(search);
        return "MISCONFIGURED: " + SEARCH_AIRPORTS_KEY + "=" + search + " but "
                + DETAIL_AIRPORTS_KEY + "=" + detail
                + "; a lot at " + (searchOnly.isEmpty() ? detailOnly : searchOnly)
                + " will price differently on the search card and the detail page";
    }
}

package com.way.vendorutil.parknflycanada;

/**
 * Listing attribute {@code EXTERNAL_VENDOR_NAME} for Park'N Fly Canada (WSTravelAgent).
 * Distinct from US Park N Fly {@code parknfly}.
 */
public final class ParkNflyCanadaVendor {

    public static final String NAME = "parknfly_ca";

    /**
     */
    public static final String VENDOR_CURRENCY = ParkNflyCanadaCurrencyPolicy.VENDOR_CURRENCY;

    private ParkNflyCanadaVendor() {
    }

    public static boolean matches(String vendorName) {
        return vendorName != null && NAME.equalsIgnoreCase(vendorName.trim());
    }
}

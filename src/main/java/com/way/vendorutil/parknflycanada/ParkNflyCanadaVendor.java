package com.way.vendorutil.parknflycanada;

/**
 * Listing attribute {@code EXTERNAL_VENDOR_NAME} for Park'N Fly Canada (WSTravelAgent).
 * Distinct from US Park N Fly {@code parknfly}.
 *
 * <p>Here rather than in any one service because five of them ask the same question. svc-orders,
 * svc-search, svc-parkingconsumer, svc-parkingadmin and svc-schedulers all decide whether a
 * listing belongs to this vendor, and each had its own copy of the string. A vendor name that
 * disagrees between two services is not a compile error - it is a lot that prices one way in
 * search and another at checkout - so the constant lives once, beside the pricing it selects.
 */
public final class ParkNflyCanadaVendor {

    public static final String NAME = "parknfly_ca";

    /**
     * Currency Park'N Fly Canada bills in. Their rates, fuel surcharge and
     * {@code total_estimated_fee} are all CAD - provincial sales tax on the confirmation only
     * makes sense in CAD - and the API carries no currency field to read it from.
     */
    public static final String VENDOR_CURRENCY = ParkNflyCanadaCurrencyPolicy.VENDOR_CURRENCY;

    private ParkNflyCanadaVendor() {
    }

    public static boolean matches(String vendorName) {
        return vendorName != null && NAME.equalsIgnoreCase(vendorName.trim());
    }
}

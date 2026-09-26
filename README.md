# way-vendor-util

Pricing rules for Way's external parking vendors, in one small library that every service can
depend on.

## Why it exists separately from way-util

A vendor's pricing is the one thing several services must agree on to the cent. svc-search puts a
price on a card, svc-parkingconsumer repeats it on the detail page, svc-orders charges it, and
svc-schedulers refreshes the rates behind all three. When each service carried its own copy of the
arithmetic, a change to one of them showed up as a lot that priced one way in search and another at
checkout - a bug with no compile error and no stack trace, found by a customer.

These rules used to live in `way-util`, alongside everything else shared. They were moved here so
that vendor pricing is a thing a service opts into, rather than something it inherits, and so that
the rules can be read, reviewed and released on their own.

## What belongs here

Money arithmetic, tax rules, the JSON shape of a vendor's stored rate card, and the constants that
identify a vendor. Plain Java and nothing else.

What does **not** belong here: Spring components, database access, HTTP clients, or anything that
talks to a vendor. Those stay in the service that owns them. The library deliberately has no
dependency on `way-util`, so a pricing calculation never drags Spring Boot, Hazelcast and JPA in
behind it, and every rule can be unit tested without standing a service up.

## Park'N Fly Canada

The only vendor in here today (`com.way.vendorutil.parknflycanada`). The pieces worth knowing:

| Class | What it decides |
| --- | --- |
| `ParkNflyCanadaVendor` | The listing attribute value `parknfly_ca`. Deliberately not the US `parknfly`, which is a different API. |
| `ParkNflyCanadaRateCards` | Reads and writes the rate card stored on the listing, and picks the card covering a date. |
| `ParkNflyCanadaStayDuration` | How many days a stay is billed as, forgiving the vendor's 15 minute grace. |
| `ParkNflyCanadaStayBaseCalculator` | The banding: whole weeks at the week rate, then the cheaper of the remaining days or one more week. |
| `ParkNflyCanadaVendorTax` | The vendor's fuel surcharge and provincial sales tax. |
| `ParkNflyCanadaWayFee` | Way's own service fee and percentage. |
| `ParkNflyCanadaCurrencyConversion` | Converting the vendor's CAD into the currency the customer is charged in. |
| `ParkNflyCanadaPricingCalculator` | The whole quote, assembled from the above. |

Adding a second vendor means a sibling package, not changes to this one.

## Using it

```xml
<dependency>
    <groupId>com.way.microservices</groupId>
    <artifactId>way-vendor-util</artifactId>
    <version>2.0</version>
</dependency>
```

The explicit `<version>` is temporary. `art-way-bom` manages the versions of Way's shared
artifacts, and it does not know about this one yet; once it does, the version comes out of every
consuming pom the way `way-util`'s already does.

## Building

```bash
mvn clean install
```

Java 21, and the same `art-way-parent` every other Way artifact inherits.

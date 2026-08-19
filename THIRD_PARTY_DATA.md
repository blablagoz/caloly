# Third-party food data

Caloly's bundled Turkey product snapshot and live packaged-product search use
[Open Food Facts](https://world.openfoodfacts.org/). The database is available
under the Open Database License (ODbL) 1.0. Product images, when displayed from
Open Food Facts, are licensed under CC BY-SA.

The snapshot contains only records updated on or after 2024-01-01 and only
nutrition values supplied by the product database. Caloly does not silently
invent missing label values. Users can add or correct a product manually.

TürKomp is intentionally not bundled: its official terms require written
permission/licensing for this use.

Caloly also bundles a compact Turkish-name subset of the ODbL-only export
published by [Leana](https://leana.app/en/data-sources/) in 2026. The export
attributes its nutritional records to OpenNutrition and other listed public
nutrition sources. Caloly retains only the Turkish names, search aliases,
calories and macros required for food logging; proprietary Leana entries are
not part of the ODbL-only download.

## Curated cafe menu estimates

The explicit online-search flow includes a small curated Turkish cafe menu index
for Starbucks, Espressolab, Gloria Jean's Coffees and Nevada Coffee. Menu names
were checked against the brands' public Turkey menus where available. Nutrition
values that are not published by the Turkey operator are recipe-and-portion
estimates and are always labelled `Kafe menüsü · tahmini` in the app; they must
not be treated as an official declaration by the brand.

Reference menus:

- https://www.starbucks.com.tr/menu
- https://espressolab.com/kurumsal/menu
- https://gloriajeans.com.tr/pages/menu
- https://www.gloriajeans.com/pages/nutritional-information-table
- https://www.nevadacoffee.co/menu
- https://www.nevada.tiklamenu.tr/atakoy-menu/

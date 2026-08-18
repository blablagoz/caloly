"""Build the bundled Turkey-focused Open Food Facts snapshot.

Only products updated on or after 2024-01-01 and carrying an energy value are
included. Open Food Facts data is available under ODbL; see THIRD_PARTY_DATA.md.
"""

from __future__ import annotations

import json
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

SEARCH_TERMS = [
    "ülker", "eti", "migros", "carrefoursa", "a101", "bim", "şok market",
    "pınar", "sütaş", "içim", "torku", "dimes", "çaykur", "doğuş çay",
    "tadım", "şölen", "tamek", "tat", "yayla", "komili", "yudum",
    "bizim yağ", "knorr", "dr oetker", "damla su", "erikli", "saka",
    "hayat su", "nescafe", "kahve dünyası", "starbucks", "lipton",
    "doritos", "lays", "ruffles", "coca cola", "pepsi", "fanta",
    "danone", "nestle", "mado", "sek süt", "ekici peynir", "muratbey",
    "banvit", "şenpiliç", "namet", "apikoğlu", "pastavilla", "filiz makarna",
    "ekmek", "çikolata", "bisküvi", "gofret", "süt", "yoğurt", "peynir",
    "ayran", "kefir", "kahve", "çay", "maden suyu", "meyve suyu",
    "gazlı içecek", "makarna", "pirinç", "bulgur", "bakliyat", "mercimek",
    "nohut", "fasulye", "zeytin", "zeytinyağı", "tereyağı", "reçel", "bal",
    "tahin", "kahvaltılık gevrek", "cips", "kraker", "dondurma", "tatlı",
    "sos", "salça", "konserve", "tavuk", "sucuk", "salam", "protein bar",
]
# Broad Turkish food groups populate the useful search surface first; brand
# passes then fill gaps and improve barcode coverage.
SEARCH_TERMS = SEARCH_TERMS[50:] + SEARCH_TERMS[:50]

FIELDS = (
    "code,product_name,product_name_tr,product_name_en,product_name_fr,"
    "brands,quantity,serving_size,image_front_small_url,nutriments,"
    "last_modified_t,lang,countries_tags"
)
MIN_TIMESTAMP = 1704067200  # 2024-01-01T00:00:00Z
OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/assets/turkey_products_2024.json"


def fetch(term: str, page: int) -> dict:
    params = urllib.parse.urlencode(
        {
            "search_terms": term,
            "search_simple": 1,
            "action": "process",
            "json": 1,
            "page_size": 100,
            "page": page,
            "fields": FIELDS,
        }
    )
    request = urllib.request.Request(
        f"https://tr.openfoodfacts.org/cgi/search.pl?{params}",
        headers={"User-Agent": "Caloly/0.9.3 (https://github.com/blablagoz/caloly)"},
    )
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.load(response)


def fetch_with_retry(term: str, page: int) -> dict:
    for attempt in range(3):
        try:
            return fetch(term, page)
        except Exception:
            if attempt == 2:
                raise
            time.sleep(18 * (attempt + 1))
    raise RuntimeError("unreachable")


def is_usable(product: dict) -> bool:
    name = product.get("product_name_tr") or product.get("product_name")
    calories = (product.get("nutriments") or {}).get("energy-kcal_100g")
    modified = int(product.get("last_modified_t") or 0)
    return bool(product.get("code") and name and calories is not None and 0 <= float(calories) <= 1_000 and modified >= MIN_TIMESTAMP)


def compact(product: dict) -> dict:
    nutriments = product.get("nutriments") or {}
    return {
        "code": product.get("code"),
        "product_name": product.get("product_name"),
        "product_name_tr": product.get("product_name_tr"),
        "product_name_en": product.get("product_name_en"),
        "product_name_fr": product.get("product_name_fr"),
        "product_name_de": product.get("product_name_de"),
        "brands": product.get("brands"),
        "quantity": product.get("quantity"),
        "serving_size": product.get("serving_size"),
        "image_front_small_url": product.get("image_front_small_url"),
        "last_modified_t": product.get("last_modified_t"),
        "lang": product.get("lang"),
        "countries_tags": product.get("countries_tags"),
        "nutriments": {
            "energy-kcal_100g": nutriments.get("energy-kcal_100g"),
            "proteins_100g": nutriments.get("proteins_100g"),
            "carbohydrates_100g": nutriments.get("carbohydrates_100g"),
            "fat_100g": nutriments.get("fat_100g"),
        },
    }


def write_snapshot(products: dict[str, dict]) -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    snapshot = {
        "license": "Open Food Facts - ODbL 1.0",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "minimum_modified_at": "2024-01-01T00:00:00Z",
        "products": sorted(products.values(), key=lambda item: ((item.get("brands") or ""), (item.get("product_name_tr") or item.get("product_name") or ""))),
    }
    OUTPUT.write_text(json.dumps(snapshot, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


def main() -> None:
    products: dict[str, dict] = {}
    if OUTPUT.exists():
        previous = json.loads(OUTPUT.read_text(encoding="utf-8"))
        products.update({str(item["code"]): compact(item) for item in previous.get("products", []) if item.get("code") and is_usable(item)})
        print(f"Loaded {len(products)} existing products", flush=True)
        write_snapshot(products)
    for term in SEARCH_TERMS:
        try:
            first = fetch_with_retry(term, 1)
            pages = min(3, max(1, (int(first.get("count") or 0) + 99) // 100))
            payloads = [first]
            for page in range(2, pages + 1):
                time.sleep(12)
                payloads.append(fetch_with_retry(term, page))
            for payload in payloads:
                for product in payload.get("products") or []:
                    if is_usable(product):
                        products[str(product["code"])] = compact(product)
            print(f"{term}: {len(products)} unique products", flush=True)
            write_snapshot(products)
        except Exception as error:
            print(f"{term}: skipped ({error})", flush=True)
        time.sleep(12)

    write_snapshot(products)
    print(f"Wrote {len(products)} products to {OUTPUT}")


if __name__ == "__main__":
    main()

"""Create Caloly's compact Turkish generic-food catalog from Leana's ODbL export.

The input is the ODbL-only JSONL export published in 2026 at
https://leana.app/en/data-sources/. The large source file remains outside the
repository; only the Turkish names, search aliases, calories and macros needed
by Caloly are emitted.
"""

from __future__ import annotations

import gzip
import hashlib
import json
import os
from datetime import datetime, timezone
from pathlib import Path

INPUT = Path(os.environ.get("TEMP", ".")) / "caloly-foods_export.jsonl.gz"
OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/assets/turkish_generic_foods_2026.json"


def quantity(nutrition: dict, key: str) -> float:
    value = (nutrition.get(key) or {}).get("quantity")
    try:
        return max(0.0, float(value or 0.0))
    except (TypeError, ValueError):
        return 0.0


def translated_aliases(row: dict, name: str) -> list[str]:
    aliases: list[str] = []
    for translation in row.get("alternate_names_translations") or []:
        value = translation.get("tr") if isinstance(translation, dict) else None
        if value and value.casefold() != name.casefold() and value not in aliases:
            aliases.append(value.strip())
    return aliases[:8]


def main() -> None:
    if not INPUT.exists():
        raise SystemExit(f"Missing {INPUT}; download foods_export.jsonl.gz first")

    foods: list[dict] = []
    seen: set[str] = set()
    with gzip.open(INPUT, "rt", encoding="utf-8") as source:
        for line in source:
            row = json.loads(line)
            titles = row.get("title_translations") or {}
            name = (titles.get("tr") or "").strip()
            nutrition = row.get("nutrition_100g") or {}
            calories = quantity(nutrition, "calories")
            if not name or calories <= 0:
                continue
            canonical = " ".join(name.casefold().split())
            nutrient_key = (
                round(calories, 2),
                round(quantity(nutrition, "protein"), 2),
                round(quantity(nutrition, "carbohydrates"), 2),
                round(quantity(nutrition, "total_fat"), 2),
            )
            dedupe_key = f"{canonical}:{nutrient_key}"
            if dedupe_key in seen:
                continue
            seen.add(dedupe_key)
            food_id = hashlib.sha1(dedupe_key.encode("utf-8")).hexdigest()[:16]
            foods.append(
                {
                    "id": f"open-nutrition:{food_id}",
                    "name": name,
                    "aliases": translated_aliases(row, name),
                    "calories": calories,
                    "protein": nutrient_key[1],
                    "carbs": nutrient_key[2],
                    "fat": nutrient_key[3],
                }
            )

    foods.sort(key=lambda item: item["name"].casefold())
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps(
            {
                "license": "ODbL 1.0",
                "source": "Leana ODbL export / OpenNutrition",
                "source_release": "2026",
                "generated_at": datetime.now(timezone.utc).isoformat(),
                "foods": foods,
            },
            ensure_ascii=False,
            separators=(",", ":"),
        ),
        encoding="utf-8",
    )
    print(f"Wrote {len(foods)} Turkish generic foods to {OUTPUT}")


if __name__ == "__main__":
    main()

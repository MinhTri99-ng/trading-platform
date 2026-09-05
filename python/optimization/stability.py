from __future__ import annotations

from collections import defaultdict
from decimal import Decimal
from typing import Sequence

from python.optimization.parameter_grid import ParameterSet


def _numeric(value: Decimal | float | int | None) -> Decimal:
    if value is None:
        return Decimal("0")
    return Decimal(str(value))


def analyze_parameter_stability(results: Sequence[dict], parameter_name: str) -> str:
    if not results:
        return "POOR"

    values: dict[object, list[Decimal]] = defaultdict(list)
    for item in results:
        key = item.get(parameter_name)
        values[key].append(_numeric(item.get("profit_factor", Decimal("0"))))

    if len(values) <= 1:
        return "POOR"

    averages = {key: sum(vals) / Decimal(len(vals)) for key, vals in values.items()}
    min_pf = min(averages.values()) if averages else Decimal("0")
    max_pf = max(averages.values()) if averages else Decimal("0")
    spread = max_pf - min_pf

    if spread <= Decimal("0.2"):
        return "GOOD"
    if spread <= Decimal("0.5"):
        return "MODERATE"
    return "POOR"


def robust_parameter_region(results: Sequence[dict]) -> dict[str, tuple[Decimal, Decimal]]:
    metrics = {
        "ema": [Decimal(str(item["ema"])) for item in results],
        "atr_buffer": [Decimal(str(item["atr_buffer"])) for item in results],
        "volume_multiplier": [Decimal(str(item["volume_multiplier"])) for item in results],
        "retest_zone": [Decimal(str(item["retest_zone"])) for item in results],
        "rr": [Decimal(str(item["rr"])) for item in results],
    }

    region: dict[str, tuple[Decimal, Decimal]] = {}
    for key, values in metrics.items():
        if not values:
            region[key] = (Decimal("0"), Decimal("0"))
            continue
        region[key] = (min(values), max(values))
    return region

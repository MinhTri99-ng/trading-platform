from __future__ import annotations

from decimal import Decimal

from .statistics import safe_decimal


def _percentile(sorted_values: list[Decimal], pct: float) -> Decimal:
    if not sorted_values:
        return Decimal("0")
    if len(sorted_values) == 1:
        return sorted_values[0]
    index = (len(sorted_values) - 1) * pct
    lower = int(index)
    upper = min(len(sorted_values) - 1, lower + 1)
    fraction = index - lower
    return sorted_values[lower] + (sorted_values[upper] - sorted_values[lower]) * Decimal(str(fraction))


def return_distribution(returns) -> dict[str, Decimal | int]:
    values = sorted(safe_decimal(value) for value in returns)
    if not values:
        return {"count": 0, "mean": Decimal("0"), "median": Decimal("0"), "std": Decimal("0"), "min": Decimal("0"), "max": Decimal("0"), "p05": Decimal("0"), "p25": Decimal("0"), "p50": Decimal("0"), "p75": Decimal("0"), "p95": Decimal("0")}
    count = len(values)
    mean = sum(values, Decimal("0")) / Decimal(count)
    median = values[count // 2] if count % 2 else (values[count // 2 - 1] + values[count // 2]) / Decimal("2")
    variance = sum((value - mean) ** 2 for value in values) / Decimal(count)
    std = variance.sqrt()
    return {
        "count": count,
        "mean": mean,
        "median": median,
        "std": std,
        "min": values[0],
        "max": values[-1],
        "p05": _percentile(values, 0.05),
        "p25": _percentile(values, 0.25),
        "p50": _percentile(values, 0.50),
        "p75": _percentile(values, 0.75),
        "p95": _percentile(values, 0.95),
    }


def drawdown_from_equity(equity_curve) -> list[Decimal]:
    values = [safe_decimal(value) for value in equity_curve]
    if not values:
        return []
    peak = values[0]
    drawdowns: list[Decimal] = []
    for value in values:
        if value > peak:
            peak = value
        drawdowns.append(max(Decimal("0"), peak - value))
    return drawdowns


def drawdown_distribution(equity_curve) -> dict[str, Decimal]:
    values = drawdown_from_equity(equity_curve)
    if not values:
        return {"max_drawdown": Decimal("0"), "median_drawdown": Decimal("0"), "p95_drawdown": Decimal("0")}
    sorted_values = sorted(values)
    count = len(sorted_values)
    median = sorted_values[count // 2] if count % 2 else (sorted_values[count // 2 - 1] + sorted_values[count // 2]) / Decimal("2")
    return {
        "max_drawdown": max(values),
        "median_drawdown": median,
        "p95_drawdown": _percentile(sorted_values, 0.95),
    }

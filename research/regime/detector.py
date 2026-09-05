from __future__ import annotations

from decimal import Decimal
from typing import Any, Sequence


def _price(item: Any, key: str) -> Decimal:
    if isinstance(item, dict):
        return Decimal(str(item[key]))
    return Decimal(str(getattr(item, key)))


def detect_regime(candles: Sequence[Any]) -> str:
    if not candles:
        return "SIDEWAYS"

    closes = [_price(c, "close") for c in candles[-20:]]
    if len(closes) < 2:
        return "SIDEWAYS"

    returns: list[Decimal] = []
    for prev, curr in zip(closes, closes[1:]):
        if prev == 0:
            continue
        returns.append(curr / prev - Decimal("1"))

    if not returns:
        return "SIDEWAYS"

    avg_return = sum(returns, Decimal("0")) / Decimal(len(returns))
    volatility = sum((r - avg_return) ** 2 for r in returns) / Decimal(len(returns))
    volatility = volatility.sqrt() if volatility >= 0 else Decimal("0")
    trend_slope = (closes[-1] - closes[0]) / closes[0] if closes[0] != 0 else Decimal("0")

    positive_returns = sum(1 for r in returns if r > 0)
    negative_returns = sum(1 for r in returns if r < 0)

    if positive_returns and negative_returns and abs(avg_return) <= Decimal("0.01"):
        return "SIDEWAYS"
    if abs(trend_slope) <= Decimal("0.015"):
        return "SIDEWAYS"
    if trend_slope > Decimal("0.01") and avg_return > Decimal("0.0005"):
        return "BULL"
    if trend_slope < Decimal("-0.01") and avg_return < Decimal("-0.0005"):
        return "BEAR"
    if volatility > Decimal("0.004"):
        return "HIGH_VOLATILITY"
    return "SIDEWAYS"

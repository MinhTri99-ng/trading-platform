from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from typing import Any, Sequence


def _to_decimal(value: Any) -> Decimal:
    if isinstance(value, Decimal):
        return value
    return Decimal(str(value))


def _get_value(candle: Any, key: str) -> Decimal:
    if isinstance(candle, dict):
        return _to_decimal(candle[key])
    return _to_decimal(getattr(candle, key))


@dataclass(frozen=True)
class VolumeFilterResult:
    status: str
    current_volume: Decimal
    average_volume: Decimal
    threshold: Decimal


class VolumeFilterService:
    LOOKBACK = 20
    MULTIPLIER = Decimal("1.5")

    def analyze(self, candles: Sequence[Any]) -> VolumeFilterResult:
        if candles is None:
            raise ValueError("Candles cannot be null")
        if len(candles) < self.LOOKBACK + 1:
            raise ValueError("Candles must contain at least LOOKBACK + 1 candles")

        current_candle = candles[-1]
        current_volume = _get_value(current_candle, "volume")
        if current_volume is None or current_volume < 0:
            raise ValueError("Current candle volume cannot be null or negative")

        previous_candles = candles[-self.LOOKBACK - 1:-1]
        average_volume = sum(_get_value(candle, "volume") for candle in previous_candles) / Decimal(len(previous_candles))
        threshold = average_volume * self.MULTIPLIER
        status = "HIGH_VOLUME" if current_volume > threshold else "LOW_VOLUME"
        return VolumeFilterResult(status, current_volume, average_volume, threshold)

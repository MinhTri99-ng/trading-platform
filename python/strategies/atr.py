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


class ATRIndicator:
    def calculate(self, candles: Sequence[Any], period: int) -> Decimal | None:
        if candles is None or len(candles) < period + 1:
            return None

        tr_sum = Decimal("0")
        for i in range(1, period + 1):
            high = _get_value(candles[i], "high")
            low = _get_value(candles[i], "low")
            previous_close = _get_value(candles[i - 1], "close")
            tr_sum += self._true_range(high, low, previous_close)

        atr = tr_sum / Decimal(period)

        for i in range(period + 1, len(candles)):
            high = _get_value(candles[i], "high")
            low = _get_value(candles[i], "low")
            previous_close = _get_value(candles[i - 1], "close")
            tr = self._true_range(high, low, previous_close)
            atr = ((atr * Decimal(period - 1)) + tr) / Decimal(period)

        return atr

    @staticmethod
    def _true_range(high: Decimal, low: Decimal, previous_close: Decimal) -> Decimal:
        high_low = abs(high - low)
        high_previous = abs(high - previous_close)
        low_previous = abs(low - previous_close)
        return max(high_low, high_previous, low_previous)


class AtrService:
    ATR_PERIOD = 14

    def calculate(self, candles: Sequence[Any]) -> Decimal:
        if candles is None:
            raise ValueError("Candles cannot be null")
        if len(candles) < self.ATR_PERIOD + 1:
            raise ValueError("Not enough candles for ATR calculation")

        for i, candle in enumerate(candles):
            if candle is None:
                raise ValueError(f"Candle at index {i} cannot be null")
            for field in ("open", "high", "low", "close"):
                value = _get_value(candle, field)
                if value is None or value < 0:
                    raise ValueError(f"{field} for candle {i} cannot be null or negative")
            if _get_value(candle, "high") < _get_value(candle, "low"):
                raise ValueError(f"High cannot be lower than low for candle {i}")

        atr = ATRIndicator().calculate(candles, self.ATR_PERIOD)
        if atr is None or atr <= 0:
            raise ValueError("ATR must be positive")
        return atr

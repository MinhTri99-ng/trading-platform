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
class BreakoutResult:
    type: str
    support: Decimal
    resistance: Decimal
    atr_buffer: Decimal
    close: Decimal


class BreakoutService:
    LOOKBACK = 20
    ATR_BUFFER_MULTIPLIER = Decimal("0.2")

    def analyze(self, candles: Sequence[Any], atr: Decimal) -> BreakoutResult:
        if candles is None or len(candles) < self.LOOKBACK + 1:
            raise ValueError("Not enough candles")
        if atr is None or atr < 0:
            raise ValueError("ATR cannot be null or negative")

        current_index = len(candles) - 1
        start_index = current_index - self.LOOKBACK
        support: Decimal | None = None
        resistance: Decimal | None = None

        for i in range(start_index, current_index):
            candle = candles[i]
            low = _get_value(candle, "low")
            high = _get_value(candle, "high")
            if support is None or low < support:
                support = low
            if resistance is None or high > resistance:
                resistance = high

        current = candles[current_index]
        close = _get_value(current, "close")
        previous_close = _get_value(candles[current_index - 1], "close")
        buffer = atr * self.ATR_BUFFER_MULTIPLIER
        breakout_resistance = resistance + buffer
        breakout_support = support - buffer

        bullish_breakout = previous_close <= resistance and close > breakout_resistance
        bearish_breakout = previous_close >= support and close < breakout_support
        fake_bullish_breakout = _get_value(current, "high") > breakout_resistance and close < resistance
        fake_bearish_breakout = _get_value(current, "low") < breakout_support and close > support

        if bullish_breakout:
            breakout_type = "BULLISH_BREAKOUT"
        elif bearish_breakout:
            breakout_type = "BEARISH_BREAKOUT"
        elif fake_bullish_breakout:
            breakout_type = "FAKE_BULLISH_BREAKOUT"
        elif fake_bearish_breakout:
            breakout_type = "FAKE_BEARISH_BREAKOUT"
        else:
            breakout_type = "NO_BREAKOUT"

        return BreakoutResult(breakout_type, support, resistance, buffer, close)

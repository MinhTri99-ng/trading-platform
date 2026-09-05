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
class SwingPoint:
    index: int
    price: Decimal
    swing_type: str


@dataclass(frozen=True)
class StructureLabel:
    index: int
    price: Decimal
    type: str


@dataclass(frozen=True)
class MarketStructureResult:
    swing_highs: list[SwingPoint]
    swing_lows: list[SwingPoint]
    labels: list[StructureLabel]
    trend: str


class MarketStructureService:
    SWING_LENGTH = 2

    def analyze(self, candles: Sequence[Any]) -> MarketStructureResult:
        if candles is None or len(candles) < 5:
            return MarketStructureResult([], [], [], "RANGE")

        swing_highs: list[SwingPoint] = []
        swing_lows: list[SwingPoint] = []

        for i in range(self.SWING_LENGTH, len(candles) - self.SWING_LENGTH):
            current = candles[i]
            if self._is_swing_high(candles, i):
                swing_highs.append(SwingPoint(i, _get_value(current, "high"), "HIGH"))
            if self._is_swing_low(candles, i):
                swing_lows.append(SwingPoint(i, _get_value(current, "low"), "LOW"))

        labels = self._classify_structure(swing_highs, swing_lows)
        trend = self._determine_trend(labels)
        return MarketStructureResult(swing_highs, swing_lows, labels, trend)

    def _is_swing_high(self, candles: Sequence[Any], index: int) -> bool:
        current_high = _get_value(candles[index], "high")
        for offset in range(1, self.SWING_LENGTH + 1):
            left_high = _get_value(candles[index - offset], "high")
            right_high = _get_value(candles[index + offset], "high")
            if current_high <= left_high or current_high <= right_high:
                return False
        return True

    def _is_swing_low(self, candles: Sequence[Any], index: int) -> bool:
        current_low = _get_value(candles[index], "low")
        for offset in range(1, self.SWING_LENGTH + 1):
            left_low = _get_value(candles[index - offset], "low")
            right_low = _get_value(candles[index + offset], "low")
            if current_low >= left_low or current_low >= right_low:
                return False
        return True

    def _classify_structure(
        self, swing_highs: list[SwingPoint], swing_lows: list[SwingPoint]
    ) -> list[StructureLabel]:
        labels: list[StructureLabel] = []

        for i in range(1, len(swing_highs)):
            previous = swing_highs[i - 1]
            current = swing_highs[i]
            label_type = "HH" if current.price > previous.price else "LH"
            labels.append(StructureLabel(current.index, current.price, label_type))

        for i in range(1, len(swing_lows)):
            previous = swing_lows[i - 1]
            current = swing_lows[i]
            label_type = "HL" if current.price > previous.price else "LL"
            labels.append(StructureLabel(current.index, current.price, label_type))

        labels.sort(key=lambda item: item.index)
        return labels

    def _determine_trend(self, labels: list[StructureLabel]) -> str:
        has_hh = any(label.type == "HH" for label in labels)
        has_hl = any(label.type == "HL" for label in labels)
        has_lh = any(label.type == "LH" for label in labels)
        has_ll = any(label.type == "LL" for label in labels)

        if has_hh and has_hl and not has_lh:
            return "BULLISH"
        if has_lh and has_ll and not has_hh:
            return "BEARISH"
        return "RANGE"

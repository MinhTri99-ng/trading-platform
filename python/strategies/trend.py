from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from typing import Any, Sequence

from python.strategies.structure import MarketStructureService


def _to_decimal(value: Any) -> Decimal:
    if isinstance(value, Decimal):
        return value
    return Decimal(str(value))


def _get_value(candle: Any, key: str) -> Decimal:
    if isinstance(candle, dict):
        return _to_decimal(candle[key])
    return _to_decimal(getattr(candle, key))


class EMAIndicator:
    def calculate(self, candles: Sequence[Any], period: int) -> Decimal | None:
        if candles is None or len(candles) < period:
            return None

        total = sum(_get_value(candle, "close") for candle in candles[:period])
        ema = total / Decimal(period)
        multiplier = Decimal(2) / Decimal(period + 1)

        for candle in candles[period:]:
            close = _get_value(candle, "close")
            ema = (close - ema) * multiplier + ema
        return ema


@dataclass(frozen=True)
class TrendResult:
    trend: str
    ema50: Decimal | None
    ema200: Decimal | None
    structure: Any


class TrendService:
    def __init__(self) -> None:
        self.ema_indicator = EMAIndicator()
        self.market_structure_service = MarketStructureService()

    def analyze(self, candles: Sequence[Any]) -> TrendResult:
        if candles is None or len(candles) == 0:
            raise ValueError("Candle list cannot be empty")

        ema50 = self.ema_indicator.calculate(candles, 50)
        ema200 = self.ema_indicator.calculate(candles, 200)
        structure = self.market_structure_service.analyze(candles)
        trend = self._determine_trend(ema50, ema200, structure)
        return TrendResult(trend, ema50, ema200, structure)

    def _determine_trend(self, ema50: Decimal | None, ema200: Decimal | None, structure: Any) -> str:
        if ema50 is None or ema200 is None:
            return "SIDEWAYS"

        bullish_ema = ema50 > ema200
        bearish_ema = ema50 < ema200
        bullish_structure = self._has_structure(structure, "HH") and self._has_structure(structure, "HL")
        bearish_structure = self._has_structure(structure, "LH") and self._has_structure(structure, "LL")

        if bullish_ema and bullish_structure:
            return "BULLISH"
        if bearish_ema and bearish_structure:
            return "BEARISH"
        return "SIDEWAYS"

    @staticmethod
    def _has_structure(structure: Any, structure_type: str) -> bool:
        return any(label.type == structure_type for label in structure.labels)

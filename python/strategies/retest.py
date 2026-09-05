from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import Enum
from typing import Any, Sequence

from python.strategies.breakout import BreakoutService


class SetupState(str, Enum):
    NONE = "NONE"
    BREAKOUT = "BREAKOUT"
    WAITING_RETEST = "WAITING_RETEST"
    RETEST = "RETEST"
    CONFIRMED = "CONFIRMED"


class BreakoutDirection(str, Enum):
    BULLISH = "BULLISH"
    BEARISH = "BEARISH"


@dataclass(frozen=True)
class RetestResult:
    state: str
    direction: str | None
    support: Decimal | None
    resistance: Decimal | None
    retest_level: Decimal | None
    atr: Decimal | None
    reason: str


class RetestService:
    ATR_BUFFER_MULTIPLIER = Decimal("0.2")

    def __init__(self) -> None:
        self.breakout_service = BreakoutService()

    def analyze(self, candles: Sequence[Any], atr: Decimal) -> RetestResult:
        if candles is None or len(candles) == 0:
            raise ValueError("Candle list cannot be empty")
        if atr is None or atr < 0:
            raise ValueError("ATR cannot be null or negative")
        if len(candles) < 21:
            raise ValueError("At least 21 candles are required")

        breakout = self.breakout_service.analyze(candles, atr)
        if breakout.type == "NO_BREAKOUT":
            return self._create_result(SetupState.NONE, None, breakout)
        if breakout.type in {"FAKE_BULLISH_BREAKOUT", "FAKE_BEARISH_BREAKOUT"}:
            return self._create_result(SetupState.NONE, None, breakout)

        direction = BreakoutDirection.BULLISH if breakout.type == "BULLISH_BREAKOUT" else BreakoutDirection.BEARISH
        return self._create_result(SetupState.BREAKOUT, direction, breakout)

    def analyze_after_breakout(
        self,
        candles: Sequence[Any],
        breakout_index: int,
        direction: BreakoutDirection,
        support: Decimal,
        resistance: Decimal,
        atr: Decimal,
    ) -> RetestResult:
        if candles is None or len(candles) == 0:
            raise ValueError("Candle list cannot be empty")
        if direction is None:
            raise ValueError("Breakout direction cannot be null")
        if support is None or resistance is None:
            raise ValueError("Support and resistance cannot be null")
        if atr is None or atr < 0:
            raise ValueError("ATR cannot be null or negative")
        if breakout_index < 0 or breakout_index >= len(candles):
            raise ValueError("Invalid breakout index")

        if len(candles) <= breakout_index + 1:
            return RetestResult(
                SetupState.BREAKOUT.value,
                direction.value,
                support,
                resistance,
                self._get_retest_level(direction, support, resistance),
                atr,
                "Breakout detected",
            )

        buffer = atr * self.ATR_BUFFER_MULTIPLIER
        retest_level = self._get_retest_level(direction, support, resistance)

        for i in range(breakout_index + 1, len(candles)):
            candle = candles[i]
            close = Decimal(str(candle["close"])) if isinstance(candle, dict) else Decimal(str(getattr(candle, "close")))
            open_p = Decimal(str(candle["open"])) if isinstance(candle, dict) else Decimal(str(getattr(candle, "open")))
            low = Decimal(str(candle["low"])) if isinstance(candle, dict) else Decimal(str(getattr(candle, "low")))
            high = Decimal(str(candle["high"])) if isinstance(candle, dict) else Decimal(str(getattr(candle, "high")))

            if direction == BreakoutDirection.BULLISH:
                if close < (resistance - buffer):
                    return RetestResult(SetupState.NONE.value, direction.value, support, resistance, retest_level, atr, "Bullish breakout invalidated")
                inside_retest_zone = low <= (resistance + buffer) and high >= (resistance - buffer)
                if inside_retest_zone:
                    confirmed = close > resistance and close > open_p
                    if confirmed:
                        return RetestResult(SetupState.CONFIRMED.value, direction.value, support, resistance, retest_level, atr, "Bullish retest confirmed")
                    return RetestResult(SetupState.RETEST.value, direction.value, support, resistance, retest_level, atr, "Bullish retest detected")
            else:
                if close > (support + buffer):
                    return RetestResult(SetupState.NONE.value, direction.value, support, resistance, retest_level, atr, "Bearish breakout invalidated")
                inside_retest_zone = low <= (support + buffer) and high >= (support - buffer)
                if inside_retest_zone:
                    confirmed = close < support and close < open_p
                    if confirmed:
                        return RetestResult(SetupState.CONFIRMED.value, direction.value, support, resistance, retest_level, atr, "Bearish retest confirmed")
                    return RetestResult(SetupState.RETEST.value, direction.value, support, resistance, retest_level, atr, "Bearish retest detected")

        return RetestResult(SetupState.WAITING_RETEST.value, direction.value, support, resistance, retest_level, atr, "Waiting for retest")

    def _get_retest_level(self, direction: BreakoutDirection, support: Decimal, resistance: Decimal) -> Decimal:
        return resistance if direction == BreakoutDirection.BULLISH else support

    def _create_result(self, state: SetupState, direction: BreakoutDirection | None, breakout: Any) -> RetestResult:
        retest_level = None if direction is None else self._get_retest_level(direction, breakout.support, breakout.resistance)
        return RetestResult(
            state.value,
            None if direction is None else direction.value,
            breakout.support,
            breakout.resistance,
            retest_level,
            None,
            "Breakout analysis",
        )

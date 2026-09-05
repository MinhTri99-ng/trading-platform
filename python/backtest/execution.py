from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from typing import Any


@dataclass(frozen=True)
class ExecutionPolicy:
    """Execution policy used by the backtest engine.

    Conservative same-candle rule: if both SL and TP are touched by the same candle,
    assume SL is hit first and exit at SL.
    """

    entry_policy: str = "SIGNAL_CANDLE -> NEXT_CANDLE_OPEN"
    same_candle_policy: str = "CONSERVATIVE_SL_FIRST"

    @staticmethod
    def is_long_sl_hit(low: Decimal, stop_loss: Decimal) -> bool:
        return low <= stop_loss

    @staticmethod
    def is_long_tp_hit(high: Decimal, take_profit: Decimal) -> bool:
        return high >= take_profit

    @staticmethod
    def is_short_sl_hit(high: Decimal, stop_loss: Decimal) -> bool:
        return high >= stop_loss

    @staticmethod
    def is_short_tp_hit(low: Decimal, take_profit: Decimal) -> bool:
        return low <= take_profit

    @staticmethod
    def resolve_exit(
        direction: str,
        low: Decimal,
        high: Decimal,
        stop_loss: Decimal,
        take_profit: Decimal,
    ) -> str:
        if direction == "LONG":
            sl_hit = ExecutionPolicy.is_long_sl_hit(low, stop_loss)
            tp_hit = ExecutionPolicy.is_long_tp_hit(high, take_profit)
            if sl_hit and tp_hit:
                return "SL"
            if sl_hit:
                return "SL"
            if tp_hit:
                return "TP"
            return "NONE"

        if direction == "SHORT":
            sl_hit = ExecutionPolicy.is_short_sl_hit(high, stop_loss)
            tp_hit = ExecutionPolicy.is_short_tp_hit(low, take_profit)
            if sl_hit and tp_hit:
                return "SL"
            if sl_hit:
                return "SL"
            if tp_hit:
                return "TP"
            return "NONE"

        return "NONE"

    @staticmethod
    def apply_slippage(price: Decimal, direction: str, slippage_rate: Decimal) -> Decimal:
        if slippage_rate <= 0:
            return price
        if direction == "LONG":
            return price * (Decimal("1") + slippage_rate)
        if direction == "SHORT":
            return price * (Decimal("1") - slippage_rate)
        return price

    @staticmethod
    def apply_fee(notional: Decimal, fee_rate: Decimal) -> Decimal:
        if fee_rate <= 0:
            return Decimal("0")
        return notional * fee_rate

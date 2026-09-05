from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal
from typing import Any, Iterable, Sequence

from python.backtest.execution import ExecutionPolicy
from python.backtest.trade import BacktestResult, Trade
from python.strategies.atr import AtrService
from python.strategies.breakout import BreakoutService
from python.strategies.risk import RiskManagementService
from python.strategies.retest import BreakoutDirection, RetestService
from python.strategies.trend import TrendService
from python.strategies.volume import VolumeFilterService


@dataclass
class BacktestConfig:
    fee_rate: Decimal = Decimal("0.001")
    slippage_rate: Decimal = Decimal("0.0005")
    use_slippage: bool = True
    debug: bool = False
    risk_percent: Decimal = Decimal("1")
    account_balance: Decimal = Decimal("10000")


class SimpleSignalStrategy:
    """Signal wrapper using the existing Python strategy modules.

    This keeps the backtest logic aligned with the parity strategy modules without
    creating a separate trading rule set. It reads the same market state available
    at a given candle and emits a directional signal only when the built strategy
    stack indicates a valid setup.
    """

    def __init__(self, risk_percent: Decimal = Decimal("1"), account_balance: Decimal = Decimal("10000")) -> None:
        self.risk_percent = risk_percent
        self.account_balance = account_balance
        self.trend_service = TrendService()
        self.atr_service = AtrService()
        self.breakout_service = BreakoutService()
        self.retest_service = RetestService()
        self.volume_service = VolumeFilterService()
        self.risk_service = RiskManagementService()

    def generate_signal(self, candles: Sequence[Any]) -> dict | None:
        if candles is None or len(candles) < 21:
            return None

        try:
            trend = self.trend_service.analyze(candles)
            atr = self.atr_service.calculate(candles)
            breakout = self.breakout_service.analyze(candles, atr)
            retest = self.retest_service.analyze(candles, atr)
            volume = self.volume_service.analyze(candles)
        except Exception:
            return None

        if trend.trend == "SIDEWAYS":
            return None

        if trend.trend == "BULLISH" and breakout.type in {"BULLISH_BREAKOUT", "NO_BREAKOUT"}:
            direction = "LONG"
        elif trend.trend == "BEARISH" and breakout.type in {"BEARISH_BREAKOUT", "NO_BREAKOUT"}:
            direction = "SHORT"
        elif breakout.type == "BULLISH_BREAKOUT":
            direction = "LONG"
        elif breakout.type == "BEARISH_BREAKOUT":
            direction = "SHORT"
        else:
            return None

        if volume.status != "HIGH_VOLUME":
            return None

        current = candles[-1]
        entry = Decimal(str(current["close"])) if isinstance(current, dict) else Decimal(str(current.close))
        if direction == "LONG":
            invalidation = breakout.support
            stop_loss = entry - atr
            tp = entry + (entry - stop_loss) * Decimal("2")
        else:
            invalidation = breakout.resistance
            stop_loss = entry + atr
            tp = entry - (stop_loss - entry) * Decimal("2")

        risk = self.risk_service.calculate_position_size(self.account_balance, self.risk_percent, entry, stop_loss)
        return {
            "direction": direction,
            "entry": entry,
            "stop_loss": stop_loss,
            "take_profit": tp,
            "position_size": risk.position_size,
            "risk_amount": risk.risk_amount,
            "invalidation": invalidation,
        }


class BacktestEngine:
    def __init__(self, strategy: Any | None = None, config: BacktestConfig | None = None) -> None:
        self.strategy = strategy or SimpleSignalStrategy()
        self.config = config or BacktestConfig()
        self.execution_policy = ExecutionPolicy()

    def run(self, candles: Sequence[Any], debug: bool = False) -> BacktestResult:
        config = self.config
        if debug:
            config = BacktestConfig(
                fee_rate=config.fee_rate,
                slippage_rate=config.slippage_rate,
                use_slippage=config.use_slippage,
                debug=True,
                risk_percent=config.risk_percent,
                account_balance=config.account_balance,
            )

        trades: list[Trade] = []
        pending_signal: dict | None = None
        open_trade: Trade | None = None

        for idx, candle in enumerate(candles):
            if open_trade is not None:
                if config.debug:
                    print(f"Candle #{idx + 1} -> active trade: {open_trade.direction}")

                low = self._price(candle, "low")
                high = self._price(candle, "high")
                exit_reason = self.execution_policy.resolve_exit(
                    open_trade.direction,
                    low,
                    high,
                    open_trade.stop_loss,
                    open_trade.take_profit,
                )

                if exit_reason != "NONE":
                    exit_price = self._exit_price(open_trade.direction, candle, exit_reason, open_trade)
                    if config.use_slippage:
                        exit_price = self._apply_slippage(exit_price, open_trade.direction, config.slippage_rate, is_entry=False)
                    trade = self._close_trade(open_trade, exit_price, exit_reason, idx, config)
                    trades.append(trade)
                    open_trade = None
                    continue

            if pending_signal is not None and idx == pending_signal["execution_index"]:
                entry_price = self._price(candle, "open")
                entry_price = self._apply_slippage(entry_price, pending_signal["direction"], config.slippage_rate, is_entry=True)
                stop_loss = pending_signal["stop_loss"]
                take_profit = pending_signal["take_profit"]
                open_trade = Trade(
                    direction=pending_signal["direction"],
                    entry=entry_price,
                    stop_loss=stop_loss,
                    take_profit=take_profit,
                    result="OPEN",
                    R=Decimal("0"),
                    timestamp=self._timestamp(candle),
                    position_size=pending_signal["position_size"],
                    risk_amount=pending_signal["risk_amount"],
                    fee=Decimal("0"),
                    slippage=Decimal("0"),
                )
                pending_signal = None
                if config.debug:
                    print(f"Candle #{idx + 1} -> signal {open_trade.direction} executed at open: {entry_price}")
                continue

            if open_trade is None and idx < len(candles) - 1:
                history = candles[: idx + 1]
                signal = self.strategy.generate_signal(history)
                if signal is not None and config.debug:
                    print(f"Candle #{idx + 1} -> signal {signal['direction']} | entry={signal['entry']} | SL={signal['stop_loss']} | TP={signal['take_profit']}")
                if signal is not None:
                    pending_signal = {**signal, "execution_index": idx + 1}

        if open_trade is not None:
            exit_price = self._price(candles[-1], "close")
            trade = self._close_trade(open_trade, exit_price, "END_OF_DATA", len(candles) - 1, config)
            trades.append(trade)

        return self._summary(trades)

    @staticmethod
    def _price(candle: Any, key: str) -> Decimal:
        if isinstance(candle, dict):
            return Decimal(str(candle[key]))
        return Decimal(str(getattr(candle, key)))

    @staticmethod
    def _timestamp(candle: Any) -> datetime | None:
        if isinstance(candle, dict):
            ts = candle.get("timestamp")
        else:
            ts = getattr(candle, "timestamp", None)
        if ts is None:
            return None
        if isinstance(ts, datetime):
            return ts
        return datetime.fromisoformat(str(ts).replace("Z", "+00:00"))

    @staticmethod
    def _apply_slippage(price: Decimal, direction: str, slippage_rate: Decimal, is_entry: bool) -> Decimal:
        if not is_entry:
            return ExecutionPolicy.apply_slippage(price, direction, slippage_rate)
        if direction == "LONG":
            return price * (Decimal("1") + slippage_rate)
        if direction == "SHORT":
            return price * (Decimal("1") - slippage_rate)
        return price

    @staticmethod
    def _exit_price(direction: str, candle: Any, exit_reason: str, trade: Trade) -> Decimal:
        if exit_reason == "SL":
            return trade.stop_loss
        if exit_reason == "TP":
            return trade.take_profit
        return BacktestEngine._price(candle, "close")

    @staticmethod
    def _close_trade(open_trade: Trade, exit_price: Decimal, exit_reason: str, index: int, config: BacktestConfig) -> Trade:
        fee = Decimal("0")
        slippage = Decimal("0")
        if open_trade.position_size is not None:
            notional = abs(exit_price * open_trade.position_size)
            fee = ExecutionPolicy.apply_fee(notional, config.fee_rate)
            if config.use_slippage:
                slippage = abs(exit_price - open_trade.entry) * (open_trade.position_size or Decimal("1")) * Decimal("0.5")

        if open_trade.direction == "LONG":
            pnl = (exit_price - open_trade.entry) * (open_trade.position_size or Decimal("1")) - fee - slippage
        else:
            pnl = (open_trade.entry - exit_price) * (open_trade.position_size or Decimal("1")) - fee - slippage

        initial_risk = open_trade.risk_amount or (abs(open_trade.entry - open_trade.stop_loss) * (open_trade.position_size or Decimal("1")))
        if initial_risk == 0:
            r_value = Decimal("0")
        else:
            r_value = pnl / initial_risk

        result = "WIN" if pnl > 0 else "LOSS"
        if pnl == 0:
            result = "BREAKEVEN"

        closed = Trade(
            direction=open_trade.direction,
            entry=open_trade.entry,
            stop_loss=open_trade.stop_loss,
            take_profit=open_trade.take_profit,
            result=result,
            R=r_value,
            fee=fee,
            slippage=slippage,
            timestamp=open_trade.timestamp,
            exit_price=exit_price,
            exit_timestamp=BacktestEngine._timestamp_from_index(index),
            position_size=open_trade.position_size,
            risk_amount=open_trade.risk_amount,
            holding_period=0,
            exit_reason=exit_reason,
            pnl=pnl,
        )
        return closed

    @staticmethod
    def _timestamp_from_index(index: int) -> datetime | None:
        return datetime.now()

    @staticmethod
    def _summary(trades: list[Trade]) -> BacktestResult:
        total_trades = len(trades)
        wins = sum(1 for trade in trades if trade.result == "WIN")
        losses = sum(1 for trade in trades if trade.result == "LOSS")
        total_r = sum((trade.R for trade in trades), Decimal("0"))
        total_pnl = sum((self_pnl_value(trade) for trade in trades), Decimal("0"))
        total_fees = sum((trade.fee for trade in trades), Decimal("0"))
        return BacktestResult(
            trades=trades,
            total_trades=total_trades,
            wins=wins,
            losses=losses,
            total_R=total_r,
            total_pnl=total_pnl,
            total_fees=total_fees,
        )


def self_pnl_value(trade: Trade) -> Decimal:
    if trade.result == "WIN":
        return trade.R * (trade.risk_amount or Decimal("0"))
    if trade.result == "LOSS":
        return trade.R * (trade.risk_amount or Decimal("0"))
    return Decimal("0")


def _run_demo() -> None:
    engine = BacktestEngine()
    candles = []
    price = Decimal("100")
    for i in range(100):
        open_p = price
        close_p = price + Decimal(str((i % 5) - 2)) / Decimal("2")
        high_p = max(open_p, close_p) + Decimal("1")
        low_p = min(open_p, close_p) - Decimal("1")
        candles.append({
            "timestamp": f"2024-01-01T00:{i:02d}:00Z",
            "open": str(open_p),
            "high": str(high_p),
            "low": str(low_p),
            "close": str(close_p),
            "volume": str(Decimal("1000") + Decimal(i) * Decimal("10")),
        })
        price = close_p
    result = engine.run(candles)
    print("Trades:", len(result.trades))
    print("Total R:", result.total_R)

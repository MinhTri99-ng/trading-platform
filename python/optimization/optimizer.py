from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from typing import Any, Sequence

from python.backtest.engine import BacktestConfig, BacktestEngine
from python.backtest.metrics import calculate_metrics
from python.backtest.trade import Trade
from python.optimization.parameter_grid import ParameterSet


@dataclass(frozen=True)
class OptimizationResult:
    ema: int
    atr_buffer: Decimal
    volume_multiplier: Decimal
    retest_zone: Decimal
    rr: Decimal
    total_trades: int
    win_rate: Decimal
    profit_factor: Decimal | None
    expectancy: Decimal
    average_r: Decimal
    total_r: Decimal
    net_profit: Decimal
    fees: Decimal
    max_drawdown: Decimal
    max_consecutive_losses: int
    robustness_score: Decimal
    overfit_risk: bool = False


def _safe_decimal(value: Any) -> Decimal:
    if value is None:
        return Decimal("0")
    return Decimal(str(value))


class ParameterizedSignalStrategy:
    def __init__(self, params: ParameterSet, risk_percent: Decimal = Decimal("1"), account_balance: Decimal = Decimal("10000")) -> None:
        self.params = params
        self.risk_percent = risk_percent
        self.account_balance = account_balance

    def generate_signal(self, candles: Sequence[Any]) -> dict | None:
        if candles is None or len(candles) < 21:
            return None
        current = candles[-1]
        entry = _safe_decimal(current["close"] if isinstance(current, dict) else getattr(current, "close"))
        atr = _safe_decimal(current["high"] if isinstance(current, dict) else getattr(current, "high")) - _safe_decimal(current["low"] if isinstance(current, dict) else getattr(current, "low"))
        atr = max(atr, Decimal("1"))

        if entry <= Decimal("0"):
            return None

        stop_loss = entry - (atr * self.params.atr_buffer)
        take_profit = entry + ((entry - stop_loss) * self.params.rr)

        if self.params.ema and len(candles) >= self.params.ema:
            ema_value = sum(_safe_decimal(c["close"] if isinstance(c, dict) else getattr(c, "close")) for c in candles[-self.params.ema:]) / Decimal(self.params.ema)
            if entry < ema_value:
                return None

        if self.params.volume_multiplier:
            recent = candles[-21:-1] if len(candles) >= 22 else candles[:-1]
            if recent:
                avg_volume = sum(_safe_decimal(c["volume"] if isinstance(c, dict) else getattr(c, "volume")) for c in recent) / Decimal(len(recent))
                current_volume = _safe_decimal(current["volume"] if isinstance(current, dict) else getattr(current, "volume"))
                if current_volume <= avg_volume * self.params.volume_multiplier:
                    return None

        direction = "LONG"
        risk = Decimal("100")
        return {
            "direction": direction,
            "entry": entry,
            "stop_loss": stop_loss,
            "take_profit": take_profit,
            "position_size": Decimal("1"),
            "risk_amount": risk,
            "execution_index": len(candles),
        }


class ParameterOptimizer:
    def __init__(self, parameter_grid: Sequence[ParameterSet] | None = None) -> None:
        self.parameter_grid = list(parameter_grid) if parameter_grid is not None else []

    def optimize(self, candles: Sequence[Any], validation_candles: Sequence[Any] | None = None) -> list[OptimizationResult]:
        if not self.parameter_grid:
            raise ValueError("Parameter grid is empty")

        results: list[OptimizationResult] = []
        for params in self.parameter_grid:
            strategy = ParameterizedSignalStrategy(params)
            engine = BacktestEngine(strategy=strategy, config=BacktestConfig(debug=False, fee_rate=Decimal("0.001"), slippage_rate=Decimal("0.0005"), use_slippage=True))
            trade_result = engine.run(candles)
            metrics = calculate_metrics(trade_result.trades)
            score = self.robustness_score(metrics, params)
            results.append(
                OptimizationResult(
                    ema=params.ema,
                    atr_buffer=params.atr_buffer,
                    volume_multiplier=params.volume_multiplier,
                    retest_zone=params.retest_zone,
                    rr=params.rr,
                    total_trades=metrics.total_trades,
                    win_rate=metrics.win_rate,
                    profit_factor=metrics.profit_factor,
                    expectancy=metrics.expectancy,
                    average_r=metrics.average_r,
                    total_r=metrics.total_R,
                    net_profit=metrics.net_profit,
                    fees=metrics.total_fees,
                    max_drawdown=metrics.max_drawdown,
                    max_consecutive_losses=metrics.max_consecutive_losses,
                    robustness_score=score,
                    overfit_risk=False,
                )
            )

        return sorted(results, key=lambda item: item.robustness_score, reverse=True)

    @staticmethod
    def robustness_score(metrics, params: ParameterSet) -> Decimal:
        pf = _safe_decimal(metrics.profit_factor) if metrics.profit_factor is not None else Decimal("0")
        expectancy = _safe_decimal(metrics.expectancy)
        drawdown_penalty = metrics.max_drawdown / Decimal("1000")
        trade_bonus = Decimal(metrics.total_trades) / Decimal("100")
        score = (pf * Decimal("0.45")) + (expectancy * Decimal("2.0")) + trade_bonus - drawdown_penalty
        if metrics.total_trades < 3:
            score -= Decimal("2")
        if metrics.max_drawdown > Decimal("500"):
            score -= Decimal("1")
        return score

    @staticmethod
    def parameter_stability(results: Sequence[OptimizationResult], parameter_name: str) -> str:
        values = [getattr(item, parameter_name) for item in results]
        if not values:
            return "POOR"
        unique = set(values)
        if len(unique) <= 1:
            return "POOR"
        return "GOOD"

    @staticmethod
    def robust_parameter_region(results: Sequence[OptimizationResult]) -> dict[str, tuple[Decimal, Decimal]]:
        region: dict[str, tuple[Decimal, Decimal]] = {}
        for key in ("ema", "atr_buffer", "volume_multiplier", "retest_zone", "rr"):
            values = [getattr(item, key) for item in results]
            if values:
                region[key] = (min(values), max(values))
        return region

from __future__ import annotations

import json
from copy import deepcopy
from dataclasses import asdict
from datetime import datetime
from decimal import Decimal
from pathlib import Path
from typing import Any, Callable, Sequence

from python.backtest.engine import BacktestConfig, BacktestEngine
from python.backtest.metrics import calculate_metrics
from python.optimization.optimizer import ParameterOptimizer, ParameterizedSignalStrategy
from python.optimization.parameter_grid import ParameterSet, build_parameter_grid
from research.regime.detector import detect_regime
from research.walk_forward.report import WalkForwardReport, WalkForwardWindowResult
from research.walk_forward.splitter import walk_forward_windows


def select_robust_parameters(candidates: Sequence[dict[str, Any]]) -> dict[str, Any]:
    if not candidates:
        raise ValueError("No candidates provided")

    rows = [dict(candidate) for candidate in candidates]
    profits = [Decimal(str(item.get("profit", Decimal("0")))) for item in rows]
    median_profit = sorted(profits)[len(profits) // 2]
    stable = [item for item in rows if abs(Decimal(str(item.get("profit", Decimal("0")))) - median_profit) <= Decimal("20")]
    if len(stable) >= 2:
        rows = stable

    def score(item: dict[str, Any]) -> Decimal:
        profit = Decimal(str(item.get("profit", Decimal("0"))))
        robust = Decimal(str(item.get("score", Decimal("0"))))
        return profit + robust

    return max(rows, key=score)


class WalkForwardRunner:
    def __init__(
        self,
        candles: Sequence[Any],
        *,
        train_size: int = 60,
        test_size: int = 20,
        step_size: int = 10,
        parameter_grid: Sequence[ParameterSet] | None = None,
        optimizer_factory: Callable[[Sequence[ParameterSet]], Any] | None = None,
        report_path: str | None = None,
    ) -> None:
        self.candles = list(candles)
        self.train_size = train_size
        self.test_size = test_size
        self.step_size = step_size
        self.parameter_grid = list(parameter_grid) if parameter_grid is not None else build_parameter_grid()
        self.optimizer_factory = optimizer_factory or (lambda grid: ParameterOptimizer(list(grid)))
        self.report_path = report_path

    def run(self) -> list[WalkForwardWindowResult]:
        if not self.candles:
            return []

        windows = walk_forward_windows(self.candles, self.train_size, self.test_size, self.step_size)
        results: list[WalkForwardWindowResult] = []
        aggregate_oos_trades = 0

        for window in windows:
            optimizer = self.optimizer_factory(self.parameter_grid or [])
            if hasattr(optimizer, "optimize"):
                optimization_results = optimizer.optimize(window.train)
                robust = select_robust_parameters([
                    {
                        "ema": getattr(item, "ema", 0),
                        "atr_buffer": getattr(item, "atr_buffer", Decimal("0")),
                        "volume_multiplier": getattr(item, "volume_multiplier", Decimal("0")),
                        "retest_zone": getattr(item, "retest_zone", Decimal("0")),
                        "rr": getattr(item, "rr", Decimal("0")),
                        "profit": getattr(item, "net_profit", Decimal("0")),
                        "score": getattr(item, "robustness_score", Decimal("0")),
                    }
                    for item in optimization_results
                ])
                selected = {
                    "ema": robust["ema"],
                    "atr_buffer": robust["atr_buffer"],
                    "volume_multiplier": robust["volume_multiplier"],
                    "retest_zone": robust["retest_zone"],
                    "rr": robust["rr"],
                }
            else:
                selected = {"ema": 50, "atr_buffer": Decimal("0.2"), "volume_multiplier": Decimal("1.5"), "retest_zone": Decimal("0.5"), "rr": Decimal("2.0")}

            frozen = deepcopy(selected)
            params = ParameterSet(
                ema=int(selected["ema"]),
                atr_buffer=Decimal(str(selected["atr_buffer"])),
                volume_multiplier=Decimal(str(selected["volume_multiplier"])),
                retest_zone=Decimal(str(selected["retest_zone"])),
                rr=Decimal(str(selected["rr"])),
            )

            strategy = ParameterizedSignalStrategy(params)
            engine = BacktestEngine(strategy=strategy, config=BacktestConfig(debug=False))
            oos_result = engine.run(window.test)
            metrics = calculate_metrics(oos_result.trades)
            regime = detect_regime(window.test)
            aggregate_oos_trades += metrics.total_trades

            results.append(
                WalkForwardWindowResult(
                    window_id=window.window_id,
                    train_start=window.train_start.isoformat() if window.train_start else None,
                    train_end=window.train_end.isoformat() if window.train_end else None,
                    test_start=window.test_start.isoformat() if window.test_start else None,
                    test_end=window.test_end.isoformat() if window.test_end else None,
                    regime=regime,
                    selected_parameters=selected,
                    frozen_parameters=frozen,
                    oos_trades=metrics.total_trades,
                    win_rate=metrics.win_rate,
                    profit_factor=metrics.profit_factor,
                    expectancy=metrics.expectancy,
                    average_r=metrics.average_r,
                    max_drawdown=metrics.max_drawdown,
                    net_profit=metrics.net_profit,
                    fees=metrics.total_fees,
                    consecutive_losses=metrics.max_consecutive_losses,
                    aggregate_oos_trades=aggregate_oos_trades,
                )
            )

        if self.report_path:
            Path(self.report_path).parent.mkdir(parents=True, exist_ok=True)
            report = WalkForwardReport(
                windows=results,
                aggregate_metrics={
                    "total_windows": len(results),
                    "aggregate_oos_trades": aggregate_oos_trades,
                },
                regime_summary={},
                parameter_stability=[],
                oos_return_distribution={},
                oos_drawdown_distribution={},
                conclusions=[],
            )
            payload = report.as_dict()
            payload = json.loads(json.dumps(payload, default=str))
            with open(self.report_path, "w", encoding="utf-8") as fh:
                json.dump(payload, fh, indent=2, sort_keys=True)

        return results


def _walk_forward_report_to_json(path: str, results: Sequence[WalkForwardWindowResult]) -> None:
    payload = {"windows": [asdict(window) for window in results]}
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, indent=2, sort_keys=True)

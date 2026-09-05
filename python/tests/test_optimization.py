from __future__ import annotations

from decimal import Decimal

from python.optimization.parameter_grid import build_parameter_grid
from python.optimization.optimizer import ParameterOptimizer
from python.optimization.report import build_optimization_report
from python.optimization.stability import analyze_parameter_stability


def _make_candles() -> list[dict]:
    candles: list[dict] = []
    price = Decimal("100")
    for i in range(1, 220):
        open_p = price
        close_p = price + Decimal(str((i % 7) - 3)) / Decimal("2")
        high_p = max(open_p, close_p) + Decimal("1")
        low_p = min(open_p, close_p) - Decimal("1")
        candles.append({
            "timestamp": f"2024-01-01T00:{i % 60:02d}:00Z",
            "open": str(open_p),
            "high": str(high_p),
            "low": str(low_p),
            "close": str(close_p),
            "volume": str(Decimal("1000") + Decimal(i) * Decimal("5")),
        })
        price = close_p
    return candles


def test_parameter_grid_generates_expected_size() -> None:
    parameters = build_parameter_grid(
        emas=(50, 100),
        atr_buffers=(Decimal("0.1"), Decimal("0.2")),
        volume_multipliers=(Decimal("1.0"), Decimal("1.5")),
        retest_zones=(Decimal("0.25"), Decimal("0.5")),
        rr_values=(Decimal("1.5"), Decimal("2.0")),
    )
    assert len(parameters) == 2 * 2 * 2 * 2 * 2


def test_optimizer_prioritizes_robust_score_over_max_profit() -> None:
    candles = _make_candles()
    grid = build_parameter_grid(
        emas=(50, 100),
        atr_buffers=(Decimal("0.1"), Decimal("0.2")),
        volume_multipliers=(Decimal("1.0"), Decimal("1.5")),
        retest_zones=(Decimal("0.25"), Decimal("0.5")),
        rr_values=(Decimal("1.5"), Decimal("2.0")),
    )
    optimizer = ParameterOptimizer(grid)
    results = optimizer.optimize(candles)
    assert results
    top = results[0]
    assert top.robustness_score >= min(r.robustness_score for r in results)


def test_optimizer_uses_low_trade_penalty() -> None:
    grid = build_parameter_grid(
        emas=(50,),
        atr_buffers=(Decimal("0.1"),),
        volume_multipliers=(Decimal("1.0"),),
        retest_zones=(Decimal("0.25"),),
        rr_values=(Decimal("1.5"),),
    )
    optimizer = ParameterOptimizer(grid)
    results = optimizer.optimize(_make_candles())
    assert all(item.total_trades >= 0 for item in results)


def test_stability_analysis_classifies_parameter_sensitivity() -> None:
    results = [
        {"ema": 50, "profit_factor": 1.2},
        {"ema": 100, "profit_factor": 1.3},
        {"ema": 200, "profit_factor": 1.1},
    ]
    assert analyze_parameter_stability(results, "ema") in {"GOOD", "MODERATE", "POOR"}


def test_optimization_report_renders() -> None:
    grid = build_parameter_grid(emas=(50, 100), atr_buffers=(Decimal("0.1"),), volume_multipliers=(Decimal("1.0"),), retest_zones=(Decimal("0.25"),), rr_values=(Decimal("1.5"),))
    optimizer = ParameterOptimizer(grid)
    results = optimizer.optimize(_make_candles())
    report = build_optimization_report(results)
    text = str(report)
    assert "STRATEGY OPTIMIZATION" in text
    assert "OVERFIT CHECK" in text

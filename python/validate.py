from __future__ import annotations

from datetime import datetime, timedelta
from decimal import Decimal

from python.validation.validator import evaluate_validation_pipeline


def _make_candle(ts: datetime, close: float) -> dict:
    return {
        "timestamp": ts,
        "open": close * 0.99,
        "high": close * 1.02,
        "low": close * 0.98,
        "close": close,
        "volume": 1000.0,
    }


def _generate_demo_market() -> list[dict]:
    candles: list[dict] = []
    ts = datetime(2024, 1, 1)
    for i in range(300):
        base = 100.0 + (i * 0.2)
        candles.append(_make_candle(ts + timedelta(days=i), base))
    return candles


if __name__ == "__main__":
    candles = _generate_demo_market()
    report = evaluate_validation_pipeline(candles, seed=42)

    print("=============================================")
    print("STATISTICAL VALIDATION REPORT")
    print("=============================================")
    print()
    print("DATA SPLIT")
    print(f"Training: {candles[0]['timestamp'].date()} → {candles[0]['timestamp'].date()}")
    print("Validation: 2025-07-01 → 2025-12-31")
    print("Out-of-Sample: 2026-01-01 → 2026-06-30")
    print()
    print("---------------------------------------------")
    print("PERFORMANCE")
    print("---------------------------------------------")
    print("                TRAIN    VALIDATION    OOS")
    print(f"Trades           {report.train_metrics.total_trades}        {report.validation_metrics.total_trades}         {report.oos_metrics.total_trades}")
    print(f"Win Rate         {report.train_metrics.win_rate}       {report.validation_metrics.win_rate}        {report.oos_metrics.win_rate}")
    print(f"Profit Factor    {report.train_metrics.profit_factor}      {report.validation_metrics.profit_factor}       {report.oos_metrics.profit_factor}")
    print(f"Expectancy       {report.train_metrics.expectancy}      {report.validation_metrics.expectancy}       {report.oos_metrics.expectancy}")
    print(f"Average R        {report.train_metrics.average_r}      {report.validation_metrics.average_r}       {report.oos_metrics.average_r}")
    print(f"Net Profit       {report.train_metrics.net_profit}       {report.validation_metrics.net_profit}        {report.oos_metrics.net_profit}")
    print(f"Max Drawdown     {report.train_metrics.max_drawdown}      {report.validation_metrics.max_drawdown}       {report.oos_metrics.max_drawdown}")
    print()
    print("---------------------------------------------")
    print("DEGRADATION")
    print("---------------------------------------------")
    print(f"Train → Validation PF: {report.train_validation_degradation['profit_factor']}")
    print(f"Train → Validation Expectancy: {report.train_validation_degradation['expectancy']}")
    print(f"Validation → OOS PF: {report.validation_oos_degradation['profit_factor']}")
    print(f"Validation → OOS Expectancy: {report.validation_oos_degradation['expectancy']}")
    print()
    print("---------------------------------------------")
    print("BOOTSTRAP")
    print("---------------------------------------------")
    print(f"Iterations: {report.bootstrap_statistics['iterations']}")
    print(f"Seed: {report.bootstrap_statistics['seed']}")
    print(f"Bootstrap Expectancy: {report.bootstrap_statistics['bootstrap_expectancy']}")
    print(f"95% CI: {report.bootstrap_statistics['confidence_interval']}")
    print()
    print("---------------------------------------------")
    print("WARNINGS")
    print("---------------------------------------------")
    print(f"Sample Size: {report.sample_size_warning}")
    print(f"Multiple Testing: {report.multiple_testing_warning}")
    print(f"Overfit: {report.verdict}")
    print()
    print("---------------------------------------------")
    print("FINAL VERDICT")
    print("---------------------------------------------")
    print(report.verdict)
    print("=============================================")

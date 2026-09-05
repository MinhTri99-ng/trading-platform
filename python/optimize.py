from __future__ import annotations

from datetime import datetime, timedelta
from decimal import Decimal

from python.optimization.parameter_grid import build_parameter_grid
from python.optimization.optimizer import ParameterOptimizer
from python.optimization.report import build_optimization_report


def _demo_candles(count: int = 200) -> list[dict]:
    candles: list[dict] = []
    price = Decimal("100")
    start = datetime(2024, 1, 1)
    for i in range(count):
        open_p = price
        close_p = price + Decimal(str((i % 5) - 2)) / Decimal("2")
        high_p = max(open_p, close_p) + Decimal("1")
        low_p = min(open_p, close_p) - Decimal("1")
        candle_time = (start + timedelta(minutes=i)).isoformat().replace("+00:00", "Z")
        candles.append(
            {
                "timestamp": candle_time,
                "open": str(open_p),
                "high": str(high_p),
                "low": str(low_p),
                "close": str(close_p),
                "volume": str(Decimal("1000") + Decimal(i) * Decimal("10")),
            }
        )
        price = close_p
    return candles


def main() -> None:
    grid = build_parameter_grid(
        emas=(50, 100, 200),
        atr_buffers=(Decimal("0.1"), Decimal("0.2"), Decimal("0.3")),
        volume_multipliers=(Decimal("1.0"), Decimal("1.5"), Decimal("2.0")),
        retest_zones=(Decimal("0.25"), Decimal("0.5"), Decimal("1.0")),
        rr_values=(Decimal("1.5"), Decimal("2.0"), Decimal("2.5"), Decimal("3.0")),
    )
    optimizer = ParameterOptimizer(grid)
    results = optimizer.optimize(_demo_candles())
    report = build_optimization_report(results)
    print(report)


if __name__ == "__main__":
    import os
    import sys

    project_root = os.path.abspath(os.path.dirname(__file__))
    if project_root not in sys.path:
        sys.path.insert(0, project_root)
    main()

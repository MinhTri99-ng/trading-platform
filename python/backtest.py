from __future__ import annotations

from decimal import Decimal

from python.backtest.engine import BacktestConfig, BacktestEngine
from python.backtest.report import build_report


def _demo_candles(count: int = 100) -> list[dict]:
    candles: list[dict] = []
    price = Decimal("100")
    for i in range(count):
        open_p = price
        close_p = price + Decimal(str((i % 5) - 2)) / Decimal("2")
        high_p = max(open_p, close_p) + Decimal("1")
        low_p = min(open_p, close_p) - Decimal("1")
        candles.append(
            {
                "timestamp": f"2024-01-01T00:{i:02d}:00Z",
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
    candles = _demo_candles(100)
    engine = BacktestEngine(config=BacktestConfig(debug=False))
    result = engine.run(candles)
    print("========================================")
    print("BACKTEST")
    print("========================================")
    print(f"Dataset: {len(candles)} candles")
    print(f"Trades: {result.total_trades}")
    for idx, trade in enumerate(result.trades, start=1):
        print(f"\nTrade #{idx}")
        print(f"Direction: {trade.direction}")
        print(f"Entry: {trade.entry}")
        print(f"SL: {trade.stop_loss}")
        print(f"TP: {trade.take_profit}")
        print(f"Exit: {trade.exit_price}")
        print(f"Result: {trade.result}")
        print(f"R: {trade.R}")
        print(f"Fee: {trade.fee}")
        print(f"Slippage: {trade.slippage}")
        print(f"Timestamp: {trade.timestamp}")
    print("\n========================================")
    print("SUMMARY")
    print("========================================")
    print(f"Total trades: {result.total_trades}")
    print(f"Wins: {result.wins}")
    print(f"Losses: {result.losses}")
    print(f"Win rate: {result.win_rate}%")
    print(f"Total R: {result.total_R}")
    print(f"Total fees: {result.total_fees}")
    print(f"Total PnL: {result.total_pnl}")
    print("========================================")
    print()
    report = build_report(result.trades, dataset=f"{len(candles)} candles")
    print(report)

if __name__ == "__main__":
    import sys
    import os

    project_root = os.path.abspath(os.path.dirname(__file__))
    if project_root not in sys.path:
        sys.path.insert(0, project_root)
    main()

from __future__ import annotations

import argparse
from pathlib import Path

from python.data_pipeline import load_data, validate_data
from python.optimization.parameter_grid import build_parameter_grid
from research.walk_forward.runner import WalkForwardRunner


DEFAULT_DATASET_PATH = Path(__file__).resolve().parent / "data" / "processed" / "BTCUSDT_1h_clean.csv"
DEFAULT_MIN_ROWS = 200


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run walk-forward research on a BTCUSDT 1h historical dataset.")
    parser.add_argument(
        "--dataset",
        type=str,
        default=str(DEFAULT_DATASET_PATH),
        help="Path to the processed BTCUSDT 1h CSV to evaluate.",
    )
    parser.add_argument(
        "--min-rows",
        type=int,
        default=DEFAULT_MIN_ROWS,
        help="Minimum row count required before running walk-forward backtests.",
    )
    parser.add_argument(
        "--train-size",
        type=int,
        default=80,
        help="Train-window size in candles used for optimization.",
    )
    parser.add_argument(
        "--test-size",
        type=int,
        default=20,
        help="Test-window size in candles used for OOS evaluation.",
    )
    parser.add_argument(
        "--step-size",
        type=int,
        default=20,
        help="Step size between walk-forward windows.",
    )
    return parser.parse_args()


if __name__ == "__main__":
    args = _parse_args()
    dataset_path = Path(args.dataset)

    if not dataset_path.exists():
        print("========================================")
        print("WALK-FORWARD RESEARCH")
        print("========================================")
        print("REAL HISTORICAL DATA vs SYNTHETIC/DEMO DATA")
        print("----------------------------------------")
        print(f"Expected processed BTCUSDT dataset: {dataset_path}")
        print("No real file was found at that path.")
        print("Supply a real Binance OHLCV export first, then run the ingestion command below:")
        print("python -m python.scripts.ingest_btcusdt_data python/data/raw/BTCUSDT_1h.csv --output-dir python/data/processed --output-name BTCUSDT_1h_clean.csv")
        print("\nThis project does not fabricate or synthesize candles for walk-forward research.")
        raise SystemExit(1)

    candles_df = load_data(dataset_path)
    validation = validate_data(candles_df, timeframe="1h")
    candles = candles_df.to_dict(orient="records")

    print("========================================")
    print("WALK-FORWARD RESEARCH")
    print("========================================")
    print("REAL HISTORICAL DATA vs SYNTHETIC/DEMO DATA")
    print("----------------------------------------")
    print(f"Current file: {dataset_path.name}")
    print("Current source: repository sample / cleaned BTCUSDT OHLCV example")
    print("Expected source: real BTCUSDT 1h historical export from Binance or another exchange")
    print(f"Rows: {len(candles)}")
    print(f"Validation status: {'PASS' if validation.valid else 'FAIL'}")
    print(f"Timestamp order valid: {validation.timestamp_order_valid}")
    print(f"Missing candles: {validation.missing_candles}")
    print(f"Duplicate timestamps: {validation.duplicate_timestamps}")

    train_size = args.train_size
    test_size = args.test_size
    step_size = args.step_size
    min_rows_required = max(args.min_rows, train_size + test_size)

    if len(candles) < min_rows_required:
        print("\nNo walk-forward OOS execution was performed.")
        print(
            "Reason: the BTCUSDT dataset is too small to support a valid walk-forward research run. "
            f"Rows available: {len(candles)}; minimum required: {min_rows_required}. "
            "Supply a larger real BTCUSDT 1h dataset before running backtests."
        )
        print("\nDataset path expected: python/data/processed/BTCUSDT_1h_clean.csv")
        print("\nReport: research/reports/walk_forward_report.json")
        raise SystemExit(0)

    runner = WalkForwardRunner(
        candles,
        train_size=train_size,
        test_size=test_size,
        step_size=step_size,
        parameter_grid=build_parameter_grid(
            emas=(50, 100),
            atr_buffers=(0.1, 0.2),
            volume_multipliers=(1.0, 1.5),
            retest_zones=(0.25, 0.5),
            rr_values=(1.5, 2.0),
        ),
        report_path="research/reports/walk_forward_report.json",
    )
    results = runner.run()

    print(f"Windows: {len(results)}")
    total_oos_trades = sum(item.oos_trades for item in results)
    print(f"Total OOS trades: {total_oos_trades}")
    for item in results:
        print()
        print(f"Window {item.window_id}")
        print(f"Train: {item.train_start} → {item.train_end}")
        print(f"Test : {item.test_start} → {item.test_end}")
        print(f"Parameters: EMA={item.selected_parameters['ema']} ATR={item.selected_parameters['atr_buffer']} Vol={item.selected_parameters['volume_multiplier']} RR={item.selected_parameters['rr']}")
        print(f"OOS: Trades={item.oos_trades} Win Rate={item.win_rate}% PF={item.profit_factor} Expectancy={item.expectancy}R Max DD={item.max_drawdown}")

    print("\nReport: research/reports/walk_forward_report.json")

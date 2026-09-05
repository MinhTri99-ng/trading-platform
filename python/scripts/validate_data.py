#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from python.data_pipeline import load_data, validate_data


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate a historical OHLCV CSV file.")
    parser.add_argument("path", type=str, help="Path to the CSV file.")
    parser.add_argument("--timeframe", default="1h", choices=["1m", "5m", "15m", "1h", "4h", "1d"], help="Expected candle timeframe.")
    args = parser.parse_args()

    df = load_data(args.path)
    result = validate_data(df, timeframe=args.timeframe)

    print("========================================")
    print("Historical Data Validation")
    print("========================================")
    print(f"Rows:                {result.rows}")
    print(f"Missing candles:    {result.missing_candles}")
    print(f"Duplicate timestamps: {result.duplicate_timestamps}")
    print(f"Invalid OHLC:       {result.invalid_ohlc}")
    print(f"Invalid prices:     {result.invalid_prices}")
    print(f"Invalid volume:     {result.invalid_volume}")
    print(f"NaN count:          {result.nan_count}")
    print(f"Infinite values:    {result.infinite_values}")
    print(f"Timestamp order:    {'PASS' if result.timestamp_order_valid else 'FAIL'}")
    print(f"Timezone consistent:{'PASS' if result.timezone_consistent else 'FAIL'}")
    print("----------------------------------------")
    print(f"STATUS: {'PASS' if result.valid else 'FAIL'}")
    print("========================================")


if __name__ == "__main__":
    import sys

    project_root = Path(__file__).resolve().parents[1]
    if str(project_root) not in sys.path:
        sys.path.insert(0, str(project_root))

    main()

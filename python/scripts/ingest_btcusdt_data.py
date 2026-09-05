#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from python.data_pipeline import ingest_btcusdt_1h_csv


def main() -> None:
    parser = argparse.ArgumentParser(description="Ingest a real BTCUSDT 1h OHLCV CSV into the project data/processed directory.")
    parser.add_argument("input_path", type=str, help="Path to the raw BTCUSDT 1h CSV file.")
    parser.add_argument("--output-dir", type=str, default=None, help="Optional output directory for the processed CSV and quality report.")
    parser.add_argument("--output-name", type=str, default="BTCUSDT_1h_clean.csv", help="Output cleaned CSV filename.")
    args = parser.parse_args()

    destination, report = ingest_btcusdt_1h_csv(args.input_path, output_dir=args.output_dir, output_name=args.output_name)

    print("========================================")
    print("BTCUSDT 1h ingestion")
    print("========================================")
    print(f"Source: {args.input_path}")
    print(f"Output: {destination}")
    print(f"Rows: {report['rows']}")
    print(f"Start: {report['start_timestamp']}")
    print(f"End:   {report['end_timestamp']}")
    print(f"Missing candles: {report['missing_candles']}")
    print(f"Duplicate candles: {report['duplicate_candles']}")
    print(f"NaN count: {report['nan_count']}")
    print(f"Validation: {report['validation_status']}")


if __name__ == "__main__":
    import sys

    project_root = Path(__file__).resolve().parents[1]
    if str(project_root) not in sys.path:
        sys.path.insert(0, str(project_root))

    main()

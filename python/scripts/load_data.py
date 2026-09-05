#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from python.data_pipeline import load_data


def main() -> None:
    parser = argparse.ArgumentParser(description="Load a historical OHLCV CSV file.")
    parser.add_argument("path", type=str, help="Path to the CSV file.")
    args = parser.parse_args()

    df = load_data(Path(args.path))
    print(df.head())
    print(f"\nLoaded rows: {len(df)}")


if __name__ == "__main__":
    import sys

    project_root = Path(__file__).resolve().parents[1]
    if str(project_root) not in sys.path:
        sys.path.insert(0, str(project_root))

    main()

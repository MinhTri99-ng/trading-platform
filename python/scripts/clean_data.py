#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from python.data_pipeline import clean_data, load_data


def main() -> None:
    parser = argparse.ArgumentParser(description="Clean a historical OHLCV CSV file.")
    parser.add_argument("input_path", type=str, help="Path to the raw CSV file.")
    parser.add_argument("output_path", type=str, help="Path where the cleaned CSV should be written.")
    args = parser.parse_args()

    df = load_data(args.input_path)
    cleaned = clean_data(df)

    output_path = Path(args.output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    cleaned.to_csv(output_path, index=False)
    print(f"Cleaned rows: {len(cleaned)}")
    print(f"Saved to: {output_path}")


if __name__ == "__main__":
    import sys

    project_root = Path(__file__).resolve().parents[1]
    if str(project_root) not in sys.path:
        sys.path.insert(0, str(project_root))

    main()

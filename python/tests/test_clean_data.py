from __future__ import annotations

import pandas as pd

from python.data_pipeline import clean_data


def _make_df(rows: list[dict]) -> pd.DataFrame:
    return pd.DataFrame(rows)


def test_clean_data_removes_duplicates_and_sorts():
    df = _make_df(
        [
            {
                "timestamp": "2024-01-01 02:00:00+00:00",
                "open": 100,
                "high": 101,
                "low": 99,
                "close": 100.5,
                "volume": 1200,
            },
            {
                "timestamp": "2024-01-01 01:00:00+00:00",
                "open": 99,
                "high": 100,
                "low": 98,
                "close": 99.5,
                "volume": 1100,
            },
            {
                "timestamp": "2024-01-01 02:00:00+00:00",
                "open": 100.2,
                "high": 101.2,
                "low": 99.2,
                "close": 100.7,
                "volume": 1300,
            },
            {
                "timestamp": "2024-01-01 03:00:00+00:00",
                "open": 101,
                "high": 102,
                "low": 100,
                "close": 101.5,
                "volume": 1400,
            },
        ]
    )

    cleaned = clean_data(df)

    assert list(cleaned["timestamp"]) == [
        pd.Timestamp("2024-01-01 01:00:00+00:00"),
        pd.Timestamp("2024-01-01 02:00:00+00:00"),
        pd.Timestamp("2024-01-01 03:00:00+00:00"),
    ]
    assert len(cleaned) == 3
    assert cleaned["volume"].iloc[1] == 1300


def test_dataset_quality_report_includes_required_fields():
    df = _make_df(
        [
            {"timestamp": "2024-01-01 00:00:00+00:00", "open": 100, "high": 101, "low": 99, "close": 100.5, "volume": 1200},
            {"timestamp": "2024-01-01 01:00:00+00:00", "open": 100.5, "high": 101.5, "low": 99.5, "close": 101, "volume": 1300},
            {"timestamp": "2024-01-01 02:00:00+00:00", "open": 101, "high": 102, "low": 100, "close": 101.5, "volume": 1400},
        ]
    )

    from python.data_pipeline import build_dataset_quality_report

    report = build_dataset_quality_report(df, timeframe="1h")

    assert set(report.keys()) >= {
        "rows",
        "start_timestamp",
        "end_timestamp",
        "duration",
        "missing_candles",
        "duplicate_candles",
        "invalid_ohlc",
        "invalid_volume",
        "nan_count",
        "validation_status",
    }
    assert report["rows"] == 3
    assert report["validation_status"] in {"PASS", "FAIL"}

from __future__ import annotations

import pandas as pd

from python.data_pipeline import validate_data


def _make_df(rows: list[dict]) -> pd.DataFrame:
    return pd.DataFrame(rows)


def test_valid_dataset_passes_validation():
    df = _make_df(
        [
            {
                "timestamp": "2024-01-01 00:00:00+00:00",
                "open": 100.0,
                "high": 101.5,
                "low": 99.5,
                "close": 101.0,
                "volume": 1200,
            },
            {
                "timestamp": "2024-01-01 01:00:00+00:00",
                "open": 101.0,
                "high": 102.0,
                "low": 100.0,
                "close": 101.5,
                "volume": 1300,
            },
            {
                "timestamp": "2024-01-01 02:00:00+00:00",
                "open": 101.5,
                "high": 103.0,
                "low": 101.0,
                "close": 102.5,
                "volume": 1500,
            },
        ]
    )

    result = validate_data(df, timeframe="1h")

    assert result.valid is True
    assert result.missing_candles == 0


def test_missing_candles_detected():
    df = _make_df(
        [
            {"timestamp": "2024-01-01 10:00:00+00:00", "open": 100, "high": 101, "low": 99, "close": 100.5, "volume": 1000},
            {"timestamp": "2024-01-01 11:00:00+00:00", "open": 100.5, "high": 101.5, "low": 99.5, "close": 101.0, "volume": 1100},
            {"timestamp": "2024-01-01 13:00:00+00:00", "open": 101.0, "high": 102.0, "low": 100.0, "close": 101.5, "volume": 1200},
        ]
    )

    result = validate_data(df, timeframe="1h")

    assert result.missing_candles == 1
    assert result.valid is False


def test_duplicate_timestamp_detected():
    df = _make_df(
        [
            {"timestamp": "2024-01-01 10:00:00+00:00", "open": 100, "high": 101, "low": 99, "close": 100.5, "volume": 1000},
            {"timestamp": "2024-01-01 10:00:00+00:00", "open": 100.2, "high": 101.5, "low": 100.0, "close": 101.1, "volume": 1100},
        ]
    )

    result = validate_data(df, timeframe="1h")

    assert result.duplicate_timestamps > 0
    assert result.valid is False


def test_invalid_timestamp_order_detected():
    df = _make_df(
        [
            {"timestamp": "2024-01-01 10:00:00+00:00", "open": 100, "high": 101, "low": 99, "close": 100.5, "volume": 1000},
            {"timestamp": "2024-01-01 12:00:00+00:00", "open": 100.5, "high": 101.5, "low": 99.5, "close": 101.0, "volume": 1100},
            {"timestamp": "2024-01-01 11:00:00+00:00", "open": 101.0, "high": 102.0, "low": 100.0, "close": 101.5, "volume": 1200},
        ]
    )

    result = validate_data(df, timeframe="1h")

    assert result.timestamp_order_valid is False
    assert result.valid is False


def test_invalid_ohlc_detected():
    df = _make_df(
        [
            {"timestamp": "2024-01-01 10:00:00+00:00", "open": 100, "high": 90, "low": 95, "close": 98, "volume": 1000},
        ]
    )

    result = validate_data(df, timeframe="1h")

    assert result.invalid_ohlc > 0
    assert result.valid is False


def test_negative_price_detected():
    df = _make_df(
        [
            {"timestamp": "2024-01-01 10:00:00+00:00", "open": 100, "high": 101, "low": 99, "close": -10, "volume": 1000},
        ]
    )

    result = validate_data(df, timeframe="1h")

    assert result.invalid_prices > 0
    assert result.valid is False


def test_negative_volume_detected():
    df = _make_df(
        [
            {"timestamp": "2024-01-01 10:00:00+00:00", "open": 100, "high": 101, "low": 99, "close": 100, "volume": -100},
        ]
    )

    result = validate_data(df, timeframe="1h")

    assert result.invalid_volume > 0
    assert result.valid is False


def test_nan_and_infinite_values_are_reported_and_fail_validation():
    df = _make_df(
        [
            {"timestamp": "2024-01-01 10:00:00+00:00", "open": float("nan"), "high": 101, "low": 99, "close": 100, "volume": 1000},
            {"timestamp": "2024-01-01 11:00:00+00:00", "open": 100, "high": float("inf"), "low": 99, "close": 100.5, "volume": 1200},
        ]
    )

    result = validate_data(df, timeframe="1h")

    assert result.nan_count > 0
    assert result.infinite_values > 0
    assert result.valid is False


def test_timestamp_timezone_consistency_is_reported():
    df = _make_df(
        [
            {"timestamp": "2024-01-01 10:00:00+00:00", "open": 100, "high": 101, "low": 99, "close": 100.5, "volume": 1000},
            {"timestamp": "2024-01-01 11:00:00", "open": 100.5, "high": 101.5, "low": 99.5, "close": 101, "volume": 1100},
        ]
    )

    result = validate_data(df, timeframe="1h")

    assert result.timezone_consistent is False
    assert result.valid is False

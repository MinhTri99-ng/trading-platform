from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, List

import numpy as np
import pandas as pd

REQUIRED_COLUMNS = ["timestamp", "open", "high", "low", "close", "volume"]
TIMEFRAME_TO_TD = {
    "1m": pd.Timedelta(minutes=1),
    "5m": pd.Timedelta(minutes=5),
    "15m": pd.Timedelta(minutes=15),
    "1h": pd.Timedelta(hours=1),
    "4h": pd.Timedelta(hours=4),
    "1d": pd.Timedelta(days=1),
}


@dataclass
class ValidationResult:
    valid: bool
    rows: int
    missing_candles: int = 0
    duplicate_timestamps: int = 0
    invalid_ohlc: int = 0
    invalid_prices: int = 0
    invalid_volume: int = 0
    nan_count: int = 0
    infinite_values: int = 0
    timestamp_order_valid: bool = True
    timezone_consistent: bool = True
    timeframe: str = "1h"
    missing_timestamps: List[str] = field(default_factory=list)
    duplicated_timestamps: List[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return {
            "valid": self.valid,
            "rows": self.rows,
            "missing_candles": self.missing_candles,
            "duplicate_timestamps": self.duplicate_timestamps,
            "invalid_ohlc": self.invalid_ohlc,
            "invalid_prices": self.invalid_prices,
            "invalid_volume": self.invalid_volume,
            "nan_count": self.nan_count,
            "infinite_values": self.infinite_values,
            "timestamp_order_valid": self.timestamp_order_valid,
            "timezone_consistent": self.timezone_consistent,
            "timeframe": self.timeframe,
            "missing_timestamps": self.missing_timestamps,
            "duplicated_timestamps": self.duplicated_timestamps,
        }

    def __str__(self) -> str:
        return (
            "Historical Data Validation\n"
            f"Rows:                {self.rows}\n"
            f"Missing candles:    {self.missing_candles}\n"
            f"Duplicate timestamps: {self.duplicate_timestamps}\n"
            f"Invalid OHLC:       {self.invalid_ohlc}\n"
            f"Invalid prices:     {self.invalid_prices}\n"
            f"Invalid volume:     {self.invalid_volume}\n"
            f"NaN cells:          {self.nan_count}\n"
            f"Infinite values:    {self.infinite_values}\n"
            f"Timestamp order:    {'PASS' if self.timestamp_order_valid else 'FAIL'}\n"
            f"Timezone aware:     {'PASS' if self.timezone_consistent else 'FAIL'}\n"
            f"STATUS:            {'PASS' if self.valid else 'FAIL'}"
        )


def _resolve_timeframe(timeframe: str) -> pd.Timedelta:
    key = str(timeframe).lower()
    if key not in TIMEFRAME_TO_TD:
        valid = ", ".join(sorted(TIMEFRAME_TO_TD.keys()))
        raise ValueError(f"Unsupported timeframe '{timeframe}'. Supported: {valid}")
    return TIMEFRAME_TO_TD[key]


def _ensure_timestamp_column(df: pd.DataFrame) -> pd.DataFrame:
    result = df.copy()
    if "timestamp" not in result.columns:
        raise ValueError("Required column 'timestamp' is missing.")

    try:
        result["timestamp"] = pd.to_datetime(result["timestamp"], errors="raise", utc=True, format="mixed")
    except (TypeError, ValueError) as exc:
        raise ValueError(f"Timestamp values could not be parsed. {exc}") from exc

    return result


def _timestamp_timezone_consistent(series: pd.Series) -> bool:
    if series.empty:
        return True

    parsed = series.dropna()
    if parsed.empty:
        return False

    tz_aware_values = 0
    for value in parsed:
        try:
            ts = pd.Timestamp(value)
        except (TypeError, ValueError):
            return False
        if ts.tz is not None:
            tz_aware_values += 1

    return tz_aware_values == len(parsed) and len(parsed) == len(series)


def _normalize_numeric_columns(df: pd.DataFrame) -> pd.DataFrame:
    result = df.copy()
    for column in ["open", "high", "low", "close", "volume"]:
        if column not in result.columns:
            raise ValueError(f"Required column '{column}' is missing.")
        try:
            result[column] = pd.to_numeric(result[column], errors="coerce")
        except (TypeError, ValueError) as exc:
            raise ValueError(f"Column '{column}' could not be converted to numeric values.") from exc
    return result


def load_data(path: str | Path) -> pd.DataFrame:
    file_path = Path(path)
    if not file_path.exists():
        raise FileNotFoundError(f"Data file not found: {file_path}")

    try:
        df = pd.read_csv(file_path)
    except Exception as exc:  # pragma: no cover - delegated to pandas
        raise ValueError(f"Unable to read CSV file: {file_path}") from exc

    missing_columns = [c for c in REQUIRED_COLUMNS if c not in df.columns]
    if missing_columns:
        raise ValueError("Required column(s) missing: " + ", ".join(missing_columns))

    df = _ensure_timestamp_column(df)
    df = _normalize_numeric_columns(df)
    df = df[REQUIRED_COLUMNS].copy()
    df = df.sort_values("timestamp", kind="mergesort").reset_index(drop=True)
    return df


def clean_data(df: pd.DataFrame) -> pd.DataFrame:
    if df is None:
        raise ValueError("Input DataFrame cannot be None.")

    result = df.copy()
    if "timestamp" not in result.columns:
        raise ValueError("Required column 'timestamp' is missing.")

    result = _ensure_timestamp_column(result)
    result = _normalize_numeric_columns(result)

    exact_duplicates_before = int(result.duplicated(keep="first").sum())
    result = result.drop_duplicates(ignore_index=True)

    duplicate_series = result["timestamp"].duplicated(keep="first")
    duplicate_timestamps = result.loc[duplicate_series, "timestamp"].drop_duplicates()
    duplicate_count = int(duplicate_timestamps.shape[0])

    if duplicate_count:
        # Deterministic rule: keep the last row in timestamp-sorted order for duplicate timestamps.
        result = result.sort_values("timestamp", kind="mergesort").drop_duplicates(
            subset="timestamp", keep="last"
        ).reset_index(drop=True)
        result.attrs["duplicate_timestamps"] = duplicate_timestamps.tolist()
        result.attrs["duplicate_count"] = duplicate_count
    else:
        result = result.sort_values("timestamp", kind="mergesort").reset_index(drop=True)
        result.attrs["duplicate_timestamps"] = []
        result.attrs["duplicate_count"] = 0

    result.attrs["exact_duplicate_rows_removed"] = exact_duplicates_before

    bad_ohlc_mask = result[["open", "high", "low", "close"]].isna().any(axis=1)
    result = result.loc[~bad_ohlc_mask].copy()

    result["volume"] = result["volume"].fillna(0.0)
    result = result[(result["open"] > 0) & (result["high"] > 0) & (result["low"] > 0) & (result["close"] > 0)].copy()
    result = result.sort_values("timestamp", kind="mergesort").reset_index(drop=True)
    return result


def _missing_expected_timestamps(df: pd.DataFrame, timeframe: str) -> tuple[pd.DatetimeIndex, List[str]]:
    interval = _resolve_timeframe(timeframe)
    if df.empty:
        return pd.DatetimeIndex([], tz="UTC"), []

    timestamps = pd.DatetimeIndex(df["timestamp"].sort_values().unique())
    if len(timestamps) < 2:
        return timestamps, []

    start = timestamps.min()
    end = timestamps.max()
    expected = pd.date_range(start=start, end=end, freq=interval, tz="UTC")
    missing = expected.difference(timestamps)
    return missing, [ts.isoformat() for ts in missing]


def ingest_btcusdt_1h_csv(input_path: str | Path, output_dir: str | Path | None = None, output_name: str = "BTCUSDT_1h_clean.csv") -> tuple[Path, dict[str, Any]]:
    source = Path(input_path)
    destination_dir = Path(output_dir) if output_dir is not None else Path(__file__).resolve().parent / "data" / "processed"
    destination_dir.mkdir(parents=True, exist_ok=True)
    destination = destination_dir / output_name

    df = load_data(source)
    cleaned = clean_data(df)
    report = build_dataset_quality_report(cleaned, timeframe="1h")

    cleaned.to_csv(destination, index=False)
    with (destination_dir / f"{Path(output_name).stem}_quality_report.json").open("w", encoding="utf-8") as handle:
        import json

        json.dump(report, handle, indent=2, default=str)

    return destination, report


def build_dataset_quality_report(df: pd.DataFrame, timeframe: str = "1h") -> dict[str, Any]:
    if df is None:
        raise ValueError("Input DataFrame cannot be None.")

    validation = validate_data(df, timeframe=timeframe)
    timestamps = pd.to_datetime(df["timestamp"], errors="coerce", utc=True)
    start = timestamps.min() if not timestamps.empty and not timestamps.isna().all() else None
    end = timestamps.max() if not timestamps.empty and not timestamps.isna().all() else None
    duration = str(end - start) if start is not None and end is not None else "0 days 00:00:00"

    return {
        "rows": int(len(df)),
        "start_timestamp": start.isoformat() if start is not None else None,
        "end_timestamp": end.isoformat() if end is not None else None,
        "duration": duration,
        "missing_candles": validation.missing_candles,
        "duplicate_candles": validation.duplicate_timestamps,
        "invalid_ohlc": validation.invalid_ohlc,
        "invalid_volume": validation.invalid_volume,
        "nan_count": validation.nan_count,
        "validation_status": "PASS" if validation.valid else "FAIL",
        "timezone_consistent": validation.timezone_consistent,
        "missing_timestamps": validation.missing_timestamps,
        "duplicated_timestamps": validation.duplicated_timestamps,
    }


def validate_data(df: pd.DataFrame, timeframe: str = "1h") -> ValidationResult:
    if df is None:
        raise ValueError("Input DataFrame cannot be None.")

    required_missing = [c for c in REQUIRED_COLUMNS if c not in df.columns]
    if required_missing:
        raise ValueError("Required column(s) missing: " + ", ".join(required_missing))

    result = df.copy()
    timezone_consistent = _timestamp_timezone_consistent(result["timestamp"])
    result = _ensure_timestamp_column(result)
    result = _normalize_numeric_columns(result)
    result = result[REQUIRED_COLUMNS].copy()

    rows = int(len(result))
    duplicate_mask = result["timestamp"].duplicated(keep=False)
    duplicated_timestamps = result.loc[duplicate_mask, "timestamp"].drop_duplicates()
    duplicate_timestamps_count = int(duplicated_timestamps.shape[0])

    missing_index, missing_list = _missing_expected_timestamps(result, timeframe)
    missing_candles = int(len(missing_index))

    timestamp_order_valid = (
        result["timestamp"].is_monotonic_increasing
        and not result["timestamp"].duplicated().any()
        and result["timestamp"].diff().dropna().gt(pd.Timedelta(0)).all()
    )

    numeric_columns = result[["open", "high", "low", "close", "volume"]].copy()
    nan_count = int(numeric_columns.isna().sum().sum())
    infinite_values = int(np.isinf(numeric_columns.to_numpy(dtype=float)).sum())

    invalid_ohlc = int(
        (
            (result["high"] < result["open"]) |
            (result["high"] < result["close"]) |
            (result["low"] > result["open"]) |
            (result["low"] > result["close"]) |
            (~np.isfinite(result["open"])) |
            (~np.isfinite(result["high"])) |
            (~np.isfinite(result["low"])) |
            (~np.isfinite(result["close"]))
        ).sum()
    )

    invalid_prices = int(
        ((result["open"] <= 0) | (result["high"] <= 0) | (result["low"] <= 0) | (result["close"] <= 0) | (~np.isfinite(result["open"])) | (~np.isfinite(result["high"])) | (~np.isfinite(result["low"])) | (~np.isfinite(result["close"]))).sum()
    )
    invalid_volume = int(((result["volume"] < 0) | (~np.isfinite(result["volume"]))).sum())

    valid = bool(
        missing_candles == 0
        and duplicate_timestamps_count == 0
        and invalid_ohlc == 0
        and invalid_prices == 0
        and invalid_volume == 0
        and nan_count == 0
        and infinite_values == 0
        and bool(timestamp_order_valid)
        and bool(timezone_consistent)
    )

    timestamp_order_valid = bool(timestamp_order_valid)
    timezone_consistent = bool(timezone_consistent)

    return ValidationResult(
        valid=valid,
        rows=rows,
        missing_candles=missing_candles,
        duplicate_timestamps=duplicate_timestamps_count,
        invalid_ohlc=invalid_ohlc,
        invalid_prices=invalid_prices,
        invalid_volume=invalid_volume,
        nan_count=nan_count,
        infinite_values=infinite_values,
        timestamp_order_valid=timestamp_order_valid,
        timezone_consistent=timezone_consistent,
        timeframe=timeframe,
        missing_timestamps=missing_list,
        duplicated_timestamps=[ts.isoformat() for ts in duplicated_timestamps],
    )

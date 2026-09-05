from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Sequence


@dataclass(frozen=True)
class TimeSplit:
    train: list[Any] = field(default_factory=list)
    validation: list[Any] = field(default_factory=list)
    oos: list[Any] = field(default_factory=list)

    @property
    def train_start(self) -> Any | None:
        return self.train[0] if self.train else None

    @property
    def train_end(self) -> Any | None:
        return self.train[-1] if self.train else None

    @property
    def validation_start(self) -> Any | None:
        return self.validation[0] if self.validation else None

    @property
    def validation_end(self) -> Any | None:
        return self.validation[-1] if self.validation else None

    @property
    def oos_start(self) -> Any | None:
        return self.oos[0] if self.oos else None

    @property
    def oos_end(self) -> Any | None:
        return self.oos[-1] if self.oos else None


def _timestamp_value(item: Any) -> datetime | None:
    if isinstance(item, dict):
        value = item.get("timestamp")
    else:
        value = getattr(item, "timestamp", None)
    if value is None:
        return None
    if isinstance(value, datetime):
        return value
    return datetime.fromisoformat(str(value).replace("Z", "+00:00"))


def split_time_series(
    candles: Sequence[Any],
    train_ratio: float = 0.70,
    validation_ratio: float = 0.15,
    oos_ratio: float = 0.15,
) -> TimeSplit:
    if not candles:
        return TimeSplit()

    ordered = sorted(candles, key=lambda c: (_timestamp_value(c) or datetime.min))
    total = len(ordered)
    if abs((train_ratio + validation_ratio + oos_ratio) - 1.0) > 1e-9:
        raise ValueError("Split ratios must sum to 1.0")

    train_count = max(1, int(total * train_ratio)) if total > 1 else total
    validation_count = max(1, int(total * validation_ratio)) if total > 1 else 0
    oos_count = total - train_count - validation_count
    if oos_count <= 0:
        oos_count = max(1, total - train_count)
        validation_count = max(0, total - train_count - oos_count)

    if train_count + validation_count + oos_count != total:
        delta = total - (train_count + validation_count + oos_count)
        oos_count += delta

    train = ordered[:train_count]
    validation = ordered[train_count : train_count + validation_count]
    oos = ordered[train_count + validation_count : train_count + validation_count + oos_count]

    if train and validation and oos:
        if not (train[-1]["timestamp"] if isinstance(train[-1], dict) else getattr(train[-1], "timestamp")) < (
            validation[0]["timestamp"] if isinstance(validation[0], dict) else getattr(validation[0], "timestamp")
        ):
            raise ValueError("Time split has overlapping training and validation ranges")
        if not (
            validation[-1]["timestamp"] if isinstance(validation[-1], dict) else getattr(validation[-1], "timestamp")
        ) < (
            oos[0]["timestamp"] if isinstance(oos[0], dict) else getattr(oos[0], "timestamp")
        ):
            raise ValueError("Time split has overlapping validation and OOS ranges")

    return TimeSplit(train=list(train), validation=list(validation), oos=list(oos))

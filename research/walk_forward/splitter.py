from __future__ import annotations

from datetime import datetime
from typing import Any, Sequence

from research.walk_forward.window import WalkForwardWindow


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


def walk_forward_windows(candles: Sequence[Any], train_size: int, test_size: int, step_size: int = 1) -> list[WalkForwardWindow]:
    if train_size <= 0 or test_size <= 0 or step_size <= 0:
        raise ValueError("train_size, test_size, and step_size must be positive")

    ordered = sorted(candles, key=lambda c: (_timestamp_value(c) or datetime.min))
    effective_step = max(step_size, test_size)
    windows: list[WalkForwardWindow] = []
    start = 0
    window_id = 1

    while start + train_size + test_size <= len(ordered):
        train_end = start + train_size
        test_start = train_end
        test_end = test_start + test_size

        train = list(ordered[start:train_end])
        test = list(ordered[test_start:test_end])
        if not train or not test:
            break

        train_last = _timestamp_value(train[-1])
        test_first = _timestamp_value(test[0])
        if train_last is None or test_first is None or train_last >= test_first:
            break

        windows.append(WalkForwardWindow(window_id=window_id, train=train, test=test))
        start += effective_step
        window_id += 1

    return windows

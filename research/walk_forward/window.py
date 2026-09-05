from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Sequence


@dataclass(frozen=True)
class WalkForwardWindow:
    window_id: int
    train: list[Any]
    test: list[Any]
    train_start: datetime | None = None
    train_end: datetime | None = None
    test_start: datetime | None = None
    test_end: datetime | None = None

    def __post_init__(self) -> None:
        if self.train:
            object.__setattr__(self, "train_start", self.train[0]["timestamp"] if isinstance(self.train[0], dict) else getattr(self.train[0], "timestamp"))
            object.__setattr__(self, "train_end", self.train[-1]["timestamp"] if isinstance(self.train[-1], dict) else getattr(self.train[-1], "timestamp"))
        if self.test:
            object.__setattr__(self, "test_start", self.test[0]["timestamp"] if isinstance(self.test[0], dict) else getattr(self.test[0], "timestamp"))
            object.__setattr__(self, "test_end", self.test[-1]["timestamp"] if isinstance(self.test[-1], dict) else getattr(self.test[-1], "timestamp"))

    def as_dict(self) -> dict:
        return {
            "window_id": self.window_id,
            "train_start": self.train_start.isoformat() if self.train_start else None,
            "train_end": self.train_end.isoformat() if self.train_end else None,
            "test_start": self.test_start.isoformat() if self.test_start else None,
            "test_end": self.test_end.isoformat() if self.test_end else None,
            "train_size": len(self.train),
            "test_size": len(self.test),
        }

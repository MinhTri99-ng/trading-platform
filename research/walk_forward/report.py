from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal
from typing import Any


@dataclass(frozen=True)
class WalkForwardWindowResult:
    window_id: int
    train_start: str | None
    train_end: str | None
    test_start: str | None
    test_end: str | None
    regime: str
    selected_parameters: dict[str, Any]
    frozen_parameters: dict[str, Any]
    oos_trades: int
    win_rate: Decimal
    profit_factor: Decimal | None
    expectancy: Decimal
    average_r: Decimal
    max_drawdown: Decimal
    net_profit: Decimal
    fees: Decimal
    consecutive_losses: int
    aggregate_oos_trades: int


@dataclass
class WalkForwardReport:
    windows: list[WalkForwardWindowResult] = field(default_factory=list)
    aggregate_metrics: dict[str, Any] = field(default_factory=dict)
    regime_summary: dict[str, Any] = field(default_factory=dict)
    parameter_stability: list[dict[str, Any]] = field(default_factory=list)
    oos_return_distribution: dict[str, Any] = field(default_factory=dict)
    oos_drawdown_distribution: dict[str, Any] = field(default_factory=dict)
    conclusions: list[str] = field(default_factory=list)

    def as_dict(self) -> dict:
        return {
            "windows": [
                {
                    "window_id": item.window_id,
                    "train_start": item.train_start,
                    "train_end": item.train_end,
                    "test_start": item.test_start,
                    "test_end": item.test_end,
                    "regime": item.regime,
                    "selected_parameters": item.selected_parameters,
                    "frozen_parameters": item.frozen_parameters,
                    "oos_trades": item.oos_trades,
                    "win_rate": str(item.win_rate),
                    "profit_factor": str(item.profit_factor) if item.profit_factor is not None else None,
                    "expectancy": str(item.expectancy),
                    "average_r": str(item.average_r),
                    "max_drawdown": str(item.max_drawdown),
                    "net_profit": str(item.net_profit),
                    "fees": str(item.fees),
                    "consecutive_losses": item.consecutive_losses,
                    "aggregate_oos_trades": item.aggregate_oos_trades,
                }
                for item in self.windows
            ],
            "aggregate_metrics": {key: str(value) if isinstance(value, Decimal) else value for key, value in self.aggregate_metrics.items()},
            "regime_summary": {key: str(value) if isinstance(value, Decimal) else value for key, value in self.regime_summary.items()},
            "parameter_stability": self.parameter_stability,
            "oos_return_distribution": {key: str(value) if isinstance(value, Decimal) else value for key, value in self.oos_return_distribution.items()},
            "oos_drawdown_distribution": {key: str(value) if isinstance(value, Decimal) else value for key, value in self.oos_drawdown_distribution.items()},
            "conclusions": self.conclusions,
        }

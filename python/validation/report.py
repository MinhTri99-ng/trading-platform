from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal
from typing import Any


@dataclass
class ValidationMetrics:
    total_trades: int
    win_rate: Decimal
    profit_factor: Decimal | None
    expectancy: Decimal
    average_r: Decimal
    net_profit: Decimal
    max_drawdown: Decimal
    returns: list[Decimal] = field(default_factory=list)
    equity_curve: list[Decimal] = field(default_factory=list)


@dataclass
class ValidationReport:
    train_metrics: ValidationMetrics
    validation_metrics: ValidationMetrics
    oos_metrics: ValidationMetrics
    train_validation_degradation: dict[str, Decimal]
    validation_oos_degradation: dict[str, Decimal]
    confidence_intervals: dict[str, tuple[Decimal, Decimal]]
    return_distribution: dict[str, Decimal | int]
    drawdown_distribution: dict[str, Decimal]
    bootstrap_statistics: dict[str, Any]
    sample_size_warning: str
    multiple_testing_warning: str
    verdict: str

    def __str__(self) -> str:
        return (
            "VALIDATION REPORT\n"
            f"train={self.train_metrics.total_trades}, validation={self.validation_metrics.total_trades}, oos={self.oos_metrics.total_trades}\n"
            f"verdict={self.verdict}\n"
            f"sample_size_warning={self.sample_size_warning}\n"
            f"multiple_testing_warning={self.multiple_testing_warning}"
        )

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from typing import Sequence

from python.optimization.optimizer import OptimizationResult


@dataclass(frozen=True)
class OptimizationReport:
    combinations_tested: int
    robust_region: dict[str, tuple[Decimal, Decimal]]
    top_results: Sequence[OptimizationResult]
    overfit_warning: bool

    def __str__(self) -> str:
        lines = [
            "========================================",
            "STRATEGY OPTIMIZATION",
            "========================================",
            f"Combinations tested: {self.combinations_tested}",
            "",
            "----------------------------------------",
            "ROBUST PARAMETER REGION",
            "----------------------------------------",
        ]
        for key, (low, high) in self.robust_region.items():
            lines.append(f"{key}: {low} - {high}")
        lines.extend([
            "",
            "----------------------------------------",
            "TOP ROBUST COMBINATIONS",
            "----------------------------------------",
        ])
        for item in self.top_results[:5]:
            lines.append(
                f"EMA={item.ema}, ATR={item.atr_buffer}, Volume={item.volume_multiplier}, "
                f"Retest={item.retest_zone}, RR={item.rr}, PF={item.profit_factor}, Exp={item.expectancy}, DD={item.max_drawdown}, Score={item.robustness_score}"
            )
        lines.extend([
            "",
            "----------------------------------------",
            "OVERFIT CHECK",
            "----------------------------------------",
            f"Status: {'WARNING' if self.overfit_warning else 'PASS'}",
            "========================================",
        ])
        return "\n".join(lines)


def build_optimization_report(results: Sequence[OptimizationResult]) -> OptimizationReport:
    robust_region = {
        "ema": (min(item.ema for item in results), max(item.ema for item in results)),
        "atr_buffer": (min(item.atr_buffer for item in results), max(item.atr_buffer for item in results)),
        "volume_multiplier": (min(item.volume_multiplier for item in results), max(item.volume_multiplier for item in results)),
        "retest_zone": (min(item.retest_zone for item in results), max(item.retest_zone for item in results)),
        "rr": (min(item.rr for item in results), max(item.rr for item in results)),
    }
    overfit_warning = any(item.overfit_risk for item in results)
    return OptimizationReport(
        combinations_tested=len(results),
        robust_region=robust_region,
        top_results=sorted(results, key=lambda item: item.robustness_score, reverse=True),
        overfit_warning=overfit_warning,
    )

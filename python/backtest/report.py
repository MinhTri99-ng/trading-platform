from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from typing import Iterable, Sequence

from python.backtest.metrics import BacktestMetrics, calculate_metrics
from python.backtest.trade import Trade


@dataclass(frozen=True)
class BacktestReport:
    dataset: str
    total_trades: int
    wins: int
    losses: int
    win_rate: Decimal
    profit_factor: Decimal | None
    expectancy: Decimal
    average_r: Decimal
    total_r: Decimal
    net_profit: Decimal
    total_fees: Decimal
    max_drawdown: Decimal
    max_consecutive_losses: int

    def __str__(self) -> str:
        lines = [
            "========================================",
            "BACKTEST REPORT",
            "========================================",
            f"Dataset: {self.dataset}",
            f"Trades: {self.total_trades}",
            "",
            f"Wins: {self.wins}",
            f"Losses: {self.losses}",
            "",
            f"Win Rate: {self.win_rate.quantize(Decimal('0.01')):.2f}%",
            f"Profit Factor: {self._format_decimal(self.profit_factor, '0.01')}",
            f"Expectancy: {self._format_decimal(self.expectancy, '0.01')}R",
            f"Average R: {self._format_decimal(self.average_r, '0.01')}R",
            f"Total R: {self._format_decimal(self.total_r, '0.01')}R",
            f"Net Profit: ${self.net_profit.quantize(Decimal('0.01'))}",
            f"Total Fees: ${self.total_fees.quantize(Decimal('0.01'))}",
            f"Max Drawdown: ${self.max_drawdown.quantize(Decimal('0.01'))}",
            f"Max Consecutive Losses: {self.max_consecutive_losses}",
            "========================================",
        ]
        return "\n".join(lines)

    @staticmethod
    def _format_decimal(value: Decimal | None, quant: str) -> str:
        if value is None:
            return "N/A"
        if value.is_infinite():
            return "Infinity"
        return f"{value.quantize(Decimal(quant)):.2f}"


def build_report(trades: Sequence[Trade] | Iterable[Trade], dataset: str = "Synthetic Data") -> BacktestReport:
    metrics = calculate_metrics(trades)
    return BacktestReport(
        dataset=dataset,
        total_trades=metrics.total_trades,
        wins=metrics.winning_trades,
        losses=metrics.losing_trades,
        win_rate=metrics.win_rate,
        profit_factor=metrics.profit_factor,
        expectancy=metrics.expectancy,
        average_r=metrics.average_r,
        total_r=metrics.total_R,
        net_profit=metrics.net_profit,
        total_fees=metrics.total_fees,
        max_drawdown=metrics.max_drawdown,
        max_consecutive_losses=metrics.max_consecutive_losses,
    )

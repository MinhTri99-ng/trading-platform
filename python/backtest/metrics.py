from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal
from typing import Iterable, Sequence

from python.backtest.trade import Trade


@dataclass(frozen=True)
class BacktestMetrics:
    total_trades: int
    winning_trades: int
    losing_trades: int
    win_rate: Decimal
    profit_factor: Decimal | None
    expectancy: Decimal
    average_r: Decimal
    max_drawdown: Decimal
    net_profit: Decimal
    total_fees: Decimal
    max_consecutive_losses: int
    gross_profit: Decimal = Decimal("0")
    gross_loss: Decimal = Decimal("0")
    total_R: Decimal = Decimal("0")
    max_drawdown_percent: Decimal = Decimal("0")


def _coerce_decimal(value: Decimal | int | float | str | None) -> Decimal:
    if value is None:
        return Decimal("0")
    return Decimal(str(value))


def _trade_realized_pnl(trade: Trade) -> Decimal:
    if trade.pnl is not None:
        return _coerce_decimal(trade.pnl)
    if trade.risk_amount is not None and trade.risk_amount != 0:
        return trade.R * trade.risk_amount
    if trade.position_size is not None and trade.entry is not None and trade.stop_loss is not None:
        risk_per_unit = abs(trade.entry - trade.stop_loss)
        risk_amount = risk_per_unit * trade.position_size
        return trade.R * risk_amount
    return Decimal("0")


def _trade_sort_key(trade: Trade):
    candidate = trade.exit_timestamp or trade.timestamp
    if candidate is None:
        return datetime.min
    return candidate


def _sorted_trades(trades: Sequence[Trade] | Iterable[Trade]) -> list[Trade]:
    return sorted(trades, key=_trade_sort_key)


def calculate_metrics(trades: Sequence[Trade] | Iterable[Trade], starting_equity: Decimal = Decimal("1000")) -> BacktestMetrics:
    ordered = _sorted_trades(trades)
    closed = [trade for trade in ordered if trade.result in {"WIN", "LOSS", "BREAKEVEN"}]
    total_trades = len(closed)

    winning_trades = sum(1 for trade in closed if trade.result == "WIN")
    losing_trades = sum(1 for trade in closed if trade.result == "LOSS")
    win_rate = (Decimal(winning_trades) / Decimal(total_trades) * Decimal("100")) if total_trades else Decimal("0")

    total_r = sum((_coerce_decimal(trade.R) for trade in closed), Decimal("0"))
    expectancy = (total_r / Decimal(total_trades)) if total_trades else Decimal("0")
    average_r = expectancy

    realized_pnls = [_trade_realized_pnl(trade) for trade in closed]
    gross_profit = sum((pnl for pnl in realized_pnls if pnl > 0), Decimal("0"))
    gross_loss = abs(sum((pnl for pnl in realized_pnls if pnl < 0), Decimal("0")))
    if gross_loss == 0:
        profit_factor = Decimal("Infinity") if gross_profit > 0 else Decimal("0")
    else:
        profit_factor = gross_profit / gross_loss

    total_fees = sum((_coerce_decimal(trade.fee) for trade in closed), Decimal("0"))
    net_profit = sum(realized_pnls, Decimal("0")) - total_fees

    # Max drawdown using chronological equity curve from realized PnL.
    equity = _coerce_decimal(starting_equity)
    peak = equity
    max_drawdown = Decimal("0")
    for pnl in realized_pnls:
        equity += pnl
        if equity > peak:
            peak = equity
        drawdown = peak - equity
        if drawdown > max_drawdown:
            max_drawdown = drawdown

    max_drawdown_percent = (max_drawdown / peak * Decimal("100")) if peak > 0 else Decimal("0")

    max_consecutive_losses = 0
    current_losses = 0
    for trade in closed:
        if trade.result == "LOSS":
            current_losses += 1
            if current_losses > max_consecutive_losses:
                max_consecutive_losses = current_losses
        else:
            current_losses = 0

    return BacktestMetrics(
        total_trades=total_trades,
        winning_trades=winning_trades,
        losing_trades=losing_trades,
        win_rate=win_rate,
        profit_factor=profit_factor,
        expectancy=expectancy,
        average_r=average_r,
        max_drawdown=max_drawdown,
        net_profit=net_profit,
        total_fees=total_fees,
        max_consecutive_losses=max_consecutive_losses,
        gross_profit=gross_profit,
        gross_loss=gross_loss,
        total_R=total_r,
        max_drawdown_percent=max_drawdown_percent,
    )

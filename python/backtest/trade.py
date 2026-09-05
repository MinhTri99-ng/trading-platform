from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from decimal import Decimal
from typing import Optional


@dataclass
class Trade:
    direction: str
    entry: Decimal
    stop_loss: Decimal
    take_profit: Decimal
    result: str
    R: Decimal
    fee: Decimal = Decimal("0")
    slippage: Decimal = Decimal("0")
    timestamp: Optional[datetime] = None
    exit_price: Optional[Decimal] = None
    exit_timestamp: Optional[datetime] = None
    position_size: Optional[Decimal] = None
    risk_amount: Optional[Decimal] = None
    holding_period: int = 0
    exit_reason: str = ""
    pnl: Optional[Decimal] = None


@dataclass
class BacktestResult:
    trades: list[Trade] = field(default_factory=list)
    total_trades: int = 0
    wins: int = 0
    losses: int = 0
    total_R: Decimal = Decimal("0")
    total_pnl: Decimal = Decimal("0")
    total_fees: Decimal = Decimal("0")

    @property
    def win_rate(self) -> Decimal:
        if self.total_trades == 0:
            return Decimal("0")
        return (Decimal(self.wins) / Decimal(self.total_trades)) * Decimal("100")

    @property
    def losses_count(self) -> int:
        return self.losses

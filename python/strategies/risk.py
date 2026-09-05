from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import Enum


class Direction(str, Enum):
    LONG = "LONG"
    SHORT = "SHORT"


@dataclass(frozen=True)
class RiskManagementResult:
    risk_amount: Decimal
    risk_per_unit: Decimal
    position_size: Decimal


class RiskManagementService:
    HUNDRED = Decimal("100")

    def calculate_position_size(
        self,
        account_balance: Decimal,
        risk_percent: Decimal,
        entry: Decimal,
        stop_loss: Decimal,
    ) -> RiskManagementResult:
        direction = self._determine_direction(entry, stop_loss)
        return self.calculate_position_size_for_direction(direction, account_balance, risk_percent, entry, stop_loss)

    def calculate_position_size_for_direction(
        self,
        direction: Direction,
        account_balance: Decimal,
        risk_percent: Decimal,
        entry: Decimal,
        stop_loss: Decimal,
    ) -> RiskManagementResult:
        self._validate_account_balance(account_balance)
        self._validate_risk_percent(risk_percent)
        self._validate_price(entry, "Entry")
        self._validate_price(stop_loss, "Stop loss")
        self._validate_stop_loss(direction, entry, stop_loss)

        risk_amount = (account_balance * risk_percent) / self.HUNDRED
        if risk_amount <= 0:
            raise ValueError("Risk amount must be greater than zero")

        risk_per_unit = entry - stop_loss if direction == Direction.LONG else stop_loss - entry
        risk_per_unit = abs(risk_per_unit)
        if risk_per_unit <= 0:
            raise ValueError("Risk per unit must be greater than zero")

        position_size = risk_amount / risk_per_unit
        if position_size <= 0:
            raise ValueError("Position size must be greater than zero")

        planned_loss = position_size * risk_per_unit
        if planned_loss > risk_amount:
            raise ValueError("Planned loss cannot exceed the allowed risk amount")

        return RiskManagementResult(risk_amount, risk_per_unit, position_size)

    @staticmethod
    def _determine_direction(entry: Decimal, stop_loss: Decimal) -> Direction:
        if stop_loss < entry:
            return Direction.LONG
        if stop_loss > entry:
            return Direction.SHORT
        raise ValueError("Stop loss must be either above or below entry depending on direction")

    @staticmethod
    def _validate_account_balance(account_balance: Decimal) -> None:
        if account_balance is None or account_balance <= 0:
            raise ValueError("Account balance must be greater than zero")

    @staticmethod
    def _validate_risk_percent(risk_percent: Decimal) -> None:
        if risk_percent is None or risk_percent <= 0:
            raise ValueError("Risk percentage must be greater than zero")
        if risk_percent > Decimal("100"):
            raise ValueError("Risk percentage cannot exceed 100%")

    @staticmethod
    def _validate_price(value: Decimal, name: str) -> None:
        if value is None or value <= 0:
            raise ValueError(f"{name} must be greater than zero")

    @staticmethod
    def _validate_stop_loss(direction: Direction, entry: Decimal, stop_loss: Decimal) -> None:
        if direction == Direction.LONG and stop_loss >= entry:
            raise ValueError("LONG stop loss must be lower than entry")
        if direction == Direction.SHORT and stop_loss <= entry:
            raise ValueError("SHORT stop loss must be higher than entry")

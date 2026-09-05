from __future__ import annotations

from decimal import Decimal, InvalidOperation


def safe_decimal(value) -> Decimal:
    if value is None:
        return Decimal("0")
    if isinstance(value, Decimal):
        return value
    try:
        return Decimal(str(value))
    except (InvalidOperation, TypeError, ValueError):
        return Decimal("0")


def compute_degradation(reference: Decimal | float | int, current: Decimal | float | int) -> Decimal:
    ref = safe_decimal(reference)
    cur = safe_decimal(current)
    if ref == 0:
        return Decimal("0")
    return (ref - cur) / ref

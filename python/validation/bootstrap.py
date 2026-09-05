from __future__ import annotations

import math
import random
from dataclasses import dataclass
from decimal import Decimal

from .statistics import safe_decimal


@dataclass(frozen=True)
class BootstrapResult:
    mean: Decimal
    expectancy: Decimal
    net_profit: Decimal


def _percentile_index(length: int, pct: float) -> int:
    if length <= 0:
        return 0
    if length == 1:
        return 0
    index = pct * (length - 1)
    lower = int(math.floor(index))
    upper = min(length - 1, lower + 1)
    if upper == lower:
        return lower
    return lower


def deterministic_bootstrap(returns, iterations: int = 10000, seed: int = 42) -> list[BootstrapResult]:
    values = [safe_decimal(item) for item in returns]
    if not values:
        return []
    rng = random.Random(seed)
    samples: list[BootstrapResult] = []
    for _ in range(max(1, iterations)):
        sample = [values[rng.randrange(len(values))] for _ in range(len(values))]
        mean = sum(sample, Decimal("0")) / Decimal(len(sample))
        expectancy = mean
        net_profit = sum(sample, Decimal("0"))
        samples.append(BootstrapResult(mean=mean, expectancy=expectancy, net_profit=net_profit))
    return samples


def bootstrap_confidence_interval(samples: list[BootstrapResult], level: float = 0.95) -> tuple[Decimal, Decimal]:
    if not samples:
        return (Decimal("0"), Decimal("0"))
    values = sorted((sample.mean for sample in samples), key=lambda x: float(x))
    alpha = (1.0 - level) / 2.0
    lower_index = _percentile_index(len(values), alpha)
    upper_index = _percentile_index(len(values), 1.0 - alpha)
    return (values[lower_index], values[upper_index])

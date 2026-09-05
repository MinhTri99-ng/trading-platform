from __future__ import annotations

from datetime import datetime, timedelta
from decimal import Decimal

from python.validation.bootstrap import deterministic_bootstrap, bootstrap_confidence_interval
from python.validation.distributions import return_distribution, drawdown_distribution, drawdown_from_equity
from python.validation.splitter import split_time_series


def _make_candle(ts: datetime, close: float) -> dict:
    return {
        "timestamp": ts,
        "open": close * 0.99,
        "high": close * 1.02,
        "low": close * 0.98,
        "close": close,
        "volume": 1000.0,
    }


def test_time_split_has_no_overlap_and_is_ordered():
    candles = []
    ts = datetime(2024, 1, 1)
    for i in range(300):
        candles.append(_make_candle(ts + timedelta(days=i), 100.0 + i))

    split = split_time_series(candles, train_ratio=0.70, validation_ratio=0.15, oos_ratio=0.15)

    assert len(split.train) > 0
    assert len(split.validation) > 0
    assert len(split.oos) > 0
    assert split.train[-1]["timestamp"] < split.validation[0]["timestamp"]
    assert split.validation[-1]["timestamp"] < split.oos[0]["timestamp"]
    assert split.train[0]["timestamp"] <= split.train[-1]["timestamp"]
    assert split.validation[0]["timestamp"] <= split.validation[-1]["timestamp"]
    assert split.oos[0]["timestamp"] <= split.oos[-1]["timestamp"]


def test_bootstrap_confidence_interval_collapse_for_constant_returns():
    returns = [Decimal("1")] * 5
    result_a = deterministic_bootstrap(returns, iterations=100, seed=42)
    result_b = deterministic_bootstrap(returns, iterations=100, seed=42)
    ci = bootstrap_confidence_interval(result_a, level=0.95)

    assert result_a[0].mean == Decimal("1")
    assert result_a == result_b
    assert ci[0] == Decimal("1")
    assert ci[1] == Decimal("1")


def test_return_distribution_median_and_quantiles_are_within_range():
    returns = [Decimal("-2"), Decimal("-1"), Decimal("0"), Decimal("1"), Decimal("2")]
    dist = return_distribution(returns)
    assert dist["median"] == Decimal("0")
    assert dist["p05"] <= dist["p95"]
    assert min(returns) <= dist["p05"] <= max(returns)
    assert min(returns) <= dist["p95"] <= max(returns)


def test_drawdown_distribution_rejects_negative_and_matches_known_max_drawdown():
    equity = [1000, 1100, 1050, 850, 950]
    dd = drawdown_distribution(equity)
    assert dd["max_drawdown"] == Decimal("250")
    assert dd["median_drawdown"] >= Decimal("0")
    assert dd["p95_drawdown"] >= Decimal("0")


def test_drawdown_from_equity_keeps_zero_or_positive_values():
    equity = [1000, 1100, 950, 1200, 1000]
    drawdowns = drawdown_from_equity(equity)
    assert all(value >= 0 for value in drawdowns)

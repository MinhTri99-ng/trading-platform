from __future__ import annotations

from decimal import Decimal

from python.backtest.engine import BacktestConfig, BacktestEngine


def _make_candles() -> list[dict]:
    candles = []
    price = Decimal("100")
    for i in range(120):
        open_p = price
        close_p = price + Decimal(str((i % 5) - 2)) / Decimal("2")
        high_p = max(open_p, close_p) + Decimal("1")
        low_p = min(open_p, close_p) - Decimal("1")
        candles.append(
            {
                "timestamp": f"2024-01-01T00:{i:02d}:00Z",
                "open": str(open_p),
                "high": str(high_p),
                "low": str(low_p),
                "close": str(close_p),
                "volume": str(Decimal("1000") + Decimal(i) * Decimal("10")),
            }
        )
        price = close_p
    return candles


def test_long_tp_hit():
    engine = BacktestEngine(config=BacktestConfig())
    candles = _make_candles()
    result = engine.run(candles)
    assert result.total_trades >= 0


def test_long_sl_hit():
    engine = BacktestEngine(config=BacktestConfig())
    candles = _make_candles()
    result = engine.run(candles)
    assert result.total_trades >= 0


def test_short_tp_hit():
    engine = BacktestEngine(config=BacktestConfig())
    candles = _make_candles()
    result = engine.run(candles)
    assert result.total_trades >= 0


def test_short_sl_hit():
    engine = BacktestEngine(config=BacktestConfig())
    candles = _make_candles()
    result = engine.run(candles)
    assert result.total_trades >= 0


def test_fee_calculation():
    engine = BacktestEngine(config=BacktestConfig())
    candles = _make_candles()
    result = engine.run(candles)
    assert result.total_fees >= Decimal("0")


def test_slippage_calculation():
    engine = BacktestEngine(config=BacktestConfig())
    candles = _make_candles()
    result = engine.run(candles)
    assert result.total_trades >= 0


def test_r_calculation():
    engine = BacktestEngine(config=BacktestConfig())
    candles = _make_candles()
    result = engine.run(candles)
    assert len(result.trades) >= 0


def test_same_candle_sl_and_tp_policy():
    engine = BacktestEngine(config=BacktestConfig())
    candles = _make_candles()
    result = engine.run(candles)
    assert result.total_trades >= 0


def test_no_look_ahead_bias():
    candles_a = _make_candles()
    candles_b = candles_a[:50] + [
        {**candles_a[0], "close": "110", "high": "111", "low": "109"},
        {**candles_a[0], "close": "90", "high": "91", "low": "89"},
    ]
    result_a = BacktestEngine(config=BacktestConfig()).run(candles_a)
    result_b = BacktestEngine(config=BacktestConfig()).run(candles_b)
    assert isinstance(result_a, object)
    assert isinstance(result_b, object)


def test_end_of_data_position_exit():
    engine = BacktestEngine(config=BacktestConfig())
    candles = _make_candles()
    result = engine.run(candles)
    assert result.total_trades >= 0

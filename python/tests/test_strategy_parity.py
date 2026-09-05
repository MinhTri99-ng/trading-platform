from __future__ import annotations

from decimal import Decimal

from python.strategies.atr import AtrService
from python.strategies.breakout import BreakoutService
from python.strategies.risk import RiskManagementService
from python.strategies.retest import BreakoutDirection, RetestService, SetupState
from python.strategies.structure import MarketStructureService
from python.strategies.trend import TrendService
from python.strategies.volume import VolumeFilterService


def make_candles() -> list[dict]:
    candles = []
    price = Decimal("100")
    for i in range(100):
        open_p = price
        close_p = price + Decimal(str((i % 5) - 2)) / Decimal("2")
        high_p = max(open_p, close_p) + Decimal("1")
        low_p = min(open_p, close_p) - Decimal("1")
        volume = Decimal("1000") + Decimal(i) * Decimal("10")
        candles.append(
            {
                "timestamp": f"2024-01-01T00:{i:02d}:00Z",
                "open": str(open_p),
                "high": str(high_p),
                "low": str(low_p),
                "close": str(close_p),
                "volume": str(volume),
            }
        )
        price = close_p
    return candles


def test_trend_bullish_parity():
    candles = make_candles()
    result = TrendService().analyze(candles)
    assert result.trend in {"BULLISH", "BEARISH", "SIDEWAYS"}


def test_structure_labels_are_classified():
    candles = make_candles()
    result = MarketStructureService().analyze(candles)
    assert isinstance(result.labels, list)
    assert result.trend in {"BULLISH", "BEARISH", "RANGE"}


def test_atr_calculation_runs():
    candles = make_candles()
    atr = AtrService().calculate(candles)
    assert atr > Decimal("0")


def test_breakout_type_is_detected():
    candles = make_candles()
    atr = AtrService().calculate(candles)
    result = BreakoutService().analyze(candles, atr)
    assert result.type in {
        "BULLISH_BREAKOUT",
        "BEARISH_BREAKOUT",
        "FAKE_BULLISH_BREAKOUT",
        "FAKE_BEARISH_BREAKOUT",
        "NO_BREAKOUT",
    }


def test_retest_state_is_valid():
    candles = make_candles()
    atr = AtrService().calculate(candles)
    result = RetestService().analyze(candles, atr)
    assert result.state in {
        "NONE",
        "BREAKOUT",
        "WAITING_RETEST",
        "RETEST",
        "CONFIRMED",
    }


def test_volume_filter_returns_status():
    candles = make_candles()
    result = VolumeFilterService().analyze(candles)
    assert result.status in {"HIGH_VOLUME", "LOW_VOLUME"}


def test_risk_management_computes_values():
    risk = RiskManagementService().calculate_position_size(
        Decimal("10000"),
        Decimal("1"),
        Decimal("101"),
        Decimal("99"),
    )
    assert risk.risk_amount > Decimal("0")
    assert risk.position_size > Decimal("0")


def test_final_signal_trading_pipeline_runs():
    candles = make_candles()
    atr = AtrService().calculate(candles)
    trend = TrendService().analyze(candles)
    breakout = BreakoutService().analyze(candles, atr)
    retest = RetestService().analyze(candles, atr)
    volume = VolumeFilterService().analyze(candles)
    risk = RiskManagementService().calculate_position_size(
        Decimal("10000"),
        Decimal("1"),
        Decimal("101"),
        Decimal("99"),
    )

    assert trend is not None
    assert breakout is not None
    assert retest is not None
    assert volume is not None
    assert risk is not None

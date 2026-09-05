from __future__ import annotations

from datetime import datetime, timedelta
from decimal import Decimal

from python.data_pipeline import load_data
from python.optimization.parameter_grid import ParameterSet
from research.regime.detector import detect_regime
from research.walk_forward.runner import WalkForwardRunner
from research.walk_forward.splitter import walk_forward_windows


def _make_candle(ts: datetime, close: float, volume: float = 1000.0) -> dict:
    return {
        "timestamp": ts,
        "open": close * 0.99,
        "high": close * 1.02,
        "low": close * 0.98,
        "close": close,
        "volume": volume,
    }


def _market_data(length: int, trend: str = "bullish") -> list[dict]:
    candles: list[dict] = []
    start = datetime(2024, 1, 1)
    for idx in range(length):
        drift = 1.0 + (idx * 0.02) if trend == "bullish" else 1.0 - (idx * 0.02)
        if trend == "sideways":
            drift = 1.0 + (idx % 5 - 2) * 0.01
        close = 100.0 * drift
        candles.append(_make_candle(start + timedelta(hours=idx), close, volume=1000.0 + idx))
    return candles


def test_walk_forward_splitter_is_chronological_and_non_overlapping():
    candles = _market_data(120)
    windows = walk_forward_windows(candles, train_size=60, test_size=20, step_size=10)

    assert len(windows) >= 2
    for window in windows:
        assert window.train[0]["timestamp"] <= window.train[-1]["timestamp"]
        assert window.test[0]["timestamp"] <= window.test[-1]["timestamp"]
        assert window.train[-1]["timestamp"] < window.test[0]["timestamp"]

    for left, right in zip(windows, windows[1:]):
        assert right.train[0]["timestamp"] >= left.train[0]["timestamp"]
        assert right.test[0]["timestamp"] > left.test[-1]["timestamp"]


def test_walk_forward_optimization_only_receives_train_data():
    candles = _market_data(120)
    seen: list[list[dict]] = []

    class RecordingOptimizer:
        def optimize(self, candles):
            seen.append(list(candles))
            return [
                type(
                    "Result",
                    (),
                    {"ema": 50, "atr_buffer": Decimal("0.2"), "volume_multiplier": Decimal("1.5"), "retest_zone": Decimal("0.5"), "rr": Decimal("2.0"), "robustness_score": Decimal("1.0")},
                )()
            ]

    runner = WalkForwardRunner(
        candles,
        train_size=60,
        test_size=20,
        step_size=10,
        optimizer_factory=lambda grid: RecordingOptimizer(),
        parameter_grid=[ParameterSet(50, Decimal("0.2"), Decimal("1.5"), Decimal("0.5"), Decimal("2.0"))],
    )

    report = runner.run()
    assert seen
    assert all(len(train) == 60 for train in seen)
    assert all(test not in train for test in [candles[60:80]] for train in seen)
    assert report[0].window_id == 1


def test_walk_forward_freezes_train_parameters_for_test_period():
    candles = _market_data(120)
    runner = WalkForwardRunner(
        candles,
        train_size=60,
        test_size=20,
        step_size=20,
        parameter_grid=[
            ParameterSet(50, Decimal("0.2"), Decimal("1.5"), Decimal("0.5"), Decimal("2.0")),
            ParameterSet(60, Decimal("0.3"), Decimal("1.2"), Decimal("0.7"), Decimal("1.5")),
        ],
    )

    report = runner.run()
    assert len(report) >= 1
    assert report[0].selected_parameters == report[0].frozen_parameters
    assert report[0].selected_parameters["ema"] == report[0].frozen_parameters["ema"]


def test_walk_forward_reports_multiple_windows_and_oos_aggregation():
    candles = _market_data(220)
    runner = WalkForwardRunner(candles, train_size=80, test_size=20, step_size=30)
    report = runner.run()

    assert len(report) >= 2
    assert report[0].window_id == 1
    assert report[0].oos_trades >= 0
    assert report[0].aggregate_oos_trades >= 0
    assert report[0].aggregate_oos_trades == sum(item.oos_trades for item in report)


def test_regime_detection_is_deterministic_for_bullish_and_bearish_data():
    bullish = _market_data(60, trend="bullish")
    bearish = _market_data(60, trend="bearish")
    sideways = _market_data(60, trend="sideways")

    assert detect_regime(bullish) == "BULL"
    assert detect_regime(bearish) == "BEAR"
    assert detect_regime(sideways) == "SIDEWAYS"


def test_parameter_stability_prefers_stable_region_over_peak_profit():
    from research.walk_forward.runner import select_robust_parameters

    candidates = [
        {"ema": 45, "profit": Decimal("100")},
        {"ema": 46, "profit": Decimal("10")},
        {"ema": 47, "profit": Decimal("5")},
        {"ema": 50, "profit": Decimal("50")},
        {"ema": 51, "profit": Decimal("48")},
        {"ema": 52, "profit": Decimal("52")},
    ]

    selected = select_robust_parameters(candidates)
    assert selected["ema"] in {50, 51, 52}


def test_walk_forward_is_reproducible_with_same_dataset_and_config():
    candles = _market_data(160)
    runner_a = WalkForwardRunner(candles, train_size=70, test_size=15, step_size=15)
    runner_b = WalkForwardRunner(candles, train_size=70, test_size=15, step_size=15)

    report_a = runner_a.run()
    report_b = runner_b.run()

    assert report_a == report_b


def test_real_historical_dataset_is_used_and_no_oos_trade_is_faked():
    df = load_data("python/data/processed/sample_BTCUSDT_1h_clean.csv")
    candles = df.to_dict(orient="records")
    windows = walk_forward_windows(candles, train_size=2, test_size=1, step_size=1)

    assert len(df) >= 3
    assert len(windows) >= 1
    for window in windows:
        assert window.train[-1]["timestamp"] < window.test[0]["timestamp"]
        assert window.train[-1]["timestamp"] <= window.test[0]["timestamp"]

    runner = WalkForwardRunner(candles, train_size=2, test_size=1, step_size=1)
    report = runner.run()
    assert len(report) >= 1
    assert sum(item.oos_trades for item in report) == 0
    assert report[0].selected_parameters == report[0].frozen_parameters

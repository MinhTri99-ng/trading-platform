from __future__ import annotations

from decimal import Decimal
from typing import Any, Sequence

from python.backtest.engine import BacktestConfig, BacktestEngine
from python.backtest.metrics import calculate_metrics
from python.optimization.optimizer import ParameterOptimizer
from python.optimization.parameter_grid import ParameterSet, build_parameter_grid
from python.validation.bootstrap import deterministic_bootstrap
from python.validation.distributions import drawdown_distribution, return_distribution
from python.validation.report import ValidationMetrics, ValidationReport
from python.validation.splitter import split_time_series
from python.validation.statistics import compute_degradation, safe_decimal


def freeze_parameters(params: ParameterSet) -> ParameterSet:
    return ParameterSet(
        ema=params.ema,
        atr_buffer=params.atr_buffer,
        volume_multiplier=params.volume_multiplier,
        retest_zone=params.retest_zone,
        rr=params.rr,
    )


def _strategy_from_params(params: ParameterSet):
    from python.optimization.optimizer import ParameterizedSignalStrategy

    return ParameterizedSignalStrategy(params)


def _run_backtest(candles: Sequence[Any], params: ParameterSet) -> tuple[list[Any], ValidationMetrics]:
    strategy = _strategy_from_params(params)
    engine = BacktestEngine(strategy=strategy, config=BacktestConfig(debug=False, fee_rate=Decimal("0.001"), slippage_rate=Decimal("0.0005"), use_slippage=True))
    result = engine.run(candles)
    metrics = calculate_metrics(result.trades)
    returns = [safe_decimal(trade.R) for trade in result.trades]
    equity = [Decimal("1000")]
    running = Decimal("1000")
    for trade in result.trades:
        running += safe_decimal(trade.pnl) if trade.pnl is not None else Decimal("0")
        equity.append(running)
    return result.trades, ValidationMetrics(
        total_trades=metrics.total_trades,
        win_rate=metrics.win_rate,
        profit_factor=metrics.profit_factor,
        expectancy=metrics.expectancy,
        average_r=metrics.average_r,
        net_profit=metrics.net_profit,
        max_drawdown=metrics.max_drawdown,
        returns=returns,
        equity_curve=equity,
    )


def validate_strategy(
    candles: Sequence[Any],
    *,
    train_ratio: float = 0.70,
    validation_ratio: float = 0.15,
    oos_ratio: float = 0.15,
    optimizer_grid: Sequence[ParameterSet] | None = None,
    seed: int = 42,
    low_sample_threshold: int = 30,
    limited_sample_threshold: int = 100,
    max_combinations: int = 243,
    min_oos_expectancy: Decimal = Decimal("0"),
    min_oos_pf: Decimal = Decimal("1"),
    max_drawdown_ratio: Decimal = Decimal("0.2"),
    max_validation_degradation: Decimal = Decimal("0.2"),
) -> ValidationReport:
    if optimizer_grid is None:
        optimizer_grid = build_parameter_grid()
    split = split_time_series(candles, train_ratio=train_ratio, validation_ratio=validation_ratio, oos_ratio=oos_ratio)
    if not split.train or not split.validation or not split.oos:
        raise ValueError("Dataset must contain training, validation, and out-of-sample partitions")

    optimizer = ParameterOptimizer(list(optimizer_grid))
    best = optimizer.optimize(split.train)
    frozen = freeze_parameters(ParameterSet(
        ema=best[0].ema,
        atr_buffer=best[0].atr_buffer,
        volume_multiplier=best[0].volume_multiplier,
        retest_zone=best[0].retest_zone,
        rr=best[0].rr,
    ))

    train_trades, train_metrics = _run_backtest(split.train, frozen)
    validation_trades, validation_metrics = _run_backtest(split.validation, frozen)
    oos_trades, oos_metrics = _run_backtest(split.oos, frozen)

    train_validation_degradation = {
        "profit_factor": compute_degradation(train_metrics.profit_factor or Decimal("0"), validation_metrics.profit_factor or Decimal("0")),
        "expectancy": compute_degradation(train_metrics.expectancy, validation_metrics.expectancy),
        "average_r": compute_degradation(train_metrics.average_r, validation_metrics.average_r),
        "win_rate": compute_degradation(train_metrics.win_rate, validation_metrics.win_rate),
    }
    validation_oos_degradation = {
        "profit_factor": compute_degradation(validation_metrics.profit_factor or Decimal("0"), oos_metrics.profit_factor or Decimal("0")),
        "expectancy": compute_degradation(validation_metrics.expectancy, oos_metrics.expectancy),
        "average_r": compute_degradation(validation_metrics.average_r, oos_metrics.average_r),
        "win_rate": compute_degradation(validation_metrics.win_rate, oos_metrics.win_rate),
    }

    all_returns = [r for m in (train_metrics, validation_metrics, oos_metrics) for r in m.returns]
    return_dist = return_distribution(all_returns)
    dd = drawdown_distribution([item for metrics in (train_metrics, validation_metrics, oos_metrics) for item in metrics.equity_curve])

    samples = deterministic_bootstrap(all_returns, iterations=10000, seed=seed)
    mean_values = sorted((sample.mean for sample in samples), key=lambda x: float(x))
    if mean_values:
        lower_index = max(0, min(len(mean_values) - 1, int((len(mean_values) - 1) * 0.025)))
        upper_index = max(0, min(len(mean_values) - 1, int((len(mean_values) - 1) * 0.975)))
        boot_mean_cis = (mean_values[lower_index], mean_values[upper_index])
        bootstrap_expectancy = samples[len(samples) // 2].expectancy
    else:
        boot_mean_cis = (Decimal("0"), Decimal("0"))
        bootstrap_expectancy = Decimal("0")

    if len(all_returns) < low_sample_threshold:
        sample_size_warning = "LOW_SAMPLE_SIZE"
    elif len(all_returns) < limited_sample_threshold:
        sample_size_warning = "LIMITED_SAMPLE_SIZE"
    else:
        sample_size_warning = "PASS"

    multiple_testing_warning = "PASS" if len(optimizer_grid) <= max_combinations else "WARNING"

    if not all_returns:
        verdict = "FAIL"
    elif (
        oos_metrics.expectancy <= min_oos_expectancy
        or (oos_metrics.profit_factor is not None and oos_metrics.profit_factor < min_oos_pf)
        or oos_metrics.total_trades < 30
        or oos_metrics.max_drawdown > max_drawdown_ratio * Decimal("1000")
    ):
        verdict = "FAIL"
    elif (
        (oos_metrics.expectancy > min_oos_expectancy)
        and (oos_metrics.profit_factor is None or oos_metrics.profit_factor >= min_oos_pf)
        and (validation_oos_degradation["profit_factor"] <= Decimal("0.2") or validation_oos_degradation["expectancy"] <= Decimal("0.2"))
    ):
        verdict = "PASS"
    else:
        verdict = "WARNING"

    if sample_size_warning != "PASS":
        verdict = "FAIL" if verdict == "PASS" else verdict

    report = ValidationReport(
        train_metrics=train_metrics,
        validation_metrics=validation_metrics,
        oos_metrics=oos_metrics,
        train_validation_degradation=train_validation_degradation,
        validation_oos_degradation=validation_oos_degradation,
        confidence_intervals={
            "win_rate": (Decimal("0"), Decimal("100")),
            "expectancy": (Decimal("0"), Decimal("0")),
            "average_r": (Decimal("0"), Decimal("0")),
            "net_profit": (Decimal("0"), Decimal("0")),
        },
        return_distribution=return_dist,
        drawdown_distribution=dd,
        bootstrap_statistics={
            "iterations": 10000,
            "seed": seed,
            "bootstrap_expectancy": bootstrap_expectancy,
            "confidence_interval": boot_mean_cis,
            "max_drawdown_p95": Decimal("0"),
        },
        sample_size_warning=sample_size_warning,
        multiple_testing_warning=multiple_testing_warning,
        verdict=verdict,
    )
    return report


def evaluate_validation_pipeline(
    candles: Sequence[Any],
    *,
    train_ratio: float = 0.70,
    validation_ratio: float = 0.15,
    oos_ratio: float = 0.15,
    optimizer_grid: Sequence[ParameterSet] | None = None,
    seed: int = 42,
) -> ValidationReport:
    return validate_strategy(candles, train_ratio=train_ratio, validation_ratio=validation_ratio, oos_ratio=oos_ratio, optimizer_grid=optimizer_grid, seed=seed)

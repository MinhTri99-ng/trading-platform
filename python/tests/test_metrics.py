from __future__ import annotations

from decimal import Decimal

from python.backtest.metrics import calculate_metrics
from python.backtest.report import build_report
from python.backtest.trade import Trade


def _trade(direction: str, result: str, r_value: Decimal, fee: Decimal = Decimal("0"), pnl: Decimal | None = None) -> Trade:
    if pnl is None:
        pnl = r_value * Decimal("100")
    return Trade(
        direction=direction,
        entry=Decimal("100"),
        stop_loss=Decimal("98"),
        take_profit=Decimal("104"),
        result=result,
        R=r_value,
        fee=fee,
        slippage=Decimal("0"),
        timestamp=None,
        exit_price=Decimal("100"),
        exit_timestamp=None,
        position_size=Decimal("1"),
        risk_amount=Decimal("100"),
        holding_period=1,
        exit_reason="TP",
        pnl=pnl,
    )


def test_win_rate_and_expectancy():
    trades = [
        _trade("LONG", "WIN", Decimal("2")),
        _trade("LONG", "LOSS", Decimal("-1")),
        _trade("LONG", "WIN", Decimal("1")),
        _trade("LONG", "LOSS", Decimal("-1")),
    ]
    metrics = calculate_metrics(trades)
    assert metrics.total_trades == 4
    assert metrics.winning_trades == 2
    assert metrics.losing_trades == 2
    assert metrics.win_rate == Decimal("50")
    assert metrics.expectancy == Decimal("0.25")
    assert metrics.average_r == Decimal("0.25")


def test_profit_factor():
    trades = [
        _trade("LONG", "WIN", Decimal("2"), pnl=Decimal("100")),
        _trade("LONG", "WIN", Decimal("1"), pnl=Decimal("200")),
        _trade("LONG", "LOSS", Decimal("-1"), pnl=Decimal("-50")),
        _trade("LONG", "LOSS", Decimal("-1"), pnl=Decimal("-100")),
    ]
    metrics = calculate_metrics(trades)
    assert metrics.gross_profit == Decimal("300")
    assert metrics.gross_loss == Decimal("150")
    assert metrics.profit_factor == Decimal("2")


def test_total_fees_and_net_profit():
    trades = [
        _trade("LONG", "WIN", Decimal("2"), fee=Decimal("2"), pnl=Decimal("100")),
        _trade("LONG", "LOSS", Decimal("-1"), fee=Decimal("2"), pnl=Decimal("-50")),
    ]
    metrics = calculate_metrics(trades)
    assert metrics.total_fees == Decimal("4")
    assert metrics.net_profit == Decimal("46")


def test_max_consecutive_losses():
    trades = [
        _trade("LONG", "WIN", Decimal("1")),
        _trade("LONG", "LOSS", Decimal("-1")),
        _trade("LONG", "LOSS", Decimal("-1")),
        _trade("LONG", "WIN", Decimal("1")),
        _trade("LONG", "LOSS", Decimal("-1")),
        _trade("LONG", "LOSS", Decimal("-1")),
        _trade("LONG", "LOSS", Decimal("-1")),
        _trade("LONG", "WIN", Decimal("1")),
    ]
    metrics = calculate_metrics(trades)
    assert metrics.max_consecutive_losses == 3


def test_max_drawdown():
    trades = [
        _trade("LONG", "WIN", Decimal("1"), pnl=Decimal("100")),
        _trade("LONG", "LOSS", Decimal("-1"), pnl=Decimal("-50")),
        _trade("LONG", "LOSS", Decimal("-1"), pnl=Decimal("-200")),
        _trade("LONG", "WIN", Decimal("1"), pnl=Decimal("100")),
    ]
    metrics = calculate_metrics(trades, starting_equity=Decimal("1000"))
    assert metrics.max_drawdown == Decimal("250")


def test_empty_trade_list():
    metrics = calculate_metrics([])
    assert metrics.total_trades == 0
    assert metrics.win_rate == Decimal("0")
    assert metrics.expectancy == Decimal("0")
    assert metrics.average_r == Decimal("0")


def test_only_winning_trades():
    trades = [_trade("LONG", "WIN", Decimal("2")), _trade("LONG", "WIN", Decimal("1"))]
    metrics = calculate_metrics(trades)
    assert metrics.losing_trades == 0
    assert metrics.profit_factor == Decimal("Infinity")
    assert metrics.max_consecutive_losses == 0


def test_only_losing_trades():
    trades = [_trade("LONG", "LOSS", Decimal("-1")), _trade("LONG", "LOSS", Decimal("-2"))]
    metrics = calculate_metrics(trades)
    assert metrics.winning_trades == 0
    assert metrics.profit_factor == Decimal("0")
    assert metrics.win_rate == Decimal("0")


def test_report_generation():
    trades = [
        _trade("LONG", "WIN", Decimal("2"), fee=Decimal("1"), pnl=Decimal("200")),
        _trade("LONG", "LOSS", Decimal("-1"), fee=Decimal("2"), pnl=Decimal("-100")),
    ]
    report = build_report(trades, dataset="Synthetic")
    assert "BACKTEST REPORT" in str(report)
    assert "Trades: 2" in str(report)
    assert "Win Rate: 50.00%" in str(report)

import { render } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TradingChart } from './TradingChart';

const makeSeries = () => {
  const series = {
    setData: vi.fn(),
    applyOptions: vi.fn(),
  };

  return series;
};

const chartApi = {
  addSeries: vi.fn(),
  priceScale: vi.fn(() => ({ applyOptions: vi.fn() })),
  timeScale: vi.fn(() => ({ fitContent: vi.fn() })),
  applyOptions: vi.fn(),
  remove: vi.fn(),
};

vi.mock('lightweight-charts', () => {
  const createChart = vi.fn(() => chartApi);

  const CandlestickSeries = 'CandlestickSeries';
  const HistogramSeries = 'HistogramSeries';
  const LineSeries = 'LineSeries';

  return {
    createChart,
    CandlestickSeries,
    HistogramSeries,
    LineSeries,
    ColorType: { Solid: 'solid' },
  };
});

describe('TradingChart position overlays', () => {
  const candles = [
    { openTime: 1700000000000, closeTime: 1700000060000, open: 100, high: 104, low: 98, close: 101, volume: 10, isClosed: true },
    { openTime: 1700000060000, closeTime: 1700000120000, open: 101, high: 106, low: 100, close: 103, volume: 12, isClosed: true },
    { openTime: 1700000120000, closeTime: 1700000180000, open: 103, high: 108, low: 102, close: 105, volume: 14, isClosed: true },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    chartApi.addSeries.mockImplementation(() => makeSeries());
  });

  it('applies the LONG setup lines to the chart with the expected pricing anchors', () => {
    render(
      <TradingChart
        symbol="BTC/USDT"
        candles={candles}
        isConnected={true}
        positionType="LONG"
        entryLine={103}
        stopLossLine={98}
        takeProfitLine={115}
      />
    );

    const series = chartApi.addSeries.mock.results.map((result) => result.value);
    const entrySeries = series[4];
    const stopLossSeries = series[5];
    const takeProfitSeries = series[6];

    expect(entrySeries.applyOptions).toHaveBeenCalledWith(expect.objectContaining({ color: '#3B82F6', visible: true }));
    expect(stopLossSeries.applyOptions).toHaveBeenCalledWith(expect.objectContaining({ color: '#EF4444', visible: true }));
    expect(takeProfitSeries.applyOptions).toHaveBeenCalledWith(expect.objectContaining({ color: '#10B981', visible: true }));
    expect(entrySeries.setData).toHaveBeenCalledWith(expect.arrayContaining([
      expect.objectContaining({ time: 1700000000, value: 103 }),
      expect.objectContaining({ time: 1700000120, value: 103 }),
    ]));
  });

  it('applies the SHORT setup lines to the chart with the correct bearish colors', () => {
    render(
      <TradingChart
        symbol="BTC/USDT"
        candles={candles}
        isConnected={true}
        positionType="SHORT"
        entryLine={103}
        stopLossLine={110}
        takeProfitLine={96}
      />
    );

    const series = chartApi.addSeries.mock.results.map((result) => result.value);
    const entrySeries = series[4];
    const stopLossSeries = series[5];
    const takeProfitSeries = series[6];

    expect(entrySeries.applyOptions).toHaveBeenCalledWith(expect.objectContaining({ color: '#F97316', visible: true }));
    expect(stopLossSeries.applyOptions).toHaveBeenCalledWith(expect.objectContaining({ color: '#F59E0B', visible: true }));
    expect(takeProfitSeries.applyOptions).toHaveBeenCalledWith(expect.objectContaining({ color: '#FB7185', visible: true }));
    expect(entrySeries.setData).toHaveBeenCalledWith(expect.arrayContaining([
      expect.objectContaining({ time: 1700000000, value: 103 }),
      expect.objectContaining({ time: 1700000120, value: 103 }),
    ]));
  });
});

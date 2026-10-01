import { useEffect, useRef } from "react";
import {
  AreaSeries,
  CandlestickSeries,
  ColorType,
  HistogramSeries,
  LineSeries,
  createChart,
  type CandlestickData,
  type HistogramData,
  type IChartApi,
  type ISeriesApi,
  type LineData,
  type UTCTimestamp,
} from "lightweight-charts";

import type { BinanceKlineCandle } from "../hooks/useBinanceWebSocket";
import { calculateEma } from "../indicators/ema";

export interface TradingChartProps {
  symbol: string;
  candles: BinanceKlineCandle[];
  isConnected: boolean;
  showEma50?: boolean;
  showEma200?: boolean;
  showVolume?: boolean;
  showSmcOverlay?: boolean;
  positionType?: "LONG" | "SHORT" | "NONE" | null;
  entryLine?: number | null;
  stopLossLine?: number | null;
  takeProfitLine?: number | null;
  structure?: {
    poi?: number | null;
    inducement?: number | null;
    bos?: number | null;
    hl?: number | null;
    hh?: number | null;
  } | null;
}

const toChartTime = (timestamp: number): UTCTimestamp => (Math.floor(timestamp / 1000) as UTCTimestamp);

const normalizeCandles = (candles: BinanceKlineCandle[]): CandlestickData<UTCTimestamp>[] =>
  candles
    .filter((candle) => candle && Number.isFinite(candle.open) && Number.isFinite(candle.high) && Number.isFinite(candle.low) && Number.isFinite(candle.close))
    .map((candle) => ({
      time: toChartTime(candle.openTime),
      open: candle.open,
      high: candle.high,
      low: candle.low,
      close: candle.close,
    }));

const normalizeVolume = (candles: BinanceKlineCandle[]): HistogramData<UTCTimestamp>[] =>
  candles.map((candle) => ({
    time: toChartTime(candle.openTime),
    value: candle.volume,
    color: candle.close >= candle.open ? "rgba(16, 185, 129, 0.7)" : "rgba(239, 68, 68, 0.7)",
  }));

const normalizeEMA = (candles: BinanceKlineCandle[], period: number): LineData<UTCTimestamp>[] => {
  const ordered = [...candles]
    .filter((candle) => candle && Number.isFinite(candle.openTime) && Number.isFinite(candle.close))
    .sort((left, right) => left.openTime - right.openTime)
    .filter((candle, index, all) => index === 0 || candle.openTime !== all[index - 1].openTime);
  if (ordered.length < period) return [];

  const closes = ordered.map((candle) => candle.close);
  const emaValues = calculateEma(closes, period);

  return ordered.slice(period - 1).map((candle, index) => ({
    time: toChartTime(candle.openTime),
    value: emaValues[index],
  }));
};

type SmcOverlay = {
  bos: number | null;
  choch: number | null;
  orderBlock: { high: number; low: number; bullish: boolean; startTime: UTCTimestamp } | null;
};

const calculateSmcOverlay = (candles: BinanceKlineCandle[]): SmcOverlay => {
  const ordered = candles.filter((candle) => [candle.openTime, candle.high, candle.low, candle.close].every(Number.isFinite)).sort((left, right) => left.openTime - right.openTime);
  if (ordered.length < 5) return { bos: null, choch: null, orderBlock: null };

  const swingHighs = ordered.slice(1, -1).filter((candle, index) => {
    const previous = ordered[index];
    const next = ordered[index + 2];
    return candle.high >= previous.high && candle.high >= next.high;
  });
  const swingLows = ordered.slice(1, -1).filter((candle, index) => {
    const previous = ordered[index];
    const next = ordered[index + 2];
    return candle.low <= previous.low && candle.low <= next.low;
  });
  const latest = ordered[ordered.length - 1];
  const latestSwingHigh = swingHighs[swingHighs.length - 1];
  const latestSwingLow = swingLows[swingLows.length - 1];
  const bullishBreak = latestSwingHigh && latest.close > latestSwingHigh.high;
  const bearishBreak = latestSwingLow && latest.close < latestSwingLow.low;
  const breakIndex = bullishBreak || bearishBreak ? ordered.length - 2 : -1;
  const breakCandle = breakIndex >= 0 ? ordered[breakIndex] : null;
  const orderBlock = breakCandle && ((bullishBreak && breakCandle.close < breakCandle.open) || (bearishBreak && breakCandle.close > breakCandle.open))
    ? { high: breakCandle.high, low: breakCandle.low, bullish: Boolean(bullishBreak), startTime: toChartTime(breakCandle.openTime) }
    : null;

  return { bos: bullishBreak ? latestSwingHigh.high : null, choch: bearishBreak ? latestSwingLow.low : null, orderBlock };
};

export function TradingChart({
  symbol,
  candles,
  isConnected,
  showEma50 = true,
  showEma200 = true,
  showVolume = true,
  showSmcOverlay = false,
  positionType = null,
  entryLine = null,
  stopLossLine = null,
  takeProfitLine = null,
  structure = null,
}: TradingChartProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const candlestickSeriesRef = useRef<ISeriesApi<"Candlestick"> | null>(null);
  const ema50SeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const ema200SeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const volumeSeriesRef = useRef<ISeriesApi<"Histogram"> | null>(null);
  const entryLineSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const stopLossLineSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const takeProfitLineSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const poiLineSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const inducementLineSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const bosLineSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const chochLineSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const hlLineSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const hhLineSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);
  const bullishOrderBlockRef = useRef<ISeriesApi<"Area"> | null>(null);
  const bearishOrderBlockRef = useRef<ISeriesApi<"Area"> | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const container = containerRef.current;
    const chart = createChart(container, {
      width: container.clientWidth,
      height: container.clientHeight || 430,
      layout: {
        background: { type: ColorType.Solid, color: "#0B0E14" },
        textColor: "#64748B",
        fontFamily: "Inter, sans-serif",
      },
      grid: {
        vertLines: { color: "rgba(148, 163, 184, 0.12)", style: 2 },
        horzLines: { color: "rgba(148, 163, 184, 0.12)", style: 2 },
      },
      crosshair: {
        mode: 1,
      },
      rightPriceScale: {
        borderColor: "rgba(148, 163, 184, 0.18)",
        textColor: "#64748B",
        scaleMargins: { top: 0.08, bottom: 0.18 },
      },
      timeScale: {
        borderColor: "rgba(148, 163, 184, 0.18)",
        timeVisible: true,
        secondsVisible: false,
        rightOffset: 8,
      },
      handleScroll: {
        mouseWheel: true,
        pressedMouseMove: true,
      },
      handleScale: {
        axisPressedMouseMove: true,
        mouseWheel: true,
        pinch: true,
      },
    });

    const candlestickSeries = chart.addSeries(CandlestickSeries, {
      upColor: "#10B981",
      downColor: "#EF4444",
      borderVisible: false,
      wickUpColor: "#10B981",
      wickDownColor: "#EF4444",
      priceLineVisible: false,
      lastValueVisible: false,
    });

    const ema50Series = chart.addSeries(LineSeries, {
      color: "#F59E0B",
      lineWidth: 2,
      crosshairMarkerVisible: false,
      lastValueVisible: false,
      priceLineVisible: false,
      visible: showEma50,
    });

    const ema200Series = chart.addSeries(LineSeries, {
      color: "#8B5CF6",
      lineWidth: 2,
      crosshairMarkerVisible: false,
      lastValueVisible: false,
      priceLineVisible: false,
      visible: showEma200,
    });

    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceFormat: {
        type: "volume",
      },
      priceScaleId: "volume",
      priceLineVisible: false,
      lastValueVisible: false,
      color: "rgba(16, 185, 129, 0.8)",
      visible: showVolume,
    });

    const entryColor = positionType === "SHORT" ? "#F97316" : "#3B82F6";
    const stopColor = positionType === "SHORT" ? "#F59E0B" : "#EF4444";
    const takeColor = positionType === "SHORT" ? "#FB7185" : "#10B981";

    const entrySeries = chart.addSeries(LineSeries, {
      color: entryColor,
      lineWidth: 2,
      visible: entryLine != null,
      priceLineVisible: false,
      lastValueVisible: false,
    });

    const stopLossSeries = chart.addSeries(LineSeries, {
      color: stopColor,
      lineWidth: 1,
      visible: stopLossLine != null,
      priceLineVisible: false,
      lastValueVisible: false,
    });

    const takeProfitSeries = chart.addSeries(LineSeries, {
      color: takeColor,
      lineWidth: 1,
      visible: takeProfitLine != null,
      priceLineVisible: false,
      lastValueVisible: false,
    });

    const poiSeries = chart.addSeries(LineSeries, {
      color: "#22D3EE",
      lineWidth: 1,
      lineStyle: 2,
      visible: false,
      priceLineVisible: false,
      lastValueVisible: false,
    });

    const inducementSeries = chart.addSeries(LineSeries, {
      color: "#A78BFA",
      lineWidth: 1,
      lineStyle: 2,
      visible: false,
      priceLineVisible: false,
      lastValueVisible: false,
    });

    const bosSeries = chart.addSeries(LineSeries, {
      color: "#34D399",
      lineWidth: 1,
      lineStyle: 2,
      visible: false,
      priceLineVisible: false,
      lastValueVisible: false,
    });

    const hlSeries = chart.addSeries(LineSeries, {
      color: "#F59E0B",
      lineWidth: 2,
      visible: false,
      priceLineVisible: false,
      lastValueVisible: false,
    });

    const hhSeries = chart.addSeries(LineSeries, {
      color: "#A78BFA",
      lineWidth: 2,
      visible: false,
      priceLineVisible: false,
      lastValueVisible: false,
    });

    const chochSeries = chart.addSeries(LineSeries, {
      color: "#FB7185",
      lineWidth: 2,
      lineStyle: 2,
      visible: false,
      priceLineVisible: false,
      lastValueVisible: false,
    });

    const bullishOrderBlockSeries = chart.addSeries(AreaSeries, {
      topColor: "rgba(16, 185, 129, 0.2)",
      bottomColor: "rgba(16, 185, 129, 0.04)",
      lineColor: "rgba(52, 211, 153, 0.75)",
      lineWidth: 1,
      visible: false,
      priceLineVisible: false,
      lastValueVisible: false,
    });

    const bearishOrderBlockSeries = chart.addSeries(AreaSeries, {
      topColor: "rgba(239, 68, 68, 0.2)",
      bottomColor: "rgba(239, 68, 68, 0.04)",
      lineColor: "rgba(251, 113, 133, 0.75)",
      lineWidth: 1,
      visible: false,
      priceLineVisible: false,
      lastValueVisible: false,
    });

    chart.priceScale("volume").applyOptions({
      scaleMargins: { top: 0.82, bottom: 0 },
      borderVisible: false,
      textColor: "#64748B",
      visible: true,
    });

    chartRef.current = chart;
    candlestickSeriesRef.current = candlestickSeries;
    ema50SeriesRef.current = ema50Series;
    ema200SeriesRef.current = ema200Series;
    volumeSeriesRef.current = volumeSeries;
    entryLineSeriesRef.current = entrySeries;
    stopLossLineSeriesRef.current = stopLossSeries;
    takeProfitLineSeriesRef.current = takeProfitSeries;
    poiLineSeriesRef.current = poiSeries;
    inducementLineSeriesRef.current = inducementSeries;
    bosLineSeriesRef.current = bosSeries;
    hlLineSeriesRef.current = hlSeries;
    hhLineSeriesRef.current = hhSeries;
    chochLineSeriesRef.current = chochSeries;
    bullishOrderBlockRef.current = bullishOrderBlockSeries;
    bearishOrderBlockRef.current = bearishOrderBlockSeries;

    const resizeObserver = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (!entry) return;
      chart.applyOptions({
        width: entry.contentRect.width,
        height: entry.contentRect.height,
      });
    });

    resizeObserver.observe(container);

    return () => {
      resizeObserver.disconnect();
      chart.remove();
      chartRef.current = null;
      candlestickSeriesRef.current = null;
      ema50SeriesRef.current = null;
      ema200SeriesRef.current = null;
      volumeSeriesRef.current = null;
      entryLineSeriesRef.current = null;
      stopLossLineSeriesRef.current = null;
      takeProfitLineSeriesRef.current = null;
      poiLineSeriesRef.current = null;
      inducementLineSeriesRef.current = null;
      bosLineSeriesRef.current = null;
      hlLineSeriesRef.current = null;
      hhLineSeriesRef.current = null;
      chochLineSeriesRef.current = null;
      bullishOrderBlockRef.current = null;
      bearishOrderBlockRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!chartRef.current) return;

    if (ema50SeriesRef.current) {
      ema50SeriesRef.current.applyOptions({ visible: showEma50 });
    }

    if (ema200SeriesRef.current) {
      ema200SeriesRef.current.applyOptions({ visible: showEma200 });
    }

    if (volumeSeriesRef.current) {
      volumeSeriesRef.current.applyOptions({ visible: showVolume });
    }
  }, [showEma50, showEma200, showVolume]);

  useEffect(() => {
    if (!chartRef.current) return;

    const normalized = normalizeCandles(candles);
    const volumeData = normalizeVolume(candles);
    const ema50 = normalizeEMA(candles, 50);
    const ema200 = normalizeEMA(candles, 200);
    const smcOverlay = calculateSmcOverlay(candles);

    if (candlestickSeriesRef.current) {
      candlestickSeriesRef.current.setData(normalized);
    }

    if (volumeSeriesRef.current) {
      volumeSeriesRef.current.setData(volumeData);
    }

    if (ema50SeriesRef.current) {
      ema50SeriesRef.current.setData(ema50);
    }

    if (ema200SeriesRef.current) {
      ema200SeriesRef.current.setData(ema200);
    }

    const firstTime = normalized[0]?.time ?? 0;
    const lastTime = normalized[normalized.length - 1]?.time ?? 0;

    if (firstTime && lastTime) {
      const entryLevels = [
        entryLine == null ? null : { time: firstTime, value: entryLine },
        entryLine == null ? null : { time: lastTime, value: entryLine },
      ].filter(Boolean) as Array<{ time: UTCTimestamp; value: number }>;

      const stopLevels = [
        stopLossLine == null ? null : { time: firstTime, value: stopLossLine },
        stopLossLine == null ? null : { time: lastTime, value: stopLossLine },
      ].filter(Boolean) as Array<{ time: UTCTimestamp; value: number }>;

      const takeProfitLevels = [
        takeProfitLine == null ? null : { time: firstTime, value: takeProfitLine },
        takeProfitLine == null ? null : { time: lastTime, value: takeProfitLine },
      ].filter(Boolean) as Array<{ time: UTCTimestamp; value: number }>;

      const structureLevels = {
        poi: structure?.poi == null ? null : [{ time: firstTime, value: structure.poi }, { time: lastTime, value: structure.poi }],
        inducement: structure?.inducement == null ? null : [{ time: firstTime, value: structure.inducement }, { time: lastTime, value: structure.inducement }],
        bos: structure?.bos == null ? null : [{ time: firstTime, value: structure.bos }, { time: lastTime, value: structure.bos }],
        hl: structure?.hl == null ? null : [{ time: firstTime, value: structure.hl }, { time: lastTime, value: structure.hl }],
        hh: structure?.hh == null ? null : [{ time: firstTime, value: structure.hh }, { time: lastTime, value: structure.hh }],
      };

      if (entryLineSeriesRef.current) {
        entryLineSeriesRef.current.setData(entryLevels);
        entryLineSeriesRef.current.applyOptions({
          visible: Boolean(entryLine != null),
          color: positionType === "SHORT" ? "#F97316" : "#3B82F6",
          lineWidth: 2,
        });
      }
      if (stopLossLineSeriesRef.current) {
        stopLossLineSeriesRef.current.setData(stopLevels);
        stopLossLineSeriesRef.current.applyOptions({
          visible: Boolean(stopLossLine != null),
          color: positionType === "SHORT" ? "#F59E0B" : "#EF4444",
          lineWidth: 1,
        });
      }
      if (takeProfitLineSeriesRef.current) {
        takeProfitLineSeriesRef.current.setData(takeProfitLevels);
        takeProfitLineSeriesRef.current.applyOptions({
          visible: Boolean(takeProfitLine != null),
          color: positionType === "SHORT" ? "#FB7185" : "#10B981",
          lineWidth: 1,
        });
      }
      if (poiLineSeriesRef.current) {
        poiLineSeriesRef.current.setData(structureLevels.poi ?? []);
        poiLineSeriesRef.current.applyOptions({ visible: Boolean(structureLevels.poi), color: "#22D3EE", lineWidth: 1, lineStyle: 2 });
      }
      if (inducementLineSeriesRef.current) {
        inducementLineSeriesRef.current.setData(structureLevels.inducement ?? []);
        inducementLineSeriesRef.current.applyOptions({ visible: Boolean(structureLevels.inducement), color: "#A78BFA", lineWidth: 1, lineStyle: 2 });
      }
      if (bosLineSeriesRef.current) {
        bosLineSeriesRef.current.setData(structureLevels.bos ?? []);
        bosLineSeriesRef.current.applyOptions({ visible: Boolean(structureLevels.bos), color: "#34D399", lineWidth: 1, lineStyle: 2 });
      }
      if (hlLineSeriesRef.current) {
        hlLineSeriesRef.current.setData(structureLevels.hl ?? []);
        hlLineSeriesRef.current.applyOptions({ visible: Boolean(structureLevels.hl), color: "#F59E0B", lineWidth: 2 });
      }
      if (hhLineSeriesRef.current) {
        hhLineSeriesRef.current.setData(structureLevels.hh ?? []);
        hhLineSeriesRef.current.applyOptions({ visible: Boolean(structureLevels.hh), color: "#A78BFA", lineWidth: 2 });
      }

      const overlayLine = (level: number | null) => level == null ? [] : [{ time: firstTime, value: level }, { time: lastTime, value: level }];
      if (bosLineSeriesRef.current) {
        bosLineSeriesRef.current.setData(overlayLine(smcOverlay.bos));
        bosLineSeriesRef.current.applyOptions({ visible: showSmcOverlay && smcOverlay.bos != null, color: "#34D399", lineWidth: 2, lineStyle: 2 });
      }
      if (chochLineSeriesRef.current) {
        chochLineSeriesRef.current.setData(overlayLine(smcOverlay.choch));
        chochLineSeriesRef.current.applyOptions({ visible: showSmcOverlay && smcOverlay.choch != null, color: "#FB7185", lineWidth: 2, lineStyle: 2 });
      }
      const orderBlockData = smcOverlay.orderBlock && normalized.length > 0
        ? normalized.filter((candle) => candle.time >= smcOverlay.orderBlock!.startTime).map((candle) => ({ time: candle.time, value: smcOverlay.orderBlock!.high }))
        : [];
      const orderBlockOptions = smcOverlay.orderBlock ? { baseValue: { type: "price" as const, price: smcOverlay.orderBlock.low } } : { baseValue: undefined };
      if (bullishOrderBlockRef.current) {
        bullishOrderBlockRef.current.setData(smcOverlay.orderBlock?.bullish ? orderBlockData : []);
        bullishOrderBlockRef.current.applyOptions({ ...orderBlockOptions, visible: showSmcOverlay && Boolean(smcOverlay.orderBlock?.bullish) });
      }
      if (bearishOrderBlockRef.current) {
        bearishOrderBlockRef.current.setData(smcOverlay.orderBlock && !smcOverlay.orderBlock.bullish ? orderBlockData : []);
        bearishOrderBlockRef.current.applyOptions({ ...orderBlockOptions, visible: showSmcOverlay && Boolean(smcOverlay.orderBlock && !smcOverlay.orderBlock.bullish) });
      }
    }

    if (normalized.length > 0) {
      chartRef.current.timeScale().fitContent();
    }
  }, [candles, entryLine, stopLossLine, takeProfitLine, structure, showSmcOverlay]);

  const smcOverlay = calculateSmcOverlay(candles);

  return (
    <div className="rounded-[28px] border border-slate-800 bg-slate-950/80 p-4 shadow-2xl shadow-slate-950/30">
      <div className="mb-4 flex items-center justify-between gap-3">
        <div>
          <p className="text-xs uppercase tracking-[0.2em] text-slate-500">{symbol}</p>
          <h2 className="mt-1 text-2xl font-bold text-slate-50">{candles.length ? `$${candles[candles.length - 1].close.toFixed(2)}` : "--"}</h2>
        </div>

        <div className="flex items-center gap-2">
          <span className={`inline-flex items-center gap-2 rounded-full border px-2.5 py-1 text-xs ${isConnected ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300" : "border-rose-500/30 bg-rose-500/10 text-rose-300"}`}>
            <span className={`h-2 w-2 rounded-full ${isConnected ? "animate-pulse bg-emerald-400" : "bg-rose-400"}`} />
            {isConnected ? "LIVE" : "OFFLINE"}
          </span>
        </div>
      </div>

      <div
        ref={containerRef}
        className="relative h-full min-h-[400px] w-full overflow-hidden rounded-2xl border border-slate-800 bg-[#0B0E14]"
      >
        {showSmcOverlay ? <div className="pointer-events-none absolute right-3 top-3 z-10 flex flex-col items-end gap-1 text-[9px] font-bold uppercase tracking-[0.14em]">
          {smcOverlay.bos != null ? <span className="rounded border border-emerald-400/40 bg-emerald-400/15 px-2 py-1 text-emerald-300">BOS 1H</span> : null}
          {smcOverlay.choch != null ? <span className="rounded border border-orange-400/40 bg-orange-400/15 px-2 py-1 text-orange-300">CHOCH 1H</span> : null}
          {smcOverlay.orderBlock ? <span className={`rounded border px-2 py-1 ${smcOverlay.orderBlock.bullish ? "border-emerald-400/40 bg-emerald-400/15 text-emerald-300" : "border-rose-400/40 bg-rose-400/15 text-rose-300"}`}>OB 1H</span> : null}
        </div> : null}
      </div>
    </div>
  );
}

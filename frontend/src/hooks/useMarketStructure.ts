import { useEffect, useMemo, useState } from "react";
import type { BinanceKlineCandle } from "./useMarketRealtime";

export interface SMCEvent {
  type: "CHOCH" | "BOS" | "HH" | "HL" | "LH" | "LL";
  time: string;
  isBullish: boolean;
}

export interface MarketStructureData {
  trend: "BULLISH" | "BEARISH" | "SIDEWAYS";
  trendLabel: string;
  badgeColor: string;
  events: SMCEvent[];
  statusSummary: string;
  setupRecommendation: string;
}

const emptyStructure: MarketStructureData = {
  trend: "SIDEWAYS",
  trendLabel: "Đang chờ cấu trúc",
  badgeColor: "#94A3B8",
  events: [],
  statusSummary: "Chưa đủ 20 nến đóng để xác nhận phá vỡ cấu trúc.",
  setupRecommendation: "Chờ thêm dữ liệu trước khi tìm điểm vào lệnh.",
};

const formatEventTime = (openTime: number): string => {
  const elapsedHours = Math.max(0, Math.round((Date.now() - openTime) / 3600000));
  return elapsedHours === 0 ? "Now" : `${elapsedHours}h ago`;
};

export const calculateMarketStructure = (candles: BinanceKlineCandle[]): MarketStructureData => {
  const validCandles = candles.filter((candle) =>
    [candle.openTime, candle.high, candle.low, candle.close].every(Number.isFinite)
  );

  if (validCandles.length < 21) return emptyStructure;

  const latest = validCandles[validCandles.length - 1];
  const previousTwenty = validCandles.slice(-21, -1);
  const previousHigh = Math.max(...previousTwenty.map((candle) => candle.high));
  const previousLow = Math.min(...previousTwenty.map((candle) => candle.low));

  if (latest.high > previousHigh) {
    return {
      trend: "BULLISH",
      trendLabel: "Tăng trưởng",
      badgeColor: "#34D399",
      events: [
        { type: "BOS", time: formatEventTime(latest.openTime), isBullish: true },
        { type: "HH", time: formatEventTime(latest.openTime), isBullish: true },
      ],
      statusSummary: "Phá vỡ cấu trúc tăng, tạo đỉnh cao hơn (HH) trên khung 1H.",
      setupRecommendation: "Ưu tiên LONG khi giá retest vùng BOS và giữ trên đỉnh vừa phá.",
    };
  }

  if (latest.low < previousLow) {
    return {
      trend: "BEARISH",
      trendLabel: "Giảm giá",
      badgeColor: "#FB385F",
      events: [
        { type: "CHOCH", time: formatEventTime(latest.openTime), isBullish: false },
        { type: "BOS", time: formatEventTime(latest.openTime), isBullish: false },
        { type: "LL", time: formatEventTime(latest.openTime), isBullish: false },
      ],
      statusSummary: "Phá vỡ cấu trúc giảm, xuất hiện CHOCH khung 1H và đáy thấp hơn (LL).",
      setupRecommendation: "Ưu tiên SHORT khi giá retest vùng BOS và bị từ chối dưới đáy mới.",
    };
  }

  return {
    trend: "SIDEWAYS",
    trendLabel: "Đi ngang",
    badgeColor: "#FBBF24",
    events: [],
    statusSummary: "Giá vẫn nằm trong biên độ 20 nến, chưa có BOS rõ ràng.",
    setupRecommendation: "Chờ phá biên và retest trước khi xác nhận setup SMC.",
  };
};

export function useMarketStructure(candles: BinanceKlineCandle[], visionStructure?: MarketStructureData | null, symbol = "BTC/USDT") {
  const [hourlyCandles, setHourlyCandles] = useState<BinanceKlineCandle[]>([]);

  useEffect(() => {
    const controller = new AbortController();
    const binanceSymbol = symbol.replace(/\//g, "").toUpperCase();

    fetch(`https://api.binance.com/api/v3/klines?symbol=${binanceSymbol}&interval=1h&limit=50`, { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) return [];
        const rows = (await response.json()) as unknown[][];
        return rows.map((row) => ({
          openTime: Number(row[0]),
          closeTime: Number(row[6]),
          open: Number(row[1]),
          high: Number(row[2]),
          low: Number(row[3]),
          close: Number(row[4]),
          volume: Number(row[5]),
          isClosed: Number(row[6]) <= Date.now(),
        }));
      })
      .then((nextCandles) => setHourlyCandles(nextCandles))
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === "AbortError") return;
        setHourlyCandles([]);
      });

    return () => controller.abort();
  }, [symbol]);

  return useMemo(
    () => visionStructure ?? calculateMarketStructure(hourlyCandles.length > 0 ? hourlyCandles : candles),
    [candles, hourlyCandles, visionStructure]
  );
}
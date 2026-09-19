import { useMemo } from "react";
import type { BinanceKlineCandle } from "./useMarketRealtime";
import type { TradeSettings } from "../context/SettingsContext";
import { calculateEma } from "../indicators/ema";

export type SMCBias = "BULLISH" | "BEARISH" | "NEUTRAL";
export type SMCSensitivity = "low" | "balanced" | "high";

export interface SMCAnalysis {
  bias: SMCBias;
  overallBiasScore: number;
  trendStrengthScore: number;
  breakoutProbability: number;
  aiInsightText: string;
}

type SMCSettings = TradeSettings["smc"];

const clamp = (value: number, minimum = 0, maximum = 100): number =>
  Math.min(maximum, Math.max(minimum, Math.round(value)));

const average = (values: number[]): number =>
  values.length > 0 ? values.reduce((sum, value) => sum + value, 0) / values.length : 0;

const averageRange = (candles: BinanceKlineCandle[]): number =>
  average(candles.map((candle) => Math.max(0, candle.high - candle.low)));

const getSensitivityWindow = (sensitivity: SMCSensitivity): number => {
  if (sensitivity === "low") return 4;
  if (sensitivity === "high") return 2;
  return 3;
};

const findStructureDirection = (candles: BinanceKlineCandle[], window: number): number => {
  const swingHighs: number[] = [];
  const swingLows: number[] = [];

  for (let index = window; index < candles.length - window; index += 1) {
    const candle = candles[index];
    const surrounding = candles.slice(index - window, index + window + 1);
    if (surrounding.every((item) => candle.high >= item.high)) swingHighs.push(candle.high);
    if (surrounding.every((item) => candle.low <= item.low)) swingLows.push(candle.low);
  }

  const highDirection = swingHighs.length >= 2
    ? Math.sign(swingHighs[swingHighs.length - 1] - swingHighs[swingHighs.length - 2])
    : 0;
  const lowDirection = swingLows.length >= 2
    ? Math.sign(swingLows[swingLows.length - 1] - swingLows[swingLows.length - 2])
    : 0;

  if (highDirection > 0 && lowDirection >= 0) return 1;
  if (highDirection < 0 && lowDirection <= 0) return -1;
  return highDirection + lowDirection > 0 ? 1 : highDirection + lowDirection < 0 ? -1 : 0;
};

const findImbalance = (candles: BinanceKlineCandle[], thresholdPercent: number): { direction: SMCBias; distance: number } => {
  for (let index = candles.length - 1; index >= 2; index -= 1) {
    const current = candles[index];
    const twoBack = candles[index - 2];
    const bullishGap = current.low - twoBack.high;
    const bearishGap = twoBack.low - current.high;
    const threshold = current.close * thresholdPercent / 100;
    if (bullishGap > threshold) return { direction: "BULLISH", distance: bullishGap };
    if (bearishGap > threshold) return { direction: "BEARISH", distance: bearishGap };
  }
  return { direction: "NEUTRAL", distance: 0 };
};

export function useSMCAnalysis(candles: BinanceKlineCandle[], smc: SMCSettings, minimumRiskReward: string): SMCAnalysis {
  return useMemo(() => {
    const validCandles = candles
      .filter((candle) => Number.isFinite(candle.open) && Number.isFinite(candle.high) && Number.isFinite(candle.low) && Number.isFinite(candle.close) && Number.isFinite(candle.volume))
      .slice(-200);

    if (validCandles.length < 20) {
      return {
        bias: "NEUTRAL",
        overallBiasScore: 0,
        trendStrengthScore: 0,
        breakoutProbability: 0,
        aiInsightText: "Đang chờ đủ dữ liệu nến để xác nhận cấu trúc SMC, BOS và CHoCH.",
      };
    }

    const closes = validCandles.map((candle) => candle.close);
    const ema20Values = calculateEma(closes, 20);
    const ema50Values = calculateEma(closes, 50);
    const latestClose = closes[closes.length - 1];
    const ema20 = ema20Values.at(-1) ?? latestClose;
    const ema50 = ema50Values.at(-1) ?? ema20;
    const window = getSensitivityWindow(smc.sensitivity);
    const structureDirection = findStructureDirection(validCandles, window);
    const emaDirection = latestClose > ema20 && ema20 >= ema50 ? 1 : latestClose < ema20 && ema20 <= ema50 ? -1 : 0;
    const recentDirection = average(validCandles.slice(-5).map((candle) => candle.close >= candle.open ? 1 : -1));
    const directionalScore = Math.abs(emaDirection) * 34 + Math.abs(structureDirection) * 34 + Math.abs(recentDirection) * 20;
    const bias: SMCBias = emaDirection + structureDirection + Math.sign(recentDirection) > 1
      ? "BULLISH"
      : emaDirection + structureDirection + Math.sign(recentDirection) < -1
        ? "BEARISH"
        : "NEUTRAL";
    const overallBiasScore = clamp(50 + (bias === "BULLISH" ? directionalScore / 2 : bias === "BEARISH" ? -directionalScore / 2 : 0));

    const ranges = validCandles.slice(-20).map((candle) => candle.high - candle.low);
    const currentRange = ranges.at(-1) ?? 0;
    const volumeAverage = average(validCandles.slice(-21, -1).map((candle) => candle.volume));
    const volumeRatio = volumeAverage > 0 ? validCandles.at(-1)!.volume / volumeAverage : 1;
    const rangeRatio = averageRange(validCandles.slice(-5)) > 0 ? currentRange / averageRange(validCandles.slice(-5)) : 1;
    const trendStrengthScore = clamp((Math.min(volumeRatio, 2) / 2) * 55 + (Math.min(rangeRatio, 2) / 2) * 45);

    const recentHigh = Math.max(...validCandles.slice(-30).map((candle) => candle.high));
    const recentLow = Math.min(...validCandles.slice(-30).map((candle) => candle.low));
    const distanceToLiquidity = Math.min(Math.abs(recentHigh - latestClose), Math.abs(latestClose - recentLow));
    const imbalance = smc.fvg ? findImbalance(validCandles, Number(smc.fvgThreshold) || 0.1) : { direction: "NEUTRAL" as SMCBias, distance: 0 };
    const range = averageRange(validCandles.slice(-20));
    const proximityScore = range > 0 ? clamp(100 - (distanceToLiquidity / range) * 35) : 0;
    const breakoutProbability = clamp(proximityScore * 0.55 + trendStrengthScore * 0.25 + (imbalance.distance > 0 ? 20 : 0));

    const structureEvent = bias === "NEUTRAL" ? "CHoCH đang hình thành hoặc cấu trúc chưa đồng thuận" : bias === "BULLISH" ? "BOS tăng với chuỗi HH/HL" : "BOS giảm với chuỗi LL/LH";
    const orderBlock = bias === "BULLISH" ? "Order Block cầu gần nhất" : bias === "BEARISH" ? "Order Block cung gần nhất" : "Order Block đối ứng gần nhất";
    const liquidityEvent = distanceToLiquidity <= range * 1.5 ? "giá đang áp sát vùng liquidity và có rủi ro Sweep Liquidity" : "liquidity gần nhất vẫn còn khoảng đệm";
    const fvgText = imbalance.direction === "NEUTRAL" ? "chưa có FVG đạt ngưỡng" : `${imbalance.direction === "BULLISH" ? "FVG tăng" : "FVG giảm"} đang được theo dõi`;
    const riskReward = Number(minimumRiskReward) > 0 ? Number(minimumRiskReward).toFixed(1) : "2.0";

    return {
      bias,
      overallBiasScore,
      trendStrengthScore,
      breakoutProbability,
      aiInsightText: `${structureEvent}; ${orderBlock} hỗ trợ vùng phản ứng, ${fvgText}, và ${liquidityEvent}. Ưu tiên xác nhận lại entry với R:R tối thiểu ${riskReward}.`,
    };
  }, [candles, minimumRiskReward, smc]);
}

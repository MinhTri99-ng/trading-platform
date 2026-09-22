import { describe, expect, it } from "vitest";
import { calculateMarketStructure } from "./useMarketStructure";
import type { BinanceKlineCandle } from "./useMarketRealtime";

const makeCandles = (latestHigh: number, latestLow: number): BinanceKlineCandle[] => [
  ...Array.from({ length: 20 }, (_, index) => ({
    openTime: 1700000000000 + index * 3600000,
    closeTime: 1700003600000 + index * 3600000,
    open: 100,
    high: 110,
    low: 90,
    close: 100,
    volume: 10,
    isClosed: true,
  })),
  {
    openTime: 1700072000000,
    closeTime: 1700075600000,
    open: 100,
    high: latestHigh,
    low: latestLow,
    close: 100,
    volume: 10,
    isClosed: true,
  },
];

describe("calculateMarketStructure", () => {
  it("emits bullish BOS and HH when the latest candle breaks the prior 20 highs", () => {
    const result = calculateMarketStructure(makeCandles(111, 95));

    expect(result.trend).toBe("BULLISH");
    expect(result.events.map((event) => event.type)).toEqual(["BOS", "HH"]);
  });

  it("emits bearish CHOCH, BOS and LL when the latest candle breaks the prior 20 lows", () => {
    const result = calculateMarketStructure(makeCandles(105, 89));

    expect(result.trend).toBe("BEARISH");
    expect(result.events.map((event) => event.type)).toEqual(["CHOCH", "BOS", "LL"]);
  });
});
import { describe, expect, it } from "vitest";
import { calculateEma } from "./ema";

describe("calculateEma", () => {
  it("uses an SMA seed and the standard alpha recurrence", () => {
    expect(calculateEma([10, 20, 30, 40, 50], 3)).toEqual([
      20,
      30,
      40,
    ]);
  });

  it("calculates EMA 50 deterministically", () => {
    const values = Array.from({ length: 60 }, (_, index) => 100 + index);
    const result = calculateEma(values, 50);

    expect(result).toHaveLength(11);
    expect(result[0]).toBe(124.5);
    expect(result.at(-1)).toBeCloseTo(134.5, 10);
  });

  it("calculates EMA 200 only after the full warm-up period", () => {
    const values = Array.from({ length: 220 }, (_, index) => 100 + index);
    const result = calculateEma(values, 200);

    expect(result).toHaveLength(21);
    expect(result[0]).toBe(199.5);
    expect(result.at(-1)).toBeCloseTo(219.5, 10);
  });

  it("returns no value for insufficient or invalid history", () => {
    expect(calculateEma([10, 20], 3)).toEqual([]);
    expect(calculateEma([10, Number.NaN, 30], 3)).toEqual([]);
  });

  it("is deterministic for ascending candle closes", () => {
    const values = [101, 99, 100, 102, 98, 103];
    expect(calculateEma(values, 3)).toEqual(calculateEma([...values], 3));
  });
});
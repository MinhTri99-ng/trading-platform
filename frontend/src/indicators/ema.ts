export function calculateEma(values: number[], period: number): number[] {
  if (period <= 0 || values.length < period || values.some((value) => !Number.isFinite(value))) {
    return [];
  }

  const alpha = 2 / (period + 1);
  const result: number[] = [];
  const seed = values.slice(0, period).reduce((sum, value) => sum + value, 0) / period;
  let previous = seed;

  result.push(previous);
  for (let index = period; index < values.length; index += 1) {
    previous = values[index] * alpha + previous * (1 - alpha);
    result.push(previous);
  }

  return result;
}
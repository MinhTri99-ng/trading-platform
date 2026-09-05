import { useEffect, useRef, useState } from "react";

export interface BinanceTicker {
  symbol: string;
  price: number;
  priceChange: number;
  priceChangePercent: number;
  highPrice: number;
  lowPrice: number;
  volume: number;
  quoteVolume: number;
  lastUpdateId: number;
}

export interface BinanceKlineCandle {
  openTime: number;
  closeTime: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  isClosed: boolean;
}

export interface UseBinanceWebSocketOptions {
  symbolCode: string;
  interval?: string;
  maxCandles?: number;
}

export interface UseBinanceWebSocketResult {
  ticker: BinanceTicker | null;
  candles: BinanceKlineCandle[];
  isConnected: boolean;
}

const DEFAULT_INTERVAL = "1h";
const DEFAULT_MAX_CANDLES = 500;

const toNumber = (value: unknown): number => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
};

const normalizeInterval = (interval: string) => {
  const normalized = interval.trim().toLowerCase();
  const map: Record<string, string> = {
    "1m": "1m",
    "5m": "5m",
    "15m": "15m",
    "1h": "1h",
    "4h": "4h",
    "1d": "1d",
    "1w": "1w",
  };
  return map[normalized] ?? DEFAULT_INTERVAL;
};

const normalizeSymbol = (symbolCode: string) => {
  const normalized = symbolCode.replace(/\s+/g, "").replace("/", "").toUpperCase();
  return normalized.length >= 6 ? normalized : "";
};

const parseTicker = (payload: Record<string, unknown>): BinanceTicker | null => {
  if (!payload || typeof payload !== "object") return null;

  const symbol = String(payload.s ?? "").toUpperCase();
  if (!symbol) return null;

  return {
    symbol,
    price: toNumber(payload.c),
    priceChange: toNumber(payload.p),
    priceChangePercent: toNumber(payload.P),
    highPrice: toNumber(payload.h),
    lowPrice: toNumber(payload.l),
    volume: toNumber(payload.v),
    quoteVolume: toNumber(payload.q),
    lastUpdateId: toNumber(payload.u),
  };
};

const parseKline = (payload: Record<string, unknown>): BinanceKlineCandle | null => {
  if (!payload || typeof payload !== "object") return null;

  const candle = (payload.k ?? payload) as Record<string, unknown>;
  if (!candle || typeof candle !== "object") return null;

  const openTime = toNumber(candle.t);
  const closeTime = toNumber(candle.T);
  const isClosed = Boolean(candle.x);

  return {
    openTime,
    closeTime,
    open: toNumber(candle.o),
    high: toNumber(candle.h),
    low: toNumber(candle.l),
    close: toNumber(candle.c),
    volume: toNumber(candle.v),
    isClosed,
  };
};

const mergeCandle = (previous: BinanceKlineCandle[], next: BinanceKlineCandle) => {
  const merged = [...previous];
  const index = merged.findIndex((item) => item.openTime === next.openTime);

  if (index >= 0) {
    merged[index] = next;
  } else {
    merged.push(next);
  }

  return merged.sort((a, b) => a.openTime - b.openTime);
};

const fetchHistoricalCandles = async (symbol: string, interval: string, maxCandles: number): Promise<BinanceKlineCandle[]> => {
  const query = new URLSearchParams({
    symbol,
    interval: normalizeInterval(interval),
    limit: String(Math.min(maxCandles, 1000)),
  });

  const response = await fetch(`https://api.binance.com/api/v3/klines?${query.toString()}`);
  if (!response.ok) {
    throw new Error(`Binance REST request failed: ${response.status}`);
  }

  const raw = (await response.json()) as unknown[][];
  const candles: BinanceKlineCandle[] = [];

  for (const entry of raw) {
    if (!Array.isArray(entry) || entry.length < 7) {
      continue;
    }

    const [openTime, open, high, low, close, volume, closeTime] = entry as [unknown, unknown, unknown, unknown, unknown, unknown, unknown];

    const candle: BinanceKlineCandle = {
      openTime: Number(openTime ?? 0),
      closeTime: Number(closeTime ?? 0),
      open: Number(open ?? 0),
      high: Number(high ?? 0),
      low: Number(low ?? 0),
      close: Number(close ?? 0),
      volume: Number(volume ?? 0),
      isClosed: Number(closeTime ?? 0) <= Date.now(),
    };

    if (candle.openTime > 0) {
      candles.push(candle);
    }
  }

  return candles;
};

export function useBinanceWebSocket({
  symbolCode,
  interval = DEFAULT_INTERVAL,
  maxCandles = DEFAULT_MAX_CANDLES,
}: UseBinanceWebSocketOptions): UseBinanceWebSocketResult {
  const [ticker, setTicker] = useState<BinanceTicker | null>(null);
  const [candles, setCandles] = useState<BinanceKlineCandle[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const socketRef = useRef<WebSocket | null>(null);
  const reconnectRef = useRef<number | null>(null);

  useEffect(() => {
    const normalizedSymbol = normalizeSymbol(symbolCode);
    if (!normalizedSymbol) {
      setTicker(null);
      setCandles([]);
      setIsConnected(false);
      return;
    }

    let isUnmounted = false;
    const normalizedInterval = normalizeInterval(interval);
    const wsUrl = `wss://stream.binance.com:9443/ws/${normalizedSymbol.toLowerCase()}@kline_${normalizedInterval}`;

    const applyHistory = async () => {
      try {
        const history = await fetchHistoricalCandles(normalizedSymbol, normalizedInterval, maxCandles);
        if (!isUnmounted && history.length) {
          const safeHistory = history.slice(-maxCandles);
          setCandles(safeHistory);
          const latest = safeHistory[safeHistory.length - 1];
          const previous = safeHistory[Math.max(0, safeHistory.length - 2)];

          if (latest && previous) {
            setTicker({
              symbol: normalizedSymbol,
              price: latest.close,
              priceChange: latest.close - previous.close,
              priceChangePercent: ((latest.close - previous.close) / previous.close) * 100,
              highPrice: Math.max(...safeHistory.map((item) => item.high)),
              lowPrice: Math.min(...safeHistory.map((item) => item.low)),
              volume: safeHistory.reduce((sum, item) => sum + item.volume, 0),
              quoteVolume: safeHistory.reduce((sum, item) => sum + item.volume * item.close, 0),
              lastUpdateId: latest.closeTime,
            });
          }
        }
      } catch {
        if (!isUnmounted) {
          setCandles([]);
        }
      }
    };

    const connect = () => {
      if (isUnmounted) return;

      const socket = new WebSocket(wsUrl);
      socketRef.current = socket;

      socket.onopen = () => {
        if (!isUnmounted) {
          setIsConnected(true);
        }
      };

      socket.onmessage = (event) => {
        if (isUnmounted) return;

        try {
          const payload = JSON.parse(event.data) as Record<string, unknown>;
          if (!payload || typeof payload !== "object") return;

          const nextTicker = parseTicker(payload as Record<string, unknown>);
          if (nextTicker) {
            setTicker(nextTicker);
          }

          const nextCandle = parseKline(payload as Record<string, unknown>);
          if (!nextCandle) return;

          setCandles((previous) => {
            const merged = mergeCandle(previous, nextCandle);
            return merged.slice(-maxCandles);
          });
        } catch {
          if (!isUnmounted) {
            setIsConnected(false);
          }
        }
      };

      socket.onerror = () => {
        if (!isUnmounted) {
          setIsConnected(false);
        }
      };

      socket.onclose = () => {
        if (!isUnmounted) {
          setIsConnected(false);
          reconnectRef.current = window.setTimeout(() => {
            if (!isUnmounted) {
              connect();
            }
          }, 1500);
        }
      };
    };

    setTicker(null);
    setCandles([]);
    setIsConnected(false);
    void applyHistory();
    connect();

    return () => {
      isUnmounted = true;
      if (reconnectRef.current) {
        window.clearTimeout(reconnectRef.current);
      }
      if (socketRef.current) {
        socketRef.current.close();
        socketRef.current = null;
      }
    };
  }, [interval, maxCandles, symbolCode]);

  return { ticker, candles, isConnected };
}

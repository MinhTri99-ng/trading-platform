import { useEffect, useState } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";

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

export interface MarketTicker {
  symbol: string;
  price: number;
  highPrice: number;
  lowPrice: number;
  volume: number;
}

export interface MarketSignal {
  symbol: string;
  direction: "LONG" | "SHORT" | "NONE";
  entry: number;
  stopLoss: number;
  takeProfit: number;
  riskReward: string;
  confidence: number;
  timestamp: number;
}

const normalizeSymbol = (symbol: string) => symbol.replace(/\s+/g, "").replace("/", "").toUpperCase();

const getApiUrl = () => {
  const configuredUrl = import.meta.env.VITE_API_URL?.trim();
  return (configuredUrl || "http://localhost:8080").replace(/\/$/, "");
};

const parseTimestamp = (value: unknown): number => {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value < 1e12 ? value * 1000 : value;
  }

  if (typeof value === "string") {
    const numeric = Number(value);
    if (Number.isFinite(numeric)) {
      return numeric < 1e12 ? numeric * 1000 : numeric;
    }

    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  return 0;
};

const deduplicateAndLimitCandles = (candles: BinanceKlineCandle[]): BinanceKlineCandle[] => {
  const byOpenTime = new Map<number, BinanceKlineCandle>();
  candles.forEach((candle) => {
    if (Number.isFinite(candle.openTime) && candle.openTime > 0) {
      byOpenTime.set(candle.openTime, candle);
    }
  });
  return [...byOpenTime.values()]
    .sort((left, right) => left.openTime - right.openTime);
};

const mapHistoryToCandles = (rows: Array<Record<string, unknown>>): BinanceKlineCandle[] =>
  rows
    .map((row) => {
      const openTime = parseTimestamp(row.timestamp);
      const open = Number(row.open ?? 0);
      const high = Number(row.high ?? 0);
      const low = Number(row.low ?? 0);
      const close = Number(row.close ?? 0);
      const volume = Number(row.volume ?? 0);

      if (!Number.isFinite(openTime) || openTime <= 0) {
        return null;
      }

      return {
        openTime,
        closeTime: openTime + 3600000,
        open,
        high,
        low,
        close,
        volume,
        isClosed: true,
      };
    })
    .filter((item): item is BinanceKlineCandle => item !== null);

const mapBinanceToCandles = (data: any[]): BinanceKlineCandle[] =>
  data.map((item) => ({
    openTime: Number(item[0]),
    closeTime: Number(item[6]),
    open: Number(item[1]),
    high: Number(item[2]),
    low: Number(item[3]),
    close: Number(item[4]),
    volume: Number(item[5]),
    isClosed: Number(item[6] ?? 0) <= Date.now(),
  }));

export function useMarketRealtime({ symbolCode, interval }: { symbolCode: string; interval: string }) {
  const [candles, setCandles] = useState<BinanceKlineCandle[]>([]);
  const [ticker, setTicker] = useState<MarketTicker | null>(null);
  const [signal, setSignal] = useState<MarketSignal | null>(null);
  const [isConnected, setIsConnected] = useState(false);

  useEffect(() => {
    const normalizedSymbol = normalizeSymbol(symbolCode);
    const normalizedInterval = interval.toLowerCase();
    const baseUrl = getApiUrl();

    let cancelled = false;
    let stompClient: Client | null = null;

    console.info(`[CHART WS CONNECTED] symbol=${normalizedSymbol} timeframe=${normalizedInterval}`);

    const safeSetTicker = (nextTicker: MarketTicker | null) => {
      if (!cancelled) {
        setTicker(nextTicker);
      }
    };

    const fetchBinanceHistory = async (): Promise<BinanceKlineCandle[]> => {
      try {
        const binanceSymbol = normalizedSymbol.toUpperCase().includes("USDT")
          ? normalizedSymbol.toUpperCase()
          : `${normalizedSymbol.toUpperCase()}USDT`;
        const response = await fetch(
          `https://api.binance.com/api/v3/klines?symbol=${binanceSymbol}&interval=${normalizedInterval}&limit=500`
        );
        if (!response.ok) return [];
        const data = await response.json();
        return mapBinanceToCandles(data);
      } catch (error) {
        console.error("Binance fetch failed:", error);
        return [];
      }
    };

    const hydrateHistory = async () => {
      let nextCandles: BinanceKlineCandle[] = [];
      try {
        const response = await fetch(
          `${baseUrl}/api/market/candles/history?symbol=${normalizedSymbol}&timeframe=${normalizedInterval}`,
          { cache: "no-store" }
        );

        if (response.ok) {
          const payload = (await response.json()) as Array<Record<string, unknown>>;
          nextCandles = deduplicateAndLimitCandles(mapHistoryToCandles(payload));
        }
      } catch (error) {
        console.warn("Market history hydrate failed, trying Binance fallback:", error);
      }

      if (nextCandles.length === 0) {
        nextCandles = deduplicateAndLimitCandles(await fetchBinanceHistory());
      }

      if (!cancelled && nextCandles.length > 0) {
        setCandles(nextCandles);
        const latest = nextCandles[nextCandles.length - 1];
        safeSetTicker({
          symbol: normalizedSymbol,
          price: latest.close,
          highPrice: Math.max(...nextCandles.map((item) => item.high)),
          lowPrice: Math.min(...nextCandles.map((item) => item.low)),
          volume: nextCandles.reduce((sum, item) => sum + item.volume, 0),
        });
      }
    };

    const connect = () => {
      try {
        stompClient = new Client({
          webSocketFactory: () => new SockJS(`${baseUrl}/ws-market`),
          reconnectDelay: 5000,
          heartbeatIncoming: 4000,
          heartbeatOutgoing: 4000,
          onConnect: () => {
            if (cancelled) return;
            setIsConnected(true);
            console.info(`[CHART WS CONNECTED] symbol=${normalizedSymbol} timeframe=${normalizedInterval}`);

            stompClient?.publish({
              destination: "/app/market/subscribe",
              body: JSON.stringify({ symbol: normalizedSymbol, interval: normalizedInterval }),
            });

            stompClient?.subscribe(`/topic/klines/${normalizedSymbol}`, (frame) => {
              try {
                const payload = JSON.parse(frame.body) as Record<string, unknown>;
                const candle = {
                  openTime: Number(payload.openTime ?? 0),
                  closeTime: Number(payload.closeTime ?? 0),
                  open: Number(payload.open ?? 0),
                  high: Number(payload.high ?? 0),
                  low: Number(payload.low ?? 0),
                  close: Number(payload.close ?? 0),
                  volume: Number(payload.volume ?? 0),
                  isClosed: Boolean(payload.isFinal ?? true),
                };

                if (!Number.isFinite(candle.openTime) || candle.openTime <= 0) {
                  return;
                }

                console.info(`[CHART UPDATE] symbol=${normalizedSymbol} timeframe=${normalizedInterval} close=${candle.close} volume=${candle.volume}`);

                setCandles((previous) => {
                  const merged = [...previous];
                  const index = merged.findIndex((item) => item.openTime === candle.openTime);

                  if (index >= 0) {
                    merged[index] = candle;
                  } else {
                    merged.push(candle);
                  }

                  return deduplicateAndLimitCandles(merged);
                });

                safeSetTicker({
                  symbol: normalizedSymbol,
                  price: candle.close,
                  highPrice: Math.max(Number(payload.high ?? candle.high), ticker?.highPrice ?? candle.high),
                  lowPrice: Math.min(Number(payload.low ?? candle.low), ticker?.lowPrice ?? candle.low),
                  volume: candle.volume,
                });
              } catch (error) {
                console.warn("Kline payload parse failed:", error);
              }
            });

            stompClient?.subscribe(`/topic/signals`, (frame) => {
              try {
                const payload = JSON.parse(frame.body) as Record<string, unknown>;
                const nextSignal = {
                  symbol: String(payload.symbol ?? normalizedSymbol),
                  direction: String(payload.direction ?? "NONE") as MarketSignal["direction"],
                  entry: Number(payload.entry ?? 0),
                  stopLoss: Number(payload.stopLoss ?? 0),
                  takeProfit: Number(payload.takeProfit ?? 0),
                  riskReward: String(payload.riskReward ?? "1:0"),
                  confidence: Number(payload.confidence ?? 0),
                  timestamp: Number(payload.timestamp ?? Date.now() / 1000),
                };
                if (nextSignal.symbol === normalizedSymbol) {
                  setSignal(nextSignal);
                }
              } catch (error) {
                console.warn("Signal payload parse failed:", error);
              }
            });
          },
          onDisconnect: () => {
            if (!cancelled) {
              console.info(`[CHART WS DISCONNECTED] symbol=${normalizedSymbol} timeframe=${normalizedInterval}`);
              setIsConnected(false);
            }
          },
          onWebSocketClose: () => {
            if (!cancelled) {
              console.warn(`[CHART WS RECONNECTING] symbol=${normalizedSymbol} timeframe=${normalizedInterval}`);
              setIsConnected(false);
            }
          },
          onStompError: () => {
            if (!cancelled) {
              console.warn(`[CHART WS RECONNECTING] symbol=${normalizedSymbol} timeframe=${normalizedInterval}`);
              setIsConnected(false);
            }
          },
        });

        stompClient.activate();
      } catch (error) {
        console.error("Failed to initialize STOMP client:", error);
        setIsConnected(false);
      }
    };

    void hydrateHistory();
    connect();

    return () => {
      cancelled = true;
      stompClient?.deactivate();
      setIsConnected(false);
    };
  }, [interval, symbolCode]);

  return { candles, ticker, signal, isConnected };
}

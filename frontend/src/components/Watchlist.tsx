import { useEffect, useMemo, useRef, useState } from "react";
import { Plus, Trash2, Wifi } from "lucide-react";
import type { TradeSettings } from "../context/SettingsContext";
import { useBinanceWebSocket } from "../hooks/useBinanceWebSocket";
import { useSMCAnalysis } from "../hooks/useSMCAnalysis";

const STORAGE_KEY = "snapchart_watchlist";
const DEFAULT_SYMBOLS = ["BTC", "ETH", "SOL", "BNB"];
const SYMBOL_SUGGESTIONS = ["BTC", "ETH", "SOL", "BNB", "NEAR", "AVAX", "LINK", "PEPE", "XRP", "ADA", "DOGE"];
type FlashDirection = "up" | "down" | null;

type BinanceTickerSnapshot = {
  symbol: string;
  price: number;
  changePercent: number;
};

const toFiniteNumber = (value: unknown): number => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
};

const parseTicker = (value: unknown): BinanceTickerSnapshot | null => {
  if (!value || typeof value !== "object") return null;
  const payload = value as Record<string, unknown>;
  const symbol = typeof payload.s === "string" ? payload.s.toUpperCase() : "";
  const price = toFiniteNumber(payload.c);
  if (!symbol || price <= 0) return null;
  return { symbol, price, changePercent: toFiniteNumber(payload.P) };
};

const parseTickerPayload = (value: unknown): BinanceTickerSnapshot[] => {
  if (Array.isArray(value)) return value.map(parseTicker).filter((item): item is BinanceTickerSnapshot => item !== null);
  const ticker = parseTicker(value);
  return ticker ? [ticker] : [];
};

function useWatchlistPrices(symbols: string[]) {
  const [prices, setPrices] = useState<Record<string, BinanceTickerSnapshot>>({});
  const symbolsKey = symbols.join(",");

  useEffect(() => {
    let cancelled = false;
    const requestedSymbols = new Set(symbolsKey.split(",").filter(Boolean).map((symbol) => `${symbol}USDT`));

    const fetchInitialPrices = async () => {
      try {
        const response = await fetch("https://api.binance.com/api/v3/ticker/24hr", { cache: "no-store" });
        if (!response.ok) return;
        const payload = (await response.json()) as unknown;
        const nextPrices = parseTickerPayload(payload).filter((ticker) => requestedSymbols.has(ticker.symbol));
        if (cancelled || nextPrices.length === 0) return;
        setPrices((current) => ({
          ...current,
          ...Object.fromEntries(nextPrices.map((ticker) => [ticker.symbol, ticker])),
        }));
      } catch {
        // Keep any previously received snapshot when REST is unavailable.
      }
    };

    void fetchInitialPrices();
    return () => { cancelled = true; };
  }, [symbolsKey]);

  useEffect(() => {
    let cancelled = false;
    let socket: WebSocket | null = null;
    let reconnectTimer: number | null = null;

    const connect = () => {
      if (cancelled) return;
      socket = new WebSocket("wss://stream.binance.com:9443/ws/!ticker@arr");
      socket.onmessage = (event) => {
        try {
          const nextPrices = parseTickerPayload(JSON.parse(event.data) as unknown);
          if (nextPrices.length === 0 || cancelled) return;
          const requestedSymbols = new Set(symbolsKey.split(",").filter(Boolean).map((symbol) => `${symbol}USDT`));
          const relevantPrices = nextPrices.filter((ticker) => requestedSymbols.has(ticker.symbol));
          if (relevantPrices.length > 0) {
            setPrices((current) => ({
              ...current,
              ...Object.fromEntries(relevantPrices.map((ticker) => [ticker.symbol, ticker])),
            }));
          }
        } catch {
          // Keep REST values if a stream payload is malformed.
        }
      };
      socket.onerror = () => socket?.close();
      socket.onclose = () => {
        socket = null;
        if (!cancelled) reconnectTimer = window.setTimeout(connect, 3000);
      };
    };

    connect();
    return () => {
      cancelled = true;
      if (reconnectTimer !== null) window.clearTimeout(reconnectTimer);
      socket?.close();
    };
  }, [symbolsKey]);

  return prices;
}

type WatchlistProps = {
  smcSettings: TradeSettings["smc"];
  minimumRiskReward: string;
  title: string;
  liveLabel: string;
  onSelectSymbol: (symbol: string) => void;
};

const readSymbols = (): string[] => {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "null") as unknown;
    if (!Array.isArray(stored)) return DEFAULT_SYMBOLS;
    const symbols = stored.filter((value): value is string => typeof value === "string")
      .map((value) => value.trim().toUpperCase())
      .filter((value) => /^[A-Z0-9]{2,15}$/.test(value));
    return symbols.length > 0 ? [...new Set(symbols)] : DEFAULT_SYMBOLS;
  } catch {
    return DEFAULT_SYMBOLS;
  }
};

const formatPrice = (value: number): string => {
  if (!Number.isFinite(value) || value <= 0) return "--";
  return new Intl.NumberFormat("en-US", {
    minimumFractionDigits: value < 1 ? 4 : value < 100 ? 2 : 0,
    maximumFractionDigits: value < 1 ? 6 : value < 100 ? 4 : 2,
  }).format(value);
};

function SMCBadges({ analysis, settings }: { analysis: ReturnType<typeof useSMCAnalysis>; settings: TradeSettings["smc"] }) {
  const badges = useMemo(() => {
    if (analysis.overallBiasScore === 0) return [];
    const next: string[] = [];
    if (settings.fvg && analysis.breakoutProbability >= 55) next.push("FVG");
    if (analysis.bias !== "NEUTRAL" && analysis.trendStrengthScore >= 45) next.push("BOS");
    if (settings.m5OrderBlock && analysis.bias !== "NEUTRAL" && analysis.overallBiasScore >= 60) next.push("OB");
    return next.slice(0, 3);
  }, [analysis, settings.fvg, settings.m5OrderBlock]);

  return badges.length > 0 ? (
    <div className="mt-1 flex justify-end gap-1">
      {badges.map((badge) => <span key={badge} className="rounded border border-cyan-400/20 bg-cyan-400/10 px-1.5 py-0.5 text-[9px] font-semibold text-cyan-200">{badge}</span>)}
    </div>
  ) : null;
}

function WatchlistRow({ symbol, marketPrice, smcSettings, minimumRiskReward, onSelect, onRemove }: {
  symbol: string;
  marketPrice?: BinanceTickerSnapshot;
  smcSettings: TradeSettings["smc"];
  minimumRiskReward: string;
  onSelect: () => void;
  onRemove: () => void;
}) {
  const { ticker, candles } = useBinanceWebSocket({ symbolCode: `${symbol}/USDT`, interval: "1h" });
  const analysis = useSMCAnalysis(candles, smcSettings, minimumRiskReward);
  const [flash, setFlash] = useState<FlashDirection>(null);
  const previousPrice = useRef<number | null>(null);
  const latestCandle = candles.at(-1);
  const dayAgoCandle = candles.at(-25);
  const currentPrice = marketPrice?.price ?? ticker?.price ?? latestCandle?.close ?? 0;
  const change = latestCandle && dayAgoCandle && dayAgoCandle.close > 0
    ? ((latestCandle.close - dayAgoCandle.close) / dayAgoCandle.close) * 100
    : marketPrice?.changePercent ?? ticker?.priceChangePercent ?? 0;

  useEffect(() => {
    if (currentPrice <= 0) return;
    if (previousPrice.current !== null && currentPrice !== previousPrice.current) {
      const direction: FlashDirection = currentPrice > previousPrice.current ? "up" : "down";
      previousPrice.current = currentPrice;
      const startTimer = window.setTimeout(() => setFlash(direction), 0);
      const endTimer = window.setTimeout(() => setFlash(null), 450);
      return () => {
        window.clearTimeout(startTimer);
        window.clearTimeout(endTimer);
      };
    }
    previousPrice.current = currentPrice;
    return undefined;
  }, [currentPrice]);

  const hasPrice = currentPrice > 0;

  return (
    <div className={`group flex items-center justify-between rounded-xl border px-3 py-2.5 transition-colors ${flash === "up" ? "border-emerald-400/50 bg-emerald-400/10" : flash === "down" ? "border-rose-400/50 bg-rose-400/10" : "border-slate-800 bg-slate-950/60 hover:border-slate-700"}`}>
      <button type="button" onClick={onSelect} className="flex min-w-0 flex-1 items-center gap-3 text-left">
        <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-slate-800 text-[10px] font-bold text-slate-200">{symbol.slice(0, 2)}</span>
        <span className="min-w-0">
          <span className="block text-sm font-medium text-slate-100">{symbol}</span>
          <SMCBadges analysis={analysis} settings={smcSettings} />
        </span>
      </button>
      <div className="ml-2 flex items-center gap-2">
        <div className="text-right">
          <div className="text-sm font-medium text-slate-100">{hasPrice ? formatPrice(currentPrice) : "--"}</div>
          <div className={`text-[10px] font-semibold ${change >= 0 ? "text-emerald-300" : "text-rose-300"}`}>
            {hasPrice ? `${change >= 0 ? "+" : ""}${change.toFixed(2)}%` : "--"}
          </div>
        </div>
        <button type="button" onClick={onRemove} aria-label={`Xóa ${symbol} khỏi watchlist`} className="rounded-md p-1.5 text-slate-600 opacity-0 transition hover:bg-rose-400/10 hover:text-rose-300 group-hover:opacity-100 focus:opacity-100">
          <Trash2 className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}

export function Watchlist({ smcSettings, minimumRiskReward, title, liveLabel, onSelectSymbol }: WatchlistProps) {
  const [symbols, setSymbols] = useState<string[]>(readSymbols);
  const [isAdding, setIsAdding] = useState(false);
  const [query, setQuery] = useState("");
  const prices = useWatchlistPrices(symbols);

  useEffect(() => {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(symbols)); } catch { /* Storage may be blocked by the browser. */ }
  }, [symbols]);

  const matches = useMemo(() => {
    const normalizedQuery = query.trim().toUpperCase();
    return SYMBOL_SUGGESTIONS.filter((symbol) => symbol.includes(normalizedQuery) && !symbols.includes(symbol)).slice(0, 6);
  }, [query, symbols]);

  const addSymbol = (value: string) => {
    const symbol = value.trim().toUpperCase();
    if (!/^[A-Z0-9]{2,15}$/.test(symbol) || symbols.includes(symbol)) return;
    setSymbols((current) => [...current, symbol]);
    setQuery("");
    setIsAdding(false);
  };

  const removeSymbol = (symbol: string) => setSymbols((current) => current.filter((item) => item !== symbol));

  return (
    <div className="rounded-[24px] border border-slate-800 bg-[#121721] p-4">
      <div className="mb-3 flex items-center justify-between">
        <div>
          <h3 className="text-sm font-semibold text-slate-100">{title}</h3>
          <span className="mt-1 flex items-center gap-1 text-[10px] uppercase tracking-[0.18em] text-slate-500"><Wifi className="h-3 w-3 text-emerald-400" />{liveLabel}</span>
        </div>
        <div className="relative">
          <button type="button" aria-label="Thêm cặp giao dịch" aria-expanded={isAdding} onClick={() => setIsAdding((open) => !open)} className="rounded-lg border border-slate-700 bg-slate-950 p-1.5 text-slate-300 transition hover:border-cyan-400/60 hover:text-cyan-200">
            <Plus className="h-4 w-4" />
          </button>
          {isAdding ? (
            <div className="absolute right-0 top-10 z-20 w-56 rounded-xl border border-slate-700 bg-[#111722] p-2 shadow-2xl shadow-black/50">
              <input autoFocus value={query} onChange={(event) => setQuery(event.target.value.replace(/[^a-z0-9]/gi, ""))} onKeyDown={(event) => { if (event.key === "Enter") addSymbol(query); if (event.key === "Escape") setIsAdding(false); }} placeholder="Tìm mã coin..." className="w-full rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-2 text-xs text-slate-100 outline-none placeholder:text-slate-500 focus:border-cyan-400/60" />
              <div className="mt-1 max-h-40 overflow-y-auto">
                {matches.map((symbol) => <button key={symbol} type="button" onClick={() => addSymbol(symbol)} className="flex w-full items-center justify-between rounded-lg px-2.5 py-2 text-left text-xs text-slate-300 hover:bg-slate-800 hover:text-cyan-200"><span>{symbol}/USDT</span><Plus className="h-3 w-3" /></button>)}
                {query && matches.length === 0 ? <button type="button" onClick={() => addSymbol(query)} className="w-full rounded-lg px-2.5 py-2 text-left text-xs text-cyan-200 hover:bg-slate-800">Thêm {query.toUpperCase()}/USDT</button> : null}
              </div>
            </div>
          ) : null}
        </div>
      </div>
      <div className="space-y-2">
        {symbols.map((symbol) => <WatchlistRow key={symbol} symbol={symbol} marketPrice={prices[`${symbol}USDT`]} smcSettings={smcSettings} minimumRiskReward={minimumRiskReward} onSelect={() => onSelectSymbol(`${symbol}/USDT`)} onRemove={() => removeSymbol(symbol)} />)}
      </div>
    </div>
  );
}

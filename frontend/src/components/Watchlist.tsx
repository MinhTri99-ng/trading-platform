import { useEffect, useMemo, useRef, useState } from "react";
import { Plus, Trash2, Wifi } from "lucide-react";

const STORAGE_KEY = "snapchart_watchlist";
const DEFAULT_SYMBOLS = ["BTC", "ETH", "SOL", "BNB"];
const SYMBOL_SUGGESTIONS = ["BTC", "ETH", "SOL", "BNB", "NEAR", "AVAX", "LINK", "PEPE", "XRP", "ADA", "DOGE"];
type FlashDirection = "up" | "down" | null;

type PriceSnapshot = {
  price: number;
  change24h: number;
};

type PriceState = Record<string, PriceSnapshot>;

const DEFAULT_PRICES: PriceState = {
  BTCUSDT: { price: 80540.01, change24h: 2.84 },
  ETHUSDT: { price: 3524.00, change24h: 1.76 },
  SOLUSDT: { price: 168.00, change24h: 3.34 },
  NEARUSDT: { price: 5.20, change24h: 2.10 },
  LINKUSDT: { price: 12.05, change24h: -2.48 },
  BNBUSDT: { price: 598.00, change24h: 1.21 },
  XRPUSDT: { price: 0.62, change24h: -0.42 },
  DOGEUSDT: { price: 0.17, change24h: 2.11 },
};

const getSymbolKey = (symbol: string): string => {
  const clean = symbol.toUpperCase().trim();
  return clean.endsWith("USDT") ? clean : `${clean}USDT`;
};

function useWatchlistPrices() {
  const [prices, setPrices] = useState<PriceState>(DEFAULT_PRICES);

  useEffect(() => {
    let cancelled = false;

    const fetchPrices = async () => {
      try {
        const response = await fetch("https://api.binance.com/api/v3/ticker/24hr", { cache: "no-store" });
        if (!response.ok) return;
        const data = (await response.json()) as unknown;
        if (cancelled || !Array.isArray(data)) return;

        setPrices((prevPrices) => {
          const nextPrices = { ...prevPrices };
          let updated = false;

          data.forEach((value) => {
            if (!value || typeof value !== "object") return;
            const item = value as Record<string, unknown>;
            const symbol = typeof item.symbol === "string" ? item.symbol.toUpperCase().trim() : "";
            const newPrice = parseFloat(String(item.lastPrice));
            const newChange = parseFloat(String(item.priceChangePercent));

            if (!symbol || !Number.isFinite(newPrice) || !Number.isFinite(newChange) || newPrice <= 0) return;
            if (!nextPrices[symbol] || nextPrices[symbol].price !== newPrice) {
              nextPrices[symbol] = { price: newPrice, change24h: newChange };
              updated = true;
            }
          });

          return updated ? { ...nextPrices } : prevPrices;
        });
      } catch {
        // Keep the last known snapshot when REST is unavailable.
      }
    };

    void fetchPrices();
    const interval = window.setInterval(fetchPrices, 2000);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, []);

  return prices;
}

type WatchlistProps = {
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

function WatchlistRow({ symbol, marketPrice, onSelect, onRemove }: {
  symbol: string;
  marketPrice: PriceSnapshot;
  onSelect: () => void;
  onRemove: () => void;
}) {
  const [flash, setFlash] = useState<FlashDirection>(null);
  const previousPrice = useRef<number | null>(null);
  const currentPrice = marketPrice.price;
  const change = marketPrice.change24h;
  const displayPrice = marketPrice.price.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const displayChange = marketPrice.change24h.toFixed(2);

  useEffect(() => {
    if (currentPrice <= 0) return;
    if (previousPrice.current !== null && currentPrice !== previousPrice.current) {
      const direction: FlashDirection = currentPrice > previousPrice.current ? "up" : "down";
      previousPrice.current = currentPrice;
      const startTimer = window.setTimeout(() => setFlash(direction), 0);
      const endTimer = window.setTimeout(() => setFlash(null), 300);
      return () => {
        window.clearTimeout(startTimer);
        window.clearTimeout(endTimer);
      };
    }
    previousPrice.current = currentPrice;
    return undefined;
  }, [currentPrice]);

  const flashTextClass = flash === "up" ? "text-green-400" : flash === "down" ? "text-red-400" : "text-slate-100";
  const flashChangeClass = flash === "up" ? "text-green-400" : flash === "down" ? "text-red-400" : change >= 0 ? "text-emerald-300" : "text-rose-300";

  return (
    <div className={`group flex items-center justify-between rounded-xl border px-3 py-2.5 transition-colors ${flash === "up" ? "border-green-400/50 bg-green-500/10 text-green-400" : flash === "down" ? "border-red-400/50 bg-red-500/10 text-red-400" : "border-slate-800 bg-slate-950/60 hover:border-slate-700"}`}>
      <button type="button" onClick={onSelect} className="flex min-w-0 flex-1 items-center gap-3 text-left">
        <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-slate-800 text-[10px] font-bold text-slate-200">{symbol.slice(0, 2)}</span>
        <span className="min-w-0">
          <span className="block text-sm font-medium text-slate-100">{symbol}</span>
        </span>
      </button>
      <div className="ml-2 flex items-center gap-2">
        <div className="text-right">
          <div className={`text-sm font-medium ${flashTextClass}`}>{displayPrice}</div>
          <div className={`text-[10px] font-semibold ${flashChangeClass}`}>
            {`${change >= 0 ? "+" : ""}${displayChange}%`}
          </div>
        </div>
        <button type="button" onClick={onRemove} aria-label={`Xóa ${symbol} khỏi watchlist`} className="rounded-md p-1.5 text-slate-600 opacity-0 transition hover:bg-rose-400/10 hover:text-rose-300 group-hover:opacity-100 focus:opacity-100">
          <Trash2 className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}

export function Watchlist({ title, liveLabel, onSelectSymbol }: WatchlistProps) {
  const [symbols, setSymbols] = useState<string[]>(readSymbols);
  const [isAdding, setIsAdding] = useState(false);
  const [query, setQuery] = useState("");
  const prices = useWatchlistPrices();

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
        {symbols.map((symbol) => {
          const key = getSymbolKey(symbol);
          const item = prices[key] || prices[symbol] || DEFAULT_PRICES[key] || { price: 0, change24h: 0 };
          return <WatchlistRow key={symbol} symbol={symbol} marketPrice={item} onSelect={() => onSelectSymbol(`${symbol}/USDT`)} onRemove={() => removeSymbol(symbol)} />;
        })}
      </div>
    </div>
  );
}

import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Bot,
  BrainCircuit,
  CandlestickChart,
  ChevronDown,
  Search,
  Settings,
  ShieldCheck,
  Sparkles,
} from "lucide-react";

import { TradingChart } from "./components/TradingChart";
import { useMarketRealtime, type MarketSignal } from "./hooks/useMarketRealtime";

type Timeframe = "1m" | "5m" | "15m" | "30m" | "1H" | "4H" | "1D" | "1W";

type MarketCard = {
  pair: string;
  price: number;
  change: number;
  sentiment: "Bullish LONG" | "Neutral WATCH";
  spark: number[];
};

const timeframes = ["1m", "5m", "15m", "30m", "1H", "4H", "1D", "1W"] as const;

const languageOptions = [
  { code: "vi", label: "Tiếng Việt" },
  { code: "en", label: "English" },
  { code: "zh", label: "中文" },
  { code: "ja", label: "日本語" },
  { code: "ko", label: "한국어" },
] as const;

const quickSymbols = ["BTC/USDT", "ETH/USDT", "SOL/USDT", "XRP/USDT", "BNB/USDT"];

const marketCards: MarketCard[] = [
  { pair: "BTC/USDT", price: 67340, change: 2.84, sentiment: "Bullish LONG", spark: [65, 66, 67, 66.8, 67.2, 67.7, 68.3, 68.7, 68.2, 68.9] },
  { pair: "ETH/USDT", price: 3524.12, change: 1.76, sentiment: "Bullish LONG", spark: [34.2, 34.8, 34.6, 35.1, 35.7, 35.5, 35.2, 35.9, 36.4, 36.8] },
  { pair: "SOL/USDT", price: 168.45, change: 3.34, sentiment: "Bullish LONG", spark: [154, 156, 161, 157, 162, 164, 167, 168, 170, 169] },
  { pair: "XRP/USDT", price: 0.6221, change: -0.42, sentiment: "Neutral WATCH", spark: [0.61, 0.62, 0.618, 0.614, 0.619, 0.621, 0.620, 0.618, 0.615, 0.617] },
];

const watchlist = [
  { symbol: "BTC", price: 67340.25, change: 2.84 },
  { symbol: "ETH", price: 3524.12, change: 1.76 },
  { symbol: "SOL", price: 168.45, change: 3.34 },
  { symbol: "XRP", price: 0.6221, change: -0.42 },
  { symbol: "BNB", price: 598.34, change: 1.21 },
  { symbol: "ADA", price: 0.74, change: -1.28 },
  { symbol: "DOGE", price: 0.1742, change: 2.11 },
];

const aiProgress = [
  { key: "dashboard.overallBias", label: "Overall Bias", value: 78 },
  { key: "dashboard.trendStrength", label: "Trend Strength", value: 82 },
  { key: "dashboard.breakoutProbability", label: "Breakout Probability", value: 71 },
];

type ScreenshotStatus = "idle" | "uploading" | "extracting" | "verifying" | "completed" | "invalid";

type ScreenshotAnalysisResult = {
  status: string;
  valid: boolean;
  reason?: string;
  symbol?: string;
  timeframe?: string;
  pattern?: string;
  indicatorsVisible?: string[];
  verification?: string;
  marketSnapshot?: string;
  direction?: "LONG" | "SHORT" | "NONE";
  entry?: number;
  stopLoss?: number;
  takeProfit?: number;
  riskReward?: string;
  confidence?: number;
};

type TradeHistoryEntry = {
  id: string;
  symbol: string;
  direction: "LONG" | "SHORT";
  entry: number;
  stopLoss: number;
  takeProfit: number;
  confidence: number;
  timeframe: string;
  status: "TP" | "SL";
  createdAt: string;
};

const STORAGE_KEYS = {
  tradeHistory: "trading-platform-trade-history",
};

const readTradeHistory = (): TradeHistoryEntry[] => {
  try {
    const value = localStorage.getItem(STORAGE_KEYS.tradeHistory);
    return value ? (JSON.parse(value) as TradeHistoryEntry[]) : [];
  } catch {
    return [];
  }
};

const formatMoney = (value: number) =>
  new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: value >= 1000 ? 0 : 2,
  }).format(value);

const formatCompact = (value: number) =>
  new Intl.NumberFormat("en-US", {
    maximumFractionDigits: value < 100 ? 2 : 0,
  }).format(value);

function StatBadge({ children, tone = "neutral" }: { children: React.ReactNode; tone?: "neutral" | "positive" | "negative" }) {
  const toneMap = {
    neutral: "border border-slate-700 bg-slate-800/80 text-slate-200",
    positive: "border border-emerald-500/30 bg-emerald-500/10 text-emerald-300",
    negative: "border border-rose-500/30 bg-rose-500/10 text-rose-300",
  };

  return <span className={`inline-flex rounded-full px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] ${toneMap[tone]}`}>{children}</span>;
}

function App() {
  const { t, i18n } = useTranslation();
  const [selectedSymbol, setSelectedSymbol] = useState("BTC/USDT");
  const [selectedTimeframe, setSelectedTimeframe] = useState<Timeframe>("4H");
  const [indicatorState, setIndicatorState] = useState({ ema50: true, ema200: true, volume: true });
  const [uploadStatus, setUploadStatus] = useState<ScreenshotStatus>("idle");
  const [dragActive, setDragActive] = useState(false);
  const [analysisResult, setAnalysisResult] = useState<ScreenshotAnalysisResult | null>(null);
  const [visionSignal, setVisionSignal] = useState<MarketSignal | null>(null);
  const [tradingSignalPanel, setTradingSignalPanel] = useState<MarketSignal | null>(null);
  const [tradeHistory, setTradeHistory] = useState<TradeHistoryEntry[]>(() => readTradeHistory());

  const historyPreview = tradeHistory.length > 0 ? tradeHistory.slice(0, 5) : [
    { id: "demo-1", symbol: "BTC/USDT", direction: "LONG" as const, entry: 67340, stopLoss: 66800, takeProfit: 68200, confidence: 82, timeframe: "4H", status: "TP" as const, createdAt: new Date().toISOString() },
    { id: "demo-2", symbol: "ETH/USDT", direction: "LONG" as const, entry: 3524, stopLoss: 3450, takeProfit: 3680, confidence: 76, timeframe: "1H", status: "SL" as const, createdAt: new Date(Date.now() - 3600000).toISOString() },
  ];

  const currentLanguage = i18n.resolvedLanguage || i18n.language || "en";

  const symbolPrice = useMemo(() => {
    if (selectedSymbol === "BTC/USDT") return 67340.25;
    if (selectedSymbol === "ETH/USDT") return 3524.12;
    if (selectedSymbol === "SOL/USDT") return 168.45;
    if (selectedSymbol === "XRP/USDT") return 0.6221;
    return 598.34;
  }, [selectedSymbol]);

  const normalizePairForDisplay = (value?: string) => {
    if (!value) return "BTC/USDT";
    const raw = value.replace(/\s+/g, "").replace("/", "").toUpperCase();
    if (raw.length >= 6 && !raw.endsWith("USDT")) {
      return `${raw.slice(0, -3)}/${raw.slice(-3)}`;
    }
    return raw.length >= 6 ? `${raw.slice(0, 3)}/${raw.slice(3)}` : raw;
  };

  const normalizeTimeframeForDisplay = (value?: string) => {
    if (!value) return "4H";
    const map: Record<string, Timeframe> = {
      "1m": "1m",
      "5m": "5m",
      "15m": "15m",
      "30m": "30m",
      "1h": "1H",
      "4h": "4H",
      "1d": "1D",
      "1w": "1W",
    };
    const key = value.toLowerCase();
    return map[key] ?? (key.includes("h") ? "4H" : key.includes("d") ? "1D" : key.includes("w") ? "1W" : "15m");
  };

  const timeframeToBinance = (timeframe: Timeframe) => {
    const map: Record<Timeframe, string> = {
      "1m": "1m",
      "5m": "5m",
      "15m": "15m",
      "30m": "30m",
      "1H": "1h",
      "4H": "4h",
      "1D": "1d",
      "1W": "1w",
    };
    return map[timeframe];
  };

  const { candles, signal, isConnected } = useMarketRealtime({
    symbolCode: selectedSymbol,
    interval: timeframeToBinance(selectedTimeframe),
  });

  const activeSignal = visionSignal ?? signal ?? tradingSignalPanel;
  const hasSignal = Boolean(activeSignal && activeSignal.direction !== "NONE" && activeSignal.entry && activeSignal.entry > 0);

  const structureMarkers = useMemo(() => {
    const base = activeSignal?.entry ?? symbolPrice;
    if (!base) {
      return null;
    }

    const directionFactor = activeSignal?.direction === "SHORT" ? -1 : 1;

    return {
      poi: base * (1 - 0.002 * directionFactor),
      inducement: base * (1 - 0.007 * directionFactor),
      bos: base * (1 - 0.011 * directionFactor),
      hl: base * (1 + 0.018 * directionFactor),
      hh: base * (1 + 0.028 * directionFactor),
    };
  }, [activeSignal, symbolPrice]);

  const toggleIndicator = (key: keyof typeof indicatorState) => {
    setIndicatorState((previous) => ({ ...previous, [key]: !previous[key] }));
  };

  const persistTradeHistory = (entry: TradeHistoryEntry) => {
    setTradeHistory((previous) => {
      const next = [entry, ...previous].slice(0, 8);
      try {
        localStorage.setItem(STORAGE_KEYS.tradeHistory, JSON.stringify(next));
      } catch {
        // Ignore storage errors.
      }
      return next;
    });
  };

  const handleResetSignals = () => {
    setAnalysisResult(null);
    setVisionSignal(null);
    setTradingSignalPanel(null);
    setUploadStatus("idle");
  };

  const handleAnalyzeScreenshot = async (file: File) => {
    const validType = file.type.startsWith("image/") || /\.(png|jpe?g|webp)$/i.test(file.name);
    if (!validType) {
      setUploadStatus("invalid");
      setAnalysisResult({
        status: "INVALID INPUT",
        valid: false,
        reason: "Unsupported file type. Please upload a chart screenshot image.",
      });
      return;
    }

    setUploadStatus("uploading");
    setAnalysisResult(null);

    const formData = new FormData();
    formData.append("file", file, file.name);
    formData.append("symbol", selectedSymbol.replace(/\//g, ""));
    formData.append("timeframe", timeframeToBinance(selectedTimeframe));

    try {
      setUploadStatus("extracting");
      console.info("[FRONTEND UPLOAD]", {
        filename: file.name,
        contentType: file.type,
        size: file.size,
        endpoint: "http://localhost:8080/api/vision/analyze",
        selectedSymbol,
        selectedTimeframe,
      });

      const response = await fetch("http://localhost:8080/api/vision/analyze", {
        method: "POST",
        body: formData,
      });

      const payload = (await response.json()) as ScreenshotAnalysisResult & { reason?: string };

      if (!response.ok || !payload || payload.status === "INVALID INPUT" || payload.status === "INVALID_INPUT" || payload.status === "TIMEFRAME_UNDETECTED") {
        setUploadStatus("invalid");
        setAnalysisResult({
          status: payload?.status ?? "INVALID INPUT",
          valid: false,
          reason: payload?.reason ?? (payload?.status === "TIMEFRAME_UNDETECTED" ? "Chart timeframe could not be detected." : "Image quality too low"),
        });
        return;
      }

      if (payload.status === "MARKET_DATA_UNAVAILABLE") {
        setUploadStatus("invalid");
        setAnalysisResult({
          status: payload.status,
          valid: false,
          reason: payload.reason ?? "Market data unavailable for verification.",
          symbol: payload.symbol,
          timeframe: payload.timeframe,
          verification: "MARKET_DATA_UNAVAILABLE",
        });
        return;
      }

      setUploadStatus("verifying");
      const generatedSignal = payload.valid ? {
        direction: payload.direction ?? "LONG",
        entry: Number(payload.entry ?? 0),
        stopLoss: Number(payload.stopLoss ?? 0),
        takeProfit: Number(payload.takeProfit ?? 0),
        riskReward: String(payload.riskReward ?? "1:0"),
        confidence: Number(payload.confidence ?? 0),
      } : null;

      setAnalysisResult({
        status: payload.status ?? "VERIFIED",
        valid: Boolean(payload.valid),
        reason: payload.reason ?? "Verified with Live Market Data",
        symbol: payload.symbol,
        timeframe: payload.timeframe,
        pattern: payload.pattern,
        indicatorsVisible: payload.indicatorsVisible ?? [],
        verification: payload.verification ?? "Verified with Live Market Data",
        marketSnapshot: payload.marketSnapshot,
        direction: generatedSignal?.direction,
        entry: generatedSignal?.entry,
        stopLoss: generatedSignal?.stopLoss,
        takeProfit: generatedSignal?.takeProfit,
        riskReward: generatedSignal?.riskReward,
        confidence: generatedSignal?.confidence,
      });

      if (payload.valid && generatedSignal && generatedSignal.direction !== "NONE") {
        const verifiedSymbol = normalizePairForDisplay(payload.symbol ?? selectedSymbol);
        const verifiedTimeframe = normalizeTimeframeForDisplay(payload.timeframe ?? selectedTimeframe);

        setSelectedSymbol(verifiedSymbol);
        setSelectedTimeframe(verifiedTimeframe);

        const tradeEntry: TradeHistoryEntry = {
          id: `${Date.now()}`,
          symbol: verifiedSymbol,
          direction: generatedSignal.direction,
          entry: generatedSignal.entry,
          stopLoss: generatedSignal.stopLoss,
          takeProfit: generatedSignal.takeProfit,
          confidence: generatedSignal.confidence,
          timeframe: verifiedTimeframe,
          status: generatedSignal.confidence >= 80 ? "TP" : "SL",
          createdAt: new Date().toISOString(),
        };

        setVisionSignal({
          symbol: verifiedSymbol,
          direction: generatedSignal.direction,
          entry: generatedSignal.entry,
          stopLoss: generatedSignal.stopLoss,
          takeProfit: generatedSignal.takeProfit,
          riskReward: generatedSignal.riskReward,
          confidence: generatedSignal.confidence,
          timestamp: Date.now(),
        });
        setTradingSignalPanel({
          symbol: verifiedSymbol,
          direction: generatedSignal.direction,
          entry: generatedSignal.entry,
          stopLoss: generatedSignal.stopLoss,
          takeProfit: generatedSignal.takeProfit,
          riskReward: generatedSignal.riskReward,
          confidence: generatedSignal.confidence,
          timestamp: Date.now(),
        });
        persistTradeHistory(tradeEntry);
        setUploadStatus("completed");
      } else {
        setVisionSignal(null);
        setTradingSignalPanel(null);
        setUploadStatus("invalid");
      }
    } catch (error) {
      setUploadStatus("invalid");
      setAnalysisResult({
        status: "INVALID INPUT",
        valid: false,
        reason: "Market verification failed. Image cannot be used as price source of truth.",
      });
      console.error("Screenshot analysis failed:", error);
    }
  };

  const handleFileInput = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      void handleAnalyzeScreenshot(file);
    }
  };

  return (
    <div className="min-h-screen bg-[#0B0E14] text-slate-100">
      <header className="sticky top-0 z-50 border-b border-slate-800/90 bg-[#0B0E14]/90 backdrop-blur-xl">
        <div className="mx-auto flex max-w-[1700px] items-center justify-between gap-4 overflow-x-auto whitespace-nowrap px-4 py-3.5 xl:px-6">
          <div className="flex min-w-0 items-center gap-4">
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 via-cyan-500 to-indigo-500 shadow-[0_0_20px_rgba(59,130,246,0.45)]">
                <CandlestickChart className="h-4 w-4 text-white" />
              </div>
              <div className="flex items-center gap-2">
                <span className="text-lg font-semibold tracking-tight text-white">TradeAI</span>
                <span className="rounded border border-blue-500/30 bg-blue-500/10 px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-[0.2em] text-blue-300">PRO</span>
              </div>
            </div>

            <div className="hidden items-center gap-2 md:flex">
              {quickSymbols.map((symbol) => {
                const active = symbol === selectedSymbol;
                return (
                  <button
                    key={symbol}
                    type="button"
                    onClick={() => setSelectedSymbol(symbol)}
                    className={`rounded-xl border px-3 py-1.5 text-[11px] font-medium transition ${
                      active
                        ? "border-blue-500/40 bg-blue-500/10 text-blue-200"
                        : "border-slate-800 bg-slate-900/70 text-slate-400 hover:border-slate-700 hover:text-slate-200"
                    }`}
                  >
                    {symbol}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="hidden items-center gap-5 lg:flex">
            <div className="flex items-center gap-3 rounded-full border border-slate-800 bg-slate-900/80 px-3 py-1.5">
              <span className="text-[10px] uppercase tracking-[0.18em] text-slate-500">{t("nav.marketCap")}</span>
              <span className="text-sm font-semibold text-slate-100">$2.41T</span>
              <span className="text-xs font-medium text-emerald-300">+1.8%</span>
            </div>
            <div className="flex items-center gap-3 rounded-full border border-slate-800 bg-slate-900/80 px-3 py-1.5">
              <span className="text-[10px] uppercase tracking-[0.18em] text-slate-500">{t("nav.btcDom")}</span>
              <span className="text-sm font-semibold text-slate-100">54.2%</span>
              <span className="text-xs font-medium text-emerald-300">+0.3%</span>
            </div>
            <div className="flex items-center gap-3 rounded-full border border-slate-800 bg-slate-900/80 px-3 py-1.5">
              <span className="text-[10px] uppercase tracking-[0.18em] text-slate-500">{t("nav.volume24h")}</span>
              <span className="text-sm font-semibold text-slate-100">$98.4B</span>
            </div>
          </div>

          <div className="flex items-center gap-2 sm:gap-3">
            <button className="flex h-9 w-9 items-center justify-center rounded-xl border border-slate-800 bg-slate-900/80 text-slate-200 transition hover:border-slate-700">
              <Search className="h-4 w-4" />
            </button>
            <button className="flex items-center gap-2 rounded-xl border border-violet-500/35 bg-violet-500/10 px-3 py-2 text-xs font-semibold text-violet-200 transition hover:border-violet-400/50">
              <Bot className="h-3.5 w-3.5" />
              {t("nav.askAi")}
            </button>
            <div className="flex items-center gap-2 rounded-xl border border-slate-800 bg-slate-900/80 px-2 py-1.5">
              <span className="text-[10px] uppercase tracking-[0.18em] text-slate-500">{t("nav.language")}</span>
              <select
                aria-label={t("nav.language")}
                value={currentLanguage}
                onChange={(event) => {
                  const nextLanguage = event.target.value;
                  if (nextLanguage && nextLanguage !== currentLanguage) {
                    void i18n.changeLanguage(nextLanguage);
                  }
                }}
                className="rounded-lg border border-slate-700 bg-slate-950 px-2 py-1 text-xs font-medium text-slate-100 outline-none transition focus:border-blue-500"
              >
                {languageOptions.map((language) => (
                  <option key={language.code} value={language.code}>
                    {language.label}
                  </option>
                ))}
              </select>
            </div>
            <button className="flex h-9 w-9 items-center justify-center rounded-xl border border-slate-800 bg-slate-900/80 text-slate-200 transition hover:border-slate-700">
              <Settings className="h-4 w-4" />
            </button>
            <button className="flex items-center gap-3 rounded-xl border border-slate-800 bg-slate-900/80 px-2.5 py-1.5">
              <div className="flex h-7 w-7 items-center justify-center rounded-full bg-gradient-to-br from-sky-500 to-indigo-600 text-[10px] font-bold text-white">J</div>
              <span className="hidden text-sm font-medium text-slate-100 sm:block">James</span>
              <ChevronDown className="hidden h-4 w-4 text-slate-400 sm:block" />
            </button>
          </div>
        </div>
      </header>

      <div className="mx-auto max-w-[1700px] px-4 pb-12 pt-5 xl:px-6">
        <div className="flex flex-col lg:flex-row w-full gap-4">
          <aside className="hidden w-[260px] shrink-0 flex-col rounded-[22px] border border-slate-800 bg-[#121721] p-3 lg:flex">
            <div className="mb-5 px-2 pt-1">
              <p className="text-[10px] uppercase tracking-[0.2em] text-slate-500">{t("dashboard.tradeHistory")}</p>
            </div>

            <div className="space-y-2">
              {historyPreview.map((entry) => (
                <div key={entry.id} className="rounded-2xl border border-slate-800 bg-slate-950/60 p-2.5">
                  <div className="mb-2 flex items-center justify-between gap-2">
                    <span className="text-sm font-semibold text-slate-100">{entry.symbol}</span>
                    <span className={`rounded-full px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-[0.12em] ${entry.status === "TP" ? "bg-emerald-500/15 text-emerald-300" : "bg-rose-500/15 text-rose-300"}`}>
                      {entry.status}
                    </span>
                  </div>
                  <div className="space-y-1 text-[10px] text-slate-400">
                    <div className="flex justify-between"><span>{entry.direction}</span><span>{entry.timeframe}</span></div>
                    <div className="flex justify-between"><span>Vào</span><span>{formatMoney(entry.entry)}</span></div>
                    <div className="flex justify-between"><span>TP</span><span>{formatMoney(entry.takeProfit)}</span></div>
                    <div className="flex justify-between"><span>SL</span><span>{formatMoney(entry.stopLoss)}</span></div>
                  </div>
                </div>
              ))}
            </div>

            <div className="mt-auto rounded-xl border border-slate-800 bg-slate-950/60 px-3 py-2 text-[10px] uppercase tracking-[0.18em] text-slate-500">
              {t("common.history")}
            </div>
          </aside>

          <div className="min-w-0 flex-1">
            <div className="mb-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              {marketCards.map((card) => (
                <div key={card.pair} className="rounded-[20px] border border-slate-800 bg-[#121721] p-3.5">
                  <div className="mb-3 flex items-start justify-between gap-2">
                    <div>
                      <p className="text-sm font-semibold text-slate-100">{card.pair}</p>
                      <p className="mt-1 text-lg font-bold text-white">{formatMoney(card.price)}</p>
                    </div>
                    <StatBadge tone={card.change >= 0 ? "positive" : "negative"}>{card.change >= 0 ? "+" : ""}{card.change.toFixed(2)}%</StatBadge>
                  </div>

                  <div className="mb-3 flex h-8 items-end gap-[2px]">
                    {card.spark.map((point, idx) => (
                      <span
                        key={`${card.pair}-${idx}`}
                        className={`w-full rounded-sm ${card.change >= 0 ? "bg-emerald-400/80" : "bg-rose-400/80"}`}
                        style={{ height: `${Math.max(18, point / 3.5)}%` }}
                      />
                    ))}
                  </div>

                  <div className="flex items-center justify-between gap-2">
                    <span className="text-[11px] text-slate-400">24h</span>
                    <span className={`rounded-full px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] ${card.sentiment === "Bullish LONG" ? "bg-emerald-500/15 text-emerald-300" : "bg-amber-500/15 text-amber-300"}`}>
                      {card.sentiment}
                    </span>
                  </div>
                </div>
              ))}
            </div>

            <div className="rounded-[24px] border border-slate-800 bg-[#121721] p-3 sm:p-4">
              <div className="mb-4 flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                <div className="flex items-center gap-3">
                  <div>
                    <p className="text-[11px] uppercase tracking-[0.18em] text-slate-500">Pair</p>
                    <div className="mt-1 flex items-center gap-2">
                      <span className="text-2xl font-bold text-white">{selectedSymbol}</span>
                      <StatBadge tone="positive">+{(symbolPrice / 2000).toFixed(2)}%</StatBadge>
                    </div>
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  {timeframes.map((timeframe) => (
                    <button
                      key={timeframe}
                      type="button"
                      onClick={() => setSelectedTimeframe(timeframe)}
                      className={`rounded-xl px-2.5 py-1.5 text-[11px] font-medium transition ${
                        selectedTimeframe === timeframe
                          ? "bg-blue-500 text-white shadow-[0_0_18px_rgba(59,130,246,0.4)]"
                          : "bg-slate-900/80 text-slate-300 hover:bg-slate-800"
                      }`}
                    >
                      {timeframe}
                    </button>
                  ))}
                </div>
              </div>

              <div className="mb-4 flex flex-wrap items-center gap-2">
                {[
                  { label: "EMA 50", key: "ema50" as const, active: indicatorState.ema50 },
                  { label: "EMA 200", key: "ema200" as const, active: indicatorState.ema200 },
                  { label: "Volume", key: "volume" as const, active: indicatorState.volume },
                  { label: "ATR", key: "atr" as const, active: false },
                ].map((indicator) => (
                  <button
                    key={indicator.label}
                    type="button"
                    onClick={() => indicator.key !== "atr" && toggleIndicator(indicator.key)}
                    className={`rounded-full border px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] ${
                      indicator.active ? "border-blue-500/35 bg-blue-500/10 text-blue-200" : "border-slate-700 bg-slate-900/70 text-slate-400"
                    }`}
                  >
                    {indicator.label}
                  </button>
                ))}
              </div>

              <div className="relative overflow-hidden rounded-[22px] border border-slate-800 bg-[#0B0E14]">
                <div className="absolute inset-x-0 top-0 z-20 flex items-center justify-between border-b border-slate-800 bg-slate-950/40 px-4 py-2 text-[10px] uppercase tracking-[0.16em] text-slate-400 backdrop-blur-sm">
                  <span>{selectedSymbol} • {selectedTimeframe}</span>
                  <span className="flex items-center gap-2 text-emerald-300"><span className="h-2 w-2 rounded-full bg-emerald-400" />{isConnected ? t("common.live") : t("common.offline")}</span>
                </div>

                <div className="relative pt-11">
                  <TradingChart
                    symbol={selectedSymbol}
                    candles={candles}
                    isConnected={isConnected}
                    showEma50={indicatorState.ema50}
                    showEma200={indicatorState.ema200}
                    showVolume={indicatorState.volume}
                    positionType={activeSignal?.direction ?? null}
                    entryLine={activeSignal?.entry ?? null}
                    stopLossLine={activeSignal?.stopLoss ?? null}
                    takeProfitLine={activeSignal?.takeProfit ?? null}
                    structure={structureMarkers}
                  />

                </div>
              </div>
            </div>

            <div className="mt-6 grid gap-4 xl:grid-cols-3">
              <div className="rounded-[22px] border border-slate-800 bg-[#121721] p-4">
                <div className="mb-4 flex items-center justify-between">
                  <h3 className="text-[11px] uppercase tracking-[0.2em] text-slate-400">{t("dashboard.marketStructure")}</h3>
                  <StatBadge tone="positive">{t("dashboard.uptrend")}</StatBadge>
                </div>

                <div className="mb-4 flex flex-wrap gap-2">
                  {[
                    ["ChoCh", "8h ago"],
                    ["BoS", "5h ago"],
                    ["HL", "2h ago"],
                    ["HH", "Now"],
                  ].map(([label, time]) => (
                    <div key={label} className="rounded-lg border border-slate-700 bg-slate-900/80 px-2.5 py-1.5 text-center">
                      <div className="text-[10px] font-bold uppercase tracking-[0.16em] text-blue-200">{label}</div>
                      <div className="mt-1 text-[9px] text-slate-400">{time}</div>
                    </div>
                  ))}
                </div>

                <div className="rounded-2xl border border-emerald-500/30 bg-emerald-500/8 p-3">
                  <div className="flex items-center gap-2 text-sm font-semibold text-emerald-300">
                    <ShieldCheck className="h-4 w-4" />
                    {t("dashboard.bullishStructureIntact")}
                  </div>
                  <p className="mt-2 text-sm leading-6 text-slate-300">
                    {activeSignal?.direction === "LONG" ? t("trade.longSetup") : activeSignal?.direction === "SHORT" ? t("trade.shortSetup") : t("trade.longSetup")}
                  </p>
                </div>
              </div>

              <div className="rounded-[22px] border border-slate-800 bg-[#121721] p-4">
                {hasSignal && activeSignal ? (
                  <>
                    <div className="mb-4 flex items-center justify-between">
                      <h3 className="text-[11px] uppercase tracking-[0.2em] text-slate-400">{activeSignal.symbol ?? selectedSymbol} {activeSignal.direction}</h3>
                      <StatBadge tone={activeSignal.direction === "LONG" ? "positive" : activeSignal.direction === "SHORT" ? "negative" : "neutral"}>{activeSignal.direction === "LONG" ? "Hoạt động" : activeSignal.direction === "SHORT" ? "Ngắn" : "Đang chờ"}</StatBadge>
                    </div>

                    <div className="grid grid-cols-2 gap-3 text-sm">
                      <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-2.5">
                        <div className="text-[10px] uppercase tracking-[0.12em] text-slate-500">{t("dashboard.entry")}</div>
                        <div className="mt-1 font-semibold text-slate-100">{formatMoney(activeSignal.entry)}</div>
                      </div>
                      <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-2.5">
                        <div className="text-[10px] uppercase tracking-[0.12em] text-slate-500">{t("dashboard.stop")}</div>
                        <div className="mt-1 font-semibold text-slate-100">{formatMoney(activeSignal.stopLoss)}</div>
                      </div>
                      <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-2.5">
                        <div className="text-[10px] uppercase tracking-[0.12em] text-slate-500">{t("dashboard.target")}</div>
                        <div className="mt-1 font-semibold text-slate-100">{formatMoney(activeSignal.takeProfit)}</div>
                      </div>
                      <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-2.5">
                        <div className="text-[10px] uppercase tracking-[0.12em] text-slate-500">{t("dashboard.riskReward")}</div>
                        <div className="mt-1 font-semibold text-slate-100">{activeSignal.riskReward || "1:0"}</div>
                      </div>
                    </div>

                    <div className="mt-4">
                      <div className="mb-2 flex items-center justify-between text-[10px] uppercase tracking-[0.15em] text-slate-400">
                        <span>{t("dashboard.confidence")}</span>
                        <span className={activeSignal.direction === "LONG" ? "text-emerald-300" : "text-rose-300"}>{Math.round(activeSignal.confidence)}%</span>
                      </div>
                      <div className="h-2 overflow-hidden rounded-full bg-slate-800">
                        <div className="h-full rounded-full bg-gradient-to-r from-emerald-500 to-green-400" style={{ width: `${Math.min(100, Math.max(0, activeSignal.confidence))}%` }} />
                      </div>
                    </div>

                    <p className="mt-4 text-sm leading-6 text-slate-300">
                      {activeSignal.direction === "LONG"
                        ? "Setup tăng đã được xác nhận bởi dữ liệu thị trường trực tiếp và engine tín hiệu. Rủi ro đã được đặt dưới cấu trúc swing gần đây."
                        : "Setup giảm đã được xác nhận bởi dữ liệu thị trường trực tiếp và engine tín hiệu. Rủi ro vẫn được kiểm soát dưới vùng kháng cự hoạt động."}
                    </p>
                  </>
                ) : (
                  <div className="flex min-h-[240px] flex-col items-center justify-center rounded-2xl border border-dashed border-slate-700 bg-slate-950/40 p-5 text-center">
                    <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-violet-500/10 text-violet-200">
                      <Bot className="h-5 w-5" />
                    </div>
                    <p className="text-sm font-semibold uppercase tracking-[0.18em] text-slate-300 break-words">{t("dashboard.waitingForSignal")}</p>
                    <p className="mt-2 max-w-[260px] text-sm leading-6 text-slate-400 break-words">{t("dashboard.uploadChartToAnalyze")}</p>
                    <button
                      type="button"
                      onClick={handleResetSignals}
                      className="mt-4 rounded-lg border border-slate-700 bg-slate-900 px-3 py-1.5 text-[10px] uppercase tracking-[0.18em] text-slate-200 transition hover:border-slate-500"
                    >
                      {t("common.reset")}
                    </button>
                  </div>
                )}
              </div>

              <div className="rounded-[22px] border border-slate-800 bg-[#121721] p-4">
                <div className="mb-4 flex items-center justify-between">
                  <h3 className="text-[11px] uppercase tracking-[0.2em] text-slate-400">{t("dashboard.alerts")}</h3>
                  <span className="rounded-full bg-rose-500/15 px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.16em] text-rose-300">{t("dashboard.critical")}</span>
                </div>

                <ul className="space-y-2 text-sm text-slate-300">
                  <li className="rounded-xl border border-slate-800 bg-slate-950/60 p-2.5">
                    <span className="font-medium text-slate-100">BTC/USDT</span> phá vỡ vùng kháng cự $67.200 trên khối lượng cao.
                  </li>
                  <li className="rounded-xl border border-slate-800 bg-slate-950/60 p-2.5">
                    ETH vẫn giữ nhịp tăng khi dòng lệnh vẫn cân bằng.
                  </li>
                </ul>
              </div>
            </div>
          </div>

          <aside className="w-full lg:w-1/3 block shrink-0 flex-col gap-4">
            <div className="rounded-[24px] border border-slate-800 bg-[#121721] p-4">
              <div className="mb-4 flex items-center justify-between">
                <div className="flex items-center gap-2 text-sm font-semibold text-slate-100">
                  <Sparkles className="h-4 w-4 text-violet-300" />
                  {t("dashboard.aiAnalysis")}
                </div>
                <StatBadge tone="positive">TĂNG</StatBadge>
              </div>

              {aiProgress.map((metric) => (
                <div key={metric.label} className="mb-4 last:mb-0">
                  <div className="mb-2 flex items-center justify-between text-[10px] uppercase tracking-[0.15em] text-slate-400">
                    <span>{metric.label}</span>
                    <span className="text-slate-200">{metric.value}%</span>
                  </div>
                  <div className="h-2 overflow-hidden rounded-full bg-slate-800">
                    <div className="h-full rounded-full bg-gradient-to-r from-blue-500 to-cyan-400" style={{ width: `${metric.value}%` }} />
                  </div>
                </div>
              ))}

              <div className="mt-5 rounded-2xl border border-slate-800 bg-slate-950/70 p-3.5">
                <p className="text-sm leading-6 text-slate-300">
                  Cấu trúc dài hạn vẫn giữ mạnh. Xu hướng đang được củng cố bởi khối lượng và vùng hỗ trợ thứ cấp.
                </p>
              </div>
            </div>

            <div className="rounded-[24px] border border-slate-800 bg-[#121721] p-4">
              <div className="mb-3 flex items-center justify-between">
                <h3 className="text-sm font-semibold text-slate-100">{t("dashboard.watchlist")}</h3>
                <span className="text-[10px] uppercase tracking-[0.18em] text-slate-500">{t("common.live")}</span>
              </div>

              <div className="space-y-2">
                {watchlist.map((item) => (
                  <div key={item.symbol} className="flex items-center justify-between rounded-xl border border-slate-800 bg-slate-950/60 px-3 py-2.5">
                    <div className="flex items-center gap-3">
                      <span className="flex h-7 w-7 items-center justify-center rounded-full bg-slate-800 text-[10px] font-bold text-slate-200">{item.symbol.slice(0, 2)}</span>
                      <span className="text-sm font-medium text-slate-100">{item.symbol}</span>
                    </div>
                    <div className="text-right">
                      <div className="text-sm font-medium text-slate-100">{formatCompact(item.price)}</div>
                      <div className={`text-[10px] font-semibold ${item.change >= 0 ? "text-emerald-300" : "text-rose-300"}`}>
                        {item.change >= 0 ? "+" : ""}{item.change.toFixed(2)}%
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="rounded-[24px] border border-slate-800 bg-[#121721] p-4">
              <div className="mb-4 flex items-center justify-between">
                <div className="flex items-center gap-2 text-sm font-semibold text-slate-100">
                  <BrainCircuit className="h-4 w-4 text-violet-300" />
                  {t("dashboard.screenshotAnalysis")}
                </div>
                <div className="flex items-center gap-2">
                  <StatBadge tone={uploadStatus === "completed" ? "positive" : uploadStatus === "invalid" ? "negative" : "neutral"}>
                    {uploadStatus === "completed" ? t("common.verified") : uploadStatus === "invalid" ? t("common.invalid") : t("common.awaiting")}
                  </StatBadge>
                  <button
                    type="button"
                    onClick={handleResetSignals}
                    className="rounded-full border border-slate-700 bg-slate-950 px-2 py-1 text-[9px] uppercase tracking-[0.14em] text-slate-200 hover:border-slate-500"
                  >
                    {t("common.reset")}
                  </button>
                </div>
              </div>

              <label
                className={`mb-4 flex cursor-pointer flex-col items-center justify-center rounded-2xl border border-dashed p-4 text-center transition ${dragActive ? "border-blue-400 bg-blue-500/5" : "border-slate-700 bg-slate-950/60 hover:border-slate-500"}`}
                onDragOver={(event) => {
                  event.preventDefault();
                  setDragActive(true);
                }}
                onDragLeave={() => setDragActive(false)}
                onDrop={(event) => {
                  event.preventDefault();
                  setDragActive(false);
                  const file = event.dataTransfer.files?.[0];
                  if (file) {
                    void handleAnalyzeScreenshot(file);
                  }
                }}
              >
                <input type="file" accept="image/*" className="hidden" onChange={handleFileInput} />
                <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-violet-500/10 text-violet-200">
                  <Bot className="h-4 w-4" />
                </div>
                <p className="text-sm font-medium text-slate-100">{t("common.upload")}</p>
                <p className="mt-1 text-[11px] text-slate-400">{t("common.uploadSub")}</p>
              </label>

              <div className="mb-4 space-y-2 text-[10px] uppercase tracking-[0.15em] text-slate-400">
                {[
                  { label: t("dashboard.uploading"), active: uploadStatus === "uploading" || uploadStatus === "extracting" || uploadStatus === "verifying" || uploadStatus === "completed" },
                  { label: t("dashboard.extractingMetadata"), active: uploadStatus === "extracting" || uploadStatus === "verifying" || uploadStatus === "completed" },
                  { label: t("dashboard.verifyingMarketData"), active: uploadStatus === "verifying" || uploadStatus === "completed" },
                  { label: t("dashboard.completed"), active: uploadStatus === "completed" },
                ].map((step) => (
                  <div key={step.label} className="flex items-center justify-between rounded-lg border border-slate-800 bg-slate-950/60 px-2.5 py-2">
                    <span>{step.label}</span>
                    <span className={`h-2.5 w-2.5 rounded-full ${step.active ? "bg-emerald-400" : "bg-slate-700"}`} />
                  </div>
                ))}
              </div>

              {analysisResult ? (
                <div className="rounded-2xl border border-slate-800 bg-slate-950/70 p-3.5">
                  <div className="mb-3 flex items-center justify-between">
                    <span className="text-[10px] uppercase tracking-[0.16em] text-slate-500">{t("dashboard.result")}</span>
                    <span className={`rounded-full px-2 py-1 text-[9px] font-semibold uppercase tracking-[0.14em] ${analysisResult.valid ? "bg-emerald-500/15 text-emerald-300" : "bg-rose-500/15 text-rose-300"}`}>
                      {analysisResult.valid ? t("common.verified") : t("common.invalid")}
                    </span>
                  </div>

                  <div className="space-y-2 text-sm text-slate-300">
                    <div className="flex justify-between gap-3"><span className="text-slate-400">{t("dashboard.symbol")}</span><span className="font-medium text-slate-100">{analysisResult.symbol ?? "—"}</span></div>
                    <div className="flex justify-between gap-3"><span className="text-slate-400">{t("dashboard.timeframe")}</span><span className="font-medium text-slate-100">{analysisResult.timeframe ?? "—"}</span></div>
                    <div className="flex justify-between gap-3"><span className="text-slate-400">{t("dashboard.pattern")}</span><span className="font-medium text-slate-100">{analysisResult.pattern ?? "—"}</span></div>
                    <div className="flex justify-between gap-3"><span className="text-slate-400">{t("dashboard.status")}</span><span className="font-medium text-slate-100">{analysisResult.verification ?? analysisResult.status ?? "—"}</span></div>
                  </div>

                  {analysisResult.indicatorsVisible && analysisResult.indicatorsVisible.length > 0 ? (
                    <div className="mt-3 flex flex-wrap gap-2">
                      {analysisResult.indicatorsVisible.map((indicator) => (
                        <span key={indicator} className="rounded-full border border-slate-700 bg-slate-900 px-2 py-1 text-[10px] uppercase tracking-[0.14em] text-slate-300">
                          {indicator}
                        </span>
                      ))}
                    </div>
                  ) : null}

                  <p className="mt-3 text-sm leading-6 text-slate-300">
                    {analysisResult.reason ?? "Scene is being checked against live Binance market data."}
                  </p>

                  {analysisResult.marketSnapshot ? (
                    <p className="mt-2 text-[11px] text-slate-400">{analysisResult.marketSnapshot}</p>
                  ) : null}
                </div>
              ) : null}
            </div>

            <div className="rounded-[24px] border border-slate-800 bg-[#121721] p-4">
              <div className="mb-4 flex items-center justify-between">
                <div>
                  <p className="text-[10px] uppercase tracking-[0.2em] text-slate-500">{t("nav.portfolio")}</p>
                  <p className="mt-2 text-3xl font-bold text-white">$24,836.50</p>
                </div>
                <div className="rounded-full bg-emerald-500/10 px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.12em] text-emerald-300">
                  +$6,218.9
                </div>
              </div>

              <div className="mb-4 space-y-2">
                <div className="text-[10px] uppercase tracking-[0.14em] text-slate-500">{t("dashboard.tradeHistory")}</div>
                {tradeHistory.length === 0 ? (
                  <div className="rounded-xl border border-dashed border-slate-700 bg-slate-950/60 p-3 text-[11px] text-slate-400">
                    {t("dashboard.noTradesYet")}
                  </div>
                ) : (
                  tradeHistory.map((entry) => (
                    <div key={entry.id} className="flex items-center justify-between rounded-xl border border-slate-800 bg-slate-950/60 px-3 py-2">
                      <div>
                        <div className="text-sm font-semibold text-slate-100">{entry.symbol} {entry.direction}</div>
                        <div className="text-[10px] text-slate-400">{entry.timeframe} • {new Date(entry.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</div>
                      </div>
                      <span className={`rounded-full px-2 py-1 text-[9px] font-bold uppercase tracking-[0.14em] ${entry.status === "TP" ? "bg-emerald-500/15 text-emerald-300" : "bg-rose-500/15 text-rose-300"}`}>
                        {entry.status}
                      </span>
                    </div>
                  ))
                )}
              </div>

              <div className="grid grid-cols-3 gap-2 text-center">
                <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-3">
                  <div className="text-[10px] uppercase tracking-[0.14em] text-slate-500">P/L</div>
                  <div className="mt-2 text-sm font-semibold text-emerald-300">+$842.30</div>
                </div>
                <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-3">
                  <div className="text-[10px] uppercase tracking-[0.14em] text-slate-500">Win</div>
                  <div className="mt-2 text-sm font-semibold text-slate-100">68%</div>
                </div>
                <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-3">
                  <div className="text-[10px] uppercase tracking-[0.14em] text-slate-500">Risk</div>
                  <div className="mt-2 text-sm font-semibold text-amber-300">12.4%</div>
                </div>
              </div>
            </div>
          </aside>
        </div>
      </div>

      <button className="fixed bottom-6 right-6 flex h-12 w-12 items-center justify-center rounded-full border border-violet-500/50 bg-violet-500/15 text-xl text-violet-200 shadow-[0_0_22px_rgba(168,85,247,0.35)] transition hover:scale-[1.02]">
        ?
      </button>
    </div>
  );
}

export default App;

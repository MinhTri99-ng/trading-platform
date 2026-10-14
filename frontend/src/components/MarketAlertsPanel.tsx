import { useEffect, useMemo, useRef, useState } from "react";
import { ArrowLeft, ArrowRight, ArrowUpRight, ExternalLink, Newspaper, RefreshCw, ShieldAlert, TrendingDown, TrendingUp } from "lucide-react";
import type { MarketSignal } from "../hooks/useMarketRealtime";
import type { MarketStructureData } from "../hooks/useMarketStructure";

type NewsItem = {
  id: string;
  title: string;
  source: string;
  sourceUrl: string;
  imageUrl: string;
  publishedAt: number;
};

type CryptoCompareNewsItem = {
  id?: number;
  title?: string;
  source_info?: { name?: string };
  url?: string;
  imageurl?: string;
  published_on?: number;
};

type MarketAlertsPanelProps = {
  signal: MarketSignal | null;
  selectedSymbol: string;
  marketStructure: MarketStructureData;
};

const fallbackNews: NewsItem[] = [
  {
    id: "btc-etf-flows",
    title: "Bitcoin market steadies as institutional flows return to digital assets",
    source: "CoinDesk",
    sourceUrl: "https://www.coindesk.com/markets/",
    imageUrl: "https://images.unsplash.com/photo-1518546305927-5a555bb7020d?auto=format&fit=crop&w=900&q=80",
    publishedAt: Date.now() - 10 * 60 * 1000,
  },
  {
    id: "ethereum-network",
    title: "Ethereum activity picks up as traders watch the next network catalyst",
    source: "Cointelegraph",
    sourceUrl: "https://cointelegraph.com/tags/ethereum",
    imageUrl: "https://images.unsplash.com/photo-1621416894569-0f39ed31d247?auto=format&fit=crop&w=900&q=80",
    publishedAt: Date.now() - 32 * 60 * 1000,
  },
  {
    id: "macro-rates",
    title: "Global markets weigh rate outlook as crypto volatility expands",
    source: "Cafef",
    sourceUrl: "https://cafef.vn/thi-truong-chung-khoan.chn",
    imageUrl: "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?auto=format&fit=crop&w=900&q=80",
    publishedAt: Date.now() - 74 * 60 * 1000,
  },
];

const formatNewsAge = (publishedAt: number) => {
  const minutes = Math.max(1, Math.floor((Date.now() - publishedAt) / 60000));
  if (minutes < 60) return `${minutes} phút trước`;
  if (minutes < 1440) return `${Math.floor(minutes / 60)} giờ trước`;
  return `${Math.floor(minutes / 1440)} ngày trước`;
};

const normalizeNews = (item: CryptoCompareNewsItem, index: number): NewsItem | null => {
  if (!item.title || !item.url || !item.imageurl) return null;
  return {
    id: String(item.id ?? `${item.url}-${index}`),
    title: item.title,
    source: item.source_info?.name ?? "Crypto news",
    sourceUrl: item.url,
    imageUrl: item.imageurl,
    publishedAt: (item.published_on ?? Math.floor(Date.now() / 1000)) * 1000,
  };
};

export function MarketAlertsPanel({ signal, selectedSymbol, marketStructure }: MarketAlertsPanelProps) {
  const [news, setNews] = useState<NewsItem[]>(fallbackNews);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [usingLiveNews, setUsingLiveNews] = useState(false);
  const [activeNewsIndex, setActiveNewsIndex] = useState(0);
  const newsTouchStartX = useRef<number | null>(null);

  const loadNews = async (isCancelled: () => boolean) => {
    setIsRefreshing(true);
    try {
      const response = await fetch("https://min-api.cryptocompare.com/data/v2/news/?lang=EN", {
        headers: { Accept: "application/json" },
      });
      if (!response.ok) throw new Error(`News API responded with ${response.status}`);
      const payload = (await response.json()) as { Data?: CryptoCompareNewsItem[] };
      const liveNews = (payload.Data ?? []).map(normalizeNews).filter((item): item is NewsItem => item !== null).slice(0, 5);
      if (!isCancelled() && liveNews.length > 0) {
        setNews(liveNews);
        setActiveNewsIndex(0);
        setUsingLiveNews(true);
      }
    } catch {
      if (!isCancelled()) setUsingLiveNews(false);
    } finally {
      if (!isCancelled()) setIsRefreshing(false);
    }
  };

  useEffect(() => {
    let cancelled = false;
    const initialLoadTimer = window.setTimeout(() => void loadNews(() => cancelled), 0);
    const refreshTimer = window.setInterval(() => void loadNews(() => cancelled), 5 * 60 * 1000);
    return () => {
      cancelled = true;
      window.clearTimeout(initialLoadTimer);
      window.clearInterval(refreshTimer);
    };
  }, []);

  useEffect(() => {
    if (news.length < 2) return;
    const autoplayTimer = window.setInterval(() => {
      setActiveNewsIndex((currentIndex) => (currentIndex + 1) % news.length);
    }, 5000);
    return () => window.clearInterval(autoplayTimer);
  }, [news.length]);

  const changeNews = (direction: 1 | -1) => {
    setActiveNewsIndex((currentIndex) => (currentIndex + direction + news.length) % news.length);
  };

  const handleNewsPointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    newsTouchStartX.current = event.clientX;
  };

  const handleNewsPointerUp = (event: React.PointerEvent<HTMLDivElement>) => {
    if (newsTouchStartX.current === null) return;
    const distance = event.clientX - newsTouchStartX.current;
    newsTouchStartX.current = null;
    if (Math.abs(distance) < 40) return;
    changeNews(distance < 0 ? 1 : -1);
  };

  const technicalAlerts = useMemo(() => {
    const direction = signal?.direction;
    return [
      {
        label: signal && direction !== "NONE" ? `${signal.symbol} tín hiệu ${direction}` : `${selectedSymbol} đang được quét`,
        detail: signal && direction !== "NONE"
          ? `Entry ${signal.entry.toLocaleString()} • R:R ${signal.riskReward || "đang cập nhật"}`
          : marketStructure.statusSummary,
        bullish: direction === "LONG" || marketStructure.trend === "BULLISH",
      },
      {
        label: marketStructure.trend === "SIDEWAYS" ? "Vùng giá đang tích lũy" : `Cấu trúc ${marketStructure.trendLabel.toLowerCase()}`,
        detail: marketStructure.setupRecommendation,
        bullish: marketStructure.trend !== "BEARISH",
      },
      {
        label: "Dòng lệnh & volume realtime",
        detail: "Đang theo dõi thanh khoản và phản ứng tại vùng giá quan trọng.",
        bullish: true,
      },
    ];
  }, [marketStructure, selectedSymbol, signal]);

  return (
    <section className="overflow-hidden rounded-[22px] border border-slate-800 bg-[#121721]">
      <div className="border-b border-slate-800 bg-gradient-to-r from-slate-900/70 via-[#121721] to-violet-950/20 p-4">
        <div className="mb-4 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <ShieldAlert className="h-4 w-4 text-amber-300" />
            <h3 className="text-[11px] font-semibold uppercase tracking-[0.2em] text-slate-300">Cảnh báo kỹ thuật (SMC &amp; Volume)</h3>
          </div>
          <span className="flex items-center gap-1.5 rounded-full bg-rose-500/15 px-2 py-1 text-[9px] font-semibold uppercase tracking-[0.14em] text-rose-300">
            <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-rose-400" /> Live
          </span>
        </div>
        <div className="space-y-2">
          {technicalAlerts.map((alert) => (
            <div key={alert.label} className="rounded-xl border border-slate-800 bg-slate-950/55 p-2.5 transition-colors hover:border-slate-700 hover:bg-slate-900/80">
              <div className="flex items-start gap-2">
                {alert.bullish ? <TrendingUp className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" /> : <TrendingDown className="mt-0.5 h-4 w-4 shrink-0 text-rose-400" />}
                <div className="min-w-0">
                  <p className="text-xs font-semibold text-slate-100">{alert.label}</p>
                  <p className="mt-1 text-[11px] leading-5 text-slate-400">{alert.detail}</p>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="p-4">
        <div className="mb-4 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Newspaper className="h-4 w-4 text-cyan-300" />
            <h3 className="text-[11px] font-semibold uppercase tracking-[0.2em] text-slate-300">Tin tức &amp; biến động vĩ mô</h3>
          </div>
          <button type="button" onClick={() => void loadNews(() => false)} disabled={isRefreshing} aria-label="Làm mới tin tức" className="rounded-lg p-1.5 text-slate-500 transition hover:bg-slate-800 hover:text-cyan-300 disabled:animate-spin disabled:opacity-60">
            <RefreshCw className="h-3.5 w-3.5" />
          </button>
        </div>
        <div
          className="group/carousel relative touch-pan-y select-none"
          onPointerDown={handleNewsPointerDown}
          onPointerUp={handleNewsPointerUp}
          onPointerCancel={() => { newsTouchStartX.current = null; }}
        >
          {news.length > 1 && (
            <>
              <button
                type="button"
                onClick={() => changeNews(-1)}
                aria-label="Tin trước"
                className="absolute left-2 top-[38%] z-10 rounded-full border border-slate-700/80 bg-slate-950/85 p-2 text-slate-200 opacity-0 shadow-lg backdrop-blur-sm transition-all hover:border-cyan-400/50 hover:text-cyan-200 group-hover/carousel:opacity-100"
              >
                <ArrowLeft className="h-3.5 w-3.5" />
              </button>
              <button
                type="button"
                onClick={() => changeNews(1)}
                aria-label="Tin tiếp theo"
                className="absolute right-2 top-[38%] z-10 rounded-full border border-slate-700/80 bg-slate-950/85 p-2 text-slate-200 opacity-0 shadow-lg backdrop-blur-sm transition-all hover:border-cyan-400/50 hover:text-cyan-200 group-hover/carousel:opacity-100"
              >
                <ArrowRight className="h-3.5 w-3.5" />
              </button>
            </>
          )}
          {news[activeNewsIndex] && (
            <a
              href={news[activeNewsIndex].sourceUrl}
              target="_blank"
              rel="noreferrer"
              className="group block overflow-hidden rounded-2xl border border-slate-800 bg-slate-950/45 transition-all duration-300 hover:border-cyan-400/35 hover:bg-gradient-to-br hover:from-cyan-500/10 hover:to-violet-500/10"
            >
              <div className="relative aspect-[16/9] overflow-hidden">
                <img src={news[activeNewsIndex].imageUrl} alt="" loading="lazy" className="h-full w-full object-cover opacity-90 transition duration-500 group-hover:scale-105 group-hover:opacity-100" />
                <div className="absolute inset-0 bg-gradient-to-t from-slate-950/80 via-transparent to-transparent" />
                <span className="absolute bottom-3 left-3 rounded-full border border-cyan-300/25 bg-slate-950/70 px-2.5 py-1 text-[9px] font-semibold uppercase tracking-[0.12em] text-cyan-200 backdrop-blur-sm">
                  {news[activeNewsIndex].source}
                </span>
              </div>
              <div className="p-3">
                <p className="line-clamp-2 min-h-[40px] text-sm font-bold leading-5 text-slate-100 transition-colors group-hover:text-cyan-100">{news[activeNewsIndex].title}</p>
                <div className="mt-2 flex items-center gap-2 text-[10px] text-slate-500">
                  <span>{formatNewsAge(news[activeNewsIndex].publishedAt)}</span>
                  <span>•</span>
                  <span>Đọc bài gốc</span>
                  <ArrowUpRight className="ml-auto h-3.5 w-3.5 text-cyan-300 opacity-70 transition group-hover:opacity-100" />
                </div>
              </div>
            </a>
          )}
          {news.length > 1 && (
            <div className="mt-3 flex items-center justify-center gap-1.5" aria-label="Chọn tin tức">
              {news.map((item, index) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setActiveNewsIndex(index)}
                  aria-label={`Chuyển đến tin ${index + 1}`}
                  aria-current={activeNewsIndex === index}
                  className={`h-1.5 rounded-full transition-all duration-300 ${activeNewsIndex === index ? "w-5 bg-cyan-300 shadow-[0_0_8px_rgba(103,232,249,0.55)]" : "w-1.5 bg-slate-600 hover:bg-slate-400"}`}
                />
              ))}
            </div>
          )}
        </div>
        <div className="mt-3 flex items-center justify-end gap-1 text-[9px] uppercase tracking-[0.14em] text-slate-600">
          {usingLiveNews ? "CryptoCompare • cập nhật 5 phút" : "Nguồn dự phòng • tin tức mẫu"}
          <ExternalLink className="h-3 w-3" />
        </div>
      </div>
    </section>
  );
}

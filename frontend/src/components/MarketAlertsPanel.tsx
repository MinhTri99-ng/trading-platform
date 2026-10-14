import { useEffect, useMemo, useState } from "react";
import { ArrowUpRight, ExternalLink, Newspaper, RefreshCw, ShieldAlert, TrendingDown, TrendingUp } from "lucide-react";
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
        <div className="space-y-2.5">
          {news.map((item) => (
            <a key={item.id} href={item.sourceUrl} target="_blank" rel="noreferrer" className="group flex gap-3 rounded-xl border border-slate-800 bg-slate-950/45 p-2 transition-all duration-300 hover:border-cyan-400/35 hover:bg-gradient-to-r hover:from-cyan-500/10 hover:to-violet-500/10">
              <img src={item.imageUrl} alt="" loading="lazy" className="h-[58px] w-[88px] shrink-0 rounded-lg object-cover opacity-90 transition duration-300 group-hover:scale-[1.03] group-hover:opacity-100" />
              <div className="min-w-0 flex-1">
                <p className="line-clamp-2 text-xs font-semibold leading-5 text-slate-100 transition-colors group-hover:text-cyan-100">{item.title}</p>
                <div className="mt-1.5 flex items-center gap-2 text-[10px] text-slate-500">
                  <span className="font-semibold text-cyan-300/80">{item.source}</span>
                  <span>•</span>
                  <span>{formatNewsAge(item.publishedAt)}</span>
                  <ArrowUpRight className="ml-auto h-3 w-3 opacity-0 transition group-hover:opacity-100" />
                </div>
              </div>
            </a>
          ))}
        </div>
        <div className="mt-3 flex items-center justify-end gap-1 text-[9px] uppercase tracking-[0.14em] text-slate-600">
          {usingLiveNews ? "CryptoCompare • cập nhật 5 phút" : "Nguồn dự phòng • tin tức mẫu"}
          <ExternalLink className="h-3 w-3" />
        </div>
      </div>
    </section>
  );
}

import { useEffect, useState } from "react";
import { ArrowUpRight, ExternalLink, Newspaper, RefreshCw } from "lucide-react";

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
  {
    id: "altcoin-rotation",
    title: "Crypto traders track sector rotation as liquidity returns to altcoins",
    source: "CryptoCompare",
    sourceUrl: "https://www.cryptocompare.com/news/",
    imageUrl: "https://images.unsplash.com/photo-1640340434855-6084b1f4901c?auto=format&fit=crop&w=900&q=80",
    publishedAt: Date.now() - 110 * 60 * 1000,
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

export function NewsAnalysisSection() {
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
      const liveNews = (payload.Data ?? []).map(normalizeNews).filter((item): item is NewsItem => item !== null).slice(0, 4);
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

  const featuredArticle = {
    title: "Phân tích Bitcoin và Ethereum: đọc cấu trúc thị trường SMC trước nhịp biến động mới",
    summary: "Khung phân tích SMC kết hợp xu hướng, thanh khoản và vùng phản ứng giúp trader xác định kịch bản BTC/ETH thay vì chạy theo biến động ngắn hạn.",
    imageUrl: "https://images.unsplash.com/photo-1518546305927-5a555bb7020d?auto=format&fit=crop&w=1400&q=85",
  };

  return (
    <section className="mx-auto max-w-[1700px] px-4 pb-12 xl:px-6" aria-labelledby="news-analysis-title">
      <div className="rounded-[28px] border border-slate-800 bg-[#101722] p-5 shadow-2xl shadow-black/20 sm:p-7">
        <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
          <div>
            <div className="mb-2 flex items-center gap-2 text-cyan-300">
              <Newspaper className="h-5 w-5" />
              <span className="text-[10px] font-semibold uppercase tracking-[0.2em]">SmartSignal Editorial</span>
            </div>
            <h2 id="news-analysis-title" className="text-xl font-bold uppercase tracking-[0.08em] text-white sm:text-2xl">
              PHÂN TÍCH THỊ TRƯỜNG &amp; TIN TỨC SMARTSIGNAL
            </h2>
          </div>
          <button type="button" onClick={() => void loadNews(() => false)} disabled={isRefreshing} className="inline-flex items-center gap-2 rounded-xl border border-slate-700 bg-slate-900/70 px-3 py-2 text-[10px] font-semibold uppercase tracking-[0.14em] text-slate-300 transition hover:border-cyan-400/50 hover:text-cyan-200 disabled:opacity-60">
            <RefreshCw className={`h-3.5 w-3.5 ${isRefreshing ? "animate-spin" : ""}`} /> Cập nhật tin
          </button>
        </div>

        <div className="grid gap-5 xl:grid-cols-[1.05fr_1.95fr]">
          <article className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-950/50">
            <div className="relative aspect-[16/9] overflow-hidden">
              <img src={featuredArticle.imageUrl} alt="Phân tích cấu trúc thị trường Bitcoin và Ethereum" loading="lazy" className="h-full w-full object-cover transition duration-500 hover:scale-105" />
              <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-transparent to-transparent" />
              <span className="absolute bottom-4 left-4 rounded-full border border-emerald-300/30 bg-slate-950/70 px-2.5 py-1 text-[9px] font-semibold uppercase tracking-[0.14em] text-emerald-200">SMC / Market Trend</span>
            </div>
            <div className="p-5">
              <p className="mb-2 text-[10px] uppercase tracking-[0.16em] text-slate-500">Bài phân tích mới nhất • BTC / ETH</p>
              <h3 className="text-lg font-bold leading-7 text-white">{featuredArticle.title}</h3>
              <p className="mt-3 text-sm leading-6 text-slate-400">{featuredArticle.summary}</p>
              <button type="button" className="mt-5 inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.14em] text-cyan-300 transition hover:text-cyan-100">
                Đọc phân tích <ArrowUpRight className="h-4 w-4" />
              </button>
            </div>
          </article>

          <div className="grid gap-4 sm:grid-cols-2">
            {news.map((item) => (
              <a key={item.id} href={item.sourceUrl} target="_blank" rel="noreferrer" className="group overflow-hidden rounded-2xl border border-slate-800 bg-slate-950/45 transition hover:border-cyan-400/40 hover:bg-cyan-500/5">
                <div className="relative aspect-[16/9] overflow-hidden">
                  <img src={item.imageUrl} alt="" loading="lazy" className="h-full w-full object-cover opacity-90 transition duration-500 group-hover:scale-105 group-hover:opacity-100" />
                  <div className="absolute inset-0 bg-gradient-to-t from-slate-950/80 via-transparent to-transparent" />
                  <span className="absolute bottom-3 left-3 rounded-full border border-cyan-300/25 bg-slate-950/75 px-2 py-1 text-[9px] font-semibold uppercase tracking-[0.12em] text-cyan-200">{item.source}</span>
                </div>
                <div className="p-3.5">
                  <h3 className="line-clamp-2 min-h-[40px] text-sm font-bold leading-5 text-slate-100 group-hover:text-cyan-100">{item.title}</h3>
                  <div className="mt-2 flex items-center gap-2 text-[10px] text-slate-500">
                    <span>{formatNewsAge(item.publishedAt)}</span><span>•</span><span>Đọc bài gốc</span><ExternalLink className="ml-auto h-3.5 w-3.5 text-cyan-300" />
                  </div>
                </div>
              </a>
            ))}
          </div>
        </div>
        <p className="mt-5 flex items-center justify-end gap-1 text-[9px] uppercase tracking-[0.14em] text-slate-600">
          {usingLiveNews ? "CryptoCompare • tự động cập nhật mỗi 5 phút" : "Nguồn dự phòng • sẽ tự đồng bộ khi API khả dụng"}
          <ExternalLink className="h-3 w-3" />
        </p>
      </div>
    </section>
  );
}

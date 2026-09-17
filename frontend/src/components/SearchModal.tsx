import { useEffect, useMemo, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from "react";
import { ArrowDown, ArrowUp, Bot, Check, Command, Moon, Search, Settings2, Sun, TrendingDown, TrendingUp, X } from "lucide-react";

type SearchAction = "symbol" | "settings" | "chat" | "theme";
type SearchItem = { id: string; group: "pairs" | "signals" | "actions"; title: string; subtitle?: string; meta?: string; price?: string; change?: number; icon?: typeof Settings2; action: SearchAction; value?: string };
type SearchModalProps = { isOpen: boolean; onClose: () => void; onToggleOpen: () => void; onSelectSymbol: (symbol: string) => void; onOpenSettings: () => void; onOpenAIChat: () => void; onToggleTheme: () => void; isDarkTheme: boolean };

const HISTORY_KEY = "snapchart-search-history";
const MAX_HISTORY = 6;
const pairItems: SearchItem[] = [
  { id: "btc", group: "pairs", title: "BTC/USDT", price: "$67,340.25", change: 2.84, action: "symbol", value: "BTC/USDT" },
  { id: "eth", group: "pairs", title: "ETH/USDT", price: "$3,524.12", change: 1.76, action: "symbol", value: "ETH/USDT" },
  { id: "sol", group: "pairs", title: "SOL/USDT", price: "$168.45", change: 3.34, action: "symbol", value: "SOL/USDT" },
  { id: "xrp", group: "pairs", title: "XRP/USDT", price: "$0.6221", change: -0.42, action: "symbol", value: "XRP/USDT" },
  { id: "bnb", group: "pairs", title: "BNB/USDT", price: "$598.34", change: 1.21, action: "symbol", value: "BNB/USDT" },
];
const signalItems: SearchItem[] = [
  { id: "btc-m15-ob", group: "signals", title: "BTC M15 Order Block", subtitle: "Bullish continuation setup", meta: "LONG", action: "symbol", value: "BTC/USDT" },
  { id: "eth-h1-fvg", group: "signals", title: "ETH H1 FVG", subtitle: "Fair Value Gap đang chờ retest", meta: "WATCH", action: "symbol", value: "ETH/USDT" },
  { id: "sol-m5-bos", group: "signals", title: "SOL M5 Break of Structure", subtitle: "Liquidity sweep đã xác nhận", meta: "LONG", action: "symbol", value: "SOL/USDT" },
];
const groupLabels = { pairs: "Cặp giao dịch", signals: "Tín hiệu SMC Hot", actions: "Thao tác nhanh" } as const;

function readHistory() {
  try { const stored = localStorage.getItem(HISTORY_KEY); return stored ? (JSON.parse(stored) as string[]) : []; } catch { return []; }
}

export function SearchModal({ isOpen, onClose, onToggleOpen, onSelectSymbol, onOpenSettings, onOpenAIChat, onToggleTheme, isDarkTheme }: SearchModalProps) {
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);
  const [history, setHistory] = useState<string[]>(readHistory);
  const inputRef = useRef<HTMLInputElement>(null);
  const actionItems = useMemo<SearchItem[]>(() => [
    { id: "settings", group: "actions", title: "Mở Cài đặt", subtitle: "Quản lý giao diện và giao dịch", icon: Settings2, action: "settings" },
    { id: "chat", group: "actions", title: "Mở AI Chat", subtitle: "Hỏi TradeAI Assistant", icon: Bot, action: "chat" },
    { id: "theme", group: "actions", title: isDarkTheme ? "Đổi sang giao diện sáng" : "Đổi sang giao diện tối", subtitle: "Thay đổi theme của SnapChart", icon: isDarkTheme ? Sun : Moon, action: "theme" },
  ], [isDarkTheme]);
  const groups = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    const matches = (item: SearchItem) => !normalizedQuery || [item.title, item.subtitle, item.meta].filter(Boolean).join(" ").toLowerCase().includes(normalizedQuery);
    return [pairItems, signalItems, actionItems].map((items) => items.filter(matches));
  }, [actionItems, query]);
  const visibleItems = groups.flat();
  const showingHistory = !query.trim() && history.length > 0;
  const selectedIndex = Math.min(activeIndex, Math.max(visibleItems.length - 1, 0));

  useEffect(() => {
    const handleGlobalKeyDown = (event: globalThis.KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") { event.preventDefault(); if (!isOpen) setQuery(""); onToggleOpen(); }
    };
    document.addEventListener("keydown", handleGlobalKeyDown);
    return () => document.removeEventListener("keydown", handleGlobalKeyDown);
  }, [isOpen, onToggleOpen]);
  useEffect(() => {
    if (!isOpen) return undefined;
    const frameId = window.requestAnimationFrame(() => { setActiveIndex(0); inputRef.current?.focus(); });
    const handleKeyDown = (event: globalThis.KeyboardEvent) => { if (event.key === "Escape") onClose(); };
    document.addEventListener("keydown", handleKeyDown);
    return () => { window.cancelAnimationFrame(frameId); document.removeEventListener("keydown", handleKeyDown); };
  }, [isOpen, onClose]);

  const saveHistory = (value: string) => {
    const nextHistory = [value, ...history.filter((entry) => entry !== value)].slice(0, MAX_HISTORY);
    setHistory(nextHistory);
    try { localStorage.setItem(HISTORY_KEY, JSON.stringify(nextHistory)); } catch { /* Storage may be blocked by the browser. */ }
  };
  const activateItem = (item: SearchItem) => {
    saveHistory(item.title);
    onClose();
    if (item.action === "symbol" && item.value) onSelectSymbol(item.value);
    if (item.action === "settings") onOpenSettings();
    if (item.action === "chat") onOpenAIChat();
    if (item.action === "theme") onToggleTheme();
  };
  const handleInputKeyDown = (event: ReactKeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown") { event.preventDefault(); setActiveIndex((index) => (index + 1) % Math.max(visibleItems.length, 1)); }
    if (event.key === "ArrowUp") { event.preventDefault(); setActiveIndex((index) => (index - 1 + visibleItems.length) % Math.max(visibleItems.length, 1)); }
    if (event.key === "Enter" && visibleItems[selectedIndex]) { event.preventDefault(); activateItem(visibleItems[selectedIndex]); }
  };
  if (!isOpen) return null;

  return <div className="fixed inset-0 z-[150] flex items-start justify-center px-3 pt-[12vh] sm:px-6" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <div className="absolute inset-0 bg-slate-950/75 backdrop-blur-md" />
    <div role="dialog" aria-modal="true" aria-labelledby="search-modal-title" className="relative flex max-h-[min(680px,76vh)] w-full max-w-2xl flex-col overflow-hidden rounded-2xl border border-slate-700/80 bg-[#111722] shadow-2xl shadow-black/70">
      <h2 id="search-modal-title" className="sr-only">Tìm kiếm nhanh</h2>
      <div className="flex items-center gap-3 border-b border-slate-800 px-4 py-3"><Search className="h-5 w-5 shrink-0 text-cyan-300" /><input ref={inputRef} value={query} onChange={(event) => setQuery(event.target.value)} onKeyDown={handleInputKeyDown} placeholder="Tìm cặp giao dịch, tín hiệu hoặc thao tác..." className="min-w-0 flex-1 bg-transparent text-sm text-slate-100 outline-none placeholder:text-slate-500" />{query ? <button type="button" aria-label="Xóa nội dung tìm kiếm" onClick={() => setQuery("")} className="rounded-md p-1 text-slate-500 hover:bg-slate-800 hover:text-slate-200"><X className="h-4 w-4" /></button> : null}<kbd className="hidden items-center gap-1 rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-[10px] font-semibold text-slate-400 sm:flex">ESC</kbd><button type="button" aria-label="Đóng tìm kiếm" onClick={onClose} className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-800 hover:text-white"><X className="h-4 w-4" /></button></div>
      <div className="min-h-0 overflow-y-auto p-2">
        {showingHistory ? <div className="mb-2 border-b border-slate-800/80 px-3 py-2"><p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-500">Tìm kiếm gần đây</p><div className="mt-2 flex flex-wrap gap-2">{history.map((entry) => <button key={entry} type="button" onClick={() => setQuery(entry)} className="rounded-full border border-slate-700 bg-slate-900 px-2.5 py-1 text-xs text-slate-300 hover:border-cyan-400/50 hover:text-cyan-200">{entry}</button>)}</div></div> : null}
        {visibleItems.length === 0 ? <p className="px-3 py-10 text-center text-sm text-slate-500">Không tìm thấy kết quả phù hợp.</p> : groups.map((items) => items.length > 0 ? <section key={items[0].group} aria-label={groupLabels[items[0].group]}><h3 className="px-3 pb-1 pt-2 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-500">{groupLabels[items[0].group]}</h3>{items.map((item) => { const itemIndex = visibleItems.indexOf(item); const Icon = item.icon; return <button key={item.id} type="button" onMouseEnter={() => setActiveIndex(itemIndex)} onClick={() => activateItem(item)} className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition ${itemIndex === selectedIndex ? "bg-cyan-400/10" : "hover:bg-slate-800/70"}`}><div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${item.group === "pairs" ? "bg-slate-800 text-slate-300" : item.group === "signals" ? "bg-amber-400/10 text-amber-300" : "bg-violet-400/10 text-violet-300"}`}>{Icon ? <Icon className="h-4 w-4" /> : item.group === "signals" ? <Check className="h-4 w-4" /> : <Command className="h-4 w-4" />}</div><div className="min-w-0 flex-1"><p className="truncate text-sm font-medium text-slate-100">{item.title}</p>{item.subtitle ? <p className="mt-0.5 truncate text-xs text-slate-500">{item.subtitle}</p> : null}</div>{item.price ? <span className="text-xs font-medium text-slate-300">{item.price}</span> : null}{typeof item.change === "number" ? <span className={`flex items-center gap-1 text-xs font-semibold ${item.change >= 0 ? "text-emerald-300" : "text-rose-300"}`}>{item.change >= 0 ? <TrendingUp className="h-3.5 w-3.5" /> : <TrendingDown className="h-3.5 w-3.5" />}{item.change >= 0 ? "+" : ""}{item.change.toFixed(2)}%</span> : null}{item.meta ? <span className="rounded-md border border-emerald-400/20 bg-emerald-400/10 px-2 py-1 text-[10px] font-semibold text-emerald-300">{item.meta}</span> : null}</button>; })}</section> : null)}
      </div>
      <footer className="flex items-center justify-between border-t border-slate-800 px-4 py-2.5 text-[10px] text-slate-500"><span className="flex items-center gap-3"><span><ArrowUp className="mr-1 inline h-3 w-3" /><ArrowDown className="mr-1 inline h-3 w-3" />Di chuyển</span><span>Enter chọn</span></span><span>Ctrl K để mở nhanh</span></footer>
    </div>
  </div>;
}
import { LockKeyhole, ShieldCheck, Zap } from "lucide-react";
import type { MarketStructureData } from "../hooks/useMarketStructure";

type MarketStructureProps = {
  data: MarketStructureData;
  isPro: boolean;
  isAdmin?: boolean;
  onUpgrade: () => void;
};

export function MarketStructure({ data, isPro, isAdmin = false, onUpgrade }: MarketStructureProps) {
  const unlocked = isPro || isAdmin;
  return (
    <div className="relative overflow-hidden rounded-[22px] border border-slate-800 bg-[#121721] p-4">
      <div className={!unlocked ? "select-none blur-[2px]" : undefined} aria-hidden={!unlocked}>
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-[11px] uppercase tracking-[0.2em] text-slate-400">Cấu trúc thị trường</h3>
          <span className="rounded-full border px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.14em]" style={{ borderColor: `${data.badgeColor}66`, backgroundColor: `${data.badgeColor}1A`, color: data.badgeColor }}>
            {data.trendLabel}
          </span>
        </div>

        <div className="mb-4 flex min-h-[52px] flex-wrap gap-2">
          {data.events.map((event, index) => (
            <div key={`${event.type}-${event.time}-${index}`} className="rounded-lg border border-slate-700 bg-slate-900/80 px-2.5 py-1.5 text-center shadow-[0_0_16px_rgba(52,211,153,0.12)]">
              <div className={`text-[10px] font-bold uppercase tracking-[0.16em] ${event.isBullish ? "text-emerald-300" : "text-rose-300"}`}>{event.type}</div>
              <div className="mt-1 text-[9px] text-slate-400">{event.time}</div>
            </div>
          ))}
          {data.events.length === 0 && <span className="text-xs text-slate-500">Đang quét swing high / swing low...</span>}
        </div>

        <div className={`rounded-2xl border p-3 ${data.trend === "BEARISH" ? "border-rose-500/40 bg-rose-500/10" : data.trend === "BULLISH" ? "border-emerald-500/30 bg-emerald-500/8" : "border-amber-500/30 bg-amber-500/8"}`}>
          <div className={`flex items-center gap-2 text-sm font-semibold ${data.trend === "BEARISH" ? "text-rose-300" : data.trend === "BULLISH" ? "text-emerald-300" : "text-amber-300"}`}>
            <ShieldCheck className="h-4 w-4" />
            {data.statusSummary}
          </div>
          <p className="mt-2 text-sm leading-6 text-slate-300">{data.setupRecommendation}</p>
        </div>
      </div>

      {!unlocked && (
        <div className="absolute inset-0 flex items-center justify-center bg-slate-900/80 p-4 backdrop-blur-sm">
          <div className="max-w-[260px] text-center">
            <LockKeyhole className="mx-auto mb-3 h-9 w-9 text-amber-300 drop-shadow-[0_0_12px_rgba(252,211,77,0.8)]" />
            <p className="text-sm font-semibold leading-6 text-white">Tính năng SMC Pro (BOS / CHOCH / Order Block)</p>
            <button type="button" onClick={onUpgrade} className="mt-4 inline-flex items-center gap-2 rounded-xl border border-amber-300/70 bg-slate-950/80 px-3 py-2 text-xs font-bold text-amber-200 transition hover:bg-amber-300/10">
              Mở khóa chỉ $5/tháng <Zap className="h-3.5 w-3.5 fill-current" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

import { ShieldCheck } from "lucide-react";
import type { MarketStructureData } from "../hooks/useMarketStructure";

type MarketStructureProps = {
  data: MarketStructureData;
};

export function MarketStructure({ data }: MarketStructureProps) {
  return (
    <div className="relative overflow-hidden rounded-[22px] border border-slate-800 bg-[#121721] p-4">
      <div>
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

    </div>
  );
}

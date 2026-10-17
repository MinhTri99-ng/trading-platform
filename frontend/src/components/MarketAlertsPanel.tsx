import { useMemo } from "react";
import { ShieldAlert, TrendingDown, TrendingUp } from "lucide-react";
import type { MarketSignal } from "../hooks/useMarketRealtime";
import type { MarketStructureData } from "../hooks/useMarketStructure";

type MarketAlertsPanelProps = {
  signal: MarketSignal | null;
  selectedSymbol: string;
  marketStructure: MarketStructureData;
};

export function MarketAlertsPanel({ signal, selectedSymbol, marketStructure }: MarketAlertsPanelProps) {
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

    </section>
  );
}

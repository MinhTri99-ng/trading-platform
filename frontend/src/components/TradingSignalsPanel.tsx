import { useEffect, useState } from "react";
import { TrendingUp, TrendingDown, AlertCircle, CheckCircle, Clock } from "lucide-react";
import type { MarketSignal } from "../hooks/useMarketRealtime";

export interface TradingSignalsPanelProps {
  signal: MarketSignal | null;
  isVisible?: boolean;
  className?: string;
}

export function TradingSignalsPanel({
  signal,
  isVisible = true,
  className = "",
}: TradingSignalsPanelProps) {
  const [displaySignal, setDisplaySignal] = useState<MarketSignal | null>(signal);
  const [isAnimating, setIsAnimating] = useState(false);

  useEffect(() => {
    if (signal && !displaySignal) {
      setIsAnimating(true);
      setDisplaySignal(signal);
      const timer = setTimeout(() => setIsAnimating(false), 500);
      return () => clearTimeout(timer);
    } else if (!signal && displaySignal) {
      setDisplaySignal(null);
    }
  }, [signal, displaySignal]);

  if (!isVisible || !displaySignal) {
    return (
      <div className={`rounded-2xl border border-slate-800 bg-slate-950/70 p-6 ${className}`}>
<div className="flex items-center justify-center py-12">
          <div className="text-center">
            <Clock className="mx-auto h-12 w-12 text-slate-600 mb-4" />
            <h3 className="text-lg font-semibold text-slate-400 mb-2">
              Waiting for Signal
            </h3>
            <p className="text-sm text-slate-500 max-w-xs">
              Upload a chart screenshot to analyze or wait for real-time market signals
            </p>
          </div>
        </div>
      </div>
    );
  }

  const getDirectionIcon = (direction: string) => {
    switch (direction) {
      case "LONG":
        return <TrendingUp className="h-5 w-5 text-emerald-400" />;
      case "SHORT":
        return <TrendingDown className="h-5 w-5 text-rose-400" />;
      default:
        return <AlertCircle className="h-5 w-5 text-slate-400" />;
    }
  };

  const getDirectionColor = (direction: string) => {
    switch (direction) {
      case "LONG":
        return "border-emerald-500/30 bg-emerald-500/10 text-emerald-300";
      case "SHORT":
        return "border-rose-500/30 bg-rose-500/10 text-rose-300";
      default:
        return "border-slate-500/30 bg-slate-500/10 text-slate-300";
    }
  };

  const formatPrice = (price: number) => {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: "USD",
      minimumFractionDigits: price < 100 ? 2 : 0,
      maximumFractionDigits: price < 100 ? 2 : 0,
    }).format(price);
  };

  const getConfidenceColor = (confidence: number) => {
    if (confidence >= 80) return "text-emerald-400";
    if (confidence >= 60) return "text-amber-400";
    return "text-rose-400";
  };

  return (
    <div className={`rounded-2xl border border-slate-800 bg-slate-950/70 p-6 transition-all duration-300 ${isAnimating ? "scale-105 shadow-lg shadow-emerald-500/20" : ""} ${className}`}>
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          {getDirectionIcon(displaySignal.direction)}
          <div>
            <h3 className="text-lg font-semibold text-slate-100">
              {displaySignal.symbol} Signal
            </h3>
            <p className="text-sm text-slate-400">
              Generated {new Date(displaySignal.timestamp).toLocaleTimeString()} • Confidence: {displaySignal.confidence}%
            </p>
          </div>
        </div>

        <span className={`inline-flex items-center gap-1 rounded-full px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] ${getDirectionColor(displaySignal.direction)}`}>
          {displaySignal.direction}
        </span>
      </div>

      <div className="grid grid-cols-2 gap-4 mb-6">
        <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-4">
          <div className="text-[10px] uppercase tracking-[0.14em] text-slate-500 mb-1">Entry</div>
          <div className="text-lg font-semibold text-slate-100">
            {formatPrice(displaySignal.entry)}
          </div>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-4">
          <div className="text-[10px] uppercase tracking-[0.14em] text-slate-500 mb-1">Stop Loss</div>
          <div className="text-lg font-semibold text-rose-400">
            {formatPrice(displaySignal.stopLoss)}
          </div>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-4">
          <div className="text-[10px] uppercase tracking-[0.14em] text-slate-500 mb-1">Take Profit</div>
          <div className="text-lg font-semibold text-emerald-400">
            {formatPrice(displaySignal.takeProfit)}
          </div>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-4">
          <div className="text-[10px] uppercase tracking-[0.14em] text-slate-500 mb-1">Risk/Reward</div>
          <div className="text-lg font-semibold text-slate-100">
            {displaySignal.riskReward}
          </div>
        </div>
      </div>

      <div className="flex items-center justify-between pt-4 border-t border-slate-800">
        <div className="flex items-center gap-2">
          <CheckCircle className="h-4 w-4 text-emerald-400" />
          <span className="text-sm text-slate-300">Live Market Verification</span>
        </div>

        <div className="text-right">
          <div className={`text-sm font-medium ${getConfidenceColor(displaySignal.confidence)}`}>Confidence: {displaySignal.confidence}%</div>
          <div className="text-[10px] text-slate-500">AI-Powered Analysis</div>
        </div>
      </div>
    </div>
  );
}
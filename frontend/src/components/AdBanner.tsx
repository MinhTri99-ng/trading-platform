type AdBannerProps = {
  slotId: string;
  format: "horizontal" | "vertical" | "rectangle";
};

const formatClasses: Record<AdBannerProps["format"], string> = {
  horizontal: "min-h-[90px]",
  vertical: "min-h-[240px]",
  rectangle: "min-h-[180px]",
};

export function AdBanner({ slotId, format }: AdBannerProps) {
  return (
    <div className={`w-full ${formatClasses[format]} flex flex-col items-center justify-center rounded-xl border border-slate-800/80 bg-slate-900/50 p-3 text-xs text-slate-500`} data-ad-slot={slotId} data-ad-format={format}>
      <span className="mb-1 rounded bg-slate-800 px-2 py-0.5 text-[10px] uppercase tracking-wider text-slate-400">Quảng cáo</span>
      {/* Code Google AdSense / Adsterra script sẽ nhúng vào đây */}
    </div>
  );
}
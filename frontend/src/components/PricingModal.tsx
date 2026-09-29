import { Check, CreditCard, QrCode, ShieldCheck, Wallet, X } from "lucide-react";
import { useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext";

type PricingModalProps = { isOpen: boolean; onClose: () => void };
type PaymentMethod = "vietqr" | "crypto" | "stripe";

const benefits = ["Unlimited SMC Vision", "Dynamic BOS / CHOCH", "Premium Signals"];

export function PricingModal({ isOpen, onClose }: PricingModalProps) {
  const { isPro, isAuthenticated, upgradeToPro } = useAuth();
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("vietqr");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    if (!isOpen) return undefined;
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === "Escape") onClose(); };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const confirmUpgrade = () => {
    if (!isAuthenticated) { setNotice("Vui lòng đăng nhập trước khi nâng cấp Pro."); return; }
    const result = upgradeToPro();
    if (result.success) { setNotice("Đã kích hoạt SMC Pro cho tài khoản này."); }
    else setNotice(result.error ?? "Không thể kích hoạt Pro.");
  };

  return (
    <div className="fixed inset-0 z-[120] flex items-center justify-center p-4" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <div className="absolute inset-0 bg-black/80 backdrop-blur-sm" />
      <div role="dialog" aria-modal="true" aria-labelledby="pricing-title" className="relative max-h-[calc(100vh-32px)] w-full max-w-2xl overflow-y-auto rounded-2xl border border-amber-300/30 bg-[#121721] shadow-2xl shadow-amber-950/30">
        <div className="border-b border-slate-800 bg-gradient-to-r from-amber-300/10 via-slate-900/20 to-cyan-300/10 p-5 sm:p-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-amber-300/30 bg-amber-300/10 px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.16em] text-amber-200"><ShieldCheck className="h-3.5 w-3.5" />Sản phẩm SMC Pro</div>
              <h2 id="pricing-title" className="text-2xl font-bold text-white">Nâng cấp SnapChart AI Pro 🚀</h2>
              <p className="mt-2 text-sm text-slate-400">Mở khóa toàn bộ bộ công cụ Smart Money Concepts chuyên sâu.</p>
            </div>
            <button type="button" aria-label="Đóng" onClick={onClose} className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-800 hover:text-white"><X className="h-5 w-5" /></button>
          </div>
          <div className="mt-5 flex flex-wrap items-end gap-x-3 gap-y-1"><span className="text-4xl font-bold text-amber-200">$5</span><span className="pb-1 text-sm text-slate-400">/ tháng</span><span className="pb-1 text-xs text-slate-500">hoặc 120.000 VNĐ / tháng</span></div>
        </div>

        <div className="grid gap-5 p-5 sm:p-6 md:grid-cols-[1fr_1.1fr]">
          <div>
            <p className="mb-3 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">Quyền lợi thành viên</p>
            <ul className="space-y-3">{benefits.map((benefit) => <li key={benefit} className="flex items-center gap-2.5 text-sm text-slate-200"><Check className="h-4 w-4 shrink-0 text-emerald-300" />{benefit}</li>)}</ul>
            <div className="mt-6 rounded-xl border border-slate-800 bg-slate-950/60 p-3 text-xs leading-5 text-slate-400">Thanh toán trong giao diện này là mô phỏng để kiểm thử. Sau khi xác nhận, tài khoản sẽ được bật Pro ngay lập tức.</div>
          </div>

          <div>
            <p className="mb-3 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">Phương thức thanh toán</p>
            <div className="grid grid-cols-3 gap-2">
              {(["vietqr", "crypto", "stripe"] as PaymentMethod[]).map((method) => {
                const Icon = method === "vietqr" ? QrCode : method === "crypto" ? Wallet : CreditCard;
                const label = method === "vietqr" ? "VietQR" : method === "crypto" ? "Crypto" : "Stripe";
                return <button key={method} type="button" onClick={() => setPaymentMethod(method)} className={`flex flex-col items-center gap-1 rounded-xl border px-2 py-2.5 text-[10px] font-semibold transition ${paymentMethod === method ? "border-amber-300/70 bg-amber-300/10 text-amber-200" : "border-slate-700 bg-slate-950/60 text-slate-400 hover:border-slate-500"}`}><Icon className="h-4 w-4" />{label}</button>;
              })}
            </div>
            <div className="mt-3 flex min-h-[130px] flex-col items-center justify-center rounded-xl border border-slate-800 bg-slate-950/60 p-4 text-center">
              {paymentMethod === "vietqr" ? <><div className="mb-2 flex h-20 w-20 items-center justify-center border-4 border-white bg-white text-2xl text-slate-900">QR</div><p className="text-xs text-slate-300">Quét VietQR để chuyển khoản</p></> : paymentMethod === "crypto" ? <><Wallet className="mb-2 h-8 w-8 text-cyan-300" /><p className="text-xs text-slate-300">USDT TRC20 / BEP20</p><p className="mt-1 break-all text-[10px] text-slate-500">TX8s...SNAPCHARTPRO</p></> : <><CreditCard className="mb-2 h-8 w-8 text-violet-300" /><p className="text-xs text-slate-300">Thanh toán an toàn qua Stripe</p></>}
            </div>
          </div>
        </div>

        <div className="border-t border-slate-800 p-5 sm:p-6">
          {notice ? <p role="status" className="mb-3 rounded-lg border border-emerald-500/20 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-300">{notice}</p> : null}
          <button type="button" onClick={confirmUpgrade} disabled={isPro} className="flex w-full items-center justify-center rounded-xl border border-amber-300/70 bg-amber-300 px-4 py-3 text-sm font-bold text-slate-950 transition hover:bg-amber-200 disabled:cursor-default disabled:opacity-60">{isPro ? "SMC Pro đã được kích hoạt" : "Xác nhận nâng cấp"}</button>
        </div>
      </div>
    </div>
  );
}

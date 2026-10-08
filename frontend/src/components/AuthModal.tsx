import { useEffect, useState, type FormEvent } from "react";
import { Eye, EyeOff, LoaderCircle, LogIn, UserPlus, X } from "lucide-react";
import { useAuth, type RegisterInput } from "../context/AuthContext";

type AuthMode = "login" | "register";
type AuthModalProps = {
  isOpen: boolean;
  onClose: () => void;
  initialMode?: AuthMode;
  onGoogleLogin?: () => void | Promise<void>;
  onFacebookLogin?: () => void | Promise<void>;
};
type RegisterDraft = Omit<RegisterInput, "id"> & { confirmPassword: string };

const emptyRegister: RegisterDraft = {
  firstName: "",
  lastName: "",
  username: "",
  email: "",
  phone: "",
  password: "",
  confirmPassword: "",
};
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/;
const MAX_ATTEMPTS = 5;
const LOCKOUT_MS = 30_000;

function Field({
  label,
  type = "text",
  value,
  onChange,
  autoComplete,
  placeholder,
}: {
  label: string;
  type?: string;
  value: string;
  onChange: (value: string) => void;
  autoComplete?: string;
  placeholder?: string;
}) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-xs font-medium text-slate-400">{label}</span>
      <input
        required
        type={type}
        value={value}
        placeholder={placeholder}
        autoComplete={autoComplete}
        onChange={(event) => onChange(event.target.value)}
        className="w-full rounded-xl border border-slate-700 bg-slate-950 px-3 py-2.5 text-sm text-slate-100 outline-none transition placeholder:text-slate-600 focus:border-cyan-400 focus:ring-1 focus:ring-cyan-400/30"
      />
    </label>
  );
}

function GoogleIcon() {
  return <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5"><path fill="#4285F4" d="M21.35 12.23c0-.72-.06-1.42-.18-2.09H12v3.95h5.24a4.48 4.48 0 0 1-1.94 2.94v2.45h3.14c1.84-1.69 2.91-4.18 2.91-7.25Z" /><path fill="#34A853" d="M12 21.6c2.63 0 4.84-.87 6.45-2.36l-3.14-2.45c-.87.58-1.98.92-3.31.92-2.54 0-4.69-1.72-5.46-4.03H3.3v2.53A9.74 9.74 0 0 0 12 21.6Z" /><path fill="#FBBC05" d="M6.54 13.68a5.85 5.85 0 0 1 0-3.36V7.79H3.3a9.74 9.74 0 0 0 0 8.42l3.24-2.53Z" /><path fill="#EA4335" d="M12 6.29c1.43 0 2.71.49 3.72 1.45l2.79-2.79C16.84 3.31 14.63 2.4 12 2.4a9.74 9.74 0 0 0-8.7 5.39l3.24 2.53C7.31 8.01 9.46 6.29 12 6.29Z" /></svg>;
}

function FacebookIcon() {
  return <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5 fill-current"><path d="M13.5 21v-8h2.7l.4-3h-3.1V8.08c0-.87.24-1.46 1.5-1.46h1.7V3.94a22.6 22.6 0 0 0-2.47-.13c-2.45 0-4.13 1.5-4.13 4.25V10H7.35v3h2.75v8h3.4Z" /></svg>;
}

export function AuthModal({ isOpen, onClose, initialMode = "login", onGoogleLogin, onFacebookLogin }: AuthModalProps) {
  const { login, register } = useAuth();
  const [mode, setMode] = useState<AuthMode>(initialMode);
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [draft, setDraft] = useState<RegisterDraft>(emptyRegister);
  const [error, setError] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [remember, setRemember] = useState(true);
  const [isLoading, setIsLoading] = useState(false);
  const [failedAttempts, setFailedAttempts] = useState(0);
  const [lockedUntil, setLockedUntil] = useState(0);

  useEffect(() => {
    if (!isOpen) return undefined;
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === "Escape" && !isLoading) onClose(); };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [isOpen, isLoading, onClose]);

  if (!isOpen) return null;
  const setDraftField = (key: keyof RegisterDraft, value: string) => setDraft((current) => ({ ...current, [key]: value }));
  const switchMode = (nextMode: AuthMode) => { setMode(nextMode); setError(""); setShowPassword(false); };
  const handleSocialLogin = async (provider: "Google" | "Facebook", callback?: () => void | Promise<void>) => {
    setError("");
    if (!callback) { setError(`${provider} OAuth chưa được cấu hình cho môi trường này.`); return; }
    setIsLoading(true);
    try { await callback(); } catch { setError(`Không thể đăng nhập bằng ${provider}. Vui lòng thử lại.`); } finally { setIsLoading(false); }
  };
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isLoading) return;
    if (lockedUntil > Date.now()) { setError("Bạn đã thử quá nhiều lần. Vui lòng đợi 30 giây rồi thử lại."); return; }
    setError("");
    if (mode === "login") {
      if (identifier.includes("@") && !emailPattern.test(identifier)) { setError("Vui lòng nhập email hợp lệ."); return; }
      if (!passwordPattern.test(password)) { setError("Mật khẩu phải có ít nhất 8 ký tự, bao gồm cả chữ và số."); return; }
      setIsLoading(true);
      await new Promise((resolve) => window.setTimeout(resolve, 350));
      const result = login({ identifier, password, remember });
      setIsLoading(false);
      if (result.success) { setFailedAttempts(0); onClose(); } else {
        const attempts = failedAttempts + 1;
        setFailedAttempts(attempts);
        if (attempts >= MAX_ATTEMPTS) { setLockedUntil(Date.now() + LOCKOUT_MS); setError("Tài khoản tạm khóa do thử đăng nhập quá nhiều lần. Vui lòng đợi 30 giây."); }
        else setError(result.error ?? "Đăng nhập thất bại.");
      }
      return;
    }
    if (!emailPattern.test(draft.email)) { setError("Vui lòng nhập email hợp lệ."); return; }
    if (!passwordPattern.test(draft.password)) { setError("Mật khẩu phải có ít nhất 8 ký tự, bao gồm cả chữ và số."); return; }
    if (draft.password !== draft.confirmPassword) { setError("Mật khẩu xác nhận không khớp."); return; }
    setIsLoading(true);
    await new Promise((resolve) => window.setTimeout(resolve, 350));
    const result = register(draft);
    setIsLoading(false);
    if (result.success) onClose();
    else { setMode("login"); setIdentifier(draft.email); setPassword(""); setError(result.error ?? "Đăng ký thất bại."); }
  };

  return (
    <div className="fixed inset-0 z-[110] flex items-center justify-center p-4" onMouseDown={(event) => { if (event.target === event.currentTarget && !isLoading) onClose(); }}>
      <div className="absolute inset-0 bg-black/80 backdrop-blur-md" />
      <div role="dialog" aria-modal="true" aria-labelledby="auth-title" className="relative max-h-[calc(100vh-32px)] w-full max-w-lg overflow-y-auto rounded-3xl border border-slate-700/80 bg-[#101722] p-5 shadow-2xl shadow-black/70 sm:p-7">
        <div className="mb-5 flex items-start justify-between">
          <div><div className="mb-3 flex h-11 w-11 items-center justify-center rounded-2xl bg-cyan-400/10 text-cyan-300">{mode === "login" ? <LogIn className="h-5 w-5" /> : <UserPlus className="h-5 w-5" />}</div><h2 id="auth-title" className="text-xl font-semibold text-white">{mode === "login" ? "Chào mừng trở lại" : "Tạo tài khoản TradeAI PRO"}</h2><p className="mt-1 text-xs text-slate-500">Bảo mật tài khoản của bạn luôn là ưu tiên.</p></div>
          <button type="button" aria-label="Đóng" disabled={isLoading} onClick={onClose} className="rounded-xl p-2 text-slate-400 transition hover:bg-slate-800 hover:text-white disabled:opacity-50"><X className="h-5 w-5" /></button>
        </div>
        <div className="mb-5 grid grid-cols-2 rounded-xl border border-slate-800 bg-slate-950/60 p-1"><button type="button" onClick={() => switchMode("login")} className={`rounded-lg py-2 text-sm font-semibold transition ${mode === "login" ? "bg-cyan-400/15 text-cyan-200" : "text-slate-500 hover:text-slate-300"}`}>Đăng nhập</button><button type="button" onClick={() => switchMode("register")} className={`rounded-lg py-2 text-sm font-semibold transition ${mode === "register" ? "bg-cyan-400/15 text-cyan-200" : "text-slate-500 hover:text-slate-300"}`}>Đăng ký</button></div>
        <div className="grid gap-3 sm:grid-cols-2"><button type="button" disabled={isLoading} onClick={() => void handleSocialLogin("Google", onGoogleLogin)} className="flex items-center justify-center gap-2 rounded-xl bg-slate-800 px-4 py-3 text-sm font-semibold text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-60"><GoogleIcon />Tiếp tục với Google</button><button type="button" disabled={isLoading} onClick={() => void handleSocialLogin("Facebook", onFacebookLogin)} className="flex items-center justify-center gap-2 rounded-xl bg-[#1877F2]/20 px-4 py-3 text-sm font-semibold text-[#1877F2] transition hover:bg-[#1877F2]/30 disabled:cursor-not-allowed disabled:opacity-60"><FacebookIcon />Tiếp tục với Facebook</button></div>
        <div className="my-4 flex items-center"><div className="flex-1 border-t border-slate-800" /><span className="px-3 text-xs text-slate-500">hoặc dùng Email</span><div className="flex-1 border-t border-slate-800" /></div>
        {error ? <p role="alert" className="mb-4 rounded-xl border border-rose-500/20 bg-rose-500/10 px-3 py-2.5 text-sm text-rose-300">{error}</p> : null}
        <form onSubmit={(event) => void submit(event)} className="space-y-4">
          {mode === "login" ? <><Field label="Username hoặc Email" value={identifier} onChange={setIdentifier} autoComplete="username" placeholder="you@example.com" /><label className="block"><span className="mb-1.5 block text-xs font-medium text-slate-400">Mật khẩu</span><div className="relative"><input required minLength={8} type={showPassword ? "text" : "password"} value={password} autoComplete="current-password" onChange={(event) => setPassword(event.target.value)} className="w-full rounded-xl border border-slate-700 bg-slate-950 px-3 py-2.5 pr-11 text-sm text-slate-100 outline-none transition focus:border-cyan-400 focus:ring-1 focus:ring-cyan-400/30" /><button type="button" aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"} onClick={() => setShowPassword((value) => !value)} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-200">{showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}</button></div></label><div className="flex items-center justify-between text-xs"><label className="flex items-center gap-2 text-slate-400"><input type="checkbox" checked={remember} onChange={(event) => setRemember(event.target.checked)} className="accent-cyan-400" />Ghi nhớ phiên đăng nhập</label><button type="button" onClick={() => setError("Tính năng đặt lại mật khẩu sẽ được gửi qua email của bạn.")} className="text-cyan-300 hover:text-cyan-200">Quên mật khẩu?</button></div></> : <><div className="grid gap-4 sm:grid-cols-2"><Field label="Họ" value={draft.firstName} onChange={(value) => setDraftField("firstName", value)} autoComplete="given-name" /><Field label="Tên" value={draft.lastName} onChange={(value) => setDraftField("lastName", value)} autoComplete="family-name" /></div><Field label="Username" value={draft.username} onChange={(value) => setDraftField("username", value)} autoComplete="username" /><Field label="Email" type="email" value={draft.email} onChange={(value) => setDraftField("email", value)} autoComplete="email" /><Field label="Số điện thoại" type="tel" value={draft.phone} onChange={(value) => setDraftField("phone", value)} autoComplete="tel" /><div className="grid gap-4 sm:grid-cols-2"><Field label="Mật khẩu" type="password" value={draft.password} onChange={(value) => setDraftField("password", value)} autoComplete="new-password" /><Field label="Xác nhận mật khẩu" type="password" value={draft.confirmPassword} onChange={(value) => setDraftField("confirmPassword", value)} autoComplete="new-password" /></div></>}
          <button type="submit" disabled={isLoading} className="flex w-full items-center justify-center gap-2 rounded-xl bg-cyan-400 px-4 py-3 text-sm font-bold text-slate-950 transition hover:bg-cyan-300 disabled:cursor-not-allowed disabled:opacity-60">{isLoading ? <><LoaderCircle className="h-4 w-4 animate-spin" />Đang xử lý...</> : mode === "login" ? "Đăng nhập" : "Tạo tài khoản"}</button>
        </form>
      </div>
    </div>
  );
}

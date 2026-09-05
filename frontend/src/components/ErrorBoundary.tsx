import { Component, type ErrorInfo, type ReactNode } from "react";

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
}

export class AppErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("AppErrorBoundary caught an error:", error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-screen items-center justify-center bg-[#0B0E14] px-6 text-slate-100">
          <div className="max-w-md rounded-2xl border border-rose-500/30 bg-slate-900/80 p-6 text-center shadow-[0_0_20px_rgba(239,68,68,0.15)]">
            <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-rose-500/10 text-2xl text-rose-300">
              !
            </div>
            <h2 className="text-xl font-semibold text-white">Ứng dụng đã gặp lỗi runtime</h2>
            <p className="mt-3 text-sm leading-6 text-slate-300">
              Dữ liệu thị trường hoặc WebSocket hiện đang bị gián đoạn. Hệ thống đã chuyển sang chế độ an toàn để tránh màn hình đen.
            </p>
            <button
              type="button"
              onClick={() => window.location.reload()}
              className="mt-5 rounded-xl bg-rose-500 px-4 py-2 text-sm font-medium text-white transition hover:bg-rose-400"
            >
              Tải lại trang
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

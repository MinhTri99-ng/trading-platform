import { useEffect, useRef, useState } from "react";
import { Bot, LoaderCircle, Send, Sparkles, X } from "lucide-react";
import ReactMarkdown from "react-markdown";

type ChatRole = "user" | "assistant";

type ChatMessage = {
  id: string;
  role: ChatRole;
  content: string;
};

type GeminiResponse = {
  candidates?: Array<{
    content?: {
      parts?: Array<{ text?: string }>;
    };
  }>;
  error?: {
    message?: string;
  };
};

interface AIChatDrawerProps {
  isOpen: boolean;
  onClose: () => void;
}

const SYSTEM_INSTRUCTION = `Bạn là TradeAI Assistant - Chuyên gia phân tích kỹ thuật Crypto và phương pháp SMC (Smart Money Concepts).
- Hãy trả lời chi tiết, chuyên nghiệp, rõ ràng bằng tiếng Việt.
- Sử dụng định dạng Markdown (bôi đen, gạch đầu dòng) để câu trả lời dễ đọc.
- Nếu người dùng hỏi về xu hướng hoặc giá cả hiện tại, hãy dựa trên kiến thức phân tích kỹ thuật SMC (như Liquidity, Order Block, FVG, Break of Structure) để đưa ra kịch bản phân tích mẫu chuẩn xác chứ không từ chối trả lời.`;

const QUICK_PROMPTS = [
  "Phân tích xu hướng BTC hiện tại",
  "Giải thích mô hình SMC",
  "Gợi ý cài đặt Risk Management",
];

const WELCOME_MESSAGE: ChatMessage = {
  id: "welcome",
  role: "assistant",
  content: "Xin chào! Tôi là TradeAI Assistant. Bạn muốn phân tích thị trường hay tìm hiểu SMC hôm nay?",
};

const createMessageId = () => `${Date.now()}-${Math.random().toString(36).slice(2)}`;

export function AIChatDrawer({ isOpen, onClose }: AIChatDrawerProps) {
  const [chatHistory, setChatHistory] = useState<ChatMessage[]>([WELCOME_MESSAGE]);
  const [inputValue, setInputValue] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLTextAreaElement | null>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [chatHistory, isLoading]);

  useEffect(() => {
    if (!isOpen) return undefined;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };

    document.addEventListener("keydown", handleKeyDown);
    inputRef.current?.focus();
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  const sendMessage = async (rawMessage: string) => {
    const message = rawMessage.trim();
    if (!message || isLoading) return;

    const userMessage: ChatMessage = { id: createMessageId(), role: "user", content: message };
    const nextHistory = [...chatHistory, userMessage];
    setChatHistory(nextHistory);
    setInputValue("");
    setErrorMessage(null);
    setIsLoading(true);

    try {
      const apiKey = import.meta.env.VITE_GEMINI_API_KEY?.trim();
      if (!apiKey) {
        throw new Error("Thiếu VITE_GEMINI_API_KEY trong file .env.local.");
      }

      // Luồng: User gõ -> lưu chatHistory -> gọi Gemini -> nhận response -> cập nhật UI.
      const response = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=${encodeURIComponent(apiKey)}`,
        
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            systemInstruction: { parts: [{ text: SYSTEM_INSTRUCTION }] },
            contents: nextHistory
              .filter((chatMessage) => chatMessage.id !== "welcome")
              .map((chatMessage) => ({
                role: chatMessage.role === "assistant" ? "model" : "user",
                parts: [{ text: chatMessage.content }],
              })),
            generationConfig: { temperature: 0.4, maxOutputTokens: 2048 },
          }),
        },
      );

      const payload = (await response.json()) as GeminiResponse;
      if (!response.ok) {
        throw new Error(payload.error?.message || "Gemini chưa thể trả lời lúc này.");
      }

      const assistantText = payload.candidates?.[0]?.content?.parts
        ?.map((part) => part.text ?? "")
        .join("")
        .trim();

      if (!assistantText) {
        throw new Error("Gemini trả về phản hồi rỗng.");
      }

      setChatHistory((currentHistory) => [
        ...currentHistory,
        { id: createMessageId(), role: "assistant", content: assistantText },
      ]);
    } catch (error) {
      const messageText = error instanceof Error ? error.message : "Không thể kết nối Gemini.";
      setErrorMessage(messageText);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSubmit = () => {
    void sendMessage(inputValue);
  };

  const handleInputKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      handleSubmit();
    }
  };

  return (
    <>
      <div
        aria-hidden={!isOpen}
        className={`fixed inset-0 z-[110] bg-black/45 backdrop-blur-[2px] transition-opacity duration-300 ${isOpen ? "pointer-events-auto opacity-100" : "pointer-events-none opacity-0"}`}
        onMouseDown={(event) => {
          if (event.target === event.currentTarget) onClose();
        }}
      />
      <aside
        aria-label="Hỏi TradeAI Assistant"
        aria-hidden={!isOpen}
        className={`fixed inset-y-0 right-0 z-[120] flex w-full max-w-[440px] flex-col border-l border-slate-700/80 bg-[#0d121b] shadow-2xl shadow-black/60 transition-transform duration-300 ease-out ${isOpen ? "translate-x-0" : "translate-x-full"}`}
      >
        <header className="flex items-center justify-between border-b border-slate-800 px-5 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-500/15 text-violet-200">
              <Sparkles className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-white">Hỏi TradeAI Assistant</h2>
              <p className="mt-1 flex items-center gap-1.5 text-[10px] uppercase tracking-[0.16em] text-emerald-300">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" /> Sẵn sàng hỗ trợ
              </p>
            </div>
          </div>
          <button type="button" aria-label="Đóng chat AI" onClick={onClose} className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-800 hover:text-white">
            <X className="h-5 w-5" />
          </button>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto px-4 py-5">
          <div className="mb-5 flex flex-wrap gap-2">
            {QUICK_PROMPTS.map((prompt) => (
              <button
                key={prompt}
                type="button"
                disabled={isLoading}
                onClick={() => void sendMessage(prompt)}
                className="rounded-full border border-violet-400/25 bg-violet-400/8 px-3 py-2 text-left text-[11px] leading-4 text-violet-200 transition hover:border-violet-300/60 hover:bg-violet-400/15 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {prompt}
              </button>
            ))}
          </div>

          <div className="space-y-4">
            {chatHistory.map((chatMessage) => (
              <div key={chatMessage.id} className={`flex gap-2.5 ${chatMessage.role === "user" ? "justify-end" : "items-start"}`}>
                {chatMessage.role === "assistant" ? (
                  <div className="mt-1 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-violet-500/15 text-violet-200">
                    <Bot className="h-4 w-4" />
                  </div>
                ) : null}
                <div className={`max-w-[82%] rounded-2xl px-3.5 py-3 text-sm leading-6 ${chatMessage.role === "user" ? "rounded-br-md bg-blue-500 text-white" : "rounded-tl-md border border-slate-800 bg-slate-900/90 text-slate-200"}`}>
                  {chatMessage.role === "assistant" ? (
                    <ReactMarkdown
                      components={{
                        p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
                        strong: ({ children }) => <strong className="font-semibold text-white">{children}</strong>,
                        h1: ({ children }) => <h1 className="mb-2 mt-1 text-base font-semibold text-white">{children}</h1>,
                        h2: ({ children }) => <h2 className="mb-2 mt-3 text-sm font-semibold text-violet-200">{children}</h2>,
                        h3: ({ children }) => <h3 className="mb-1 mt-3 text-sm font-semibold text-slate-100">{children}</h3>,
                        ul: ({ children }) => <ul className="mb-2 ml-4 list-disc space-y-1 last:mb-0">{children}</ul>,
                        ol: ({ children }) => <ol className="mb-2 ml-4 list-decimal space-y-1 last:mb-0">{children}</ol>,
                        li: ({ children }) => <li className="pl-1">{children}</li>,
                        br: () => <br />,
                      }}
                    >
                      {chatMessage.content}
                    </ReactMarkdown>
                  ) : chatMessage.content}
                </div>
              </div>
            ))}

            {isLoading ? (
              <div className="flex items-center gap-2.5 text-sm text-slate-400">
                <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-violet-500/15 text-violet-200"><Bot className="h-4 w-4" /></div>
                <div className="flex items-center gap-2 rounded-2xl rounded-tl-md border border-slate-800 bg-slate-900/90 px-3.5 py-3">
                  <LoaderCircle className="h-4 w-4 animate-spin text-violet-300" />
                  AI đang suy nghĩ...
                </div>
              </div>
            ) : null}

            {errorMessage ? <p role="alert" className="rounded-xl border border-rose-500/25 bg-rose-500/10 px-3 py-2 text-xs leading-5 text-rose-200">{errorMessage}</p> : null}
            <div ref={messagesEndRef} />
          </div>
        </div>

        <footer className="border-t border-slate-800 bg-[#0f141d] p-4">
          <div className="flex items-end gap-2 rounded-2xl border border-slate-700 bg-slate-950/70 p-2 transition focus-within:border-violet-400/60">
            <textarea
              ref={inputRef}
              rows={1}
              value={inputValue}
              disabled={isLoading}
              onChange={(event) => setInputValue(event.target.value)}
              onKeyDown={handleInputKeyDown}
              placeholder="Hỏi về Crypto, SMC, Risk..."
              aria-label="Nhập câu hỏi cho TradeAI"
              className="max-h-28 min-h-10 flex-1 resize-none bg-transparent px-2 py-2 text-sm leading-6 text-slate-100 outline-none placeholder:text-slate-600 disabled:cursor-not-allowed"
            />
            <button type="button" aria-label="Gửi câu hỏi" disabled={!inputValue.trim() || isLoading} onClick={handleSubmit} className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-violet-500 text-white transition hover:bg-violet-400 disabled:cursor-not-allowed disabled:opacity-40">
              <Send className="h-4 w-4" />
            </button>
          </div>
          <p className="mt-3 text-center text-[10px] uppercase tracking-[0.14em] text-slate-600">Powered by ✦ Gemini</p>
        </footer>
      </aside>
    </>
  );
}

/* eslint-disable react-refresh/only-export-components -- Provider, hook, and shared settings types intentionally form one public API. */
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import i18n from "../i18n";

export type TradeSettings = {
  chart: { theme: "dark" | "light"; candleType: "candles" | "bars" | "heikin-ashi"; candleColor: "green-red" | "blue-orange" | "monochrome"; showGrid: boolean };
  trading: { oneClickTrading: boolean; slippage: "0.1" | "0.5" | "1" | "custom"; customSlippage: string; soundAlerts: boolean };
  tradeAi: { realtimeSuggestions: boolean; timeframe: "M15" | "H1" | "H4"; riskLevel: "conservative" | "balanced" | "aggressive" };
  system: { currency: "USD" | "VND"; language: string };
  explanation: { enabled: boolean; detail: "short" | "detailed"; showNoTradeReason: boolean; showM15Structure: boolean; showLiquidity: boolean; showM1Confirmation: boolean; showM5EntryModel: boolean };
  risk: { riskPerTrade: string; minimumRiskReward: string; maxTradesPerDay: string; maxDailyLoss: string };
  smc: { m15Bias: boolean; liquidity: boolean; m1StructureShift: boolean; m5OrderBlock: boolean; fvg: boolean; ifvg: boolean; londonSession: boolean; newYorkSession: boolean; timezone: string };
};

export type SettingsPatch = { [K in keyof TradeSettings]?: Partial<TradeSettings[K]> };

export const defaultSettings: TradeSettings = {
  chart: { theme: "dark", candleType: "candles", candleColor: "green-red", showGrid: true },
  trading: { oneClickTrading: false, slippage: "0.5", customSlippage: "0.25", soundAlerts: true },
  tradeAi: { realtimeSuggestions: true, timeframe: "M15", riskLevel: "balanced" },
  system: { currency: "USD", language: "vi" },
  explanation: { enabled: true, detail: "detailed", showNoTradeReason: true, showM15Structure: true, showLiquidity: true, showM1Confirmation: true, showM5EntryModel: true },
  risk: { riskPerTrade: "1.0", minimumRiskReward: "2.0", maxTradesPerDay: "3", maxDailyLoss: "3" },
  smc: { m15Bias: true, liquidity: true, m1StructureShift: true, m5OrderBlock: true, fvg: true, ifvg: true, londonSession: true, newYorkSession: true, timezone: "America/New_York" },
};

type SettingsContextValue = {
  settings: TradeSettings;
  updateSettings: (newSettings: SettingsPatch) => void;
  isM15Bias: boolean;
  isLiquidity: boolean;
  isM1StructureShift: boolean;
  isM5OrderBlock: boolean;
  isFvg: boolean;
  isIfvg: boolean;
  timezone: string;
};

const STORAGE_KEY = "tradeai-pro-settings";
const SettingsContext = createContext<SettingsContextValue | null>(null);

const readStoredSettings = (): TradeSettings => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return defaultSettings;
    const stored = JSON.parse(raw) as SettingsPatch;
    return {
      ...defaultSettings,
      ...stored,
      chart: { ...defaultSettings.chart, ...stored.chart },
      trading: { ...defaultSettings.trading, ...stored.trading },
      tradeAi: { ...defaultSettings.tradeAi, ...stored.tradeAi },
      system: { ...defaultSettings.system, ...stored.system },
      explanation: { ...defaultSettings.explanation, ...stored.explanation },
      risk: { ...defaultSettings.risk, ...stored.risk },
      smc: { ...defaultSettings.smc, ...stored.smc },
    };
  } catch {
    return defaultSettings;
  }
};

export function SettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<TradeSettings>(readStoredSettings);

  // A patch updates one settings group, persists the complete snapshot, and notifies every consumer.
  const updateSettings = (newSettings: SettingsPatch) => {
    setSettings((current) => {
      const next = {
        ...current,
        ...newSettings,
        chart: { ...current.chart, ...newSettings.chart },
        trading: { ...current.trading, ...newSettings.trading },
        tradeAi: { ...current.tradeAi, ...newSettings.tradeAi },
        system: { ...current.system, ...newSettings.system },
        explanation: { ...current.explanation, ...newSettings.explanation },
        risk: { ...current.risk, ...newSettings.risk },
        smc: { ...current.smc, ...newSettings.smc },
      };
      try { localStorage.setItem(STORAGE_KEY, JSON.stringify(next)); } catch { /* Storage may be blocked by the browser. */ }
      return next;
    });
  };

  // The root class is the global theme contract; UI components can react without prop drilling.
  useEffect(() => {
    document.documentElement.classList.toggle("dark", settings.chart.theme === "dark");
    document.documentElement.classList.toggle("light", settings.chart.theme === "light");
    document.documentElement.dataset.theme = settings.chart.theme;
  }, [settings.chart.theme]);

  // Keep the existing i18next instance synchronized with the system tab.
  useEffect(() => {
    if (settings.system.language !== i18n.resolvedLanguage) void i18n.changeLanguage(settings.system.language);
  }, [settings.system.language]);

  // These aliases make SMC consumers independent from the modal's visual grouping.
  const value = useMemo(() => ({
    settings,
    updateSettings,
    isM15Bias: settings.smc.m15Bias,
    isLiquidity: settings.smc.liquidity,
    isM1StructureShift: settings.smc.m1StructureShift,
    isM5OrderBlock: settings.smc.m5OrderBlock,
    isFvg: settings.smc.fvg,
    isIfvg: settings.smc.ifvg,
    timezone: settings.smc.timezone,
  }), [settings]);
  return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>;
}

export function useSettings() {
  const context = useContext(SettingsContext);
  if (!context) throw new Error("useSettings must be used inside SettingsProvider");
  return context;
}
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

import en from './locales/en.json';
import ja from './locales/ja.json';
import ko from './locales/ko.json';
import vi from './locales/vi.json';
import zh from './locales/zh.json';

const STORAGE_KEY = 'trading-platform-language';
const supportedLngs = ['vi', 'en', 'zh', 'ja', 'ko'];

const getSavedLanguage = (): string | null => {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
};

const detectBrowserLanguage = (): string => {
  const saved = getSavedLanguage();
  if (saved && supportedLngs.includes(saved)) {
    return saved;
  }

  return 'vi';
};

void i18n.use(initReactI18next).init({
  resources: {
    vi: { translation: vi },
    en: { translation: en },
    zh: { translation: zh },
    ja: { translation: ja },
    ko: { translation: ko },
  },
  lng: detectBrowserLanguage(),
  fallbackLng: 'vi',
  supportedLngs,
  interpolation: {
    escapeValue: false,
  },
  react: {
    useSuspense: false,
  },
});

const currentLanguage = i18n.resolvedLanguage ?? i18n.language;
if (currentLanguage && supportedLngs.includes(currentLanguage)) {
  try {
    localStorage.setItem(STORAGE_KEY, currentLanguage);
  } catch {
    // Ignore storage failures for private browsing or restricted contexts.
  }
}

i18n.on('languageChanged', (lng) => {
  try {
    localStorage.setItem(STORAGE_KEY, lng);
  } catch {
    // Ignore storage failures.
  }

  document.documentElement.lang = lng;
});

document.documentElement.lang = i18n.resolvedLanguage ?? i18n.language ?? 'vi';

export default i18n;

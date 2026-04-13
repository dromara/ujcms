import { createI18n } from 'vue-i18n';
import type { Language } from 'element-plus/es/locale/index';
import ElZhCn from 'element-plus/es/locale/lang/zh-cn';
import ElEn from 'element-plus/es/locale/lang/en';
import { getCookieLocale } from '@/utils/common';
import en from './locales/en';
import zhCn from './locales/zh-cn';

const messages = {
  'zh-cn': {
    ...zhCn,
  },
  en: {
    ...en,
  },
};

const numberFormats: any = {
  'zh-cn': {
    decimal: {
      style: 'decimal',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    },
    percent: {
      style: 'percent',
      useGrouping: false,
    },
  },
  en: {
    decimal: {
      style: 'decimal',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    },
    percent: {
      style: 'percent',
      useGrouping: false,
    },
  },
};

const elMessages: Record<string, Language> = {
  'zh-cn': ElZhCn,
  en: ElEn,
};

export const languages: Record<string, string> = { 'zh-cn': '中文', en: 'English' };

const i18nFallbackLocale = import.meta.env.VITE_I18N_FALLBACK_LOCALE || 'zh-cn';

export function getElementPlusLocale(lang: string): Language {
  return elMessages[lang] ?? elMessages[i18nFallbackLocale] ?? ElZhCn;
}

export function getLanguage(): string {
  const chooseLanguage = getCookieLocale();
  if (chooseLanguage) return chooseLanguage;
  return import.meta.env.VITE_I18N_LOCALE || 'zh-cn';
}

export default createI18n({
  legacy: false,
  locale: getLanguage(),
  fallbackLocale: i18nFallbackLocale,
  globalInjection: true,
  numberFormats,
  messages,
});

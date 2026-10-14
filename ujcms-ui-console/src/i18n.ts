import { createI18n } from 'vue-i18n';
import type { Language } from 'element-plus/es/locale/index';
import ElZhCn from 'element-plus/es/locale/lang/zh-cn';
import ElEn from 'element-plus/es/locale/lang/en';
import { getCookieLocale } from '@/utils/common';

// 语言包按需装载：各语言的 locales/<lang>/index.ts 各自打成独立 chunk，启动时只装载当前语言（约 240 kB），
// 不再把 zh-cn 与 en 同时打进入口包。切换语言会整页刷新（AppHeader.changeLocale），运行时无需装载第二种语言。
const localeLoaders = import.meta.glob<{ default: Record<string, string> }>('./locales/*/index.ts');
const localeLoader = (lang: string) => localeLoaders[`./locales/${lang}/index.ts`];

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
  // cookie 可能残留历史值或不受支持的语言码，此时回退到默认语言，否则找不到对应的语言包
  if (chooseLanguage && localeLoader(chooseLanguage)) return chooseLanguage;
  return import.meta.env.VITE_I18N_LOCALE || 'zh-cn';
}

const i18n = createI18n({
  legacy: false,
  locale: getLanguage(),
  fallbackLocale: i18nFallbackLocale,
  globalInjection: true,
  numberFormats,
  messages: {},
});

export default i18n;

/** 装载当前语言的词条。main.ts 在安装路由、挂载应用之前 await 此函数，此后任何 t() 调用都能取到文案 */
export async function loadLocaleMessages(): Promise<void> {
  const locale = i18n.global.locale.value;
  const loader = localeLoader(locale) ?? localeLoader(i18nFallbackLocale);
  if (!loader) return;
  const { default: messages } = await loader();
  i18n.global.setLocaleMessage(locale, messages);
}

/** 控制台语言码（`zh-cn` / `en`）→ BCP-47 标签（`zh-CN` / `en`）：主题清单/形态的多语言 label 键与 Accept-Language 均用后者 */
export const localeTag = (lang: string): string => ({ 'zh-cn': 'zh-CN' })[lang] ?? lang;

/** 当前控制台语言的 BCP-47 标签 */
export const currentLocaleTag = (): string => localeTag(i18n.global.locale.value);

/**
 * 取多语言 label 映射（如形态/清单 JSON 的 `{"zh-CN": "…", "en": "…"}`）在当前控制台语言下的文本：
 * 当前语言 → zh-CN → en → fallback
 */
export const localizedLabel = (label: any, fallback: string): string => label?.[currentLocaleTag()] ?? label?.['zh-CN'] ?? label?.en ?? fallback;

/** dayjs 语言包名：控制台中文用 `zh-cn`（各视图已按需 import），其余用 dayjs 内置的 `en` */
export const dayjsLocale = (lang: string): string => (lang.startsWith('zh') ? 'zh-cn' : 'en');

/** 随每个后端请求携带控制台语言，后端 MessageSource 据此返回对应语言的业务错误信息（messages_en.properties） */
export const getLocaleHeaders = (): Record<string, string> => ({ 'Accept-Language': currentLocaleTag() });

import { defineStore } from 'pinia';
import { updatePreferences } from '@/api/personal';
import { applyThemeColor, DEFAULT_THEME_COLOR, isThemeColor, type ThemeColor } from '@/utils/theme';

/**
 * 控制台个人偏好（docs/spec/console-shell/plan.md S1–S2、D7）。
 *
 * 按用户存于服务端（ujcms_user_ext.preferences_），随 backend-state 下发、登录时播种，不做浏览器持久化——
 * 同一操作员换浏览器仍是同一套偏好。外观两项（侧栏风格、主题色）可为空：空即跟随管理员在“全局设置 › 基础设置”指定的
 * 控制台默认外观（Config.Console，随 /env/config 与 backend-state 下发，登录页也按它着色）；有效值 = 自选值 ?? 默认值，
 * 由本 store 的 getter 合成，服务端不合成。改动即时预览（preview），确认后落库（save）；取消由对话框回放快照。
 * 本模块不得引用 useCurrentUser / sysConfigStore（二者播种本 store，避免循环引用）。
 */
export type SidebarTheme = 'light' | 'dark';

export interface HomePreferences {
  /** 首页隐藏的区块键 */
  hidden: string[];
  /** 常用功能（路由名，有序） */
  shortcuts: string[];
}

/** 用户自选的偏好：外观两项为 null 即跟随控制台默认外观 */
export interface OwnPreferences {
  sidebarTheme: SidebarTheme | null;
  themeColor: ThemeColor | null;
  home: HomePreferences;
}

/** 安装级默认外观（全局设置 › 基础设置 › 控制台默认外观） */
export interface AppearanceDefaults {
  sidebarTheme: SidebarTheme;
  themeColor: ThemeColor;
}

export interface PreferencesPatch {
  /** null = 清除自选值，回到跟随默认 */
  sidebarTheme?: SidebarTheme | null;
  themeColor?: ThemeColor | null;
  home?: Partial<HomePreferences>;
}

/** 与后端 UserExt.Preferences.Home 的校验上限一致 */
export const HIDDEN_MAX = 20;
export const SHORTCUTS_MAX = 12;

const uniqueStrings = (value: unknown, max: number): string[] => {
  if (!Array.isArray(value)) return [];
  const items = value.filter((it): it is string => typeof it === 'string' && it.length > 0 && it.length <= 64);
  return [...new Set(items)].slice(0, max);
};
const asSidebarTheme = (value: unknown): SidebarTheme | null => (value === 'light' || value === 'dark' ? value : null);

/** 归一自选值：非法枚举 / 非预设色视同未自选（null），列表去重截断。服务端存的值与控制台预设可能脱节（旧版本、手改），恢复时一律校验 */
export const normalizeOwn = (raw?: any): OwnPreferences => ({
  sidebarTheme: asSidebarTheme(raw?.sidebarTheme),
  themeColor: isThemeColor(raw?.themeColor) ? raw.themeColor : null,
  home: {
    hidden: uniqueStrings(raw?.home?.hidden, HIDDEN_MAX),
    shortcuts: uniqueStrings(raw?.home?.shortcuts, SHORTCUTS_MAX),
  },
});

/** 归一安装默认：非法值回落到代码默认（深色、EP 蓝，与 Config.Console 一致） */
export const normalizeDefaults = (raw?: any): AppearanceDefaults => ({
  sidebarTheme: asSidebarTheme(raw?.sidebarTheme) ?? 'dark',
  themeColor: isThemeColor(raw?.themeColor) ? raw.themeColor : DEFAULT_THEME_COLOR,
});

export const usePreferencesStore = defineStore('ujcmsPreferencesStore', {
  state: () => ({
    own: normalizeOwn(),
    defaults: normalizeDefaults(),
  }),
  getters: {
    /** 有效侧栏风格：自选值 ?? 安装默认 */
    sidebarTheme: (state): SidebarTheme => state.own.sidebarTheme ?? state.defaults.sidebarTheme,
    /** 有效主题色：自选值 ?? 安装默认 */
    themeColor: (state): ThemeColor => state.own.themeColor ?? state.defaults.themeColor,
    home: (state): HomePreferences => state.own.home,
    /** 外观两项均未自选，跟随安装默认（外观设置据此显示提示或“恢复默认”） */
    followsDefault: (state): boolean => state.own.sidebarTheme === null && state.own.themeColor === null,
  },
  actions: {
    merge(patch: PreferencesPatch): OwnPreferences {
      return normalizeOwn({ ...this.own, ...patch, home: { ...this.own.home, ...patch.home } });
    },
    apply(next: OwnPreferences): void {
      this.own = next;
      applyThemeColor(this.themeColor);
    },
    /**
     * backend-state 到达时播种：自选值与当时的安装默认一并到达。无参调用（登出）只清空自选值、保留安装默认——
     * 登录页仍按安装默认着色
     */
    seed(raw?: any, defaults?: any): void {
      if (defaults !== undefined) {
        this.defaults = normalizeDefaults(defaults);
      }
      this.apply(normalizeOwn(raw));
    },
    /** /env/config 到达时（启动、全局设置保存后）更新安装默认；跟随默认的用户即时生效 */
    seedDefaults(raw?: any): void {
      this.defaults = normalizeDefaults(raw);
      applyThemeColor(this.themeColor);
    },
    /** 即时预览：改动立即生效但不落库 */
    preview(patch: PreferencesPatch): void {
      this.apply(this.merge(patch));
    },
    /** 落库：PUT 成功后才更新状态（失败时由请求拦截器提示，状态不变；演示站的写请求会被拒绝）。外观两项的 null 原样写入 = 跟随默认 */
    async save(patch: PreferencesPatch): Promise<void> {
      const next = this.merge(patch);
      await updatePreferences(next);
      this.apply(next);
    },
    /** 常用功能的加入 / 移出（页头 ☆）；已满时不改动 */
    async toggleShortcut(name: string): Promise<'added' | 'removed' | 'full'> {
      const shortcuts = [...this.own.home.shortcuts];
      const index = shortcuts.indexOf(name);
      let result: 'added' | 'removed';
      if (index >= 0) {
        shortcuts.splice(index, 1);
        result = 'removed';
      } else {
        if (shortcuts.length >= SHORTCUTS_MAX) return 'full';
        shortcuts.push(name);
        result = 'added';
      }
      await this.save({ home: { shortcuts } });
      return result;
    },
  },
});

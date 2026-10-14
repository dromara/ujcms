/**
 * 控制台主题色（docs/spec/console-shell/plan.md S3）。
 *
 * 主题色只以 Element Plus 的主色变量链存在：在 :root 上写入 --el-color-primary 及其 light-3/5/7/8/9、dark-2 派生色，
 * EP 组件与 Tailwind 的 primary 系列（tailwind.config.js 已改为引用这些变量）即随之变化；代码中不再出现主色十六进制值。
 */

/** 主题色预设（外观设置的六个色块）；首项即 Element Plus 默认蓝，选中它时不写任何变量、恢复 EP 样式表的取值 */
export const THEME_COLORS = ['#409eff', '#4f46e5', '#16a34a', '#0d9488', '#ea580c', '#db2777'] as const;
export type ThemeColor = (typeof THEME_COLORS)[number];
export const DEFAULT_THEME_COLOR: ThemeColor = THEME_COLORS[0];
/** 与 THEME_COLORS 同序的名称键（shell.appearance.color.<name>） */
export const THEME_COLOR_NAMES = ['blue', 'indigo', 'green', 'teal', 'orange', 'rose'] as const;

const LIGHT_LEVELS = [3, 5, 7, 8, 9];
const VAR_NAMES = ['--el-color-primary', '--el-color-primary-dark-2', ...LIGHT_LEVELS.map((n) => `--el-color-primary-light-${n}`)];

const parse = (hex: string): [number, number, number] => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16)) as [number, number, number];
const toHex = (n: number): string => Math.round(n).toString(16).padStart(2, '0');

/**
 * Sass 的 mix(color1, color2, weight)：weight 为 color1 的占比。
 * EP 的派生色公式：light-N = mix(white, primary, N × 10%)，dark-2 = mix(black, primary, 20%)。
 */
export const mix = (color1: string, color2: string, weight: number): string => {
  const a = parse(color1);
  const b = parse(color2);
  return `#${a.map((c, i) => toHex(c * weight + b[i] * (1 - weight))).join('')}`;
};

export const isThemeColor = (value: unknown): value is ThemeColor => (THEME_COLORS as readonly string[]).includes(value as string);

/** 在 :root 上覆盖（或清除）EP 主色变量链。内联的 :root 属性优先于 EP 样式表的 :root 声明 */
export const applyThemeColor = (primary: string): void => {
  const { style } = document.documentElement;
  if (primary === DEFAULT_THEME_COLOR) {
    VAR_NAMES.forEach((name) => style.removeProperty(name));
    return;
  }
  style.setProperty('--el-color-primary', primary);
  LIGHT_LEVELS.forEach((n) => style.setProperty(`--el-color-primary-light-${n}`, mix('#ffffff', primary, n / 10)));
  style.setProperty('--el-color-primary-dark-2', mix('#000000', primary, 0.2));
};

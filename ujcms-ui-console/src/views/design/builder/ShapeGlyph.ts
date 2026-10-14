import { h, type FunctionalComponent } from 'vue';

/**
 * 组件占位图（调色板缩略图回退 / 检查器允许组件芯片）：受控 `presentation.icon` 枚举 → 与主题缩略图
 * 同语言的通用线框（56×36 视窗）。配色走 EP 主色阶 CSS 变量（内联 style——SVG 展示属性对 var() 支持
 * 不稳），数据驱动 + h() 渲染，不走 v-html，不发网络请求。未知/缺省 → card。
 */
const C_ACCENT = 'var(--el-color-primary)';
const C_MID = 'var(--el-color-primary-light-3)';
const C_LIGHT = 'var(--el-color-primary-light-5)';
const C_FAINT = 'var(--el-color-primary-light-7)';
const C_WHITE = '#ffffff';

type GlyphNode = [tag: 'rect' | 'circle' | 'path', attrs: Record<string, string | number | Record<string, string | number>>];
const rect = (x: number, y: number, w: number, hh: number, rx: number, fill: string): GlyphNode => ['rect', { x, y, width: w, height: hh, rx, style: { fill } }];
const circle = (cx: number, cy: number, r: number, fill: string): GlyphNode => ['circle', { cx, cy, r, style: { fill } }];
const path = (d: string, fill: string): GlyphNode => ['path', { d, style: { fill } }];

const CARD: GlyphNode[] = [rect(13, 7, 30, 22, 2, C_WHITE), rect(17, 11, 10, 3, 1.5, C_ACCENT), rect(17, 18, 22, 2.5, 1.25, C_LIGHT), rect(17, 23, 16, 2.5, 1.25, C_FAINT)];
const GLYPHS: Record<string, GlyphNode[]> = {
  list: [
    rect(12, 8, 7, 5, 1, C_LIGHT),
    rect(22, 9, 22, 3, 1.5, C_MID),
    rect(12, 16, 7, 5, 1, C_LIGHT),
    rect(22, 17, 22, 3, 1.5, C_MID),
    rect(12, 24, 7, 5, 1, C_LIGHT),
    rect(22, 25, 22, 3, 1.5, C_MID),
  ],
  grid: [rect(12, 7, 14, 10, 1.5, C_LIGHT), rect(30, 7, 14, 10, 1.5, C_LIGHT), rect(12, 20, 14, 10, 1.5, C_LIGHT), rect(30, 20, 14, 10, 1.5, C_LIGHT)],
  card: CARD,
  image: [rect(13, 7, 30, 22, 2, C_WHITE), circle(22, 14, 2.5, C_ACCENT), path('M16 27 L25 18 L31 23 L35 19 L40 27 Z', C_MID)],
  text: [rect(12, 8, 20, 3.5, 1.5, C_ACCENT), rect(12, 15, 32, 2.5, 1.25, C_LIGHT), rect(12, 20, 28, 2.5, 1.25, C_LIGHT), rect(12, 25, 18, 2.5, 1.25, C_FAINT)],
  video: [rect(13, 7, 30, 22, 2, C_LIGHT), circle(28, 18, 5.5, C_WHITE), path('M26.5 15 L31 18 L26.5 21 Z', C_ACCENT)],
  carousel: [
    rect(4, 9, 5, 15, 1.5, C_FAINT),
    rect(47, 9, 5, 15, 1.5, C_FAINT),
    rect(12, 7, 32, 19, 2, C_LIGHT),
    circle(24, 31, 1.5, C_ACCENT),
    circle(28, 31, 1.5, C_FAINT),
    circle(32, 31, 1.5, C_FAINT),
  ],
  link: [
    ['rect', { x: 10, y: 15, width: 20, height: 7, rx: 3.5, style: { fill: 'none', stroke: C_MID, 'stroke-width': 2.2 } }],
    ['rect', { x: 26, y: 15, width: 20, height: 7, rx: 3.5, style: { fill: 'none', stroke: C_ACCENT, 'stroke-width': 2.2 } }],
  ],
  banner: [rect(8, 10, 40, 16, 2, C_LIGHT), rect(16, 16, 24, 4, 2, C_WHITE)],
  ad: [rect(13, 7, 30, 22, 2, C_WHITE), path('M33 7 H43 V17 Z', C_ACCENT), rect(17, 19, 16, 3, 1.5, C_MID)],
};

/** 函数式组件：按 icon 枚举名渲染 GLYPHS 里的基元；铺满宿主盒（viewBox 56×36）。 */
export const ShapeGlyph: FunctionalComponent<{ icon?: string }> = (props) =>
  h(
    'svg',
    { viewBox: '0 0 56 36', width: '100%', height: '100%', 'aria-hidden': 'true' },
    (GLYPHS[props.icon ?? ''] ?? CARD).map(([tag, attrs]) => h(tag, attrs)),
  );
ShapeGlyph.props = { icon: { type: String, default: '' } };

export default ShapeGlyph;

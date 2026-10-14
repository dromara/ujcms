/**
 * UJCMS preview-bridge core — protocol v2 (visual-builder §3/§4): the in-page sensor +
 * decorator half of the builder architecture (§2).
 * The console owns all editing logic and drag state; this file only measures (throttled
 * `layout` geometry snapshots), decorates (edit-mode outlines, empty-target placeholders,
 * disabled chips, selection ring + label tabs, insertion line / invalid tint / drop-zone highlights —
 * in-page, so scroll-proof) and reports clicks (`select`), intercepted link navigations
 * (`navigate`), keyboard shortcuts (`key`: undo/redo/delete, forwarded only) and drag-handle gestures (`dragStart`/`dragMove`/`drop`/`dragCancel` —
 * §7.3: pointerdown happens inside the iframe, so the bridge pointer-captures its handle
 * and streams document coordinates; the console runs the drag state machine). No mutation
 * authority lives here: every payload is non-sensitive structural identifiers + coordinates;
 * tokens never cross the bridge in either direction.
 *
 * SINGLE SOURCE: `ujcms-cms/src/main/frontend/preview-bridge/src/bridge-core.ts`. Delivery
 * target (§3, sole target since ssr-reduction P3): the IIFE committed to
 * `ujcms-cms/src/main/resources/static/_ujcms/preview-bridge.js` (rebuild via this package's
 * `build.mjs`), injected into template-track preview responses by
 * `PreviewBridgeInjectionFilter` — served by the backend that renders the page, so it can
 * never version-skew.
 *
 * Activation gate: framed (`window.parent !== window`) + `?_preview=draft` + `_pchannel=<nonce>`.
 * Envelope both directions: `{ source, channel: <nonce>, v: 2, action, … }`; the bridge scopes
 * `postMessage` to the parent origin derived from `document.referrer` and ignores host messages
 * whose origin/source/channel/version don't match. Protocol is v2-only — no v1 pathway; a host
 * that never sends `mode {edit:true}` leaves the page as a plain preview (navigate interception
 * still active so click-through keeps the draft context).
 */

export type RefreshScope = 'page' | 'region';

export interface PreviewBridgeOptions {
  /** Hosting renderer, reported in `ready` ('react' | 'vue' | 'template'). */
  track: string;
  /**
   * Framework soft refresh (`router.refresh()` / `refreshNuxtData()`). Return `false` (or throw)
   * to make the bridge fall back to a full reload with scroll restore (the template-track path).
   */
  refresh?: (scope: RefreshScope, region?: string) => boolean;
}

export interface SelectPayload {
  kind: 'panel' | 'row' | 'region' | 'slot' | 'setting';
  id: string;
  region?: string;
}

interface Rect {
  top: number;
  left: number;
  width: number;
  height: number;
}

/** Label tab (canvas-chrome D21): one pill per anchor — `[⠿ grip][name][actions]` — replacing badge, handle and chip group. */
type TabKind = 'panel' | 'row' | 'region' | 'slot';
interface Tab {
  el: HTMLElement;
  grip: HTMLElement;
  name: HTMLElement;
  deleteChip: HTMLElement;
  /** Page element the tab is currently anchored to (null while hidden). */
  anchor: Element | null;
  kind: TabKind | null;
}
interface TabPlacement {
  above: boolean;
  side: 'left' | 'right';
}

const FRAME_SOURCE = 'ujcms-slot-preview';
const HOST_SOURCE = 'ujcms-slot-preview-host';
const PROTOCOL_V = 2;
const CAPABILITIES = ['layout', 'decorate', 'drag', 'navigate', 'place', 'quickEdit', 'edit', 'delete', 'device', 'labels', 'key', 'hover'];
const SCROLL_STASH_PREFIX = 'ujcms-pb-scroll:';
const STYLE_ID = 'ujcms-preview-bridge-style';
const EDIT_ATTR = 'data-ujcms-edit';
/** Set on <html> while a handle drag is live (grabbing cursor everywhere). */
const DRAG_ATTR = 'data-ujcms-drag';
/** Set on the drag-source element (dimmed while its ghost travels). */
const DRAGGING_ATTR = 'data-ujcms-dragging';
/** Marks the decoration layer so hover/select logic never treats it as page content. */
const DECOR_ATTR = 'data-ujcms-decoration';
/** 桥打在「渲染期无输出」插槽锚点上的标记 — 编辑态 CSS 据此撑出可点选的 配置投放 占位框。 */
const SLOT_EMPTY_ATTR = 'data-ujcms-slot-empty';
/** 桥打在「无面板」列上的标记 — 编辑态 CSS 据此撑出可命中的空列占位框（拖放/点击放置的投放面）。 */
const COL_EMPTY_ATTR = 'data-ujcms-col-empty';
/**
 * 宿主视口预设映射的设备类别（design-review P2-5）：`device {device:'mobile'}` 置 `mobile`，其余移除
 * （缺省 = 桌面）。编辑态 CSS 据此隐藏按设备显示的面板（渲染器在预览中一律渲染并标
 * `data-panel-visibility`），预设切换即模拟生产的按设备隐藏——三渲染器同一机制。
 */
const DEVICE_ATTR = 'data-ujcms-device';
/** Host-driven label stylesheet (empty-slot placeholder text carries the console locale). */
const STYLE_LABELS_ID = 'ujcms-preview-bridge-labels';
const TAB_H = 22;
const GRIP_W = 22;
const TAB_MAX_W = 260;
/** Anchors narrower than this collapse the tab to the grip alone (name + chips hidden). */
const TAB_COMPACT_W = 120;
/** D23 toggle: action chips (删除面板) on the hover tab too; default = selected tab only. */
const HOVER_TAB_ACTIONS = false;
const RING_BORDER = 2;
/** Marks the selected page element so the CSS hover outline yields to the ring. */
const SELECTED_ATTR = 'data-ujcms-selected';
/** Console font stack (tailwind.config.js fontFamily.sans) so in-canvas chrome reads as part of the console. */
const FONT = 'ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif';
/**
 * Element Plus palette (D25): TS constants interpolated into the stylesheet — the page-level rules
 * (hover outlines, placeholders) live outside the decoration layer, so CSS custom properties would
 * have to sit on `html` and become theme-overridable.
 */
const C = {
  primary: '#409eff',
  light: '#79bbff',
  lighter: '#a0cfff',
  tint: '#ecf5ff',
  border: '#dcdfe6',
  text: '#606266',
  secondary: '#909399',
  placeholder: '#c0c4cc',
  danger: '#f56c6c',
  success: '#67c23a',
} as const;

/**
 * Chrome labels (shell-polish): the host sends them via `labels` (console locale + shape/row-layout display
 * names); the Chinese defaults serve hosts that never send the action (older console builds).
 */
interface Labels {
  dragPanel: string;
  dragRow: string;
  deletePanel: string;
  editArticle: string;
  editChannel: string;
  editSetting: string;
  emptySlot: string;
  emptyColumn: string;
  emptyPanel: string;
  emptyRegion: string;
  dropHere: string;
  row: string;
  region: string;
  slot: string;
  disabled: string;
}
const DEFAULT_LABELS: Labels = {
  dragPanel: '拖动以移动面板',
  dragRow: '拖动以重排行',
  deletePanel: '删除面板',
  editArticle: '编辑文章',
  editChannel: '编辑栏目',
  editSetting: '编辑此处设置',
  emptySlot: '插槽「{name}」暂无内容 — 点击配置投放',
  emptyColumn: '空列 · 拖入或点击放置组件',
  emptyPanel: '面板暂无内容 · 点选查看设置',
  emptyRegion: '空区域 · 拖入布局或在检查器中添加行',
  dropHere: '点击放置到此处',
  row: '行',
  region: '区域',
  slot: '插槽',
  disabled: '已禁用',
};

/**
 * Edit-mode-only decoration CSS (§5: production output stays clean — everything is gated on the html
 * attribute). Chrome anatomy (canvas-chrome D21–D25): CSS hover outline on leaves, dashed hover box on
 * containers, selection ring + fused label tab, chip pills for gear / quick-edit, insertion line with
 * end caps, invalid tint + reason pill, drop zones with a "+" disc in placement mode, labeled empty
 * placeholders. Ring and selected tab glide (120ms) between anchors; hover chrome snaps;
 * `prefers-reduced-motion` disables every transition.
 */
const EDIT_CSS = `
html[${EDIT_ATTR}] [data-panel-id], html[${EDIT_ATTR}] [data-panel-disabled],
html[${EDIT_ATTR}] [data-slot], html[${EDIT_ATTR}] [data-ujcms-slot], html[${EDIT_ATTR}] [data-ujcms-setting] { cursor: pointer; }
html[${EDIT_ATTR}] [data-panel-id]:hover:not([${SELECTED_ATTR}]), html[${EDIT_ATTR}] [data-panel-disabled]:hover:not([${SELECTED_ATTR}]),
html[${EDIT_ATTR}] [data-slot]:hover:not([${SELECTED_ATTR}]), html[${EDIT_ATTR}] [data-ujcms-slot]:hover:not([${SELECTED_ATTR}]) { outline: 1px solid ${C.light}; outline-offset: -1px; }
html[${EDIT_ATTR}] [data-ujcms-setting]:hover { outline: 1px dotted ${C.success}; outline-offset: -1px; }
html[${EDIT_ATTR}] [data-region] [data-col][${COL_EMPTY_ATTR}], html[${EDIT_ATTR}] [data-region] [data-panel-id]:empty, html[${EDIT_ATTR}] [${SLOT_EMPTY_ATTR}] {
  display: grid; place-items: center; align-content: center; min-height: 56px; outline: 1px dashed ${C.placeholder}; outline-offset: -1px; border-radius: 6px; background: rgba(144, 147, 153, 0.04); }
html[${EDIT_ATTR}] [data-region]:empty { display: grid; place-items: center; align-content: center; min-height: 72px; outline: 1px dashed ${C.placeholder}; outline-offset: -1px; border-radius: 6px; background: rgba(144, 147, 153, 0.04); }
html[${EDIT_ATTR}] [data-panel-disabled] { display: block; min-height: 40px; border-radius: 6px; outline: 1px dashed ${C.placeholder}; outline-offset: -1px;
  background: repeating-linear-gradient(45deg, rgba(144, 147, 153, 0.12), rgba(144, 147, 153, 0.12) 8px, transparent 8px, transparent 16px); }
html[${EDIT_ATTR}][${DEVICE_ATTR}="mobile"] [data-panel-visibility="desktop"] { display: none !important; }
html[${EDIT_ATTR}]:not([${DEVICE_ATTR}="mobile"]) [data-panel-visibility="mobile"] { display: none !important; }
html[${DRAG_ATTR}], html[${DRAG_ATTR}] * { cursor: grabbing !important; }
html[${EDIT_ATTR}] [${DRAGGING_ATTR}] { opacity: 0.4; }
#ujcms-preview-bridge-ring { position: absolute; pointer-events: none; display: none; border: ${RING_BORDER}px solid ${C.primary}; border-radius: 6px;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.16); z-index: 2147483646; transition: top 0.12s ease-out, left 0.12s ease-out, width 0.12s ease-out, height 0.12s ease-out; }
#ujcms-pb-hover-box { position: absolute; pointer-events: none; display: none; box-sizing: border-box; border: 1px dashed ${C.lighter}; border-radius: 6px; z-index: 2147483642; }
.ujcms-pb-tab { position: absolute; z-index: 2147483645; display: none; box-sizing: border-box; height: ${TAB_H}px; max-width: ${TAB_MAX_W}px; align-items: stretch; overflow: hidden;
  white-space: nowrap; border-radius: 4px; background: #fff; color: ${C.primary}; border: 1px solid ${C.lighter}; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  font: 500 12px/${TAB_H - 2}px ${FONT}; font-variant-numeric: tabular-nums; pointer-events: auto; user-select: none; -webkit-user-select: none; }
.ujcms-pb-tab[data-ujcms-above] { border-radius: 4px 4px 0 0; box-shadow: none; }
.ujcms-pb-tab[data-ujcms-side="right"] { flex-direction: row-reverse; }
.ujcms-pb-tab[data-ujcms-sel] { z-index: 2147483647; background: ${C.primary}; color: #fff; border-color: ${C.primary}; transition: top 0.12s ease-out, left 0.12s ease-out, right 0.12s ease-out; }
.ujcms-pb-tab[data-ujcms-active] { background: ${C.primary}; color: #fff; border-color: ${C.primary}; }
.ujcms-pb-tab[data-ujcms-compact] .ujcms-pb-name, .ujcms-pb-tab[data-ujcms-compact] .ujcms-pb-chip { display: none !important; }
.ujcms-pb-name { flex: 0 1 auto; min-width: 0; padding: 0 8px; overflow: hidden; text-overflow: ellipsis; cursor: pointer; }
.ujcms-pb-grip { flex: none; width: ${GRIP_W}px; font: 13px/${TAB_H - 2}px ${FONT}; text-align: center; cursor: grab; touch-action: none; transition: background-color 0.12s; }
.ujcms-pb-grip:hover { background: ${C.tint}; }
.ujcms-pb-tab[data-ujcms-sel] .ujcms-pb-grip:hover, .ujcms-pb-tab[data-ujcms-active] .ujcms-pb-grip { background: rgba(255, 255, 255, 0.18); }
html[${DRAG_ATTR}] .ujcms-pb-grip { cursor: grabbing; }
.ujcms-pb-chips { position: absolute; z-index: 2147483646; display: none; box-sizing: border-box; height: ${TAB_H}px; overflow: hidden; border-radius: 4px;
  background: #fff; border: 1px solid ${C.border}; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08); white-space: nowrap; pointer-events: auto; }
.ujcms-pb-chip { flex: none; display: block; height: ${TAB_H - 2}px; padding: 0 8px; color: ${C.text}; font: 12px/${TAB_H - 2}px ${FONT}; text-align: center; cursor: pointer;
  user-select: none; -webkit-user-select: none; white-space: nowrap; transition: background-color 0.12s, color 0.12s; }
.ujcms-pb-chips .ujcms-pb-chip + .ujcms-pb-chip { border-left: 1px solid ${C.border}; }
.ujcms-pb-tab .ujcms-pb-chip { color: inherit; border-left: 1px solid rgba(255, 255, 255, 0.35); }
.ujcms-pb-chip:hover { background: ${C.primary}; color: #fff; }
.ujcms-pb-chip[data-ujcms-danger]:hover { background: ${C.danger}; color: #fff; }
#ujcms-pb-line { position: absolute; pointer-events: none; display: none; height: 3px; border-radius: 2px; background: ${C.primary};
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.8), 0 0 0 3px rgba(64, 158, 255, 0.18); z-index: 2147483645; }
#ujcms-pb-line::before, #ujcms-pb-line::after { content: ''; position: absolute; top: 50%; width: 8px; height: 8px; margin-top: -4px; border-radius: 50%; background: ${C.primary}; box-shadow: 0 0 0 2px #fff; }
#ujcms-pb-line::before { left: -4px; }
#ujcms-pb-line::after { right: -4px; }
#ujcms-pb-tint { position: absolute; pointer-events: none; display: none; border-radius: 6px; background: rgba(245, 108, 108, 0.06);
  outline: 1px dashed ${C.danger}; outline-offset: -1px; z-index: 2147483644; }
#ujcms-pb-reason { position: absolute; pointer-events: none; display: none; box-sizing: border-box; height: ${TAB_H}px; max-width: ${TAB_MAX_W}px; padding: 0 8px; border-radius: 4px;
  background: ${C.danger}; color: #fff; font: 500 12px/${TAB_H}px ${FONT}; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; z-index: 2147483646; }
#ujcms-pb-reason[data-ujcms-above] { border-radius: 4px 4px 0 0; }
#ujcms-pb-zones { position: absolute; top: 0; left: 0; width: 0; height: 0; overflow: visible; pointer-events: none; }
#ujcms-pb-zones > div { position: absolute; pointer-events: none; box-sizing: border-box; border-radius: 6px; background: rgba(64, 158, 255, 0.06);
  outline: 1px dashed ${C.lighter}; outline-offset: -1px; z-index: 2147483643; }
#ujcms-pb-zones > div[data-ujcms-zone] { pointer-events: auto; cursor: copy; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; overflow: hidden;
  background: rgba(64, 158, 255, 0.08); outline: 2px dashed ${C.light}; outline-offset: -2px; color: ${C.primary}; font: 500 12px/1.4 ${FONT}; transition: background-color 0.12s; }
#ujcms-pb-zones > div[data-ujcms-zone]::before { content: '+'; box-sizing: border-box; width: 24px; height: 24px; border-radius: 50%; background: #fff; border: 1px solid ${C.light};
  font: 500 18px/22px ${FONT}; text-align: center; }
#ujcms-pb-zones > div[data-ujcms-zone][data-ujcms-compact]::before { width: 18px; height: 18px; font: 500 14px/16px ${FONT}; }
#ujcms-pb-zones > div[data-ujcms-zone][data-ujcms-compact]::after { display: none; }
#ujcms-pb-zones > div[data-ujcms-zone]:hover { background: rgba(64, 158, 255, 0.18); outline-style: solid; }
@media (prefers-reduced-motion: reduce) {
  #ujcms-preview-bridge-ring, .ujcms-pb-tab, .ujcms-pb-grip, .ujcms-pb-chip, #ujcms-pb-zones > div { transition: none !important; }
}
`;

const cssEscape = (value: string): string => (typeof CSS !== 'undefined' && CSS.escape ? CSS.escape(value) : value.replace(/["\\]/g, '\\$&'));

const PANEL_SELECTOR = '[data-panel-id],[data-panel-disabled]';

/**
 * Mount the bridge. Returns a cleanup function, or `null` when the activation gate fails
 * (not framed / not a draft preview / no channel nonce) — zero cost on normal renders.
 */
export function initPreviewBridge(options: PreviewBridgeOptions): (() => void) | null {
  if (typeof window === 'undefined' || window.parent === window) {
    return null;
  }
  const params = new URLSearchParams(window.location.search);
  if (params.get('_preview') !== 'draft') {
    return null;
  }
  const nonce = params.get('_pchannel');
  if (!nonce) {
    return null;
  }
  // The console (parent) origin — used to scope postMessage and validate host messages; falls
  // back to '*' when the referrer is unavailable (payloads are non-sensitive by design).
  let parentOrigin = '*';
  try {
    if (document.referrer) {
      parentOrigin = new URL(document.referrer).origin;
    }
  } catch {
    /* keep '*' */
  }

  let editMode = false;
  let selected: SelectPayload | null = null;
  let lastLayoutHash = '';
  let raf = 0;

  // ---- host-provided chrome labels (shell-polish) ------------------------------------------------

  let labels: Labels = { ...DEFAULT_LABELS };
  let shapeLabels: Record<string, string> = {};
  let layoutLabels: Record<string, string> = {};
  let regionLabels: Record<string, string> = {};

  /** Host label maps are display data: string values only, bounded length and entry count. */
  const sanitizeLabelMap = (value: unknown, maxEntries = 500): Record<string, string> => {
    const out: Record<string, string> = {};
    if (value == null || typeof value !== 'object') {
      return out;
    }
    let n = 0;
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      if (typeof v !== 'string' || k.length > 64) {
        continue;
      }
      out[k] = v.slice(0, 120);
      n += 1;
      if (n >= maxEntries) {
        break;
      }
    }
    return out;
  };

  const cssString = (value: string): string => '"' + value.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\a ') + '"';

  /** Host-driven text rules: empty-slot / empty-column / empty-panel / empty-region placeholders and the placement-zone caption. */
  const labelCss = (): string => {
    const template = labels.emptySlot;
    const marker = template.indexOf('{name}');
    const content = (attr: string): string =>
      marker >= 0 ? `${cssString(template.slice(0, marker))} attr(${attr}) ${cssString(template.slice(marker + 6))}` : cssString(template);
    const placeholder = `color: ${C.secondary}; font: 12px/1.5 ${FONT}; padding: 0 12px; text-align: center;`;
    return [
      `html[${EDIT_ATTR}] [data-slot][${SLOT_EMPTY_ATTR}]::before { content: ${content('data-slot')}; ${placeholder} }`,
      `html[${EDIT_ATTR}] [data-ujcms-slot][${SLOT_EMPTY_ATTR}]::before { content: ${content('data-ujcms-slot')}; ${placeholder} }`,
      `html[${EDIT_ATTR}] [data-region] [data-col][${COL_EMPTY_ATTR}]::before { content: ${cssString(labels.emptyColumn)}; ${placeholder} }`,
      `html[${EDIT_ATTR}] [data-region] [data-panel-id]:empty::before { content: ${cssString(labels.emptyPanel)}; ${placeholder} }`,
      `html[${EDIT_ATTR}] [data-region]:empty::before { content: ${cssString(labels.emptyRegion)}; ${placeholder} }`,
      `#ujcms-pb-zones > div[data-ujcms-zone]::after { content: ${cssString(labels.dropHere)}; }`,
    ].join('\n');
  };

  const post = (action: string, payload: Record<string, unknown>): void => {
    window.parent.postMessage({ source: FRAME_SOURCE, channel: nonce, v: PROTOCOL_V, action, ...payload }, parentOrigin);
  };

  const docRect = (el: Element): Rect => {
    const r = el.getBoundingClientRect();
    return {
      top: Math.round(r.top + window.scrollY),
      left: Math.round(r.left + window.scrollX),
      width: Math.round(r.width),
      height: Math.round(r.height),
    };
  };

  /**
   * Bootstrap-style gutter systems (`.row.g-*`) give the row negative margins (compensated by
   * positive margins/padding on its cols), so the row's border box overlaps the section above it —
   * decorations drawn from the raw box bleed onto neighbouring content. Trim the negative-margin
   * overhang to get the box the user actually sees.
   */
  const visualRect = (el: Element): Rect => {
    const rect = docRect(el);
    const style = window.getComputedStyle(el);
    const overhang = (margin: string): number => {
      const value = parseFloat(margin);
      return Number.isFinite(value) && value < 0 ? Math.round(-value) : 0;
    };
    const top = overhang(style.marginTop);
    const left = overhang(style.marginLeft);
    return {
      top: rect.top + top,
      left: rect.left + left,
      width: Math.max(0, rect.width - left - overhang(style.marginRight)),
      height: Math.max(0, rect.height - top - overhang(style.marginBottom)),
    };
  };

  /** Region decoration rect: union of its rows' visual rects — the wrapper's own box inherits the first row's overhang. Empty region: own box (placeholder). */
  const regionVisualRect = (regionEl: Element): Rect => {
    let top = Infinity;
    let left = Infinity;
    let right = -Infinity;
    let bottom = -Infinity;
    regionEl.querySelectorAll('[data-row]').forEach((rowEl) => {
      const r = visualRect(rowEl);
      top = Math.min(top, r.top);
      left = Math.min(left, r.left);
      right = Math.max(right, r.left + r.width);
      bottom = Math.max(bottom, r.top + r.height);
    });
    return top === Infinity ? docRect(regionEl) : { top, left, width: right - left, height: bottom - top };
  };

  /** Rect for rect-positioned decorations (ring / handles / drop hints), kind-aware. */
  const decorRect = (el: Element): Rect => (el.hasAttribute('data-row') ? visualRect(el) : el.hasAttribute('data-region') ? regionVisualRect(el) : docRect(el));

  // ---- layout snapshot (§4 `layout`) -----------------------------------------------------------

  const scanLayout = (): Record<string, unknown> => {
    const regions: Record<string, unknown>[] = [];
    document.querySelectorAll('[data-region]').forEach((regionEl) => {
      const rows: Record<string, unknown>[] = [];
      regionEl.querySelectorAll('[data-row]').forEach((rowEl) => {
        const columns: Record<string, unknown>[] = [];
        rowEl.querySelectorAll('[data-col]').forEach((colEl) => {
          const panels: Record<string, unknown>[] = [];
          colEl.querySelectorAll(PANEL_SELECTOR).forEach((panelEl) => {
            const disabled = panelEl.hasAttribute('data-panel-disabled');
            panels.push({
              id: (disabled ? panelEl.getAttribute('data-panel-disabled') : panelEl.getAttribute('data-panel-id')) ?? '',
              shape: panelEl.getAttribute('data-panel') ?? '',
              rect: docRect(panelEl),
              ...(disabled ? { disabled: true } : {}),
            });
          });
          // 空列（行在、面板为零——删掉行内最后一个面板 / 插入空行即达）：多数渲染器在 data-col 内
          // 还包一层布局 div（Liquid/React/Vue 的 space-y-8），CSS :empty 永不匹配，列高塌成 0——
          // 无从命中也无从渲染投放区（宿主/桥两侧都按 rect 面积工作），选择环亦退化成一条蓝线。
          // 与插槽占位（SLOT_EMPTY_ATTR）同法：按「无面板后代」JS 判定打标记，编辑态 CSS 撑出
          // 占位框；先打标记再量测（getBoundingClientRect 强制同步回流，rect 即占位后的终值）。
          colEl.toggleAttribute(COL_EMPTY_ATTR, panels.length === 0);
          columns.push({ index: Number(colEl.getAttribute('data-col') ?? '0'), rect: docRect(colEl), panels });
        });
        rows.push({ id: rowEl.getAttribute('data-row') ?? '', layout: rowEl.getAttribute('data-row-layout') ?? '', rect: visualRect(rowEl), columns });
      });
      regions.push({ name: regionEl.getAttribute('data-region') ?? '', rect: regionVisualRect(regionEl), empty: rows.length === 0, rows });
    });
    const slots: Record<string, unknown>[] = [];
    document.querySelectorAll('[data-slot],[data-ujcms-slot]').forEach((el) => {
      // 空插槽（未配置投放/查询零命中——渲染期无输出）：锚点高 0，画布里看不见也点不中。先打占位标记
      // （编辑态 CSS 撑出虚线框 + 标签，点选走既有 select {kind:'slot'} → 检查器 配置投放），再量测——
      // getBoundingClientRect 强制同步回流，rect 即占位后的终值。模板轨包裹层含模板缩进空白，CSS :empty
      // 永不匹配，故按「无元素子节点且文本为空白」JS 判定；MutationObserver 只观察 childList，属性翻转
      // 不会自触发重扫。
      const empty = el.childElementCount === 0 && (el.textContent ?? '').trim() === '';
      el.toggleAttribute(SLOT_EMPTY_ATTR, empty);
      slots.push({ name: el.getAttribute('data-ujcms-slot') ?? el.getAttribute('data-slot') ?? '', rect: docRect(el), ...(empty ? { empty: true } : {}) });
    });
    const settings: Record<string, unknown>[] = [];
    document.querySelectorAll('[data-ujcms-setting]').forEach((el) => {
      settings.push({ key: el.getAttribute('data-ujcms-setting') ?? '', rect: docRect(el) });
    });
    return {
      regions,
      slots,
      settings,
      scroll: { x: Math.round(window.scrollX), y: Math.round(window.scrollY) },
      viewport: { w: window.innerWidth, h: window.innerHeight },
    };
  };

  const postLayout = (): void => {
    const payload = scanLayout();
    const hash = JSON.stringify(payload);
    if (hash !== lastLayoutHash) {
      lastLayoutHash = hash;
      post('layout', payload);
    }
  };

  // ---- decoration layer -------------------------------------------------------------------------

  // One container for every decoration element (ring, drag handles, insertion line, invalid tint,
  // reason tip, drop-zone highlights). Anchored at the document origin; children are positioned in
  // document coordinates, so everything is scroll-proof by construction. The DECOR_ATTR keeps
  // hover/click classification from ever treating decorations as page content.
  const layer = document.createElement('div');
  layer.setAttribute(DECOR_ATTR, '');
  layer.style.cssText = 'position:absolute;top:0;left:0;width:0;height:0;overflow:visible;';

  const ring = document.createElement('div');
  ring.id = 'ujcms-preview-bridge-ring';
  layer.appendChild(ring);

  const insertLine = document.createElement('div');
  insertLine.id = 'ujcms-pb-line';
  layer.appendChild(insertLine);

  const invalidTint = document.createElement('div');
  invalidTint.id = 'ujcms-pb-tint';
  layer.appendChild(invalidTint);

  const reasonTip = document.createElement('div');
  reasonTip.id = 'ujcms-pb-reason';
  layer.appendChild(reasonTip);

  const zoneLayer = document.createElement('div');
  zoneLayer.id = 'ujcms-pb-zones';
  layer.appendChild(zoneLayer);

  // Container hover feedback (row / region) is a positioned box, not a CSS outline on the element:
  // Bootstrap-style gutter rows have negative margins, so an element outline would trace the
  // overhanging border box across the section above (visualRect rationale).
  const hoverBox = document.createElement('div');
  hoverBox.id = 'ujcms-pb-hover-box';
  layer.appendChild(hoverBox);

  // Chip pills for the gear (⚙) and content quick-edit (编辑文章/编辑栏目) affordances: one bordered
  // pill per anchor; chips are re-parented into the pill only when its membership changes.
  const makeChipGroup = (): HTMLElement => {
    const group = document.createElement('div');
    group.className = 'ujcms-pb-chips';
    layer.appendChild(group);
    return group;
  };
  const settingGroup = makeChipGroup();
  const refGroup = makeChipGroup();

  /** Resolve a select kind + id to the live page element (selection ring, host hover anchor). */
  const resolveElement = (kind: SelectPayload['kind'], rawId: string): Element | null => {
    const id = cssEscape(rawId);
    switch (kind) {
      case 'panel':
        return document.querySelector(`[data-panel-id="${id}"],[data-panel-disabled="${id}"]`);
      case 'row':
        return document.querySelector(`[data-row="${id}"]`);
      case 'region':
        return document.querySelector(`[data-region="${id}"]`);
      case 'slot':
        return document.querySelector(`[data-slot="${id}"],[data-ujcms-slot="${id}"]`);
      case 'setting':
        return document.querySelector(`[data-ujcms-setting="${id}"]`);
      default:
        return null;
    }
  };
  const selectedElement = (): Element | null => (selected ? resolveElement(selected.kind, selected.id) : null);

  let markedEl: Element | null = null;

  const repositionRing = (): void => {
    const el = editMode ? selectedElement() : null;
    if (markedEl !== el) {
      // Attribute-only mutation (the body observer watches childList): the CSS hover outline yields to the ring
      markedEl?.removeAttribute(SELECTED_ATTR);
      el?.setAttribute(SELECTED_ATTR, '');
      markedEl = el;
    }
    const hideRing = (): void => {
      ring.style.display = 'none';
      ring.style.borderTopLeftRadius = '';
      ring.style.borderTopRightRadius = '';
      placeTab(selTab, null, null, { selected: true });
    };
    if (!el) {
      hideRing();
      return;
    }
    const rect = decorRect(el);
    if (rect.width <= 0 && rect.height <= 0) {
      // 零矩形（元素在场但 display:none——按设备隐藏的面板在当前设备预设下即此）：环不画，
      // 否则退化成钉在文档原点的光点
      hideRing();
      return;
    }
    ring.style.display = 'block';
    ring.style.top = `${rect.top - RING_BORDER}px`;
    ring.style.left = `${rect.left - RING_BORDER}px`;
    ring.style.width = `${rect.width}px`;
    ring.style.height = `${rect.height}px`;
    const placement = placeTab(selTab, el, rect, { selected: true });
    // The tab fuses with the ring: square the ring corner under an "above" tab
    ring.style.borderTopLeftRadius = placement?.above && placement.side === 'left' ? '0' : '';
    ring.style.borderTopRightRadius = placement?.above && placement.side === 'right' ? '0' : '';
  };

  // ---- hover / drag state (§7.3) ------------------------------------------------------------------

  let hoverPanelEl: Element | null = null;
  let hoverRowEl: Element | null = null;
  let hoverRegionEl: Element | null = null;
  let hoverSlotEl: Element | null = null;
  // 宿主侧悬停锚点（console → iframe `hover`，canvas-chrome D30）：检查器区域卡片 / 面包屑悬停时与指针悬停同款 chrome
  let externalHoverEl: Element | null = null;
  let drag: { kind: 'panel' | 'row'; id: string; el: Element; tab: Tab; handle: HTMLElement; pointerId: number; lastX: number; lastY: number } | null = null;
  let moveRaf = 0;
  // 拖拽结束（放下 / Esc 取消）后，指针释放时浏览器会在手柄与释放点的最近公共祖先上合成一次 click
  // （通常是 body）——若放行，会被当作点空白处而清掉选择。Esc 后指针可能仍按着（释放可迟到数秒），
  // 故不用时间窗：吞掉拖拽后的下一次 click，下一次 pointerdown（新交互开始）即复位。
  let swallowNextClick = false;

  // ---- hover chips (§8.1 gear affordance / §8.3 content quick actions, P5) ----------------------

  // Gear on [data-ujcms-setting] hover → same select {kind:'setting'} as clicking the element,
  // but discoverable; 编辑文章/编辑栏目 on ref-bearing element hover ([data-article-id]/[data-channel-id],
  // renderer hooks §5.5) → quickEdit up to the console, which opens the EXISTING ArticleForm/
  // ChannelForm (§8.3 — deep-links, never in-builder content forms). Chips are decoration-layer
  // buttons (pointer-events auto); ids they echo come from page-owned DOM attributes and are
  // re-validated console-side like every frame message.
  const makeChip = (text: string, title: string): HTMLElement => {
    const chip = document.createElement('div');
    chip.className = 'ujcms-pb-chip';
    chip.setAttribute('data-ujcms-chip', '');
    chip.textContent = text;
    chip.title = title;
    return chip;
  };

  const settingChip = makeChip('⚙', labels.editSetting);
  const articleChip = makeChip(labels.editArticle, labels.editArticle);
  const channelChip = makeChip(labels.editChannel, labels.editChannel);

  // ---- label tabs (canvas-chrome D21–D23) -------------------------------------------------------

  // 名称角标 + 拖拽手柄 + 直达角标合为一枚「标签页」：`[⠿][名称][删除面板]`。叶子（面板/插槽）挂元素左上、
  // 容器（行/区域）挂右上——首面板的标签与其行的标签永不相撞；上方有空间时贴在元素外侧（不遮内容），
  // 否则落入元素内侧。悬停一枚（描边）、选中一枚（实底，与选择环融合）。子节点固定终生、只切换 display：
  // 逐帧重挂曾让角标在 mousedown/mouseup 之间脱离文档、浏览器不合成 click（P10 回归），此处结构上杜绝。
  // 删除面板（画布直达操作）默认只在选中标签上（D23，HOVER_TAB_ACTIONS 切换）：桥只上报意图（deleteIntent）——
  // 二次确认、草稿树核验与保存全在控制台侧。编辑面板 角标已移除（D16）；卡片级角标已移除（2026-07-06）：
  // 列表面板一条数据即一张卡片，逐条目角标遮挡行内容。
  const makeTab = (sel: boolean): Tab => {
    const el = document.createElement('div');
    el.className = 'ujcms-pb-tab';
    if (sel) {
      el.setAttribute('data-ujcms-sel', '');
    }
    const grip = document.createElement('div');
    grip.className = 'ujcms-pb-grip';
    grip.textContent = '⠿';
    const name = document.createElement('div');
    name.className = 'ujcms-pb-name';
    name.setAttribute('data-ujcms-tab-name', '');
    const deleteChip = makeChip(labels.deletePanel, labels.deletePanel);
    deleteChip.setAttribute('data-ujcms-danger', '');
    el.append(grip, name, deleteChip);
    layer.appendChild(el);
    return { el, grip, name, deleteChip, anchor: null, kind: null };
  };
  const hoverTab = makeTab(false);
  const selTab = makeTab(true);

  const kindOf = (el: Element): TabKind | null =>
    el.matches(PANEL_SELECTOR) ? 'panel' : el.hasAttribute('data-row') ? 'row' : el.hasAttribute('data-region') ? 'region' : el.matches('[data-slot],[data-ujcms-slot]') ? 'slot' : null;

  const setDisplay = (el: HTMLElement, value: string): void => {
    if (el.style.display !== value) {
      el.style.display = value;
    }
  };
  const setAttr = (el: HTMLElement, name: string, value: string | null): void => {
    if (value == null) {
      if (el.hasAttribute(name)) {
        el.removeAttribute(name);
      }
    } else if (el.getAttribute(name) !== value) {
      el.setAttribute(name, value);
    }
  };
  const hideTab = (tab: Tab): null => {
    setDisplay(tab.el, 'none');
    tab.anchor = null;
    tab.kind = null;
    return null;
  };

  /**
   * Anchor a tab to `el` (document coordinates; `rect` = a precomputed decorRect). Above the element
   * when there is room, inside its top corner otherwise (`forceInside`: the hover tab yields to the
   * selected one on collision). Writes only what changed — layer-internal mutations are ignored by
   * the observer, but stable nodes are what keep real clicks alive.
   */
  const placeTab = (tab: Tab, el: Element | null, rect: Rect | null, opts: { selected: boolean; forceInside?: boolean }): TabPlacement | null => {
    if (!editMode || !el || !el.isConnected) {
      return hideTab(tab);
    }
    if (drag && drag.tab !== tab && !opts.selected) {
      return hideTab(tab);
    }
    const kind = kindOf(el);
    const text = kind ? badgeText(el) : '';
    const r = rect ?? decorRect(el);
    if (!kind || !text || (r.width <= 0 && r.height <= 0)) {
      return hideTab(tab);
    }
    const side: 'left' | 'right' = kind === 'row' || kind === 'region' ? 'right' : 'left';
    const above = !opts.forceInside && r.top >= TAB_H + RING_BORDER;
    const draggable = kind === 'panel' || kind === 'row';
    const compact = r.width < TAB_COMPACT_W;
    const showDelete = kind === 'panel' && (opts.selected || HOVER_TAB_ACTIONS) && !compact;
    if (tab.kind !== kind) {
      tab.grip.title = kind === 'row' ? labels.dragRow : labels.dragPanel;
    }
    if (tab.name.textContent !== text) {
      tab.name.textContent = text;
      tab.name.title = text;
    }
    setDisplay(tab.grip, draggable ? '' : 'none');
    setDisplay(tab.deleteChip, showDelete ? '' : 'none');
    setAttr(tab.el, 'data-ujcms-above', above ? '' : null);
    setAttr(tab.el, 'data-ujcms-compact', compact ? '' : null);
    setAttr(tab.el, 'data-ujcms-side', side);
    tab.anchor = el;
    tab.kind = kind;
    // Selected: flush with the ring's outer edge; hover: flush with the element (its outline is inset)
    const edge = opts.selected ? RING_BORDER : 0;
    tab.el.style.maxWidth = `${Math.max(GRIP_W + 2, Math.min(TAB_MAX_W, r.width + 2 * edge))}px`;
    tab.el.style.top = `${above ? r.top - TAB_H : r.top + 4}px`;
    if (side === 'left') {
      tab.el.style.right = 'auto';
      tab.el.style.left = `${Math.max(0, above ? r.left - edge : r.left + 4)}px`;
    } else {
      // The layer is a zero-width box at the document origin: a negative `right` puts the tab's
      // right edge at +X without measuring the tab
      tab.el.style.left = 'auto';
      tab.el.style.right = `${-(above ? r.left + r.width + edge : r.left + r.width - 4)}px`;
    }
    setDisplay(tab.el, 'flex');
    return { above, side };
  };

  /** `labels` (console → iframe): chrome text + shape/row-layout display names; unknown keys ignored, missing keys keep defaults. */
  const applyLabels = (data: Record<string, unknown>): void => {
    const next: Labels = { ...DEFAULT_LABELS };
    for (const key of Object.keys(DEFAULT_LABELS) as Array<keyof Labels>) {
      const value = data[key];
      if (typeof value === 'string' && value.trim() !== '') {
        next[key] = value.slice(0, 200);
      }
    }
    labels = next;
    shapeLabels = sanitizeLabelMap(data.shapeLabels);
    layoutLabels = sanitizeLabelMap(data.layoutLabels);
    regionLabels = sanitizeLabelMap(data.regionLabels);
    for (const tab of [hoverTab, selTab]) {
      tab.deleteChip.textContent = labels.deletePanel;
      tab.deleteChip.title = labels.deletePanel;
      tab.grip.title = tab.kind === 'row' ? labels.dragRow : labels.dragPanel;
    }
    articleChip.textContent = labels.editArticle;
    articleChip.title = labels.editArticle;
    channelChip.textContent = labels.editChannel;
    channelChip.title = labels.editChannel;
    settingChip.title = labels.editSetting;
    labelStyle.textContent = labelCss();
    repositionRing();
    repositionHandles();
  };

  let hoverSettingEl: Element | null = null;
  let hoverRefEl: Element | null = null;

  /** Right-aligned chip pill inside `el`'s top edge (chips re-parented only when membership changes). */
  const sameChildren = (group: HTMLElement, chips: HTMLElement[]): boolean =>
    group.childElementCount === chips.length && chips.every((chip, i) => group.children[i] === chip);
  const placeChipRow = (group: HTMLElement, chips: HTMLElement[], el: Element | null): void => {
    if (!editMode || drag || !el || !el.isConnected || chips.length === 0) {
      group.style.display = 'none';
      group.replaceChildren();
      return;
    }
    // 成员未变则不重挂：每帧 replaceChildren 会让芯片在 mousedown 与 mouseup 之间离开文档一次，浏览器据此
    // 放弃合成 click——角标（⚙/编辑文章/编辑栏目）真实点击全部失灵，只有合成 click 能过；
    // 重挂本身还是 body 子树的 childList 变动，会被下方 MutationObserver 喂回 schedule，芯片可见期间空转每帧
    if (!sameChildren(group, chips)) {
      group.replaceChildren(...chips);
    }
    group.style.display = 'flex';
    const rect = docRect(el);
    const width = group.getBoundingClientRect().width;
    group.style.top = `${rect.top + 4}px`;
    group.style.left = `${Math.max(rect.left + 4, rect.left + rect.width - 4 - width)}px`;
    // 与可见标签页相撞（标签落入元素内侧、或行/区域标签在右上）则下移到标签之下
    const mine = group.getBoundingClientRect();
    for (const tab of [selTab, hoverTab]) {
      if (tab.el.style.display === 'none') {
        continue;
      }
      const other = tab.el.getBoundingClientRect();
      if (rectsOverlap(mine, other)) {
        group.style.top = `${rect.top + 4 + (other.bottom - mine.top) + 4}px`;
        break;
      }
    }
  };

  const repositionChips = (): void => {
    placeChipRow(settingGroup, hoverSettingEl ? [settingChip] : [], hoverSettingEl);
    const showArticle = hoverRefEl?.hasAttribute('data-article-id') === true;
    const showChannel = hoverRefEl?.hasAttribute('data-channel-id') === true;
    const refChips = [showChannel ? channelChip : null, showArticle ? articleChip : null].filter(Boolean) as HTMLElement[];
    // 内容深链挂 ref 元素（§5.5，逐条目）；删除面板已并入选中标签页（D23），不再有面板级芯片行
    placeChipRow(refGroup, refChips, hoverRefEl);
  };

  settingChip.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (!editMode || !hoverSettingEl || !hoverSettingEl.isConnected) {
      return;
    }
    const payload = classify(hoverSettingEl);
    if (payload) {
      selected = payload;
      repositionRing();
      repositionHandles();
      post('select', payload as unknown as Record<string, unknown>);
    }
  });

  const onQuickEditClick = (entity: 'article' | 'channel', attr: string) => (e: MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!editMode || !hoverRefEl || !hoverRefEl.isConnected) {
      return;
    }
    const id = hoverRefEl.getAttribute(attr);
    if (id) {
      post('quickEdit', { entity, id });
    }
  };
  articleChip.addEventListener('click', onQuickEditClick('article', 'data-article-id'));
  channelChip.addEventListener('click', onQuickEditClick('channel', 'data-channel-id'));

  const onDeleteIntent = (tab: Tab) => (e: MouseEvent): void => {
    e.preventDefault();
    e.stopPropagation();
    const el = tab.anchor;
    if (!editMode || tab.kind !== 'panel' || !el || !el.isConnected) {
      return;
    }
    const id = el.getAttribute('data-panel-id') ?? el.getAttribute('data-panel-disabled');
    if (!id) {
      return;
    }
    const region = el.closest('[data-region]')?.getAttribute('data-region');
    post('deleteIntent', { kind: 'panel', id, ...(region ? { region } : {}) });
  };

  /** Clicking a tab's name selects its anchor (the hover tab turns into the selected one in place). */
  const onTabNameClick = (tab: Tab) => (e: MouseEvent): void => {
    e.preventDefault();
    e.stopPropagation();
    const el = tab.anchor;
    if (!editMode || drag || !el || !el.isConnected) {
      return;
    }
    const payload = classify(el);
    if (payload) {
      selected = payload;
      repositionRing();
      repositionHandles();
      post('select', payload as unknown as Record<string, unknown>);
    }
  };

  for (const tab of [hoverTab, selTab]) {
    tab.deleteChip.addEventListener('click', onDeleteIntent(tab));
    tab.name.addEventListener('click', onTabNameClick(tab));
  }

  const clearHover = (): void => {
    hoverPanelEl = null;
    hoverRowEl = null;
    hoverRegionEl = null;
    hoverSlotEl = null;
    hoverSettingEl = null;
    hoverRefEl = null;
    externalHoverEl = null;
  };

  /** Innermost hovered anchor (D22): a real pointer hover wins over the host-driven anchor. */
  const hoverTarget = (): Element | null => hoverPanelEl ?? hoverSlotEl ?? hoverRowEl ?? hoverRegionEl ?? externalHoverEl;

  const rectsOverlap = (a: DOMRect, b: DOMRect): boolean => a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;

  /** Dashed box for a hovered container (row / region) — only when nothing inside it is hovered, never on the selected element. */
  const placeHoverBox = (target: Element | null, selectedEl: Element | null): void => {
    const container = target != null && (target.hasAttribute('data-row') || target.hasAttribute('data-region')) ? target : null;
    if (!editMode || drag || !container || !container.isConnected || container === selectedEl) {
      hoverBox.style.display = 'none';
      return;
    }
    const rect = decorRect(container);
    hoverBox.style.display = 'block';
    hoverBox.style.top = `${rect.top - 2}px`;
    hoverBox.style.left = `${rect.left - 2}px`;
    hoverBox.style.width = `${rect.width + 4}px`;
    hoverBox.style.height = `${rect.height + 4}px`;
  };

  /** Re-anchor every hover-driven decoration (name kept from the handle era; called from schedule / pointer / labels / drag). */
  const repositionHandles = (): void => {
    const selectedEl = selected ? selectedElement() : null;
    const target = hoverTarget();
    const hovered = target != null && target !== selectedEl ? target : null;
    placeTab(hoverTab, hovered, null, { selected: false });
    // Both tabs on one line (a hovered element sharing the selected one's top edge): the transient tab yields and drops inside
    if (hovered && hoverTab.el.style.display !== 'none' && selTab.el.style.display !== 'none' && rectsOverlap(hoverTab.el.getBoundingClientRect(), selTab.el.getBoundingClientRect())) {
      placeTab(hoverTab, hovered, null, { selected: false, forceInside: true });
    }
    placeHoverBox(target, selectedEl);
    repositionChips();
  };

  /** A new interaction starts: a drag that ended without a synthesized click (pointercancel) must not swallow this one. */
  const onPointerDown = (): void => {
    if (!drag) {
      swallowNextClick = false;
    }
  };

  const onPointerOver = (e: PointerEvent): void => {
    if (!editMode || drag) {
      return;
    }
    const target = e.target instanceof Element ? e.target : null;
    if (!target || target.closest(`[${DECOR_ATTR}]`)) {
      // Hovering a decoration (e.g. the tab itself) keeps the current anchors.
      return;
    }
    hoverPanelEl = target.closest(PANEL_SELECTOR);
    hoverRowEl = target.closest('[data-row]');
    hoverRegionEl = target.closest('[data-region]');
    hoverSlotEl = target.closest('[data-slot],[data-ujcms-slot]');
    hoverSettingEl = target.closest('[data-ujcms-setting]');
    hoverRefEl = target.closest('[data-article-id],[data-channel-id]');
    // A real pointer hover supersedes the host-driven anchor
    externalHoverEl = null;
    repositionHandles();
  };

  /** Pointer left the document (console chrome, drag shield, scrollbar): drop the pointer-driven hover chrome (D27). */
  const onPointerOut = (e: PointerEvent): void => {
    if (!editMode || drag || e.relatedTarget != null) {
      return;
    }
    if (!hoverPanelEl && !hoverRowEl && !hoverRegionEl && !hoverSlotEl && !hoverSettingEl && !hoverRefEl) {
      return;
    }
    hoverPanelEl = null;
    hoverRowEl = null;
    hoverRegionEl = null;
    hoverSlotEl = null;
    hoverSettingEl = null;
    hoverRefEl = null;
    repositionHandles();
  };

  const indexAmong = (list: NodeListOf<Element>, el: Element): number => Array.prototype.indexOf.call(list, el);

  // ---- name badges (shell-polish) --------------------------------------------------------------

  const badgeText = (el: Element): string => {
    if (el.hasAttribute('data-panel-id') || el.hasAttribute('data-panel-disabled')) {
      const shape = el.getAttribute('data-panel') ?? '';
      const name = shapeLabels[shape] ?? shape;
      return el.hasAttribute('data-panel-disabled') ? `${name} · ${labels.disabled}` : name;
    }
    if (el.hasAttribute('data-row')) {
      const regionEl = el.closest('[data-region]');
      const index = regionEl ? indexAmong(regionEl.querySelectorAll('[data-row]'), el) : -1;
      const layout = el.getAttribute('data-row-layout') ?? '';
      const layoutName = layout ? (layoutLabels[layout] ?? layout) : '';
      return `${labels.row} ${index + 1}${layoutName ? ` · ${layoutName}` : ''}`;
    }
    if (el.hasAttribute('data-region')) {
      const name = el.getAttribute('data-region') ?? '';
      return regionLabels[name] ?? `${labels.region} ${name}`;
    }
    if (el.hasAttribute('data-slot') || el.hasAttribute('data-ujcms-slot')) {
      return `${labels.slot} ${el.getAttribute('data-ujcms-slot') ?? el.getAttribute('data-slot') ?? ''}`;
    }
    return '';
  };

  /** Source position in display order — untrusted hint for the console (its draft tree is the truth). */
  const dragSourceFrom = (kind: 'panel' | 'row', el: Element): Record<string, unknown> | null => {
    const regionEl = el.closest('[data-region]');
    const region = regionEl?.getAttribute('data-region');
    if (!regionEl || !region) {
      return null;
    }
    if (kind === 'row') {
      const index = indexAmong(regionEl.querySelectorAll('[data-row]'), el);
      return index < 0 ? null : { region, index };
    }
    const colEl = el.closest('[data-col]');
    const rowEl = el.closest('[data-row]');
    if (!colEl || !rowEl) {
      return null;
    }
    const index = indexAmong(colEl.querySelectorAll(PANEL_SELECTOR), el);
    return index < 0 ? null : { region, row: rowEl.getAttribute('data-row') ?? '', col: Number(colEl.getAttribute('data-col') ?? '0'), index };
  };

  const hideDropDecor = (): void => {
    insertLine.style.display = 'none';
    invalidTint.style.display = 'none';
    reasonTip.style.display = 'none';
  };

  const clearZones = (): void => {
    zoneLayer.textContent = '';
  };

  const postDragMove = (): void => {
    if (moveRaf) {
      return;
    }
    moveRaf = requestAnimationFrame(() => {
      moveRaf = 0;
      if (drag) {
        post('dragMove', { x: Math.round(drag.lastX + window.scrollX), y: Math.round(drag.lastY + window.scrollY) });
      }
    });
  };

  const endDrag = (): void => {
    if (!drag) {
      return;
    }
    drag.el.removeAttribute(DRAGGING_ATTR);
    drag.tab.el.removeAttribute('data-ujcms-active');
    drag = null;
    swallowNextClick = true;
    document.documentElement.removeAttribute(DRAG_ATTR);
    if (moveRaf) {
      cancelAnimationFrame(moveRaf);
      moveRaf = 0;
    }
    hideDropDecor();
    clearZones();
    repositionHandles();
  };

  const cancelDrag = (): void => {
    if (!drag) {
      return;
    }
    try {
      drag.handle.releasePointerCapture(drag.pointerId);
    } catch {
      /* already released */
    }
    endDrag();
    post('dragCancel', {});
  };

  const onGripPointerDown = (tab: Tab) => (e: PointerEvent) => {
    if (!editMode || drag || e.button !== 0) {
      return;
    }
    const el = tab.anchor;
    const kind = tab.kind;
    if (!el || !el.isConnected || (kind !== 'panel' && kind !== 'row')) {
      return;
    }
    const from = dragSourceFrom(kind, el);
    const id = kind === 'panel' ? (el.getAttribute('data-panel-id') ?? el.getAttribute('data-panel-disabled')) : el.getAttribute('data-row');
    if (!from || !id) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    const handle = tab.grip;
    try {
      handle.setPointerCapture(e.pointerId);
    } catch {
      return;
    }
    drag = { kind, id, el, tab, handle, pointerId: e.pointerId, lastX: e.clientX, lastY: e.clientY };
    el.setAttribute(DRAGGING_ATTR, '');
    tab.el.setAttribute('data-ujcms-active', '');
    document.documentElement.setAttribute(DRAG_ATTR, '');
    // Only the active tab (and the selected one) stay visible while the ghost travels
    repositionHandles();
    // preventDefault suppresses the focus switch into this frame — pull it in explicitly so the
    // Escape-cancel keydown reaches the iframe document (the console never sees mid-drag keys).
    try {
      window.focus();
    } catch {
      /* ignore */
    }
    post('dragStart', { kind, id, from });
  };

  const onHandlePointerMove = (e: PointerEvent): void => {
    if (!drag || e.pointerId !== drag.pointerId) {
      return;
    }
    drag.lastX = e.clientX;
    drag.lastY = e.clientY;
    postDragMove();
  };

  const onHandlePointerUp = (e: PointerEvent): void => {
    if (!drag || e.pointerId !== drag.pointerId) {
      return;
    }
    const x = Math.round(e.clientX + window.scrollX);
    const y = Math.round(e.clientY + window.scrollY);
    try {
      drag.handle.releasePointerCapture(drag.pointerId);
    } catch {
      /* already released */
    }
    endDrag();
    post('drop', { x, y });
  };

  const onHandlePointerCancel = (e: PointerEvent): void => {
    if (drag && e.pointerId === drag.pointerId) {
      cancelDrag();
    }
  };

  /** Keys typed into page-owned form controls (theme search box, comment form…) are never forwarded. */
  const inEditable = (e: KeyboardEvent): boolean => {
    const target = e.target instanceof Element ? e.target : document.activeElement;
    return target != null && target.matches('input,textarea,select,[contenteditable]:not([contenteditable="false"])');
  };

  /** Shortcut → host action (shell-polish): the console guards permission/modals and dispatches to the toolbar handlers. */
  const keyCommand = (e: KeyboardEvent): 'undo' | 'redo' | 'delete' | null => {
    if (typeof e.key !== 'string') {
      return null;
    }
    const mod = e.metaKey || e.ctrlKey;
    const key = e.key.toLowerCase();
    if (mod && !e.altKey && key === 'z') {
      return e.shiftKey ? 'redo' : 'undo';
    }
    if (e.ctrlKey && !e.metaKey && !e.altKey && !e.shiftKey && key === 'y') {
      return 'redo';
    }
    if (!mod && !e.altKey && !e.shiftKey && (e.key === 'Delete' || e.key === 'Backspace') && (selected?.kind === 'panel' || selected?.kind === 'row')) {
      return 'delete';
    }
    return null;
  };

  const onKeyDown = (e: KeyboardEvent): void => {
    if (e.key === 'Escape') {
      if (drag) {
        e.preventDefault();
        cancelDrag();
      } else if (zonesClickable) {
        // Placement mode (click-to-place): Escape exits and informs the console.
        e.preventDefault();
        exitPlacement(true);
      } else if (editMode && selected && !e.repeat && !inEditable(e)) {
        // Deselect (canvas-chrome D26): the same {kind:null,id:null} sentinel as a blank-space click
        e.preventDefault();
        selected = null;
        repositionRing();
        repositionHandles();
        post('select', { kind: null, id: null });
      }
      return;
    }
    if (!editMode || drag || zonesClickable || e.repeat || inEditable(e)) {
      return;
    }
    const command = keyCommand(e);
    if (command) {
      e.preventDefault();
      post('key', { key: command });
    }
  };

  for (const tab of [hoverTab, selTab]) {
    tab.grip.addEventListener('pointerdown', onGripPointerDown(tab));
    tab.grip.addEventListener('pointermove', onHandlePointerMove);
    tab.grip.addEventListener('pointerup', onHandlePointerUp);
    tab.grip.addEventListener('pointercancel', onHandlePointerCancel);
  }

  // ---- console-driven drop feedback (§4 dropHint / dropZones / autoScroll) -----------------------

  const clampCoord = (value: unknown): number | null => {
    const n = Number(value);
    return Number.isFinite(n) ? Math.max(-1e6, Math.min(1e6, Math.round(n))) : null;
  };

  /**
   * Insertion line (valid) or tint + reason (invalid) for the console-computed target. Resolved
   * against the live DOM (fresher than any snapshot); ids arrive as untrusted display data —
   * query via cssEscape, render reason via textContent only.
   */
  const renderDropHint = (data: Record<string, unknown>): void => {
    hideDropDecor();
    const target = (data.target ?? null) as { region?: unknown; row?: unknown; col?: unknown; index?: unknown } | null;
    if (!editMode || target == null || typeof target.region !== 'string') {
      return;
    }
    const regionEl = document.querySelector(`[data-region="${cssEscape(target.region)}"]`);
    if (!regionEl) {
      return;
    }
    let containerEl: Element = regionEl;
    let items: Element[];
    if (typeof target.row === 'string') {
      const rowEl = regionEl.querySelector(`[data-row="${cssEscape(target.row)}"]`);
      const colEl = rowEl?.querySelector(`[data-col="${cssEscape(String(clampCoord(target.col) ?? 0))}"]`);
      if (!colEl) {
        return;
      }
      containerEl = colEl;
      items = Array.from(colEl.querySelectorAll(PANEL_SELECTOR));
    } else {
      items = Array.from(regionEl.querySelectorAll('[data-row]'));
    }
    const cRect = decorRect(containerEl);
    if (data.valid !== true) {
      invalidTint.style.display = 'block';
      invalidTint.style.top = `${cRect.top}px`;
      invalidTint.style.left = `${cRect.left}px`;
      invalidTint.style.width = `${cRect.width}px`;
      invalidTint.style.height = `${cRect.height}px`;
      if (typeof data.reason === 'string' && data.reason) {
        reasonTip.textContent = data.reason.slice(0, 200);
        // Same anatomy as a label tab: above the tinted container when there is room, inside otherwise
        const above = cRect.top >= TAB_H + 2;
        reasonTip.toggleAttribute('data-ujcms-above', above);
        reasonTip.style.display = 'block';
        reasonTip.style.top = `${above ? cRect.top - TAB_H : cRect.top + 4}px`;
        reasonTip.style.left = `${above ? cRect.left : cRect.left + 4}px`;
      }
      return;
    }
    const index = Math.max(0, Math.min(items.length, Math.trunc(Number(target.index) || 0)));
    // 显式取元素再判空：Vue 轨 noUncheckedIndexedAccess 下索引访问为 T | undefined（三份拷贝逐字节一致）
    const anchorBefore = index < items.length ? items[index] : undefined;
    const anchorLast = items[items.length - 1];
    let lineTop: number;
    if (anchorLast === undefined) {
      lineTop = cRect.top + 6;
    } else if (anchorBefore !== undefined) {
      lineTop = decorRect(anchorBefore).top - 3;
    } else {
      const last = decorRect(anchorLast);
      lineTop = last.top + last.height + 1;
    }
    insertLine.style.display = 'block';
    insertLine.style.top = `${lineTop}px`;
    insertLine.style.left = `${cRect.left + 2}px`;
    insertLine.style.width = `${Math.max(0, cRect.width - 4)}px`;
  };

  /**
   * Valid-target highlights: passive during handle drags; with `clickable: true` this is placement
   * mode (§7.2 click-to-place, D4) — zone divs become clickable and echo the console-computed
   * structural target back as `zoneClick` (ids only, re-sanitized; the console re-validates against
   * its draft tree before placing). Escape or a click outside any zone exits placement mode
   * (`placeCancel`). Rects are console-computed document coords.
   */
  let zonesClickable = false;

  const renderDropZones = (data: Record<string, unknown>): void => {
    clearZones();
    zonesClickable = editMode && data.clickable === true;
    if (!editMode || !Array.isArray(data.zones)) {
      return;
    }
    for (const zone of (data.zones as unknown[]).slice(0, 200)) {
      const raw = zone as { rect?: unknown; region?: unknown; row?: unknown; col?: unknown; index?: unknown } | null;
      const rect = raw?.rect as Record<string, unknown> | undefined;
      const top = clampCoord(rect?.top);
      const left = clampCoord(rect?.left);
      const width = clampCoord(rect?.width);
      const height = clampCoord(rect?.height);
      if (top == null || left == null || width == null || height == null || width <= 0 || height <= 0) {
        continue;
      }
      const div = document.createElement('div');
      div.style.top = `${top}px`;
      div.style.left = `${left}px`;
      div.style.width = `${width}px`;
      div.style.height = `${height}px`;
      if (zonesClickable && raw && typeof raw.region === 'string') {
        // Structural ids only, re-sanitized before echoing (untrusted host data stays inert).
        const target: Record<string, unknown> = { region: raw.region.slice(0, 256), index: clampCoord(raw.index) ?? 0 };
        if (typeof raw.row === 'string') {
          target.row = raw.row.slice(0, 256);
          target.col = clampCoord(raw.col) ?? 0;
        }
        div.setAttribute('data-ujcms-zone', '');
        // Small zones (an empty 56px column, a narrow aside) show the "+" disc without the caption
        if (height < 44 || width < 160) {
          div.setAttribute('data-ujcms-compact', '');
        }
        div.addEventListener('click', (e) => {
          e.preventDefault();
          e.stopPropagation();
          exitPlacement(false);
          post('zoneClick', { target });
        });
      }
      zoneLayer.appendChild(div);
    }
  };

  /** Leave placement mode; `cancel` also tells the console (Escape / outside click). */
  const exitPlacement = (cancel: boolean): void => {
    if (!zonesClickable) {
      return;
    }
    zonesClickable = false;
    clearZones();
    if (cancel) {
      post('placeCancel', {});
    }
  };

  const schedule = (): void => {
    if (raf) {
      return;
    }
    raf = requestAnimationFrame(() => {
      raf = 0;
      postLayout();
      repositionRing();
      repositionHandles();
    });
  };

  // ---- click: select (edit mode) / navigate interception (plain preview) ------------------------

  const SELECTABLE = '[data-ujcms-setting],[data-panel-disabled],[data-panel-id],[data-slot],[data-ujcms-slot],[data-row],[data-region]';

  const classify = (el: Element): SelectPayload | null => {
    const region = el.closest('[data-region]')?.getAttribute('data-region') ?? undefined;
    if (el.hasAttribute('data-ujcms-setting')) {
      return { kind: 'setting', id: el.getAttribute('data-ujcms-setting') ?? '', region };
    }
    if (el.hasAttribute('data-panel-disabled')) {
      return { kind: 'panel', id: el.getAttribute('data-panel-disabled') ?? '', region };
    }
    if (el.hasAttribute('data-panel-id')) {
      return { kind: 'panel', id: el.getAttribute('data-panel-id') ?? '', region };
    }
    if (el.hasAttribute('data-slot') || el.hasAttribute('data-ujcms-slot')) {
      return { kind: 'slot', id: el.getAttribute('data-ujcms-slot') ?? el.getAttribute('data-slot') ?? '', region };
    }
    if (el.hasAttribute('data-row')) {
      return { kind: 'row', id: el.getAttribute('data-row') ?? '', region };
    }
    if (el.hasAttribute('data-region')) {
      // region 自身也带 region 字段（closest 命中自身，region === id）：控制台的草稿映射/检查器
      // 一律按 selection.region 取草稿——缺失即误判「不在当前草稿中」，添加行入口随之消失。
      return { kind: 'region', id: el.getAttribute('data-region') ?? '', region };
    }
    return null;
  };

  const onClick = (e: MouseEvent): void => {
    const target = e.target instanceof Element ? e.target : null;
    if (!target) {
      return;
    }
    if (editMode) {
      // Chip clicks (gear / quick actions) must reach the chip's own listener — this handler runs
      // in capture phase and its stopPropagation would swallow them (same rule as zone clicks).
      if (target.closest('[data-ujcms-chip],[data-ujcms-tab-name]')) {
        return;
      }
      if (swallowNextClick) {
        // The click synthesized when the pointer is released after a handle drag (see swallowNextClick)
        swallowNextClick = false;
        e.preventDefault();
        e.stopPropagation();
        return;
      }
      if (zonesClickable) {
        // Placement mode: a zone click must reach the zone div's own listener (this handler runs
        // in capture phase — stopPropagation here would swallow it); any other click cancels.
        if (target.closest('[data-ujcms-zone]')) {
          return;
        }
        e.preventDefault();
        e.stopPropagation();
        exitPlacement(true);
        return;
      }
      // Clicks select; navigation never happens in edit mode (a page swap would drop the session).
      e.preventDefault();
      e.stopPropagation();
      if (target.closest(`[${DECOR_ATTR}]`)) {
        // Decoration clicks (e.g. the residual click after a handle drag) never select.
        return;
      }
      const selectable = target.closest(SELECTABLE);
      const payload = selectable ? classify(selectable) : null;
      if (payload) {
        selected = payload;
        repositionRing();
        repositionHandles();
        post('select', payload as unknown as Record<string, unknown>);
      } else {
        // 未命中任何可选元素 = 点空白处取消选择（画布惯例；与宿主→桥 select(null) 同一
        // {kind:null,id:null} 哨兵，宿主据此清检查器选择态）。
        selected = null;
        repositionRing();
        repositionHandles();
        post('select', { kind: null, id: null });
      }
      return;
    }
    // Plain preview: intercept same-origin full-page navigations so the console can re-append the
    // draft-preview context (v2 `navigate` — the console approves/rewrites; tokens stay console-side).
    if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) {
      return;
    }
    const anchor = target.closest('a');
    if (!anchor || (anchor.target && anchor.target !== '_self')) {
      return;
    }
    const href = anchor.getAttribute('href') ?? '';
    if (!href || href.startsWith('#') || /^(javascript:|mailto:|tel:|data:|blob:)/i.test(href)) {
      return;
    }
    let url: URL;
    try {
      url = new URL(href, window.location.href);
    } catch {
      return;
    }
    if (url.origin !== window.location.origin) {
      return;
    }
    e.preventDefault();
    post('navigate', { url: url.href });
  };

  // ---- host messages -----------------------------------------------------------------------------

  const setEditMode = (on: boolean): void => {
    editMode = on;
    document.documentElement.toggleAttribute(EDIT_ATTR, on);
    if (!on) {
      selected = null;
      cancelDrag();
      clearHover();
      hideDropDecor();
      zonesClickable = false;
      clearZones();
      repositionHandles();
    }
    repositionRing();
    // 编辑态开关改变装饰相关布局（空面板/禁用面板占位撑高区域），同步重报几何——否则快照要
    // 等 ResizeObserver→rAF 才更新，后台标签页 rAF 节流时会滞留（拖拽投放区据此定位，会错位）。
    postLayout();
  };

  /** 设备预设（design-review P2-5）：'mobile' 置 DEVICE_ATTR，其余移除（缺省桌面）。 */
  const setDevice = (device: unknown): void => {
    if (device === 'mobile') {
      document.documentElement.setAttribute(DEVICE_ATTR, 'mobile');
    } else {
      document.documentElement.removeAttribute(DEVICE_ATTR);
    }
    // 隐藏/显示面板改变布局几何，同步重报（同 setEditMode 的口径——后台标签页 rAF 节流不可依赖）
    postLayout();
    repositionRing();
    repositionHandles();
  };

  const fullReload = (): void => {
    try {
      sessionStorage.setItem(SCROLL_STASH_PREFIX + nonce, String(Math.round(window.scrollY)));
    } catch {
      /* ignore */
    }
    window.location.reload();
  };

  // 模板轨道区域级刷新的碎片换血（P8，重开 §13 Q1——落地的是 swap 而非 endpoint）：self-fetch
  // 本页自身 URL（URL 即渲染上下文：路径/频道/变体/范围 + _preview/_ptoken/_pchannel 原样携带，
  // 与整页重载发出的请求完全相同——零新增后端/鉴权面），DOMParser 惰性解析（规范保证不执行任何
  // 脚本），仅把目标 [data-region] 的子树换入。任何存疑一律回退整页重载（快路径失败无损）。
  let fragmentAbort: AbortController | null = null;

  const swapRegionFragment = async (region: string): Promise<void> => {
    fragmentAbort?.abort();
    const ctl = new AbortController();
    fragmentAbort = ctl;
    try {
      const res = await fetch(window.location.href, { credentials: 'same-origin', signal: ctl.signal });
      if (!res.ok) {
        fullReload();
        return;
      }
      const doc = new DOMParser().parseFromString(await res.text(), 'text/html');
      if (ctl.signal.aborted) {
        return; // 已被更新的刷新取代
      }
      // 预览身份守卫：注入的桥脚本标签只出现在「已认证的草稿预览」响应上（PreviewBridgeInjectionFilter，
      // P1 已证）。缺失 = 令牌过期/失效——此时重载只会落到「已发布页 + 死桥」（控制台无从察觉，
      // 画布与草稿静默失同步）：保持当前 DOM 不动，上报 previewLost 由宿主重铸令牌重建画布。
      // 该守卫同时天然区分轨道：SSR 页以应用代码挂桥（无注入标签），异常路径照旧降级重载。
      if (!doc.querySelector('script[src*="/_ujcms/preview-bridge"]')) {
        post('previewLost', { scope: 'region', region });
        return;
      }
      const selector = `[data-region="${cssEscape(region)}"]`;
      const incoming = doc.querySelector(selector);
      const current = document.querySelector(selector);
      if (incoming == null || current == null) {
        fullReload();
        return;
      }
      // innerHTML 赋值不执行 <script>（HTML5 规范）——比它替代的整页重载更保守
      current.innerHTML = incoming.innerHTML;
      // 主题自有 JS 的重初始化钩子（参考主题面板片段无客户端脚本，自定义主题按需监听；
      // 约定收录于 theme-authoring.md「Preview & builder markup conventions」）
      current.dispatchEvent(new CustomEvent('ujcms:region-refreshed', { detail: { region }, bubbles: true }));
      // 悬停缓存指向已脱离文档的旧节点，清空待下次 pointerover 重建。
      clearHover();
      // 换入后立即（同步）重报布局 + 重锚选择环/手柄：innerHTML 赋值后新子树已在文档中，
      // getBoundingClientRect 强制同步回流即得终值。仅靠 schedule() 的 rAF 合批不够稳——
      // 后台标签页 rAF 被节流至近乎停摆（画布 iframe 常处非前台），装饰会滞留在换入前的旧几何，
      // 直到整页「刷新」重建桥才纠正（运营报告的「插入后边框错位，刷新才好」即此）。
      // 再挂一次 schedule()：迟到的图片加载/字体回流仍由 rAF 合批那条路径兜底二次重锚。
      postLayout();
      repositionRing();
      repositionHandles();
      schedule();
    } catch {
      if (!ctl.signal.aborted) {
        fullReload();
      }
    } finally {
      if (fragmentAbort === ctl) {
        fragmentAbort = null;
      }
    }
  };

  const doRefresh = (scope: RefreshScope, region?: string): void => {
    if (options.refresh) {
      try {
        if (options.refresh(scope, region) !== false) {
          return;
        }
      } catch {
        /* fall through to the template-track paths below */
      }
    }
    // 区域级刷新走碎片换血（免整页闪白，页面级镶边的 JS 状态如轮播不被打断）；页面级刷新
    // （发布/丢弃/设置保存——变更波及区域之外）保持整页重载语义
    if (scope === 'region' && typeof region === 'string' && region !== '') {
      void swapRegionFragment(region);
      return;
    }
    void guardedFullReload();
  };

  /**
   * 页面级重载前的预览身份预检：令牌过期时 location.reload() 会落到「已发布页 + 死桥」，
   * 宿主无从察觉（握手计时器只在 newChannel 时武装）。借碎片换血同款自取（响应就是重载
   * 将得到的那份），有桥标记才真重载；缺标记改报 previewLost；网络异常维持既有重载兜底。
   */
  const guardedFullReload = async (): Promise<void> => {
    try {
      const res = await fetch(window.location.href, { credentials: 'same-origin' });
      if (res.ok) {
        const doc = new DOMParser().parseFromString(await res.text(), 'text/html');
        if (!doc.querySelector('script[src*="/_ujcms/preview-bridge"]')) {
          post('previewLost', { scope: 'page' });
          return;
        }
      }
    } catch {
      /* 网络异常：按既有语义重载 */
    }
    fullReload();
  };

  const SELECT_KINDS = ['panel', 'row', 'region', 'slot', 'setting'];

  const onMessage = (event: MessageEvent): void => {
    if (parentOrigin !== '*' && event.origin !== parentOrigin) {
      return;
    }
    const data = event.data as { source?: unknown; channel?: unknown; v?: unknown; action?: unknown; [key: string]: unknown } | null;
    if (!data || data.source !== HOST_SOURCE || data.channel !== nonce || data.v !== PROTOCOL_V || typeof data.action !== 'string') {
      return;
    }
    switch (data.action) {
      case 'mode':
        setEditMode(data.edit === true);
        break;
      case 'device':
        setDevice(data.device);
        break;
      case 'hover': {
        // 宿主侧悬停锚点（canvas-chrome D30）：检查器区域卡片 / 面包屑悬停 → 与指针悬停同款 chrome；
        // {kind:null} 清除；任何真实 pointerover 覆盖之；元素不在场即无事
        const kind = data.kind;
        externalHoverEl =
          editMode && typeof kind === 'string' && SELECT_KINDS.includes(kind) && typeof data.id === 'string' ? resolveElement(kind as SelectPayload['kind'], data.id) : null;
        repositionHandles();
        break;
      }
      case 'select':
        selected =
          typeof data.kind === 'string' && SELECT_KINDS.includes(data.kind) && typeof data.id === 'string' ? { kind: data.kind as SelectPayload['kind'], id: data.id } : null;
        repositionRing();
        repositionHandles();
        if (data.scroll === true && selected) {
          // Inspector / structure-view driven selection: bring the element into view (canvas clicks never scroll).
          const el = selectedElement();
          if (el) {
            try {
              el.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
            } catch {
              el.scrollIntoView();
            }
            schedule();
          }
        }
        break;
      case 'labels':
        applyLabels(data);
        break;
      case 'dropHint':
        renderDropHint(data);
        break;
      case 'dropZones':
        renderDropZones(data);
        break;
      case 'autoScroll': {
        // Edge scrolling during drag (§7.2): scroll, then re-report the stationary pointer's new
        // document coordinates so the console re-hit-tests (the scroll listener reposts `layout`).
        const dy = clampCoord(data.dy);
        if (dy != null && dy !== 0) {
          window.scrollBy(0, Math.max(-240, Math.min(240, dy)));
          if (drag) {
            postDragMove();
          }
        }
        break;
      }
      case 'refresh':
        doRefresh(data.scope === 'region' ? 'region' : 'page', typeof data.region === 'string' ? data.region : undefined);
        break;
      case 'reload':
        window.location.reload();
        break;
      default:
        // Unknown actions are ignored (forward-compatible: future protocol additions).
        break;
    }
  };

  // ---- boot --------------------------------------------------------------------------------------

  const style = document.createElement('style');
  style.id = STYLE_ID;
  style.textContent = EDIT_CSS;
  document.head.appendChild(style);
  const labelStyle = document.createElement('style');
  labelStyle.id = STYLE_LABELS_ID;
  labelStyle.textContent = labelCss();
  document.head.appendChild(labelStyle);
  document.body.appendChild(layer);

  // Template-track full reloads restore the pre-refresh scroll position (§4 `refresh.preserveScroll`).
  try {
    const stashed = sessionStorage.getItem(SCROLL_STASH_PREFIX + nonce);
    if (stashed != null) {
      sessionStorage.removeItem(SCROLL_STASH_PREFIX + nonce);
      window.scrollTo(0, Number(stashed) || 0);
    }
  } catch {
    /* ignore */
  }

  window.addEventListener('message', onMessage);
  window.addEventListener('scroll', schedule, { passive: true });
  window.addEventListener('resize', schedule);
  document.addEventListener('click', onClick, true);
  document.addEventListener('pointerdown', onPointerDown, true);
  document.addEventListener('pointerover', onPointerOver, true);
  document.addEventListener('pointerout', onPointerOut, true);
  document.addEventListener('keydown', onKeyDown, true);
  // 装饰层自身的增删（芯片组重挂、投放区高亮）不改变页面几何，且会把 schedule 喂回自己——只对页面内容的变动重测
  const observer = new MutationObserver((records) => {
    if (records.some((record) => !layer.contains(record.target))) {
      schedule();
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });
  // 不触发 scroll/resize/mutation 的迟到回流也要重测几何（P8 后续修复）：桥在 defer 时刻量测，
  // 图片随后加载完会把内容撑高、令滚动条出现、整页横向收窄——快照/选择环从此陈旧，拖拽的投放区
  // 虚线框会切在文字中间。捕获态 load 监听逐张图片触发重测；ResizeObserver 兜底一切内容尺寸变化
  // （字体、滚动条增减）。两者都汇入 rAF 合批 + 几何哈希门控的 schedule，无重发风暴。
  document.addEventListener('load', schedule, true);
  let resizeObserver: ResizeObserver | null = null;
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(schedule);
    resizeObserver.observe(document.documentElement);
    resizeObserver.observe(document.body);
  }

  post('ready', { capabilities: CAPABILITIES, track: options.track });
  postLayout();

  return () => {
    cancelDrag();
    window.removeEventListener('message', onMessage);
    window.removeEventListener('scroll', schedule);
    window.removeEventListener('resize', schedule);
    document.removeEventListener('click', onClick, true);
    document.removeEventListener('pointerdown', onPointerDown, true);
    document.removeEventListener('pointerover', onPointerOver, true);
    document.removeEventListener('pointerout', onPointerOut, true);
    document.removeEventListener('keydown', onKeyDown, true);
    document.removeEventListener('load', schedule, true);
    resizeObserver?.disconnect();
    observer.disconnect();
    if (raf) {
      cancelAnimationFrame(raf);
    }
    if (moveRaf) {
      cancelAnimationFrame(moveRaf);
    }
    style.remove();
    labelStyle.remove();
    layer.remove();
    document.documentElement.removeAttribute(EDIT_ATTR);
    document.documentElement.removeAttribute(DRAG_ATTR);
    document.documentElement.removeAttribute(DEVICE_ATTR);
  };
}

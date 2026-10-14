import { onBeforeUnmount, ref, type Ref } from 'vue';
import i18n from '@/i18n';

/**
 * 预览桥宿主端（visual-builder §3/§4，协议 v2）——可视化构建器（/design/builder 的 CanvasStage）
 * 的消息中介（原预览抽屉 PagePreview.vue 已移除，本组合式函数是唯一宿主端）。
 *
 * 页面内的桥（bridge-core，模板轨道注入 IIFE）
 * 只做「传感器 + 装饰器」：上报几何快照（layout）、点击选择（select）、拦截的站内导航
 * （navigate）；宿主（本组合式函数）持有全部编辑逻辑与写权限。任何 iframe 消息都按不可信
 * 展示数据对待：校验 event.source 身份 + 消息源标识 + 每次装载的随机数 + 协议版本 + 逐动作
 * 的结构校验（kind 白名单、字符串截断、URL 可解析），未知动作忽略。令牌绝不过桥。
 */
export const FRAME_SOURCE = 'ujcms-slot-preview';
export const HOST_SOURCE = 'ujcms-slot-preview-host';
export const PROTOCOL_V = 2;

const SELECT_KINDS = ['panel', 'row', 'region', 'slot', 'setting'] as const;
export type BridgeSelectKind = (typeof SELECT_KINDS)[number];

export interface BridgeSelection {
  kind: BridgeSelectKind;
  id: string;
  region?: string;
}

export interface BridgeReadyInfo {
  capabilities: string[];
  track: string;
}

/** 拖拽源位置（桥按显示序推算的展示数据；草稿树才是权威，落点前按 id 重解析）。 */
export interface BridgeDragFrom {
  region: string;
  row?: string;
  col?: number;
  index: number;
}

export interface BridgeDragStart {
  kind: 'panel' | 'row';
  id: string;
  from: BridgeDragFrom;
}

/**
 * 画布编辑/删除意图（桥悬停角标，仅面板级）：标识符按展示数据校验。
 * 卡片级意图（kind:'card'）已按操作员反馈移除（2026-07-06）——卡片操作走点选 → 检查器。
 */
export interface BridgeCanvasIntent {
  kind: 'panel';
  id: string;
  region?: string;
}

/** 投放目标（控制台命中测试产物，经 dropHint/dropZones 下发桥渲染，§4）。 */
export interface DropTarget {
  region: string;
  row?: string;
  col?: number;
  index: number;
}

/** 桥转发的键盘快捷键（shell-polish）：iframe 有焦点时 ⌘/Ctrl+Z、⇧⌘Z/Ctrl+Y、Delete 经此到达宿主。 */
export type BridgeKey = 'undo' | 'redo' | 'delete';
const BRIDGE_KEYS = ['undo', 'redo', 'delete'] as const;

/** 宿主下发桥的展示标签映射（Shape 名 / 行布局键 / 区域名 → 显示名），供画布标签页。 */
export interface BridgeLabelMaps {
  shapeLabels?: Record<string, string>;
  layoutLabels?: Record<string, string>;
  regionLabels?: Record<string, string>;
}

export interface PreviewBridgeHandlers {
  onReady?: (info: BridgeReadyInfo) => void;
  /** 几何快照（§4 layout）：P1 供检查器/结构视图对照；P2 起驱动拖拽命中测试。 */
  onLayout?: (layout: Record<string, unknown>) => void;
  /** 画布点选；`null` = 桥上报点空白处取消选择（与宿主→桥 select(null) 同一哨兵）。 */
  onSelect?: (selection: BridgeSelection | null) => void;
  /** 桥拦截的站内整页导航：宿主校验并重写（补 _preview/_ptoken/_pchannel）后再导航。 */
  onNavigate?: (url: string) => void;
  /** 画布拖拽流（§7.3）：桥指针捕获手柄后流式上报，控制台跑状态机（useBuilderDrag）。 */
  onDragStart?: (payload: BridgeDragStart) => void;
  onDragMove?: (x: number, y: number) => void;
  onDrop?: (x: number, y: number) => void;
  onDragCancel?: () => void;
  /** 点击放置（§7.2 P3）：放置模式下桥回发被点击投放区的结构目标（控制台对照草稿树再校验）。 */
  onZoneClick?: (target: DropTarget) => void;
  /** 放置模式取消（iframe 内 Escape / 点击投放区之外）。 */
  onPlaceCancel?: () => void;
  /** 内容深链快捷操作（§8.3 P5）：桥内 编辑文章/编辑栏目 悬停按钮 → 打开既有 ArticleForm/ChannelForm。 */
  onQuickEdit?: (entity: 'article' | 'channel', id: string) => void;
  /** 画布删除意图（桥内 删除面板 悬停角标）：桥只上报结构标识，确认与草稿树核验在宿主。 */
  onDeleteIntent?: (payload: BridgeCanvasIntent) => void;
  /** 画布内键盘快捷键（桥仅转发动作枚举；守卫与分发在宿主，与顶栏按钮同终点）。 */
  onKey?: (key: BridgeKey) => void;
  onTimeout?: () => void;
  /** 预览会话失效（_ptoken 过期）：桥在刷新自取时发现响应缺预览标记——宿主应重铸令牌重建画布（buildSrc）。 */
  onPreviewLost?: () => void;
}

/** 防御性截断：iframe 上报的标识符按展示数据对待，超长即截断。 */
const clamp = (value: unknown, max = 256): string => (typeof value === 'string' ? value.slice(0, max) : '');

/** 坐标/下标校验：有限数值 + 量级钳制（iframe 上报按不可信数据对待）。 */
const toCoord = (value: unknown): number | null => {
  const n = Number(value);
  return typeof value === 'number' && Number.isFinite(n) && Math.abs(n) <= 1e7 ? Math.round(n) : null;
};

/** 实体ID校验：纯数字字符串（进入 beanId API 调用前的白名单）。 */
const toEntityId = (value: unknown): string | null => (typeof value === 'string' && /^\d{1,19}$/.test(value) ? value : null);

/** 画布意图负载解析（deleteIntent）：kind 白名单 + 标识符截断。
 * 旧缓存桥仍可能上报 kind:'card'（卡片角标已移除）或 editIntent（编辑面板 角标 2026-09-10 移除）——前者不入白名单、
 * 后者走未知动作默认分支，都静默忽略。 */
const toCanvasIntent = (data: Record<string, unknown>): BridgeCanvasIntent | null => {
  const id = clamp(data.id);
  if (data.kind !== 'panel' || !id) return null;
  const payload: BridgeCanvasIntent = { kind: 'panel', id };
  const region = clamp(data.region);
  if (region) payload.region = region;
  return payload;
};

/**
 * 画布装饰的界面文案（桥 chrome 的 i18n，shell-polish）：按控制台语言取值——语言切换整页重载，
 * 调用期求值即可。emptySlot 保留 {name} 字面占位（传 { name: '{name}' } 让 vue-i18n 原样输出），
 * 桥据此拆分前后缀拼 attr(data-slot)。
 */
const canvasLabels = (): Record<string, string> => {
  const t = i18n.global.t;
  return {
    dragPanel: t('builder.canvas.dragPanel'),
    dragRow: t('builder.canvas.dragRow'),
    deletePanel: t('builder.canvas.deletePanel'),
    editArticle: t('builder.canvas.editArticle'),
    editChannel: t('builder.canvas.editChannel'),
    editSetting: t('builder.canvas.editSetting'),
    emptySlot: t('builder.canvas.emptySlot', { name: '{name}' }),
    emptyColumn: t('builder.canvas.emptyColumn'),
    emptyPanel: t('builder.canvas.emptyPanel'),
    emptyRegion: t('builder.canvas.emptyRegion'),
    dropHere: t('builder.canvas.dropHere'),
    row: t('builder.canvas.row'),
    region: t('builder.canvas.region'),
    slot: t('builder.canvas.slot'),
    disabled: t('builder.canvas.disabled'),
  };
};

export function usePreviewBridge(iframeRef: Ref<HTMLIFrameElement | undefined>, handlers: PreviewBridgeHandlers = {}) {
  const channel = ref<string>('');
  const bridgeReady = ref<boolean>(false);
  const bridgeTimedOut = ref<boolean>(false);
  // 宿主期望的编辑模式：桥每次 ready（含模板轨道整页重载后）都会重新下发，编辑态跨重载存续
  const editMode = ref<boolean>(false);
  let targetOrigin = window.location.origin;
  let timer = 0;

  const clearTimer = (): void => {
    if (timer) {
      window.clearTimeout(timer);
      timer = 0;
    }
  };

  const armTimer = (): void => {
    clearTimer();
    timer = window.setTimeout(() => {
      timer = 0;
      if (!bridgeReady.value) {
        bridgeTimedOut.value = true;
        handlers.onTimeout?.();
      }
    }, 5000);
  };

  /**
   * 生成新的握手随机数（每次装载一枚，§4）并复位握手状态；origin 为 iframe 页面源
   * （与控制台同源），宿主消息只发往该源。
   */
  const newChannel = (origin?: string): string => {
    channel.value = `${Math.random().toString(36).slice(2)}${Date.now().toString(36)}`;
    targetOrigin = origin || window.location.origin;
    lastHover = '';
    bridgeReady.value = false;
    bridgeTimedOut.value = false;
    armTimer();
    return channel.value;
  };

  const post = (action: string, payload: Record<string, unknown> = {}): void => {
    const win = iframeRef.value?.contentWindow;
    if (!win || !channel.value) return;
    win.postMessage({ source: HOST_SOURCE, channel: channel.value, v: PROTOCOL_V, action, ...payload }, targetOrigin);
  };

  const setEditMode = (edit: boolean): void => {
    editMode.value = edit;
    if (bridgeReady.value) post('mode', { edit });
  };

  // 宿主期望的设备预设（design-review P2-5：视口预设映射的设备类别，画布据此隐藏按设备显示的面板）：
  // 与编辑态同口径——桥每次 ready（含模板轨道整页重载后）重新下发，跨重载存续
  const device = ref<'desktop' | 'mobile'>('desktop');

  const setDevice = (value: 'desktop' | 'mobile'): void => {
    device.value = value;
    if (bridgeReady.value) post('device', { device: value });
  };

  // 展示标签映射（Shape/行布局显示名）：宿主装载/变更时下发；桥每次 ready 重发（与 mode/device 同口径）
  let labelMaps: BridgeLabelMaps = {};
  // 映射复制为纯对象：宿主传入的可能是响应式代理，postMessage 的结构化克隆无法克隆 Proxy
  const labelsPayload = (): Record<string, unknown> => ({
    ...canvasLabels(),
    shapeLabels: { ...labelMaps.shapeLabels },
    layoutLabels: { ...labelMaps.layoutLabels },
    regionLabels: { ...labelMaps.regionLabels },
  });

  const setLabels = (maps: BridgeLabelMaps): void => {
    labelMaps = maps;
    if (bridgeReady.value) post('labels', labelsPayload());
  };

  /** 检查器驱动的选择环同步；传 null 清除。scroll = 令桥把选中元素滚入视口（检查器/结构视图来源的选择）。 */
  const select = (selection: BridgeSelection | null, options: { scroll?: boolean } = {}): void => {
    post('select', selection ? { kind: selection.kind, id: selection.id, ...(options.scroll ? { scroll: true } : {}) } : { kind: null, id: null });
  };

  /**
   * 宿主侧悬停锚点（console → iframe `hover`，canvas-chrome D30）：检查器区域卡片 / 面包屑祖先悬停时令桥绘制
   * 与指针悬停相同的 chrome；null 清除。瞬态、不随 ready 重发；同一目标去重。旧 IIFE 未知动作忽略。
   */
  let lastHover = '';
  const hover = (target: Pick<BridgeSelection, 'kind' | 'id'> | null): void => {
    const key = target ? `${target.kind}:${target.id}` : '';
    if (key === lastHover) return;
    lastHover = key;
    post('hover', target ? { kind: target.kind, id: target.id } : { kind: null, id: null });
  };

  const refresh = (scope: 'page' | 'region', region?: string): void => {
    post('refresh', { scope, region, preserveScroll: true });
  };

  const reload = (): void => {
    post('reload');
  };

  // ---- 拖拽反馈下发（§4 console→iframe；桥负责渲染插入线/无效着色/投放区高亮/边缘滚动） ----

  const dropHint = (target: DropTarget | null, valid: boolean, reason?: string): void => {
    post('dropHint', { target, valid, ...(reason ? { reason } : {}) });
  };

  /** 投放区高亮；{@code clickable} 进入放置模式（§7.2 点击放置，D4）——桥把区块变为可点击并回发 zoneClick。 */
  const dropZones = (zones: Array<DropTarget & { rect: { top: number; left: number; width: number; height: number } }>, clickable = false): void => {
    post('dropZones', { zones, clickable });
  };

  const autoScroll = (dy: number): void => {
    post('autoScroll', { dy });
  };

  const onMessage = (event: MessageEvent): void => {
    const iframe = iframeRef.value;
    // 身份核对：仅接受来自本 iframe 的消息（比对 window 引用，而非可伪造的字段）
    if (!iframe || event.source !== iframe.contentWindow) return;
    const data = event.data as { source?: unknown; channel?: unknown; v?: unknown; action?: unknown; [key: string]: unknown } | null;
    if (!data || data.source !== FRAME_SOURCE || data.channel !== channel.value || data.v !== PROTOCOL_V) return;
    switch (data.action) {
      case 'ready': {
        bridgeReady.value = true;
        bridgeTimedOut.value = false;
        clearTimer();
        // 展示标签先于 mode 下发：首次编辑态绘制（空插槽占位文字等）即用控制台语言，无中文闪现
        post('labels', labelsPayload());
        // 编辑态跨重载存续：模板轨道 refresh = 整页重载，桥重新握手后补发 mode
        if (editMode.value) post('mode', { edit: true });
        if (device.value !== 'desktop') post('device', { device: device.value });
        const capabilities = Array.isArray(data.capabilities) ? data.capabilities.filter((c) => typeof c === 'string').map((c) => clamp(c, 32)) : [];
        handlers.onReady?.({ capabilities, track: clamp(data.track, 32) });
        break;
      }
      case 'layout': {
        const { regions, slots, settings, scroll, viewport } = data;
        if (Array.isArray(regions) && Array.isArray(slots) && Array.isArray(settings)) {
          handlers.onLayout?.({ regions, slots, settings, scroll, viewport });
        }
        break;
      }
      case 'select': {
        const kind = data.kind;
        if (kind == null && data.id == null) {
          // 点空白处取消选择哨兵（{kind:null,id:null}，与宿主→桥方向一致）
          handlers.onSelect?.(null);
          break;
        }
        if (typeof kind === 'string' && (SELECT_KINDS as readonly string[]).includes(kind) && typeof data.id === 'string') {
          const selection: BridgeSelection = { kind: kind as BridgeSelectKind, id: clamp(data.id) };
          const region = clamp(data.region);
          if (region) selection.region = region;
          handlers.onSelect?.(selection);
        }
        break;
      }
      case 'navigate': {
        const url = clamp(data.url, 2048);
        if (url) {
          try {
            void new URL(url);
            handlers.onNavigate?.(url);
          } catch {
            // 非法 URL 忽略
          }
        }
        break;
      }
      case 'dragStart': {
        const kind = data.kind;
        if (kind !== 'panel' && kind !== 'row') break;
        const id = clamp(data.id);
        const raw = data.from as { region?: unknown; row?: unknown; col?: unknown; index?: unknown } | null;
        if (!id || raw == null || typeof raw !== 'object') break;
        const region = clamp(raw.region);
        const index = toCoord(raw.index);
        if (!region || index == null || index < 0) break;
        const from: BridgeDragFrom = { region, index };
        if (kind === 'panel') {
          const row = clamp(raw.row);
          const col = toCoord(raw.col);
          if (!row || col == null || col < 0) break;
          from.row = row;
          from.col = col;
        }
        handlers.onDragStart?.({ kind, id, from });
        break;
      }
      case 'dragMove': {
        const x = toCoord(data.x);
        const y = toCoord(data.y);
        if (x != null && y != null) handlers.onDragMove?.(x, y);
        break;
      }
      case 'drop': {
        const x = toCoord(data.x);
        const y = toCoord(data.y);
        if (x != null && y != null) handlers.onDrop?.(x, y);
        break;
      }
      case 'dragCancel':
        handlers.onDragCancel?.();
        break;
      case 'zoneClick': {
        // 结构目标按不可信数据校验（区域/行为截断字符串，列/下标为钳制数值）；控制台对照草稿树再校验
        const raw = data.target as { region?: unknown; row?: unknown; col?: unknown; index?: unknown } | null;
        if (raw == null || typeof raw !== 'object') break;
        const region = clamp(raw.region);
        const index = toCoord(raw.index);
        if (!region || index == null || index < 0) break;
        const target: DropTarget = { region, index };
        if (raw.row != null) {
          const row = clamp(raw.row);
          const col = toCoord(raw.col);
          if (!row || col == null || col < 0) break;
          target.row = row;
          target.col = col;
        }
        handlers.onZoneClick?.(target);
        break;
      }
      case 'placeCancel':
        handlers.onPlaceCancel?.();
        break;
      case 'previewLost':
        handlers.onPreviewLost?.();
        break;
      case 'quickEdit': {
        // 内容深链（§8.3）：实体类型白名单 + 纯数字ID；打开的是控制台既有表单，权限由后端接口再校验
        const entity = data.entity;
        const id = toEntityId(data.id);
        if ((entity === 'article' || entity === 'channel') && id) {
          handlers.onQuickEdit?.(entity, id);
        }
        break;
      }
      case 'deleteIntent': {
        // 删除角标：二次确认与草稿树核验在宿主侧（桥无变更权）
        const payload = toCanvasIntent(data);
        if (payload) handlers.onDeleteIntent?.(payload);
        break;
      }
      case 'key': {
        // 画布内快捷键：动作白名单；权限/弹层/忙碌守卫在宿主分发处
        const key = data.key;
        if (typeof key === 'string' && (BRIDGE_KEYS as readonly string[]).includes(key)) handlers.onKey?.(key as BridgeKey);
        break;
      }
      default:
        // 未知动作忽略（前向兼容：后续协议新增动作）
        break;
    }
  };

  window.addEventListener('message', onMessage);

  const dispose = (): void => {
    clearTimer();
    window.removeEventListener('message', onMessage);
  };

  onBeforeUnmount(dispose);

  return { channel, bridgeReady, bridgeTimedOut, editMode, newChannel, post, setEditMode, setDevice, setLabels, select, hover, refresh, reload, dropHint, dropZones, autoScroll, dispose };
}

// ---- 预览 URL 构造（构建器画布 / 新标签页试看共用） ------------------------------------------------

/** 模板轨道：开发期经 /_ujcms_preview 代理使 iframe 与控制台同源（:5173）；生产期直接用真实路径。 */
export const templatePreviewBase = import.meta.env.DEV ? '/_ujcms_preview' : '';

/**
 * 新标签页整站试看（主题预览 / 模板管理的预览按钮）的地址前缀：不内嵌、无同源之需。开发期直接开后端源
 * （VITE_PROXY_API）——前台页面内的根相对链接（/channel/x、/article/x…）才能原生解析到后端；若仍走
 * /_ujcms_preview 代理开新标签页，首次点击站内链接就落到 :5173（仅该前缀与少数资源前缀被代理）的
 * 控制台 SPA 上。生产期控制台与前台同源，空前缀（拼 site.dy 即可）。
 */
export const newTabPreviewBase = import.meta.env.DEV ? (import.meta.env.VITE_PROXY_API || '').replace(/\/+$/, '') : '';

export interface PreviewUrlParts {
  /** 前台路径（以 / 开头，可含查询参数）。 */
  path: string;
  token: string;
  channel: string;
}

/** 预览 iframe 地址：补 _preview=draft&_ptoken&_pchannel。 */
export const buildPreviewUrl = ({ path, token, channel }: PreviewUrlParts): string => {
  const normalized = sanitizePreviewPath(path);
  const sep = normalized.includes('?') ? '&' : '?';
  return `${templatePreviewBase}${normalized}${sep}_preview=draft&_ptoken=${encodeURIComponent(token)}&_pchannel=${encodeURIComponent(channel)}`;
};

/**
 * 新标签页整站草稿试看地址（构建器「新标签打开」）：不内嵌——无 _pchannel（桥仅在被内嵌时激活），
 * 前缀用 newTabPreviewBase 而非 iframe 的同源代理（开发期经 /_ujcms_preview 开新标签页，页内根相对
 * 链接首次点击即落到 :5173 控制台 SPA）。顶层文档的同源链接/表单/子请求由后端注入的
 * /_ujcms/preview-context.js 续带 _preview=draft&_ptoken，可在草稿态下浏览整站。
 */
export const buildNewTabPreviewUrl = ({ path, token }: Omit<PreviewUrlParts, 'channel'>): string => {
  const normalized = sanitizePreviewPath(path);
  const sep = normalized.includes('?') ? '&' : '?';
  return `${newTabPreviewBase}${normalized}${sep}_preview=draft&_ptoken=${encodeURIComponent(token)}`;
};

/**
 * 归一化为「本源绝对路径」（安全：令牌只拼到同源路径上）。协议相对路径（{@code //host}）在模板轨道
 * （生产期 base 为空）会被浏览器解析为跨域 {@code //host?_ptoken=...}，把预览令牌泄漏到外域——故这里
 * 折叠前导多斜杠为单斜杠，非 {@code /} 开头的补 {@code /}。防御 iframe 上报的 navigate URL（不可信数据）。
 */
export const sanitizePreviewPath = (path: string): string => {
  const withSlash = path.startsWith('/') ? path : `/${path}`;
  return withSlash.replace(/^\/+/, '/');
};

/** 预览 iframe 页面自身的源（postMessage targetOrigin 用）。 */
export const previewTargetOrigin = (): string => window.location.origin;

import { computed, ref, type Ref } from 'vue';
import type { BridgeDragStart, DropTarget } from '@/composables/usePreviewBridge';
import { canAddShape, canInsertRow, canMovePanel, columnFull, firstRowLayout, rowsFull, shapeAllowed, type Check } from './constraints';
import type { Drafts, PanelVM, RegionDraftState } from './types';

/**
 * 画布拖拽状态机（visual-builder §2/§7.2/§7.3/§7.4，P2/P3）——控制台持有全部拖拽逻辑与状态。
 * 两个事件源：
 * 1. 桥驱动（P2 §7.3）：桥指针捕获手柄并流式上报 dragStart → dragMove（iframe 文档坐标）→
 *    drop/dragCancel；
 * 2. 控制台指针驱动（P3 §7.2 调色板幽灵拖拽）：BuilderPage 把控制台指针坐标换算为 iframe 文档
 *    坐标（docX = clientX − iframeRect.left + scroll.x）后送入同一状态机（startPaletteDrag →
 *    onDragMove → onDrop）。
 * 命中测试对照最近一次 layout 快照做纯函数计算；约束校验同步对照草稿树 + 区域详情
 * （constraints.ts：allowedShapes / maxRows / maxPanelsPerColumn；无效目标从投放区集合排除，
 * 悬停给出 dropHint {valid:false, reason}，§7.4）。拖拽源以草稿树为权威（桥上报的 from 仅是展示序提示，
 * 按 id 重解析）。落点成立 → 草稿树操作（movePanel/moveRow/新增面板）→ save-per-op（§6）。
 * 服务端保存时会再校验。
 *
 * 新增落点（调色板拖拽 + 点击放置共用 placeShape，§7.2）：空区域整体即投放区，落点物化首行
 * （full 优先，其次首个允许布局）+ 面板。调色板布局卡片（paletteRow）复用行移动的行间隙命中，
 * 放下即插入空行并立即保存（空行无需配置，不走延迟提交）。新增面板**延迟提交**：落点只改本地草稿并开 PanelForm，
 * 确定才首次保存（模板轨道每次新增由两次整页重载减为一次，且不再打断拖放手势），取消经回滚闭包
 * 纯本地撤销（零保存零刷新）。跨区域面板移动（§6 stretch）= 目标区新增（复制面板配置）+ 源区
 * 移除的两次保存（即时提交不变），仅当 shape ∈ 目标 allowedShapes 时提供。
 */

export interface SnapshotRect {
  top: number;
  left: number;
  width: number;
  height: number;
}

/** 投放区（结构目标 + 快照矩形），经 dropZones 下发桥渲染。 */
export type DropZone = DropTarget & { rect: SnapshotRect };

interface ActiveDrag {
  kind: 'panel' | 'row' | 'palette' | 'paletteRow';
  /** 面板/行 ID（调色板新增无既有实体，为空串）。 */
  id: string;
  /** 源区域（调色板新增为空串）。 */
  region: string;
  /** 拖拽物展示标签（幽灵卡）。 */
  label: string;
  /** 面板/调色板拖拽的 Shape（目标区域 allowedShapes 校验用）。 */
  shape?: string;
  /** 调色板布局卡片拖拽的行布局键（目标区域 allowedRowLayouts 校验用）。 */
  layout?: string;
  /** 当前命中目标 + 有效性（变化时才下发 dropHint）。 */
  target: DropTarget | null;
  valid: boolean;
  reason?: string;
  /** 最近一次流式坐标（iframe 文档坐标系）。 */
  pointer: { x: number; y: number };
}

export interface UseBuilderDragOptions {
  /** Record<regionName, useRegionDraft 实例（reactive 解包）> */
  drafts: Ref<Drafts>;
  /** 最近一次桥 layout 快照（文档坐标几何树）。 */
  snapshot: Ref<any | null>;
  t: (key: string, args?: Record<string, unknown>) => string;
  labelFor: (kind: 'panel' | 'row', info: { shape?: string; rowIndex?: number }) => string;
  /** 行布局键 → 列数（空区域物化首行用；BuilderPage 从行布局目录解析）。 */
  layoutColumns: (key: string) => number;
  dropHint: (target: DropTarget | null, valid: boolean, reason?: string) => void;
  /** 投放区高亮；clickable = 放置模式（点击放置 D4）。 */
  dropZones: (zones: DropZone[], clickable?: boolean) => void;
  autoScroll: (dy: number) => void;
  /** 落点成立且树已变更：save-per-op + 区域刷新（BuilderPage 实现）。返回保存是否成功。 */
  commit: (region: string) => Promise<boolean>;
  /**
   * 新面板已置入本地草稿（调色板拖拽/点击放置，延迟提交）：BuilderPage 打开 PanelForm——
   * 确定才首次保存 + 刷新；取消调用 {@code rollback} 纯本地回滚（撤面板、撤空区域物化的首行、
   * 复原脏标）。
   */
  onPlaced?: (region: string, panel: PanelVM, rollback: () => void) => void;
}

/** 边缘自动滚动：指针距 iframe 视口上/下边 48px 内启动（§7.2），按贴近程度加速。 */
const EDGE_PX = 48;
const EDGE_TICK_MS = 80;
const MAX_STEP = 60;

const contains = (rect: SnapshotRect | undefined, x: number, y: number): boolean =>
  rect != null && x >= rect.left && x < rect.left + rect.width && y >= rect.top && y < rect.top + rect.height;

/**
 * 快照矩形去响应式（投放区经 postMessage 下发桥，structuredClone 无法克隆 Vue reactive Proxy——
 * 必须复制为纯对象）。
 */
const plainRect = (rect: SnapshotRect): SnapshotRect => ({ top: rect.top, left: rect.left, width: rect.width, height: rect.height });

/** 插入下标：目标容器内（含被拖拽项）中点在 y 之上的条目数。 */
const insertIndex = (items: Array<{ rect: SnapshotRect }>, y: number): number => {
  let index = 0;
  for (const item of items) {
    if (y > item.rect.top + item.rect.height / 2) index += 1;
  }
  return index;
};

export function useBuilderDrag(options: UseBuilderDragOptions) {
  const { drafts, snapshot, t, labelFor } = options;

  const state = ref<ActiveDrag | null>(null);
  const dragging = computed<boolean>(() => state.value != null);
  /** 幽灵卡位置（iframe 视口坐标 = 画布舞台内坐标）+ 标签。 */
  const ghost = computed<{ x: number; y: number; label: string } | null>(() => {
    const s = state.value;
    const scroll = snapshot.value?.scroll;
    if (s == null || scroll == null) return null;
    return { x: s.pointer.x - (scroll.x ?? 0), y: s.pointer.y - (scroll.y ?? 0), label: s.label };
  });

  let edgeTimer = 0;

  const stopEdgeScroll = (): void => {
    if (edgeTimer) {
      window.clearInterval(edgeTimer);
      edgeTimer = 0;
    }
  };

  const edgeDy = (): number => {
    const s = state.value;
    const snap = snapshot.value;
    if (s == null || snap?.scroll == null || snap?.viewport == null) return 0;
    const vy = s.pointer.y - (snap.scroll.y ?? 0);
    if (vy < EDGE_PX) return -Math.min(MAX_STEP, Math.ceil((EDGE_PX - vy) / 2));
    const h = snap.viewport.h ?? 0;
    if (vy > h - EDGE_PX) return Math.min(MAX_STEP, Math.ceil((vy - (h - EDGE_PX)) / 2));
    return 0;
  };

  const updateEdgeScroll = (): void => {
    const dy = edgeDy();
    if (dy !== 0 && !edgeTimer) {
      edgeTimer = window.setInterval(() => {
        const tick = edgeDy();
        if (tick === 0 || state.value == null) {
          stopEdgeScroll();
          return;
        }
        options.autoScroll(tick);
      }, EDGE_TICK_MS);
    } else if (dy === 0) {
      stopEdgeScroll();
    }
  };

  // ---- 草稿树内的源解析（权威）------------------------------------------------------------------

  const resolvePanelSource = (draft: RegionDraftState, id: string): { row: string; col: number; index: number; shape: string } | null => {
    for (const row of draft.rows) {
      for (let col = 0; col < row.columns.length; col += 1) {
        const column = row.columns[col];
        if (column == null) continue;
        const index = column.panels.findIndex((p) => String(p.id) === id);
        const hit = index >= 0 ? column.panels[index] : undefined;
        if (hit != null) return { row: row.id, col, index, shape: hit.shape };
      }
    }
    return null;
  };

  const resolveRowSource = (draft: RegionDraftState, id: string): number => draft.rows.findIndex((r) => r.id === id);

  // ---- 命中测试 + 约束校验（纯函数，对照快照 + 草稿树）-------------------------------------------

  type Validity = { valid: boolean; reason?: string };
  const toValidity = (check: Check): Validity => (check.ok ? { valid: true } : { valid: false, reason: check.key != null ? t(check.key, check.args) : undefined });

  /**
   * 「新增面板」目标校验（调色板拖拽/点击放置/跨区域移动的目标侧共用，§7.2/§7.4/§6）：
   * shape ∈ allowedShapes；列落点校验 maxPanelsPerColumn；无行落点（空区域物化首行）校验 maxRows。
   */
  const validateAddTarget = (shape: string, target: DropTarget): Validity => {
    const draft = drafts.value[target.region];
    if (draft == null) return { valid: false };
    return toValidity(canAddShape(draft, shape, target));
  };

  const validatePanelTarget = (s: ActiveDrag, target: DropTarget): Validity => {
    if (target.region !== s.region) {
      // 跨区域移动（§6 stretch）：目标侧按「新增」校验（shape ∈ allowedShapes、列未满/行未达上限）
      return validateAddTarget(s.shape ?? '', target);
    }
    const draft = drafts.value[target.region];
    if (draft == null) return { valid: false };
    const source = resolvePanelSource(draft, s.id);
    if (source == null) return { valid: false };
    return toValidity(canMovePanel(draft, s.shape ?? '', source, target));
  };

  const validateRowTarget = (s: ActiveDrag, target: DropTarget): Validity => {
    if (target.region !== s.region) {
      return { valid: false, reason: t('builder.drag.crossRegion') };
    }
    return { valid: true };
  };

  /** 「插入新行」目标校验（调色板布局卡片拖拽）：layout ∈ allowedRowLayouts + maxRows。 */
  const validateInsertRowTarget = (layout: string, target: DropTarget): Validity => {
    const draft = drafts.value[target.region];
    if (draft == null) return { valid: false };
    return toValidity(canInsertRow(draft, layout));
  };

  /**
   * 指针命中的投放目标（快照坐标）：面板/调色板 Shape → 列 + 插入下标（列未命中且区域草稿为空 →
   * 无行目标，落点物化首行）；行移动/调色板布局卡片 → 区域 + 行插入下标。无命中 → null。
   */
  const hitTest = (s: ActiveDrag, x: number, y: number): { target: DropTarget | null; valid: boolean; reason?: string } => {
    const regions: any[] = snapshot.value?.regions ?? [];
    if (s.kind === 'row' || s.kind === 'paletteRow') {
      for (const region of regions) {
        if (!contains(region.rect, x, y)) continue;
        const target: DropTarget = { region: region.name, index: insertIndex(region.rows ?? [], y) };
        return { target, ...(s.kind === 'row' ? validateRowTarget(s, target) : validateInsertRowTarget(s.layout ?? '', target)) };
      }
      return { target: null, valid: false };
    }
    const validate = (target: DropTarget) => (s.kind === 'palette' ? validateAddTarget(s.shape ?? '', target) : validatePanelTarget(s, target));
    for (const region of regions) {
      for (const row of region.rows ?? []) {
        for (const col of row.columns ?? []) {
          if (!contains(col.rect, x, y)) continue;
          const target: DropTarget = { region: region.name, row: row.id, col: col.index, index: insertIndex(col.panels ?? [], y) };
          return { target, ...validate(target) };
        }
      }
    }
    // 列未命中：空区域（草稿树无行）整体即投放区，落点物化首行（§7.2）
    for (const region of regions) {
      if (!contains(region.rect, x, y)) continue;
      if ((drafts.value[region.name]?.rows?.length ?? -1) === 0) {
        const target: DropTarget = { region: region.name, index: 0 };
        return { target, ...validate(target) };
      }
      break;
    }
    return { target: null, valid: false };
  };

  // ---- 投放区集合（§7.4：无效目标直接排除）--------------------------------------------------------

  /** 区域内可「新增面板」的投放区（调色板 + 跨区域移动的目标区共用）：未满的列 + 空区域整体。 */
  const addZonesForRegion = (regionSnap: any, shape: string, zones: DropZone[]): void => {
    const draft = drafts.value[regionSnap.name];
    if (draft == null || !shapeAllowed(draft, shape).ok) return;
    if (draft.rows.length === 0) {
      if (rowsFull(draft).ok) {
        zones.push({ region: regionSnap.name, index: 0, rect: plainRect(regionSnap.rect) });
      }
      return;
    }
    for (const row of regionSnap.rows ?? []) {
      const draftRow = draft.findRow(row.id);
      for (const col of row.columns ?? []) {
        const count = draftRow?.columns?.[col.index]?.panels?.length;
        if (count == null || !columnFull(draft, row.id, col.index).ok) continue;
        zones.push({ region: regionSnap.name, row: row.id, col: col.index, index: count, rect: plainRect(col.rect) });
      }
    }
  };

  /** 调色板 Shape 的全部有效投放区（点击放置的 dropZones 亦经此计算，§7.2 D4）。 */
  const computeAddZones = (shape: string): DropZone[] => {
    const zones: DropZone[] = [];
    for (const region of snapshot.value?.regions ?? []) {
      addZonesForRegion(region, shape, zones);
    }
    return zones;
  };

  /** 调色板布局卡片的全部有效投放区：允许该布局且未达行数上限的区域整体。 */
  const computeRowZones = (layout: string): DropZone[] => {
    const zones: DropZone[] = [];
    for (const region of snapshot.value?.regions ?? []) {
      if (validateInsertRowTarget(layout, { region: region.name, index: 0 }).valid) {
        zones.push({ region: region.name, index: 0, rect: plainRect(region.rect) });
      }
    }
    return zones;
  };

  const computeZones = (s: ActiveDrag): DropZone[] => {
    if (s.kind === 'palette') {
      return computeAddZones(s.shape ?? '');
    }
    if (s.kind === 'paletteRow') {
      return computeRowZones(s.layout ?? '');
    }
    const region = (snapshot.value?.regions ?? []).find((r: any) => r.name === s.region);
    if (region == null) return [];
    if (s.kind === 'row') {
      return [{ region: s.region, index: 0, rect: plainRect(region.rect) }];
    }
    const draft = drafts.value[s.region];
    const source = draft != null ? resolvePanelSource(draft, s.id) : null;
    if (draft == null || source == null) return [];
    const zones: DropZone[] = [];
    for (const row of region.rows ?? []) {
      for (const col of row.columns ?? []) {
        const sameColumn = source.row === row.id && source.col === col.index;
        if (!columnFull(draft, row.id, col.index, sameColumn).ok) continue;
        zones.push({ region: s.region, row: row.id, col: col.index, index: 0, rect: plainRect(col.rect) });
      }
    }
    // 跨区域移动（§6 stretch）：其余区域按「新增」口径给出投放区（shape ∈ allowedShapes 才提供）
    for (const other of snapshot.value?.regions ?? []) {
      if (other.name === s.region) continue;
      addZonesForRegion(other, s.shape ?? '', zones);
    }
    return zones;
  };

  // ---- 放置（调色板拖拽 / 点击放置 / 跨区域移动的目标侧共用）--------------------------------------

  /**
   * 在目标位置构建新面板（不保存）：无行目标 = 空区域物化首行（full 优先，其次首个允许布局）；
   * {@code config} 用于跨区域移动复制面板配置（设置值/停用态）。
   */
  const buildPanelAt = (draft: RegionDraftState, target: DropTarget, shape: string, config?: Pick<PanelVM, 'settings' | 'enabled' | 'visibility'>): PanelVM | null => {
    let panel: PanelVM | null = null;
    if (target.row == null) {
      const layoutKey = firstRowLayout(draft);
      if (!draft.addRow(layoutKey, Math.max(1, options.layoutColumns(layoutKey)))) return null;
      const firstColumn = draft.rows[draft.rows.length - 1]?.columns[0];
      if (firstColumn == null) return null;
      panel = draft.addPanel(firstColumn, shape);
    } else {
      const col = draft.findRow(target.row)?.columns?.[target.col ?? -1];
      if (col == null) return null;
      panel = draft.addPanel(col, shape, target.index);
    }
    if (panel != null && config != null) {
      Object.assign(panel, {
        settings: JSON.parse(JSON.stringify(config.settings ?? {})),
        enabled: config.enabled,
        visibility: config.visibility ?? 'all',
      });
    }
    return panel;
  };

  /**
   * 放置新面板（§7.2 点击放置与调色板拖拽的统一终点，延迟提交）：校验 → 本地构建 → onPlaced
   * （BuilderPage 开 PanelForm）。首次保存推迟到 PanelForm 确定——放置手势本身零保存零刷新
   * （模板轨道 refresh = 整页重载，落点即存会在拖放瞬间重载整页且每次新增重载两次）；取消经
   * rollback 撤销本地面板（空区域落点连带撤掉物化的首行）并复原脏标。
   */
  const placeShape = (shape: string, target: DropTarget): boolean => {
    if (!validateAddTarget(shape, target).valid) return false;
    const draft = drafts.value[target.region];
    if (draft == null) return false;
    const wasDirty: boolean = draft.dirty;
    const materializedRow = target.row == null;
    const panel = buildPanelAt(draft, target, shape);
    if (panel == null) return false;
    const rollback = (): void => {
      for (const row of draft.rows) {
        const col = row.columns.find((c) => c.panels.includes(panel));
        if (col != null) {
          draft.removePanel(col, col.panels.indexOf(panel));
          break;
        }
      }
      // 空区域落点连带撤掉物化的首行（buildPanelAt 追加于尾部；防御性地仅撤仍为空的行）
      if (materializedRow) {
        const last = draft.rows[draft.rows.length - 1];
        if (last != null && last.columns.every((c) => c.panels.length === 0)) {
          draft.removeRow(draft.rows.length - 1);
        }
      }
      draft.dirty = wasDirty;
    };
    options.onPlaced?.(target.region, panel, rollback);
    return true;
  };

  // ---- 拖拽事件入口（桥驱动 + 控制台指针驱动共用状态机）------------------------------------------

  const onDragStart = (payload: BridgeDragStart): void => {
    reset();
    const draft = drafts.value[payload.from.region];
    if (draft == null) return;
    let label: string;
    let shape: string | undefined;
    if (payload.kind === 'panel') {
      const source = resolvePanelSource(draft, payload.id);
      if (source == null) return;
      shape = source.shape;
      label = labelFor('panel', { shape: source.shape });
    } else {
      const index = resolveRowSource(draft, payload.id);
      if (index < 0) return;
      label = labelFor('row', { rowIndex: index });
    }
    state.value = {
      kind: payload.kind,
      id: payload.id,
      region: payload.from.region,
      label,
      shape,
      target: null,
      valid: false,
      pointer: { x: 0, y: 0 },
    };
    options.dropZones(computeZones(state.value));
  };

  /** 调色板幽灵拖拽入口（§7.2 控制台指针驱动）：坐标由 BuilderPage 换算后送 onDragMove/onDrop。 */
  const startPaletteDrag = (shape: string, label: string): void => {
    reset();
    state.value = { kind: 'palette', id: '', region: '', label, shape, target: null, valid: false, pointer: { x: 0, y: 0 } };
    options.dropZones(computeZones(state.value));
  };

  /** 调色板布局卡片幽灵拖拽入口：落点=行间隙（复用行移动的区域+行插入下标命中），放下即插入空行。 */
  const startPaletteRowDrag = (layout: string, label: string): void => {
    reset();
    state.value = { kind: 'paletteRow', id: '', region: '', label, layout, target: null, valid: false, pointer: { x: 0, y: 0 } };
    options.dropZones(computeZones(state.value));
  };

  const onDragMove = (x: number, y: number): void => {
    const s = state.value;
    if (s == null) return;
    s.pointer = { x, y };
    const hit = hitTest(s, x, y);
    const changed =
      hit.valid !== s.valid ||
      hit.reason !== s.reason ||
      hit.target?.region !== s.target?.region ||
      hit.target?.row !== s.target?.row ||
      hit.target?.col !== s.target?.col ||
      hit.target?.index !== s.target?.index ||
      (hit.target == null) !== (s.target == null);
    s.target = hit.target;
    s.valid = hit.valid;
    s.reason = hit.reason;
    if (changed) {
      options.dropHint(hit.target, hit.valid, hit.reason);
    }
    updateEdgeScroll();
  };

  const onDrop = async (x: number, y: number): Promise<void> => {
    const s = state.value;
    if (s == null) return;
    s.pointer = { x, y };
    const hit = hitTest(s, x, y);
    reset();
    if (hit.target == null || !hit.valid) return;
    const draft = drafts.value[hit.target.region];
    if (draft == null) return;
    if (s.kind === 'palette') {
      placeShape(s.shape ?? '', hit.target);
      return;
    }
    if (s.kind === 'paletteRow') {
      // 空行无需配置——不走延迟提交（PanelForm），落点即存（与 添加行 下拉同终点）
      const layout = s.layout ?? '';
      if (draft.addRow(layout, Math.max(1, options.layoutColumns(layout)), hit.target.index)) {
        await options.commit(hit.target.region);
      }
      return;
    }
    if (s.kind === 'panel' && hit.target.region !== s.region) {
      await movePanelAcrossRegions(s, hit.target);
      return;
    }
    let changed = false;
    if (s.kind === 'panel') {
      // 源以草稿树为权威（按 id 现取）；目标下标已含被拖拽项，movePanel 自行调整
      const source = resolvePanelSource(draft, s.id);
      if (source == null || hit.target.row == null || hit.target.col == null) return;
      changed = draft.movePanel(source, { row: hit.target.row, col: hit.target.col, index: hit.target.index });
    } else {
      const from = resolveRowSource(draft, s.id);
      if (from < 0) return;
      changed = draft.moveRow(from, hit.target.index);
    }
    if (changed) {
      await options.commit(hit.target.region);
    }
  };

  /**
   * 跨区域面板移动（§6 stretch）：目标区新增（复制 shape/设置值/停用态，即「以相同配置重建
   * 投放」）+ 源区移除，两次 save-per-op。目标区保存失败（409 刷新/校验拒绝）则不动源区——
   * 绝不先删后加。
   */
  const movePanelAcrossRegions = async (s: ActiveDrag, target: DropTarget): Promise<void> => {
    const sourceDraft = drafts.value[s.region];
    const targetDraft = drafts.value[target.region];
    if (sourceDraft == null || targetDraft == null) return;
    const source = resolvePanelSource(sourceDraft, s.id);
    if (source == null) return;
    const original = sourceDraft.findRow(source.row)?.columns?.[source.col]?.panels?.[source.index];
    if (original == null) return;
    if (buildPanelAt(targetDraft, target, original.shape, original) == null) return;
    if (!(await options.commit(target.region))) return;
    // 目标区已持久化（面板ID已回填）→ 移除源面板（源投放由发布/丢弃时的孤立清理回收）
    const freshSource = resolvePanelSource(sourceDraft, s.id);
    const freshRow = freshSource != null ? sourceDraft.findRow(freshSource.row) : null;
    const freshColumn = freshSource != null ? freshRow?.columns[freshSource.col] : undefined;
    if (freshSource == null || freshColumn == null) return;
    sourceDraft.removePanel(freshColumn, freshSource.index);
    await options.commit(s.region);
  };

  const onDragCancel = (): void => {
    reset();
  };

  const reset = (): void => {
    stopEdgeScroll();
    state.value = null;
    // 桥内 endDrag 只在 iframe 手柄拖拽时清理装饰；调色板拖拽由控制台指针驱动，投放区/插入线
    // 必须在此显式清除——否则残留在拖拽前的旧几何上（新增面板后虚线框不含新面板，刷新才消失）
    options.dropZones([]);
    options.dropHint(null, false);
  };

  return { state, dragging, ghost, onDragStart, startPaletteDrag, startPaletteRowDrag, onDragMove, onDrop, onDragCancel, reset, computeAddZones, validateAddTarget, placeShape };
}

import type { DropTarget, RegionDraftState, RowLayout } from './types';

/**
 * 区域约束校验（visual-builder §7.4）——画布拖拽状态机（useBuilderDrag）、结构视图 SortableJS :move、
 * 调色板置灰（BuilderPage.paletteItems）、检查器 添加行 共用的纯函数：无 Vue、无 i18n，返回
 * 消息键 + 参数，调用方按需 t()。原先同一套规则在五处各抄一份，且结构视图跨列拖拽从未校验
 * maxPanelsPerColumn。规则：shape ∈ allowedShapes、layout ∈ allowedRowLayouts（空 = 全部）、
 * rows.length < maxRows、column.panels.length < maxPanelsPerColumn（同列内移动豁免）。
 * 服务端保存时照旧复验（shipped 兜底）。
 */
export type Check = { ok: true } | { ok: false; key?: string; args?: Record<string, unknown> };

export type DraftLike = Pick<RegionDraftState, 'rows' | 'allowedShapes' | 'maxRows' | 'maxPanelsPerColumn' | 'detail' | 'findRow'>;

const OK: Check = { ok: true };

export const shapeAllowed = (draft: Pick<DraftLike, 'allowedShapes'>, shape: string): Check =>
  shape !== '' && draft.allowedShapes.length > 0 && !draft.allowedShapes.includes(shape) ? { ok: false, key: 'builder.drag.shapeNotAllowed' } : OK;

export const layoutAllowed = (draft: Pick<DraftLike, 'detail'>, layout: string): Check => {
  const allowed: string[] = draft.detail?.allowedRowLayouts ?? [];
  return allowed.length > 0 && !allowed.includes(layout) ? { ok: false, key: 'builder.drag.layoutNotAllowed' } : OK;
};

export const rowsFull = (draft: Pick<DraftLike, 'rows' | 'maxRows'>): Check =>
  draft.maxRows != null && draft.rows.length >= draft.maxRows ? { ok: false, key: 'builder.drag.maxRows', args: { max: draft.maxRows } } : OK;

/**
 * 列满校验；行/列无法在草稿树解析（快照过期）视为不可放置（无消息）。
 * exempt = 同列内移动：源面板已计入列内，不占新位。
 */
export const columnFull = (draft: DraftLike, rowId: string, col: number, exempt = false): Check => {
  const column = draft.findRow(rowId)?.columns?.[col];
  if (column == null) return { ok: false };
  const max = draft.maxPanelsPerColumn;
  return !exempt && max != null && column.panels.length >= max ? { ok: false, key: 'builder.drag.columnFull', args: { max } } : OK;
};

type Target = Pick<DropTarget, 'row' | 'col'>;

/**
 * 「新增面板」目标（调色板拖拽 / 点击放置 / 结构视图克隆拖入 / 跨区域移动的目标侧）：
 * shape ∈ allowedShapes；无行目标（空区域物化首行）按 maxRows；列目标按 maxPanelsPerColumn。
 */
export const canAddShape = (draft: DraftLike, shape: string, target: Target): Check => {
  const shapeCheck = shapeAllowed(draft, shape);
  if (!shapeCheck.ok) return shapeCheck;
  return target.row == null ? rowsFull(draft) : columnFull(draft, target.row, target.col ?? -1);
};

/** 同区域面板移动：shape 校验 + 目标列未满（同列内移动豁免）；无行目标不可作为移动落点。 */
export const canMovePanel = (draft: DraftLike, shape: string, source: { row: string; col: number }, target: Target): Check => {
  const shapeCheck = shapeAllowed(draft, shape);
  if (!shapeCheck.ok) return shapeCheck;
  if (target.row == null) return { ok: false };
  return columnFull(draft, target.row, target.col ?? -1, source.row === target.row && source.col === target.col);
};

/** 「插入新行」（调色板布局卡片 / 添加行）：layout ∈ allowedRowLayouts + maxRows。 */
export const canInsertRow = (draft: DraftLike, layout: string): Check => {
  const layoutCheck = layoutAllowed(draft, layout);
  if (!layoutCheck.ok) return layoutCheck;
  return rowsFull(draft);
};

/** 空区域物化首行的布局：full 优先，其次首个允许布局（画布 buildPanelAt 与结构视图空区域落点同一规则）。 */
export const firstRowLayout = (draft: Pick<DraftLike, 'detail'>): string => {
  const layouts: string[] = draft.detail?.allowedRowLayouts ?? [];
  return layouts.includes('full') ? 'full' : (layouts[0] ?? 'full');
};

/** 区域允许的行布局目录条目（allowedRowLayouts 为空 = 全部）。 */
export const allowedLayoutsOf = (draft: Pick<DraftLike, 'detail'> | null | undefined, rowLayouts: RowLayout[]): RowLayout[] =>
  rowLayouts.filter((l) => draft == null || layoutAllowed(draft, l.key).ok);

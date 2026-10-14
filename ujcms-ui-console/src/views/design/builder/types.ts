import type { UnwrapNestedRefs } from 'vue';
import type { PanelVM, RegionDetail, useRegionDraft } from '@/composables/useRegionDraft';

/**
 * 构建器共享类型（visual-builder shell-polish）：跨组件边界的草稿/调色板/行布局模型。
 * 草稿树模型本身随 useRegionDraft 定义（对象在哪构建，类型就在哪）；此处只做汇集与构建器专有类型。
 */
export type { BridgeSelection, BridgeSelectKind, BridgeCanvasIntent, DropTarget } from '@/composables/usePreviewBridge';
export type { RegionDraft, RegionDetail, PanelVM, RowVM, ColumnVM, PanelDetail, PanelVisibility, SaveDraftResult } from '@/composables/useRegionDraft';

/** useRegionDraft 实例经 `ref` 深层解包后的形态（`drafts.value[name]`：ref/computed 已自动解包）。 */
export type RegionDraftState = UnwrapNestedRefs<ReturnType<typeof useRegionDraft>>;

/** Record<regionName, 解包后的草稿实例>。 */
export type Drafts = Record<string, RegionDraftState>;

/** 行布局目录条目（GET page-region/row-layouts；固定代码枚举）。 */
export interface RowLayout {
  key: string;
  columns: number;
  widths: number[];
  label: Record<string, string>;
}

/** 全站共享区域状态（page-regions §15）：none = 自有区域；shared = 跟随宿主；own/ownPending = 本页分叉（Pending = 行未创建）。 */
export type ShareState = 'none' | 'shared' | 'own' | 'ownPending';

export type Viewport = 'desktop' | 'tablet' | 'mobile';

interface PaletteItemBase {
  name: string;
  label: string;
  description: string;
  group: string;
  order: number;
  disabled: boolean;
  disabledReason: string;
}

export interface PaletteShapeItem extends PaletteItemBase {
  kind: 'shape';
  icon: string;
  thumbnail: string | null;
  missing: boolean;
}

export interface PaletteLayoutItem extends PaletteItemBase {
  kind: 'layout';
  widths: number[];
  columns: number;
}

export type PaletteItem = PaletteShapeItem | PaletteLayoutItem;

/** 调色板幽灵拖拽起点（画布态）。 */
export interface PaletteDragStart {
  kind: 'shape' | 'layout';
  name: string;
  label: string;
  event: PointerEvent;
}

/** 延迟提交的新面板登记（画布 onPlaced / 结构视图 placed 同一契约）。 */
export interface PlacedPayload {
  region: string;
  panel: PanelVM;
  rollback: () => void;
}

/** GET page-region/page-detail 响应。 */
export interface PageDetailResult {
  page: string;
  pageLabel: Record<string, string>;
  scope: string;
  scopes: string[];
  regions: RegionDetail[];
  shapes: any[];
}

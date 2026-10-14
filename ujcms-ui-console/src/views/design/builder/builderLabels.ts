import { localizedLabel } from '@/i18n';
import type { RegionDraftState, RowLayout } from './types';

/**
 * 构建器展示标签工具（检查器 / 结构视图 / 顶栏共用；原各自复制一份）。
 * 区域标签来自清单声明（RegionDetail.label，后端 SlotDef 透传）；缺失/空映射时回退区域名——
 * 旧后端响应无 label 字段亦安全。
 */
type DraftLike = Pick<RegionDraftState, 'detail'> | null | undefined;

export const layoutLabel = (l: RowLayout): string => localizedLabel(l.label, l.key);

export const shapeLabelOf = (map: Record<string, string>, shape: string): string => map[shape] ?? shape;

/** 区域允许的行布局：规则随其余约束一并落在 constraints.ts，此处转出口保持既有导入不变。 */
export { allowedLayoutsOf } from './constraints';

export const regionLabelOf = (draft: DraftLike, region: string): string => localizedLabel(draft?.detail?.label, region);

export const regionDescriptionOf = (draft: DraftLike): string => draft?.detail?.description ?? '';

/** 草稿树内面板总数（区域卡片「N 行 · M 面板」）。 */
export const regionPanelCount = (draft: Pick<RegionDraftState, 'rows'> | null | undefined): number =>
  (draft?.rows ?? []).reduce((sum, row) => sum + row.columns.reduce((s, col) => s + col.panels.length, 0), 0);

// ---- 页面显示名缓存 ----------------------------------------------------------------------------

/**
 * 页面名 → 多语言 label 映射的会话级缓存：页面目录（设计 → 页面 / 顶栏切换器）与页面详情装载时写入，
 * 构建器切页重挂载时同步读取——顶栏标题不再在 page-detail 返回前闪现原始页面名（如 channel）。
 * 语言切换整页重载，缓存自然失效；清单无 label 时缓存空映射，读取侧回退页面名。
 */
const pageLabelCache = new Map<string, Record<string, string>>();

export const rememberPageLabel = (name: string, label: Record<string, string> | null | undefined): void => {
  pageLabelCache.set(name, label ?? {});
};

/** 未缓存返回 null（顶栏据此显示占位骨架而非原始页面名）。 */
export const cachedPageLabel = (name: string): Record<string, string> | null => pageLabelCache.get(name) ?? null;

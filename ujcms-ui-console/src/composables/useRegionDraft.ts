import { computed, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import i18n from '@/i18n';
import { handleError } from '@/utils/request';
import { queryRegion, saveRegionDraft, publishRegion, discardRegion } from '@/api/composition';

/**
 * 区域草稿模型（visual-builder §6）——构建器画布与结构视图（StructureView）共用
 * 同一份树模型与保存/发布/丢弃/冲突处理，按区域各一实例（一个页面可承载多个区域，各自
 * 行树 + 乐观锁版本）。面板即组件实例（component-settings §3）：{id, shape, settings, enabled}
 * 一个设置值块，来源类型/装饰字段/卡片编排已随 v2 契约退役。
 *
 * 保存即逐操作持久化（save-per-op，Drupal Layout Builder 模型）：每次结构操作 PUT 整棵区域
 * 树；saveDraft 响应 {version, panels}（新版本号 + 按遍历序的真实面板ID）就地回填，版本链
 * 无需回读详情。409 冲突沿用 覆盖/刷新 对话框。雪花ID（面板ID）全程字符串以免 JS 精度丢失。
 */
export type PanelVisibility = 'all' | 'desktop' | 'mobile';

/** 面板视图模型（组件实例）：id 为 null = 新建尚未首存（保存响应回填真实ID）；key 仅供 v-for / 拖拽。 */
export interface PanelVM {
  key: string;
  id: string | null;
  shape: string;
  settings: Record<string, any>;
  enabled: boolean;
  visibility: PanelVisibility;
}

export interface ColumnVM {
  key: string;
  panels: PanelVM[];
}

export interface RowVM {
  key: string;
  id: string;
  layout: string;
  columns: ColumnVM[];
}

/** 持久化结构树（RegionLayout）：面板以ID引用。 */
export interface RegionLayoutTree {
  rows: Array<{ id: string; layout: string; columns: Array<{ panels: string[] }> }>;
}

export interface PanelDetail {
  id: string;
  shape: string;
  settings: Record<string, any> | null;
  enabled: boolean;
  visibility?: string | null;
}

/**
 * 区域详情（RegionService.RegionDetail；GET page-region / page-detail）。label/description 自
 * shell-polish 起由后端透传清单声明——旧响应缺失时按区域名回退，两侧独立部署安全。
 */
export interface RegionDetail {
  page: string;
  region: string;
  scope: string;
  overrideExists: boolean;
  version: number;
  published: RegionLayoutTree;
  draft: RegionLayoutTree | null;
  allowedShapes: string[];
  allowedRowLayouts: string[];
  maxRows: number | null;
  maxPanelsPerColumn: number | null;
  panels: PanelDetail[];
  sharedFrom: string | null;
  forked: boolean;
  label?: Record<string, string> | null;
  description?: string | null;
}

/** saveDraft 响应：新版本号 + 按遍历序的真实面板ID。 */
export interface SaveDraftResult {
  version: number;
  panels: Array<{ id: string; shape: string }>;
}

export interface UseRegionDraftOptions {
  page: string;
  region: string;
  /** 区域范围（visual-builder §9：'default' 页面默认 / 'channel:<id>' / 'variant:<surface>:<value>'）。 */
  scope?: string;
  /** 撤销栈新增一条时回调（BuilderPage 以此维护跨区域的全局撤销顺序，§6/D8）。 */
  onHistoryPush?: () => void;
}

/**
 * 面板设置一行摘要（结构视图 / 检查器共用）：优先 settings.title，其次首个非空字符串设置值；
 * 无可显示值返回空串（调用方隐藏该行）。启发式取值，无需回查组件设置结构。
 */
export const panelSettingsSummary = (panel: { settings?: Record<string, any> | null }): string => {
  const settings: Record<string, any> = panel.settings ?? {};
  const title = settings.title;
  if (typeof title === 'string' && title.trim() !== '') return title;
  for (const value of Object.values(settings)) {
    if (typeof value === 'string' && value.trim() !== '') return value;
  }
  return '';
};

// 客户端稳定 key（仅供 v-for / 拖拽）；行ID（持久化到树）用 UUID
let keySeq = 0;
const genKey = (): string => `k${(keySeq += 1)}`;
const genRowId = (): string => (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function' ? `row-${crypto.randomUUID()}` : `row-${(keySeq += 1)}`);

export function useRegionDraft(options: UseRegionDraftOptions) {
  const { page, region } = options;
  const scope = options.scope ?? 'default';
  // 实例按区域动态创建（异步加载后），不在 setup 上下文——用全局 i18n 而非 useI18n()
  const { t } = i18n.global;

  const loading = ref<boolean>(false);
  const saving = ref<boolean>(false);
  const detail = ref<RegionDetail>();
  const rows = ref<RowVM[]>([]);
  const hasDraft = ref<boolean>(false);
  const dirty = ref<boolean>(false);

  const allowedShapes = computed<string[]>(() => detail.value?.allowedShapes ?? []);
  const maxRows = computed<number | null>(() => detail.value?.maxRows ?? null);
  const maxPanelsPerColumn = computed<number | null>(() => detail.value?.maxPanelsPerColumn ?? null);
  const version = computed<number>(() => detail.value?.version ?? 0);
  // 覆盖行是否已存在（§9：false = 尚未创建，画布显示默认树复制种子，首次保存创建覆盖行）
  const overrideExists = computed<boolean>(() => detail.value?.overrideExists ?? true);
  // 共享区域的宿主页名（page-regions §15；自有区域为 null）。未分叉时本实例的 page 即宿主页
  const sharedFrom = computed<string | null>(() => detail.value?.sharedFrom ?? null);

  const buildRows = (d: RegionDetail): RowVM[] => {
    const tree: RegionLayoutTree = d.draft ?? d.published ?? { rows: [] };
    const panelsById: Record<string, PanelDetail> = {};
    (d.panels ?? []).forEach((p) => {
      panelsById[String(p.id)] = p;
    });
    return (tree.rows ?? []).map((row) => ({
      key: genKey(),
      id: row.id,
      layout: row.layout,
      columns: (row.columns ?? []).map((col) => ({
        key: genKey(),
        panels: (col.panels ?? [])
          .map((pid): PanelVM | null => {
            const p = panelsById[String(pid)];
            // 悬空引用（带外删除残留）：跳过，由健康检查另行提示
            if (p == null) return null;
            return {
              key: genKey(),
              id: String(p.id),
              shape: p.shape,
              settings: p.settings ?? {},
              enabled: p.enabled,
              visibility: (p.visibility ?? 'all') as PanelVisibility,
            };
          })
          .filter((p): p is PanelVM => p != null),
      })),
    }));
  };

  /** 以既得详情装载（页面聚合详情 page-detail 一次取回全部区域，P3——装载路径免逐区域回读）。 */
  const applyDetail = (d: RegionDetail): void => {
    detail.value = d;
    rows.value = buildRows(d);
    hasDraft.value = d.draft != null;
    dirty.value = false;
    // 树被服务器版本整体替换：撤销/重做栈随之失效（发布/丢弃/冲突刷新都经此路径，D8）
    clearHistory();
  };

  const fetchDetail = async (): Promise<void> => {
    loading.value = true;
    try {
      applyDetail(await queryRegion(page, region, scope));
    } finally {
      loading.value = false;
    }
  };

  const findPanel = (panelId: string): PanelVM | null => {
    for (const row of rows.value) {
      for (const col of row.columns) {
        const panel = col.panels.find((p) => p.id === panelId);
        if (panel) return panel;
      }
    }
    return null;
  };

  const findRow = (rowId: string): RowVM | null => rows.value.find((r) => r.id === rowId) ?? null;

  // ---- 结构操作（操作后由调用方决定何时 save） --------------------------------------------------

  /** 新建空行（默认追加到区域尾部；{@code index} 指定插入下标——调色板布局卡片按落点插入）。 */
  const addRow = (layoutKey: string, columns: number, index?: number): boolean => {
    if (maxRows.value != null && rows.value.length >= maxRows.value) {
      ElMessage.warning(t('region.maxRows', { max: maxRows.value }));
      return false;
    }
    const insert = index == null ? rows.value.length : Math.min(Math.max(index, 0), rows.value.length);
    rows.value.splice(insert, 0, {
      key: genKey(),
      id: genRowId(),
      layout: layoutKey,
      columns: Array.from({ length: columns }, () => ({ key: genKey(), panels: [] })),
    });
    dirty.value = true;
    return true;
  };

  const changeLayout = (row: RowVM, newKey: string, newCols: number): void => {
    if (newKey === row.layout) return;
    const oldCols = row.columns.length;
    if (newCols < oldCols) {
      const overflow = row.columns.slice(newCols).flatMap((c) => c.panels);
      const keep = row.columns.slice(0, newCols);
      const lastKeep = keep[newCols - 1];
      if (overflow.length > 0 && lastKeep != null) {
        lastKeep.panels.push(...overflow);
        ElMessage.warning(t('region.reflow.merged', { count: overflow.length }));
      }
      row.columns = keep;
    } else if (newCols > oldCols) {
      for (let i = oldCols; i < newCols; i += 1) row.columns.push({ key: genKey(), panels: [] });
    }
    row.layout = newKey;
    dirty.value = true;
  };

  const removeRow = (index: number): void => {
    rows.value.splice(index, 1);
    dirty.value = true;
  };

  /**
   * 新建面板（默认来源，追加到列尾；{@code index} 指定插入下标——调色板幽灵拖拽按插入线落点，P3）。
   */
  const addPanel = (column: ColumnVM, shape: string, index?: number): PanelVM | null => {
    if (maxPanelsPerColumn.value != null && column.panels.length >= maxPanelsPerColumn.value) {
      ElMessage.warning(t('region.maxPanels', { max: maxPanelsPerColumn.value }));
      return null;
    }
    // 空设置实例化（component-settings §3 / Phase A savePanel 设计）：设置留空，渲染期以组件
    // 声明的缺省值补齐；PanelForm 打开时按同一缺省值回退展示
    const panel: PanelVM = {
      key: genKey(),
      id: null,
      shape,
      settings: {},
      enabled: true,
      visibility: 'all',
    };
    const insert = index == null ? column.panels.length : Math.min(Math.max(index, 0), column.panels.length);
    column.panels.splice(insert, 0, panel);
    dirty.value = true;
    return panel;
  };

  const removePanel = (column: ColumnVM, index: number): void => {
    column.panels.splice(index, 1);
    dirty.value = true;
  };

  /**
   * 复制面板（检查器/结构视图「复制」）：设置深拷贝、原位其后插入；id 置空由保存响应回填真实ID
   * （与新增同一契约）。复制值已过保存期校验，无需再开配置表单——落点即逐操作持久化。
   */
  const duplicatePanel = (column: ColumnVM, index: number): PanelVM | null => {
    if (maxPanelsPerColumn.value != null && column.panels.length >= maxPanelsPerColumn.value) {
      ElMessage.warning(t('region.maxPanels', { max: maxPanelsPerColumn.value }));
      return null;
    }
    const src = column.panels[index];
    if (src == null) return null;
    const panel: PanelVM = {
      key: genKey(),
      id: null,
      shape: src.shape,
      settings: JSON.parse(JSON.stringify(src.settings ?? {})),
      enabled: src.enabled,
      visibility: src.visibility ?? 'all',
    };
    column.panels.splice(index + 1, 0, panel);
    dirty.value = true;
    return panel;
  };

  /**
   * 面板移动（画布拖拽 P2，§7.3）：from/to 均为 { row: 行ID, col: 列下标, index: 面板下标 }；
   * to.index 是「含源面板在内」的插入下标（同列下移时自动 -1）。返回是否实际变更（原位落点
   * 为 no-op，调用方据此跳过保存）。
   */
  const movePanel = (from: { row: string; col: number; index: number }, to: { row: string; col: number; index: number }): boolean => {
    const fromCol = findRow(from.row)?.columns?.[from.col];
    const toCol = findRow(to.row)?.columns?.[to.col];
    if (fromCol == null || toCol == null) return false;
    const panel = fromCol.panels[from.index];
    if (panel == null) return false;
    let insert = Math.min(Math.max(to.index, 0), toCol.panels.length);
    if (fromCol === toCol) {
      if (insert > from.index) insert -= 1;
      if (insert === from.index) return false;
    }
    fromCol.panels.splice(from.index, 1);
    toCol.panels.splice(insert, 0, panel);
    dirty.value = true;
    return true;
  };

  /** 行重排（画布拖拽 P2）：to 为「含源行在内」的插入下标。返回是否实际变更。 */
  const moveRow = (from: number, to: number): boolean => {
    const row = rows.value[from];
    if (row == null) return false;
    let insert = Math.min(Math.max(to, 0), rows.value.length);
    if (insert > from) insert -= 1;
    if (insert === from) return false;
    rows.value.splice(from, 1);
    rows.value.splice(insert, 0, row);
    dirty.value = true;
    return true;
  };

  const markDirty = (): void => {
    dirty.value = true;
  };

  // ---- 撤销/重做（visual-builder §6 / D8）---------------------------------------------------------
  // save-per-op 之上的控制台侧逆操作栈：每次成功保存记录 {保存前, 保存后} 的树快照（快照还原
  // 本身就是一次区域树变换，也是一次普通的 save-per-op PUT）。发布/丢弃/冲突刷新时清空
  // （fetchDetail 统一清理）；快照不含客户端 key（还原时重新生成）。

  const undoStack = ref<{ before: string; after: string }[]>([]);
  const redoStack = ref<{ before: string; after: string }[]>([]);
  let lastSaved = '';

  const canUndo = computed<boolean>(() => undoStack.value.length > 0);
  const canRedo = computed<boolean>(() => redoStack.value.length > 0);

  /** 树的持久化形态序列化（无客户端 key；与 buildPayload 的行结构一致）。 */
  const serializeRows = (): string =>
    JSON.stringify(
      rows.value.map((r) => ({
        id: r.id,
        layout: r.layout,
        columns: r.columns.map((c) => ({
          panels: c.panels.map((p) => ({
            id: p.id,
            shape: p.shape,
            settings: p.settings,
            enabled: p.enabled,
            visibility: p.visibility ?? 'all',
          })),
        })),
      })),
    );

  const hydrateRows = (snapshot: string): RowVM[] =>
    JSON.parse(snapshot).map((row: any) => ({
      key: genKey(),
      id: row.id,
      layout: row.layout,
      columns: (row.columns ?? []).map((col: any) => ({
        key: genKey(),
        panels: (col.panels ?? []).map((p: any) => ({ key: genKey(), ...p })),
      })),
    }));

  const clearHistory = (): void => {
    undoStack.value = [];
    redoStack.value = [];
    lastSaved = serializeRows();
  };

  /** 保存成功后记账：树有实际变更才入栈（record=false 用于 undo/redo 自身的保存）。 */
  const recordHistory = (record: boolean): void => {
    const now = serializeRows();
    if (record && now !== lastSaved) {
      undoStack.value = [...undoStack.value, { before: lastSaved, after: now }];
      redoStack.value = [];
      options.onHistoryPush?.();
    }
    lastSaved = now;
  };

  // ---- 持久化 ------------------------------------------------------------------------------------

  const buildPayload = () => ({
    page,
    region,
    scope,
    // 乐观锁：详情读取（或上次保存响应）时的版本号（无区域行为 0）；后端不符 → 409
    version: version.value,
    rows: rows.value.map((r) => ({
      id: r.id,
      layout: r.layout,
      columns: r.columns.map((c) => ({
        panels: c.panels.map((p) => ({
          id: p.id,
          shape: p.shape,
          settings: p.settings,
          enabled: p.enabled,
          visibility: p.visibility ?? 'all',
        })),
      })),
    })),
  });

  /**
   * 保存响应 {version, panels} 就地回填（visual-builder §6 版本链）：新版本号 + 按遍历序
   * （行 → 列 → 面板）的真实面板ID，构建器无需回读详情即可串联下一次操作。
   */
  const applySaveResult = (result: SaveDraftResult | null | undefined): void => {
    if (detail.value != null && typeof result?.version === 'number') {
      detail.value.version = result.version;
      // 保存成功即意味着该范围行已存在（创建覆盖的首次保存在服务端建行，§9）
      detail.value.overrideExists = true;
    }
    const refs = Array.isArray(result?.panels) ? result.panels : [];
    let i = 0;
    for (const row of rows.value) {
      for (const col of row.columns) {
        for (const panel of col.panels) {
          const ref = refs[i];
          i += 1;
          if (ref?.id != null) panel.id = String(ref.id);
        }
      }
    }
    hasDraft.value = true;
    dirty.value = false;
  };

  // 409 乐观锁冲突：覆盖（以服务器当前版本重试一次，丢弃对方改动）或 刷新（丢弃我的改动，加载最新）
  const handleConflict = async (history: boolean): Promise<boolean> => {
    try {
      await ElMessageBox.confirm(t('region.conflict.body'), t('region.confirm.title'), {
        confirmButtonText: t('region.conflict.overwrite'),
        cancelButtonText: t('region.conflict.reload'),
        distinguishCancelAndClose: true,
        type: 'warning',
      });
    } catch (action) {
      if (action === 'cancel') {
        await fetchDetail();
        ElMessage.info(t('region.conflict.reloaded'));
      }
      return false;
    }
    try {
      const latest = await queryRegion(page, region, scope);
      const result = await saveRegionDraft({ ...buildPayload(), version: latest?.version ?? 0 });
      applySaveResult(result);
      recordHistory(history);
      ElMessage.success(t('success'));
      return true;
    } catch (e: any) {
      // 重试仍失败（又一次并发修改或校验错误）：提示并加载最新
      if (e?.response?.data != null) {
        handleError(e.response.data);
      }
      await fetchDetail();
      return false;
    }
  };

  /**
   * 保存草稿（save-per-op）：成功后以响应回填版本与面板ID，并记录撤销快照（history=false
   * 供 undo/redo 自身使用）；409 → 覆盖/刷新对话框。
   */
  const save = async (opts: { history?: boolean } = {}): Promise<boolean> => {
    const history = opts.history !== false;
    saving.value = true;
    try {
      const result = await saveRegionDraft(buildPayload());
      applySaveResult(result);
      recordHistory(history);
      return true;
    } catch (e: any) {
      if (e?.response?.status === 409) {
        return await handleConflict(history);
      }
      if (e?.response?.data != null) {
        handleError(e.response.data);
      }
      return false;
    } finally {
      saving.value = false;
    }
  };

  /** 撤销：还原上一次保存前的树并立即持久化（又一次 save-per-op；D8）。 */
  const undo = async (): Promise<boolean> => {
    const entry = undoStack.value[undoStack.value.length - 1];
    if (entry == null || saving.value) return false;
    undoStack.value = undoStack.value.slice(0, -1);
    rows.value = hydrateRows(entry.before);
    dirty.value = true;
    if (await save({ history: false })) {
      redoStack.value = [...redoStack.value, entry];
      return true;
    }
    return false;
  };

  /** 重做：还原被撤销的树并立即持久化。 */
  const redo = async (): Promise<boolean> => {
    const entry = redoStack.value[redoStack.value.length - 1];
    if (entry == null || saving.value) return false;
    redoStack.value = redoStack.value.slice(0, -1);
    rows.value = hydrateRows(entry.after);
    dirty.value = true;
    if (await save({ history: false })) {
      undoStack.value = [...undoStack.value, entry];
      return true;
    }
    return false;
  };

  /**
   * 发布：返回是否成功（调用方汇总提示——多区域批量发布须点名失败区域，单区域成功由调用方 toast）。
   * 失败时全局错误处理已提示（409 → 刷新到最新版本后可重试），无论成败都回读详情以重同步版本链。
   */
  const publish = async (): Promise<boolean> => {
    let ok = false;
    try {
      await publishRegion({ page, region, scope, version: version.value });
      ok = true;
    } catch {
      // 全局错误处理已提示
    }
    try {
      await fetchDetail();
    } catch {
      // 回读失败已提示；版本链留待下次操作重同步
    }
    return ok;
  };

  /** 丢弃草稿：语义同 publish（返回成败，调用方汇总提示）。 */
  const discard = async (): Promise<boolean> => {
    let ok = false;
    try {
      await discardRegion({ page, region, scope, version: version.value });
      ok = true;
    } catch {
      // 全局错误处理已提示
    }
    try {
      await fetchDetail();
    } catch {
      // 回读失败已提示
    }
    return ok;
  };

  return {
    page,
    region,
    scope,
    overrideExists,
    sharedFrom,
    loading,
    saving,
    detail,
    rows,
    hasDraft,
    dirty,
    version,
    allowedShapes,
    maxRows,
    maxPanelsPerColumn,
    applyDetail,
    fetchDetail,
    findPanel,
    findRow,
    addRow,
    changeLayout,
    removeRow,
    addPanel,
    removePanel,
    duplicatePanel,
    movePanel,
    moveRow,
    markDirty,
    buildPayload,
    save,
    publish,
    discard,
    canUndo,
    canRedo,
    undo,
    redo,
    clearHistory,
  };
}

export type RegionDraft = ReturnType<typeof useRegionDraft>;

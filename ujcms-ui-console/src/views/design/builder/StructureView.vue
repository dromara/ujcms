<script setup lang="ts">
import { computed, nextTick, ref, watch, type PropType } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Delete, Rank } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import draggable from 'vuedraggable';
import { perm } from '@/stores/useCurrentUser';
import { panelSettingsSummary } from '@/composables/useRegionDraft';
import { allowedLayoutsOf, layoutLabel, regionLabelOf, shapeLabelOf } from './builderLabels';
import { columnFull, firstRowLayout } from './constraints';
import LayoutThumb from './LayoutThumb.vue';
import type { BridgeSelection, ColumnVM, Drafts, PanelVM, PlacedPayload, RegionDraftState, RowLayout, RowVM } from './types';

/**
 * 结构视图（visual-builder §10 / D7）——原独立区域编辑器折叠为构建器内的切换视图：页面全部区域各一节，
 * 行 × 列 × 面板的二维拖拽编排（行纵向重排、行布局改列数、面板跨列拖动、添加/删除行与面板）。
 * 与画布共享同一份 useRegionDraft 草稿模型，切换视图不丢编辑；亦是桥超时的降级路径。
 * 结构操作与画布同为逐操作持久化（P9/D14：每手势经 commit 上抛，BuilderPage 微任务合并 +
 * 区域内串行后保存并触发区域级软刷新）；新增面板亦与画布同走延迟提交（placed 上抛，
 * PanelForm 确定首存、取消本地回滚）。发布/丢弃按区域操作。
 * 选择感知（canvas-chrome D29）：与画布/检查器共用同一 selection 模型——点击节点标签/节点体即选中（检查器随之
 * 显示），选中节点高亮并滚入视野；「编辑」仍开对话框；删除选中节点即清选择。本视图不持本地选择态，只按 prop 高亮
 * （检查器脏值守卫取消时 BuilderPage 回滚 selection，高亮随之回滚）。
 */
defineOptions({
  name: 'StructureView',
});
const props = defineProps({
  // Record<regionName, useRegionDraft 实例（reactive 解包）>
  drafts: { type: Object as PropType<Drafts>, required: true },
  // 区域渲染顺序（声明序）
  regionOrder: { type: Array as PropType<string[]>, default: () => [] },
  rowLayouts: { type: Array as PropType<RowLayout[]>, default: () => [] },
  shapeLabelMap: { type: Object as PropType<Record<string, string>>, default: () => ({}) },
  // 逐操作提交终点（P9 §3.1，BuilderPage.commitRegion：微任务合并 + 区域内串行 + 成功后区域刷新）
  commit: { type: Function as PropType<(draft: RegionDraftState) => void>, required: true },
  // 当前选择（画布 / 检查器 / 本视图三源同一模型）
  selection: { type: Object as PropType<BridgeSelection | null>, default: null },
  // 视图可见（BuilderPage v-show）：可见且有选择时把选中节点滚入视野（隐藏时 scrollIntoView 无效）
  active: { type: Boolean, default: false },
});
const emit = defineEmits<{
  // 节点点选 → BuilderPage 设 selection（检查器随之切换；回到画布时选择环 + 滚动跟随）；null = 清选择
  select: [selection: BridgeSelection | null];
  // 面板点击 → BuilderPage 打开 PanelForm（保存后 save-per-op + 区域刷新）
  editPanel: [payload: { region: string; panel: PanelVM }];
  // 新增面板（添加面板下拉 / 调色板拖入）：{ region, panel, rollback }——BuilderPage 复用画布
  // onPlaced 登记（pendingPlacement + PanelForm，P9 §3.2：确定首存，取消纯本地回滚）
  placed: [payload: PlacedPayload];
  // 发布/丢弃后上抛（BuilderPage 触发桥刷新）
  changed: [region: string];
}>();

const { t } = useI18n();

const cannotEdit = computed<boolean>(() => perm('pageRegion:update'));
const layoutMap = computed<Record<string, RowLayout>>(() => Object.fromEntries(props.rowLayouts.map((l) => [l.key, l])));
const shapeLabel = (shape: string): string => shapeLabelOf(props.shapeLabelMap, shape);
const allowedLayouts = (draft: RegionDraftState): RowLayout[] => allowedLayoutsOf(draft, props.rowLayouts);

// ---- 选择感知（D29） ------------------------------------------------------------------------------

const rootRef = ref<HTMLElement>();
const isSelected = (kind: BridgeSelection['kind'], id: string | null): boolean => id != null && props.selection?.kind === kind && props.selection.id === id;
// 未首存的新面板无 id（PanelForm 开着），不可选
const selectNode = (kind: 'panel' | 'row' | 'region', id: string | null, region: string): void => {
  if (id == null) return;
  emit('select', { kind, id, region });
};
// 节点体点击：控件 / 手柄 / 标签内的点击不选（按钮各有动作，手柄归 SortableJS，标签按钮自行 selectNode）；
// 嵌套节点（区域 ⊃ 行 ⊃ 面板）只由最近的节点根处理
const CONTROL_SELECTOR = 'button, a, input, select, textarea, [role="button"], .el-select, .el-dropdown, .el-tag, .panel-handle, .row-handle';
const onNodeClick = (e: MouseEvent, kind: 'panel' | 'row' | 'region', id: string | null, region: string): void => {
  const target = e.target instanceof Element ? e.target : null;
  if (target == null || target.closest(CONTROL_SELECTOR) != null || target.closest('[data-structure-node]') !== e.currentTarget) return;
  selectNode(kind, id, region);
};
const cssEscape = (v: string): string => (typeof CSS !== 'undefined' && CSS.escape ? CSS.escape(v) : v);
watch(
  () => [props.active, props.selection?.kind, props.selection?.id] as const,
  async ([active]) => {
    const sel = props.selection;
    if (!active || sel == null) return;
    await nextTick();
    rootRef.value?.querySelector(`[data-structure-node="${cssEscape(`${sel.kind}:${sel.id}`)}"]`)?.scrollIntoView({ block: 'nearest' });
  },
  { flush: 'post' },
);

// 行的列宽（CSS grid 模板列）：用行布局的 widths 比例；与列数不一致时退化为等分
// （columns 防御性可选链：调色板布局克隆占位若意外存活到渲染没有 columns，兜底不崩）
const gridStyle = (row: RowVM): Record<string, string> => {
  const widths: number[] = layoutMap.value[row.layout]?.widths ?? [];
  const cols = row.columns?.length ?? 0;
  const template = widths.length === cols ? widths.map((w) => `${w}fr`).join(' ') : `repeat(${cols}, 1fr)`;
  return { 'grid-template-columns': template };
};

const handleAddRow = (draft: RegionDraftState, layoutKey: string): void => {
  if (draft.addRow(layoutKey, layoutMap.value[layoutKey]?.columns ?? 1)) {
    props.commit(draft);
  }
};

const handleChangeLayout = (draft: RegionDraftState, row: RowVM, newKey: string): void => {
  draft.changeLayout(row, newKey, layoutMap.value[newKey]?.columns ?? 1);
  props.commit(draft);
};

// 删除选中节点（或含选中面板的行）时同步清选择（画布 / 检查器路径已清，结构视图此前未清）
const handleRemoveRow = (draft: RegionDraftState, index: number): void => {
  const row = draft.rows[index];
  const sel = props.selection;
  if (row != null && sel != null && (isSelected('row', row.id) || (sel.kind === 'panel' && row.columns.some((c) => c.panels.some((p) => p.id === sel.id))))) {
    emit('select', null);
  }
  draft.removeRow(index);
  props.commit(draft);
};

const handleRemovePanel = (draft: RegionDraftState, column: ColumnVM, index: number): void => {
  if (isSelected('panel', column.panels[index]?.id ?? null)) emit('select', null);
  draft.removePanel(column, index);
  props.commit(draft);
};

// 复制面板：设置深拷贝原位其后插入（不开配置表单——复制值已过保存期校验），落点即逐操作提交
const handleDuplicatePanel = (draft: RegionDraftState, column: ColumnVM, index: number): void => {
  if (draft.duplicatePanel(column, index) != null) {
    props.commit(draft);
  }
};

// 行重排 / 列内重排 / 跨列移动（vuedraggable :list/v-model 已就地改树）：标脏并提交——跨列一次
// 手势触发源列 removed + 目标列 added 两个 @change，由 commit 微任务合并为一次 PUT（P9 §3.1）
const handleTreeChange = (draft: RegionDraftState): void => {
  draft.markDirty();
  props.commit(draft);
};

// 新增面板统一走画布的延迟提交终点（P9 §3.2）：本地构建 + 回滚闭包上抛 placed——确定即首存，
// 取消纯本地回滚（零保存），与 useBuilderDrag.placeShape 同一契约形态
const handleAddPanel = (draft: RegionDraftState, column: ColumnVM, shape: string, index?: number): void => {
  const wasDirty: boolean = draft.dirty;
  const panel = draft.addPanel(column, shape, index);
  if (panel == null) return;
  emit('placed', {
    region: draft.region,
    panel,
    rollback: (): void => {
      const idx = column.panels.indexOf(panel);
      if (idx >= 0) draft.removePanel(column, idx);
      draft.dirty = wasDirty;
    },
  });
};

// ---- 调色板拖入（P7 §3.2/§3.3）：列与空区域接受 palette-shapes 克隆组 ----------------------------

// 列内 change 统一入口：调色板克隆占位（__paletteShape 标记）即刻按落点下标剔除、换成真实面板并
// 打开 PanelForm（与 添加面板 下拉同终点、延迟提交；占位对象绝不能存活到保存载荷）；其余变更
// （列内重排/跨列移动）标脏并逐操作提交
const onColumnChange = (draft: RegionDraftState, col: ColumnVM, evt: any): void => {
  const added = evt.added;
  if (added?.element?.__paletteShape != null) {
    col.panels.splice(added.newIndex, 1);
    handleAddPanel(draft, col, added.element.__paletteShape, added.newIndex);
    return;
  }
  handleTreeChange(draft);
};

// 行列表 change 统一入口（D15）：调色板布局卡片克隆占位（__paletteLayout 标记）同步剔除（存活到
// 渲染会因缺 columns 崩掉 gridStyle）并按落点下标插入空行——空行无需配置，不走延迟提交，落点即
// 逐操作提交；其余变更（行重排）走原路径
const onRowsChange = (draft: RegionDraftState, evt: any): void => {
  const added = evt.added;
  if (added?.element?.__paletteLayout != null) {
    draft.rows.splice(added.newIndex, 1);
    const key = added.element.__paletteLayout;
    if (draft.addRow(key, Math.max(1, layoutMap.value[key]?.columns ?? 1), added.newIndex)) {
      props.commit(draft);
    }
    return;
  }
  handleTreeChange(draft);
};

// 列内/跨列面板拖拽的 :move 校验（§7.4；SortableJS 的 onMove 取自拖拽「起点」列表的实例——面板的起点
// 就是某一列，故绑在每个列上即覆盖区域内全部面板移动；调色板克隆拖入仍由源侧 structureMove 校验）：
// 目标列经 evt.to 的 data-builder-row/col 在草稿树定位，maxPanelsPerColumn 满则拒放（同列内重排豁免）。
// 此前结构视图无任何 :move，跨列拖拽可无视单列上限，仅靠服务端复验兜底。
const onPanelMove = (draft: RegionDraftState, evt: any): boolean => {
  const toEl: HTMLElement | null = evt.to instanceof HTMLElement ? evt.to : null;
  const rowId = toEl?.dataset?.builderRow;
  const col = Number(toEl?.dataset?.builderCol);
  // 非列目标（组接受规则下不会出现）：放行，交给 Sortable 的还原分支
  if (toEl == null || rowId == null || !Number.isInteger(col)) return true;
  return columnFull(draft, rowId, col, evt.from === toEl).ok;
};

// 空区域落点的傀儡列表（仅作 SortableJS 放置目标，处理后即清空；每区域一份避免串扰）
const emptyDropLists: Record<string, any[]> = {};
const emptyDropListOf = (regionName: string): any[] => (emptyDropLists[regionName] ??= []);

// 空区域（无行）落点：物化首行（full 优先，其次首个允许布局——与画布 buildPanelAt 同一规则，
// §7.2 对齐）+ 面板进第 0 列，延迟提交上抛（取消时连带撤掉物化的首行——placeShape 同规，
// 防御性地仅撤仍为空的行）
const onEmptyRegionChange = (draft: RegionDraftState, evt: any): void => {
  const shape = evt.added?.element?.__paletteShape;
  const layoutFromPalette = evt.added?.element?.__paletteLayout;
  emptyDropListOf(draft.region).length = 0;
  // 布局卡片落空区域（D15）：直接建首行——空行无需配置，不走延迟提交（无 placed/回滚）
  if (layoutFromPalette != null) {
    if (draft.addRow(layoutFromPalette, Math.max(1, layoutMap.value[layoutFromPalette]?.columns ?? 1))) {
      props.commit(draft);
    }
    return;
  }
  if (shape == null) return;
  const wasDirty: boolean = draft.dirty;
  const layoutKey = firstRowLayout(draft);
  if (!draft.addRow(layoutKey, Math.max(1, layoutMap.value[layoutKey]?.columns ?? 1))) return;
  const column = draft.rows[draft.rows.length - 1]?.columns[0];
  if (column == null) return;
  const panel = draft.addPanel(column, shape);
  if (panel == null) {
    draft.removeRow(draft.rows.length - 1);
    draft.dirty = wasDirty;
    return;
  }
  emit('placed', {
    region: draft.region,
    panel,
    rollback: (): void => {
      const idx = column.panels.indexOf(panel);
      if (idx >= 0) draft.removePanel(column, idx);
      const last = draft.rows[draft.rows.length - 1];
      if (last != null && last.columns.every((c) => c.panels.length === 0)) {
        draft.removeRow(draft.rows.length - 1);
      }
      draft.dirty = wasDirty;
    },
  });
};

const handlePublish = async (draft: RegionDraftState): Promise<void> => {
  try {
    await ElMessageBox.confirm(t('region.publish.confirm'), t('region.confirm.title'), { type: 'warning' });
  } catch {
    return; // 取消
  }
  if (await draft.publish()) ElMessage.success(t('success'));
  emit('changed', draft.region);
};

const handleDiscard = async (draft: RegionDraftState): Promise<void> => {
  try {
    await ElMessageBox.confirm(t('region.discard.confirm'), t('region.confirm.title'), { type: 'warning' });
  } catch {
    return; // 取消
  }
  if (await draft.discard()) ElMessage.success(t('success'));
  emit('changed', draft.region);
};
</script>

<template>
  <div ref="rootRef" class="structure space-y-6">
    <el-empty v-if="regionOrder.length <= 0" :description="$t('builder.noRegions')" />
    <!-- 节点状态（悬停/选中）走 scoped CSS 的 EP 变量：节点根与标签钮上不叠 border-*/bg-*/text-*/hover:* 工具类
         （Tailwind important 会压过 scoped 的边框/背景/文字色） -->
    <div
      v-for="regionName in regionOrder"
      :key="regionName"
      class="structure-node structure-node-region rounded-lg border p-4"
      :class="{ 'structure-node-selected': isSelected('region', regionName) }"
      :data-structure-node="`region:${regionName}`"
      @click="(e: MouseEvent) => onNodeClick(e, 'region', regionName, regionName)"
    >
      <template v-if="drafts[regionName] != null">
        <!-- 区域头：显示名 / 区域名（点选）/ 状态 / 区域级操作 -->
        <div class="mb-3 flex flex-wrap items-center gap-x-2 gap-y-1">
          <button
            type="button"
            class="structure-node-title flex min-w-0 items-center gap-2 rounded px-0.5 text-left"
            :aria-pressed="isSelected('region', regionName)"
            :title="$t('builder.structure.selectTip')"
            @click="() => selectNode('region', regionName, regionName)"
          >
            <span class="text-sm font-semibold">{{ regionLabelOf(drafts[regionName], regionName) }}</span>
            <span class="font-mono text-xs text-gray-placeholder">{{ regionName }}</span>
          </button>
          <span class="inline-flex items-center gap-1.5 text-xs text-gray-regular">
            <span class="h-2 w-2 rounded-full" :class="drafts[regionName]?.hasDraft ? 'bg-warning' : 'bg-success'"></span>
            {{ drafts[regionName]?.hasDraft ? $t('region.status.draft') : $t('region.status.published') }}
          </span>
          <!-- 未保存 = 失败态指示（P9 §3.3：保存失败后本地树与服务端分叉；保存中不显示，
               下次成功操作整树自愈；路由离开守卫为兜底） -->
          <el-tag v-if="drafts[regionName]?.dirty && !drafts[regionName]?.saving" size="small" type="danger">{{ $t('builder.unsaved') }}</el-tag>
          <div class="structure-actions ml-auto flex items-center gap-2">
            <el-button size="small" type="primary" plain :disabled="!drafts[regionName]?.hasDraft || cannotEdit" @click="() => handlePublish(drafts[regionName]!)">
              {{ $t('region.op.publish') }}
            </el-button>
            <el-button size="small" :disabled="!drafts[regionName]?.hasDraft || cannotEdit" @click="() => handleDiscard(drafts[regionName]!)">
              {{ $t('region.op.discard') }}
            </el-button>
          </div>
        </div>

        <!-- 行画布：可拖拽重排。:list 就地重排（vuedraggable；数组引用不变，避免对 props 赋值）；显式组名保持行只在
             区域内排序（pull:false 不外流、sort 照旧），put 仅接受调色板布局卡片克隆组（D15）——
             组名匹配即隔离：Shape 卡片（palette-shapes）进不了行列表，布局卡片进不了列；
             data-builder-region 供调色板源侧 :move 走 allowedRowLayouts + maxRows 口径校验 -->
        <draggable
          :list="drafts[regionName]!.rows"
          item-key="key"
          handle=".row-handle"
          :animation="150"
          :group="{ name: 'region-rows-' + regionName, pull: false, put: ['palette-rows'] }"
          class="space-y-4"
          :data-builder-region="regionName"
          @change="(evt: any) => onRowsChange(drafts[regionName]!, evt)"
        >
          <template #item="{ element: row, index: rowIndex }">
            <div
              class="structure-node structure-node-row rounded-md border p-3"
              :class="{ 'structure-node-selected': isSelected('row', row.id) }"
              :data-structure-node="`row:${row.id}`"
              @click="(e: MouseEvent) => onNodeClick(e, 'row', row.id, regionName)"
            >
              <div class="mb-3 flex items-center gap-2">
                <el-icon class="row-handle cursor-move text-gray-placeholder hover:text-gray-regular"><rank /></el-icon>
                <button
                  type="button"
                  class="structure-node-title rounded px-0.5 text-xs font-medium"
                  :aria-pressed="isSelected('row', row.id)"
                  :title="$t('builder.structure.selectTip')"
                  @click="() => selectNode('row', row.id, regionName)"
                >
                  {{ $t('region.row') }} {{ rowIndex + 1 }}
                </button>
                <el-select
                  :model-value="row.layout"
                  size="small"
                  class="w-44"
                  :disabled="cannotEdit"
                  @update:model-value="(val: string) => handleChangeLayout(drafts[regionName]!, row, val)"
                >
                  <el-option v-for="l in allowedLayouts(drafts[regionName]!)" :key="l.key" :label="layoutLabel(l)" :value="l.key">
                    <div class="flex items-center gap-2">
                      <layout-thumb :widths="l.widths" class="h-4 w-7 rounded-sm border border-gray-100 bg-gray-50" />
                      <span>{{ layoutLabel(l) }}</span>
                    </div>
                  </el-option>
                </el-select>
                <el-popconfirm :title="$t('region.row.delete.confirm')" @confirm="() => handleRemoveRow(drafts[regionName]!, rowIndex)">
                  <template #reference>
                    <el-button :icon="Delete" size="small" text type="danger" class="ml-auto" :disabled="cannotEdit">{{ $t('region.row.delete') }}</el-button>
                  </template>
                </el-popconfirm>
              </div>

              <div class="grid gap-3" :style="gridStyle(row)">
                <div v-for="(col, colIndex) in row.columns" :key="col.key" class="region-col rounded-md border border-dashed border-gray-300 bg-white p-2">
                  <div class="mb-1 text-xs text-gray-placeholder">{{ $t('region.column') }} {{ Number(colIndex) + 1 }}</div>
                  <!-- put 数组显式列出本组名以保留跨列移动（数组会替换隐式同名接受）；追加接受
                       调色板克隆组（P7）；data-builder-* 供源侧 :move（调色板克隆 / 本列面板移动）在草稿树定位目标列 -->
                  <draggable
                    v-model="col.panels"
                    :group="{ name: 'region-panels-' + regionName, put: ['palette-shapes', 'region-panels-' + regionName] }"
                    item-key="key"
                    :animation="150"
                    handle=".panel-handle"
                    class="min-h-[44px] space-y-2"
                    :data-builder-region="regionName"
                    :data-builder-row="row.id"
                    :data-builder-col="colIndex"
                    :move="(evt: any) => onPanelMove(drafts[regionName]!, evt)"
                    @change="(evt: any) => onColumnChange(drafts[regionName]!, col, evt)"
                  >
                    <template #item="{ element: panel, index: panelIndex }">
                      <div
                        class="structure-node structure-node-panel rounded-md border px-2 py-1.5"
                        :class="{ 'structure-node-selected': isSelected('panel', panel.id), 'opacity-60': panel.enabled === false }"
                        :data-structure-node="`panel:${panel.id ?? panel.key}`"
                        @click="(e: MouseEvent) => onNodeClick(e, 'panel', panel.id, regionName)"
                      >
                        <div class="flex items-center gap-1">
                          <el-icon class="panel-handle cursor-move text-gray-placeholder"><rank /></el-icon>
                          <button
                            type="button"
                            class="structure-node-title min-w-0 flex-1 truncate text-left text-sm font-medium"
                            :disabled="panel.id == null"
                            :aria-pressed="isSelected('panel', panel.id)"
                            :title="$t('builder.structure.selectTip')"
                            @click="() => selectNode('panel', panel.id, regionName)"
                          >
                            {{ shapeLabel(panel.shape) }}
                          </button>
                          <el-tag v-if="panel.enabled === false" size="small" type="info">{{ $t('placement.status.disabled') }}</el-tag>
                          <el-tag v-if="panel.visibility === 'desktop' || panel.visibility === 'mobile'" size="small" type="warning">
                            {{ $t(`placement.visibility.${panel.visibility}`) }}
                          </el-tag>
                          <el-button size="small" link type="primary" :disabled="cannotEdit" @click="() => emit('editPanel', { region: regionName, panel })">
                            {{ $t('edit') }}
                          </el-button>
                          <el-button size="small" link :disabled="cannotEdit" @click="() => handleDuplicatePanel(drafts[regionName]!, col, panelIndex)">
                            {{ $t('region.panel.duplicate') }}
                          </el-button>
                          <el-button size="small" link type="danger" :disabled="cannotEdit" @click="() => handleRemovePanel(drafts[regionName]!, col, panelIndex)">
                            {{ $t('delete') }}
                          </el-button>
                        </div>
                        <div v-if="panelSettingsSummary(panel) !== ''" class="truncate pl-5 text-xs text-gray-secondary">{{ panelSettingsSummary(panel) }}</div>
                      </div>
                    </template>
                  </draggable>
                  <!-- 添加面板：从 allowedShapes 选择 Shape → 创建面板（投放）并打开配置 -->
                  <el-dropdown class="mt-2 block" :disabled="cannotEdit" @command="(shape: string) => handleAddPanel(drafts[regionName]!, col, shape)">
                    <el-button size="small" text :icon="Plus" :disabled="cannotEdit">{{ $t('region.panel.add') }}</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item v-for="shape in drafts[regionName]!.allowedShapes" :key="shape" :command="shape">
                          <span>{{ shapeLabel(shape) }}</span>
                          <span class="ml-2 font-mono text-xs text-gray-placeholder">{{ shape }}</span>
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </div>
            </div>
          </template>
        </draggable>

        <!-- 空区域：调色板落点（P7 §3.3）——整块即投放区，放下物化首行 + 面板；空态插画走 footer
             插槽（draggable 根的直接子元素会被 Sortable 视作条目，filter 把它排除在拖拽外）；
             data-builder-empty-region 供源侧 :move 走 maxRows 口径校验 -->
        <draggable
          v-if="drafts[regionName]!.rows.length <= 0"
          :list="emptyDropListOf(regionName)"
          item-key="label"
          :group="{ name: 'region-empty-' + regionName, pull: false, put: ['palette-shapes', 'palette-rows'] }"
          :sort="false"
          filter=".region-empty-hint"
          class="rounded-lg border border-dashed border-gray-300 bg-gray-50"
          :data-builder-region="regionName"
          data-builder-empty-region="true"
          @change="(evt: any) => onEmptyRegionChange(drafts[regionName]!, evt)"
        >
          <template #item="{ element }">
            <div class="hidden">{{ element.label }}</div>
          </template>
          <template #footer>
            <div class="region-empty-hint">
              <el-empty :description="$t('region.empty')" :image-size="60" />
            </div>
          </template>
        </draggable>

        <!-- 添加行 -->
        <div class="mt-3">
          <el-dropdown :disabled="cannotEdit" @command="(key: string) => handleAddRow(drafts[regionName]!, key)">
            <el-button type="primary" plain size="small" :icon="Plus" :disabled="cannotEdit">{{ $t('region.row.add') }}</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="l in allowedLayouts(drafts[regionName]!)" :key="l.key" :command="l.key">
                  <div class="flex items-center gap-2">
                    <layout-thumb :widths="l.widths" class="h-4 w-7 rounded-sm border border-gray-100 bg-gray-50" />
                    <span>{{ layoutLabel(l) }}</span>
                    <span class="text-xs text-gray-placeholder">{{ l.columns }} {{ $t('region.columns') }}</span>
                  </div>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.region-col {
  min-width: 0;
}
/* 间距由 flex gap 控制：EP 相邻按钮的默认左外边距归零 */
.structure :deep(.el-button + .el-button) {
  margin-left: 0;
}
/* 节点层级：区域（白 + 轻阴影）› 行（浅灰）› 列（白虚线）› 面板（白实线）；选中 = 主色边框 + 光晕（与画布选择环同语言） */
.structure-node {
  border-color: var(--el-border-color-light);
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}
.structure-node-region {
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-lighter);
}
.structure-node-row {
  background: var(--el-fill-color-light);
}
.structure-node-panel {
  cursor: pointer;
  background: var(--el-bg-color);
}
.structure-node-panel:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: var(--el-box-shadow-lighter);
}
.structure-node-selected,
.structure-node-selected:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-7);
}
.structure-node-region.structure-node-selected {
  box-shadow:
    0 0 0 2px var(--el-color-primary-light-7),
    var(--el-box-shadow-lighter);
}
.structure-node-panel.structure-node-selected {
  background: var(--el-color-primary-light-9);
}
.structure-node-title {
  color: var(--el-text-color-primary);
  cursor: pointer;
}
.structure-node-row .structure-node-title {
  color: var(--el-text-color-regular);
}
.structure-node-title:hover {
  color: var(--el-color-primary);
}
.structure-node-title:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--el-color-primary-light-5);
}
.structure-node-title:disabled {
  color: var(--el-text-color-placeholder);
  cursor: default;
}
@media (prefers-reduced-motion: reduce) {
  .structure-node {
    transition: none;
  }
}
</style>

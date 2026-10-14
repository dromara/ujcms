<script setup lang="ts">
import { computed, nextTick, ref, watch, type Component, type PropType } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useI18n } from 'vue-i18n';
import { useLocalStorage } from '@vueuse/core';
import { localizedLabel } from '@/i18n';
import { Close, Connection, CopyDocument, Delete, FullScreen, Grid, InfoFilled, Menu, Operation, Postcard, Setting } from '@element-plus/icons-vue';
import { perm } from '@/stores/useCurrentUser';
import ShapeFieldItem from '@/views/design/fields/ShapeFieldItem.vue';
import PanelSettingsFields from '@/views/design/PanelSettingsFields.vue';
import { usePanelSettingsForm, type PanelFormValues } from '@/views/design/usePanelSettingsForm';
import { allowedLayoutsOf, regionDescriptionOf, regionLabelOf, regionPanelCount, shapeLabelOf } from './builderLabels';
import { rowsFull as checkRowsFull } from './constraints';
import { ShapeGlyph } from './ShapeGlyph';
import InspectorSection from './InspectorSection.vue';
import LayoutPicker from './LayoutPicker.vue';
import SidebarHeader from './SidebarHeader.vue';
import type { BridgeSelection, Drafts, PaletteItem, PanelVM, RegionDraftState, RowLayout, RowVM, ShareState } from './types';

/**
 * 检查器侧栏（visual-builder §10；shell-polish 改版）：随选择上下文切换——
 * 面板 → 内联设置表单（usePanelSettingsForm / PanelSettingsFields，与 PanelForm 对话框同一模型；
 * 显式保存 → BuilderPage save-per-op + 区域刷新；切换选择时脏值须确认丢弃）+ 复制/删除；
 * 行 → 行布局瓦片选择 + 删除行；区域 → 约束清单 + 允许组件 + 添加行瓦片 + 全站共享分叉/恢复（§15）；
 * 插槽/设置锚点 → 说明 + 主题设置入口；空选择 → 区域卡片（显示名/区域名/规模/状态）。
 * 顶栏 主题设置/页面设置 打开设置态（§8.1/§8.2）：复用 ShapeFieldItem 表单渲染器编辑
 * theme-marketplace A1 的分范围设置（global / pages.<page> / variants.<surface>:<value>）。
 * 齿轮锚点（§8.1 P5）：select {kind:'setting'} → settingsFocus 滚动到对应 group.field 并短暂高亮。
 */
defineOptions({
  name: 'InspectorSidebar',
});
const props = defineProps({
  selection: { type: Object as PropType<BridgeSelection | null>, default: null },
  // 选中区域的 useRegionDraft 实例（reactive 解包；无区域上下文为 null）
  draft: { type: Object as PropType<RegionDraftState | null>, default: null },
  drafts: { type: Object as PropType<Drafts>, default: () => ({}) },
  regionOrder: { type: Array as PropType<string[]>, default: () => [] },
  rowLayouts: { type: Array as PropType<RowLayout[]>, default: () => [] },
  shapeLabelMap: { type: Object as PropType<Record<string, string>>, default: () => ({}) },
  // 调色板条目（允许组件芯片的缩略图/占位图）
  shapes: { type: Array as PropType<PaletteItem[]>, default: () => [] },
  // 设置态：'theme' | 'page' | null（非空时覆盖选择显示）
  settingsScope: { type: String as PropType<'theme' | 'page' | null>, default: null },
  // 设置数据（BuilderPage 持有并全量保存）：{ groups, values, pages, pageValues, variants, variantValues }
  settings: { type: Object as PropType<any>, default: null },
  settingsLoading: { type: Boolean, default: false },
  settingsSaving: { type: Boolean, default: false },
  // 当前页面清单名（页面设置按此过滤 pages[] 范围）
  page: { type: String, required: true },
  // 活动布局变体值（顶栏切换器，P5 §8.2；'' = 未指定 → 不显示变体设置节）
  variant: { type: String, default: '' },
  // 齿轮锚点焦点（§8.1）：'group.field'（data-ujcms-setting 值）；变化时滚动 + 高亮
  settingsFocus: { type: String, default: '' },
  // 全站共享区域状态（page-regions §15）
  shareStates: { type: Object as PropType<Record<string, ShareState>>, default: () => ({}) },
  // 内联面板保存进行中（保存按钮转圈、复制/删除暂禁）
  panelSaving: { type: Boolean, default: false },
});
const emit = defineEmits<{
  // 在对话框中编辑（结构视图同款 PanelForm）
  editPanel: [panel: PanelVM];
  // 内联设置保存（BuilderPage：就地合并 + save-per-op + 区域刷新）
  savePanel: [payload: { panel: PanelVM; values: PanelFormValues }];
  // 内联表单脏态（BuilderPage 离开守卫）
  dirtyChange: [dirty: boolean];
  rowLayoutChange: [payload: { row: RowVM; layout: string }];
  selectRegion: [name: string];
  // 祖先链导航（面包屑）：选中父行 / 清空选择回页面摘要 / 还原上一选择（丢弃确认被取消）
  selectRow: [rowId: string];
  clearSelection: [];
  select: [selection: BridgeSelection | null];
  saveSettings: [];
  closeSettings: [];
  openThemeSettings: [];
  // 设置值写入交由 BuilderPage（设置状态的持有者）执行，避免子组件改动 props
  setGlobalValue: [payload: { group: string; field: string; value: any }];
  setPageValue: [payload: { page: string; group: string; field: string; value: any }];
  setVariantValue: [payload: { key: string; group: string; field: string; value: any }];
  // 结构操作（P3）：BuilderPage 执行草稿树变更 + save-per-op + 区域刷新
  deletePanel: [panel: PanelVM];
  duplicatePanel: [panel: PanelVM];
  deleteRow: [row: RowVM];
  addRow: [payload: { layout: string }];
  // 全站共享区域（§15）：仅此页面定制 / 恢复为全站共享，由 BuilderPage 执行
  forkRegion: [name: string];
  unforkRegion: [name: string];
  // 宿主侧悬停锚点（canvas-chrome D30）：区域卡片 / 面包屑祖先悬停 → 画布同款悬停 chrome；null 清除
  hover: [target: BridgeSelection | null];
}>();

const { t } = useI18n();
const cannotEdit = computed<boolean>(() => perm('pageRegion:update'));

// 可折叠（对齐左侧调色板，状态持久化）：折叠成 40px 窄条；画布点选/打开设置会切换检查器内容——自动展开，
// 避免折叠态下「点了没反应」
const collapsed = useLocalStorage<boolean>('ujcms.builder.inspector.collapsed', false);
watch(
  () => [props.selection, props.settingsScope] as const,
  ([sel, scope]) => {
    if (collapsed.value && (sel != null || scope != null)) collapsed.value = false;
  },
);

const shapeLabel = (shape: string): string => shapeLabelOf(props.shapeLabelMap, shape);
const localized = localizedLabel;
const shapeItemOf = (shape: string): PaletteItem | undefined => props.shapes.find((s) => s.name === shape);

const selectedPanel = computed<PanelVM | null>(() => (props.selection?.kind === 'panel' && props.draft != null ? props.draft.findPanel(props.selection.id) : null));
const selectedRow = computed<RowVM | null>(() => (props.selection?.kind === 'row' && props.draft != null ? props.draft.findRow(props.selection.id) : null));
const selectedRowIndex = computed<number>(() => (selectedRow.value != null && props.draft != null ? props.draft.rows.indexOf(selectedRow.value) : -1));
const selectionRegion = computed<string>(() => props.selection?.region ?? '');

// ---- 祖先链面包屑：页面 › 区域 › 行 N › 面板（当前选中项之前的每级可点选） ----------------------

/** 面板所在的父行（草稿树遍历）。 */
const parentRowOfPanel = (panelId: string): { row: RowVM; index: number } | null => {
  const rows = props.draft?.rows ?? [];
  for (let i = 0; i < rows.length; i += 1) {
    const row = rows[i];
    if (row != null && row.columns.some((col) => col.panels.some((p) => String(p.id) === String(panelId)))) return { row, index: i };
  }
  return null;
};

// 共享区域状态标签（§15）：shared = 全站共享（info）；own/ownPending = 本页独立（warning）；none = 不显示
const shareLabel = (region: string): string => {
  const s = props.shareStates[region];
  if (s === 'shared') return t('builder.share.site');
  if (s === 'own') return t('builder.share.own');
  if (s === 'ownPending') return t('builder.share.ownPending');
  return '';
};
const shareTagType = (region: string): 'info' | 'warning' | null => {
  const s = props.shareStates[region];
  if (s === 'shared') return 'info';
  if (s === 'own' || s === 'ownPending') return 'warning';
  return null;
};

interface Crumb {
  label: string;
  select: () => void;
  // 悬停时在画布高亮的祖先（页面根无对应元素）
  hover?: BridgeSelection;
}
const ancestorCrumbs = computed<Crumb[]>(() => {
  const sel = props.selection;
  if (sel == null) return [];
  const crumbs: Crumb[] = [{ label: t('builder.inspector.page'), select: () => emit('clearSelection') }];
  const region = sel.region ?? '';
  // 区域是树根；插槽/设置锚点可能无区域上下文——只给页面根
  if (sel.kind === 'region' || region === '') return crumbs;
  crumbs.push({ label: regionLabelOf(props.drafts[region], region), select: () => emit('selectRegion', region), hover: { kind: 'region', id: region, region } });
  if (sel.kind === 'row') return crumbs;
  if (sel.kind !== 'panel') return crumbs;
  const hit = parentRowOfPanel(sel.id);
  if (hit == null) return crumbs; // 草稿树中定位不到（预览与草稿失步）：面包屑到区域级为止
  crumbs.push({ label: `${t('builder.inspector.row')} ${hit.index + 1}`, select: () => emit('selectRow', String(hit.row.id)), hover: { kind: 'row', id: String(hit.row.id), region } });
  return crumbs;
});
const hoverCrumb = (crumb: Crumb, on: boolean): void => {
  if (crumb.hover != null) emit('hover', on ? crumb.hover : null);
};
const hoverRegion = (name: string, on: boolean): void => {
  emit('hover', on ? { kind: 'region', id: name, region: name } : null);
};

// ---- 头部：种类图标 + 标题 ------------------------------------------------------------------------

// 主题设置 / 页面设置 与顶栏同一动作图标（Setting / Operation）；空选择态（页面区域）用区域/结构图标 Grid——
// 页面种类图标只出现在顶栏页面切换器，避免两侧头部同图标却指不同事物
const KIND_ICONS: Record<string, Component> = { panel: Postcard, row: Menu, region: Grid, slot: Connection, setting: Setting };
const headerIcon = computed<Component>(() => {
  if (props.settingsScope === 'theme') return Setting;
  if (props.settingsScope === 'page') return Operation;
  return (props.selection != null ? KIND_ICONS[props.selection.kind] : undefined) ?? Grid;
});
const headerTitle = computed<string>(() => {
  if (props.settingsScope === 'theme') return t('builder.themeSettings');
  if (props.settingsScope === 'page') return t('builder.pageSettings');
  const sel = props.selection;
  if (sel == null) return t('builder.regions');
  if (sel.kind === 'panel') return selectedPanel.value != null ? shapeLabel(selectedPanel.value.shape) : t('builder.inspector.panel');
  if (sel.kind === 'row') return selectedRowIndex.value >= 0 ? `${t('builder.inspector.row')} ${selectedRowIndex.value + 1}` : t('builder.inspector.row');
  if (sel.kind === 'region') return regionLabelOf(props.drafts[sel.id], sel.id);
  return t(`builder.inspector.${sel.kind}`);
});
// 空选择态的计数胶囊 = 区域数（与调色板头部的组件数对称）
const headerCount = computed<number | null>(() => (props.settingsScope == null && props.selection == null ? props.regionOrder.length : null));

const rowAllowedLayouts = computed<RowLayout[]>(() => allowedLayoutsOf(props.draft, props.rowLayouts));
const rowsFull = computed<boolean>(() => props.draft != null && !checkRowsFull(props.draft).ok);

// ---- 面板内联设置表单（shell-polish）：选择变化装载；脏值切换须确认；保存上抛 -------------------

const panelForm = usePanelSettingsForm();
const panelKey = computed<string>(() => (props.selection?.kind === 'panel' && selectedPanel.value != null ? `${selectionRegion.value}/${props.selection.id}` : ''));
let loadedKey = '';
let loadedSelection: BridgeSelection | null = null;
let confirming = false;

const loadedPanelExists = (): boolean => loadedSelection != null && (props.drafts[loadedSelection.region ?? '']?.findPanel(loadedSelection.id) ?? null) != null;

watch(
  [panelKey, selectedPanel],
  async ([key, panel]) => {
    if (key === loadedKey) {
      // 同一面板但树被重建（撤销/刷新）：未改动时按新对象重载，保留进行中的修改
      if (key !== '' && panel != null && panel !== panelForm.panel && !panelForm.dirty) await panelForm.load(panel);
      return;
    }
    if (loadedKey !== '' && panelForm.dirty && loadedPanelExists() && !confirming) {
      confirming = true;
      try {
        await ElMessageBox.confirm(t('builder.inspector.discardChanges'), t('region.confirm.title'), { type: 'warning' });
      } catch {
        confirming = false;
        emit('select', loadedSelection); // 还原上一选择（BuilderPage 随之回发选择环）
        return;
      }
      confirming = false;
    }
    loadedKey = key;
    loadedSelection = key !== '' && props.selection != null ? { ...props.selection } : null;
    if (key === '' || panel == null) {
      panelForm.clear();
    } else {
      await panelForm.load(panel);
    }
  },
  { immediate: true },
);
watch(
  () => panelForm.dirty,
  (dirty) => emit('dirtyChange', dirty),
);

const handleSavePanel = (): void => {
  const panel = selectedPanel.value;
  if (panel == null) return;
  const problem = panelForm.firstProblemMessage();
  if (problem != null) {
    ElMessage.error(problem);
    return;
  }
  emit('savePanel', { panel, values: panelForm.result() });
};
const handleDeletePanel = (): void => {
  const panel = selectedPanel.value;
  if (panel == null) return;
  panelForm.clear();
  loadedKey = '';
  emit('deletePanel', panel);
};
const handleDuplicatePanel = (): void => {
  if (selectedPanel.value != null) emit('duplicatePanel', selectedPanel.value);
};
const handleOpenDialog = (): void => {
  if (selectedPanel.value != null) emit('editPanel', selectedPanel.value);
};
const handleRowLayoutPick = (key: string): void => {
  if (selectedRow.value != null) emit('rowLayoutChange', { row: selectedRow.value, layout: key });
};
const handleDeleteRow = (): void => {
  if (selectedRow.value != null) emit('deleteRow', selectedRow.value);
};

// ---- 设置态 ---------------------------------------------------------------------------------------

// 页面设置：当前页面声明的设置组（A1 pages[] 范围）
const currentPageSettings = computed<any[]>(() => (props.settings?.pages ?? []).filter((p: any) => p.name === props.page));

// 主题未声明任何设置结构（global/pages/variants 全空）——与后端保存的 400 判定完全一致
// （ThemeSettingsService.save：schema.isEmpty() && !hasPageOrVariantSettings）：隐藏保存按钮
const settingsSchemaEmpty = computed<boolean>(() => {
  const s: any = props.settings;
  return s != null && (s.groups ?? []).length <= 0 && (s.pages ?? []).length <= 0 && (s.variants ?? []).length <= 0;
});

// 变体设置（P5 §8.2）：顶栏切换器选中的变体在本页 surface 声明的设置组
const currentVariantSettings = computed<any[]>(() => {
  if (props.variant === '') return [];
  return (props.settings?.variants ?? []).filter((v: any) => v.surface === props.page && v.value === props.variant);
});

const setGlobalValue = (group: string, field: string, value: any): void => {
  emit('setGlobalValue', { group, field, value });
};
const setPageValue = (pageName: string, group: string, field: string, value: any): void => {
  emit('setPageValue', { page: pageName, group, field, value });
};
const setVariantValue = (key: string, group: string, field: string, value: any): void => {
  emit('setVariantValue', { key, group, field, value });
};

// ---- 齿轮锚点滚动（§8.1 P5）：settingsFocus = 'group.field' → 滚到该表单项并短暂高亮 -------------
const rootRef = ref<HTMLElement>();
let flashTimer = 0;

const applyFocus = async (): Promise<void> => {
  if (props.settingsFocus === '' || props.settingsScope !== 'theme' || props.settingsLoading || props.settings == null) return;
  await nextTick();
  const root = rootRef.value;
  if (root == null) return;
  const esc = (v: string): string => (typeof CSS !== 'undefined' && CSS.escape ? CSS.escape(v) : v);
  const group = props.settingsFocus.split('.')[0] ?? '';
  // 优先精确到字段，组存在而字段不存在时退回组头
  const el = root.querySelector(`[data-setting-field="${esc(props.settingsFocus)}"]`) ?? root.querySelector(`[data-setting-group="${esc(group)}"]`);
  if (el == null) return;
  el.scrollIntoView({ block: 'center' });
  el.classList.add('setting-focus-flash');
  window.clearTimeout(flashTimer);
  flashTimer = window.setTimeout(() => el.classList.remove('setting-focus-flash'), 1600);
};

watch(
  () => [props.settingsFocus, props.settingsLoading, props.settingsScope],
  () => void applyFocus(),
);

const closeCurrent = (): void => {
  if (props.settingsScope != null) emit('closeSettings');
  else emit('clearSelection');
};
</script>

<template>
  <div ref="rootRef" class="inspector flex h-full flex-col overflow-hidden bg-white" :class="{ 'inspector-collapsed': collapsed }">
    <!-- 统一头部（与调色板同构）：种类图标 + 标题 + 计数/脏点 + 动作（对话框编辑 / 关闭）+ 折叠；
         折叠态为窄条，图标即展开钮（画布点选/打开设置会自动展开） -->
    <sidebar-header v-model:collapsed="collapsed" :icon="headerIcon" :title="headerTitle" :count="headerCount" side="right" :toggle-label="$t('builder.inspector.toggle')">
      <span v-if="panelForm.dirty" class="h-1.5 w-1.5 shrink-0 rounded-full bg-warning" :title="$t('builder.inspector.unsavedChanges')"></span>
      <template #actions>
        <el-tooltip v-if="settingsScope == null && selectedPanel != null" :content="$t('builder.inspector.openDialog')" placement="bottom">
          <el-button :icon="FullScreen" link :aria-label="$t('builder.inspector.openDialog')" @click="handleOpenDialog"></el-button>
        </el-tooltip>
        <el-tooltip v-if="settingsScope != null || selection != null" :content="$t('close')" placement="bottom">
          <el-button :icon="Close" link :aria-label="$t('close')" @click="closeCurrent"></el-button>
        </el-tooltip>
      </template>
    </sidebar-header>
    <template v-if="!collapsed">
      <!-- 祖先链面包屑（页面 › 区域 › 行 N › 当前），祖先可点选跳到父级 -->
      <div
        v-if="settingsScope == null && ancestorCrumbs.length > 0"
        class="flex shrink-0 flex-wrap items-center gap-1 border-b border-gray-100 px-3 py-1.5 text-xs text-gray-secondary"
      >
        <template v-for="(crumb, i) in ancestorCrumbs" :key="i">
          <button
            type="button"
            class="rounded px-0.5 hover:text-primary"
            @click="crumb.select"
            @mouseenter="() => hoverCrumb(crumb, true)"
            @mouseleave="() => hoverCrumb(crumb, false)"
            @focus="() => hoverCrumb(crumb, true)"
            @blur="() => hoverCrumb(crumb, false)"
          >
            {{ crumb.label }}
          </button>
          <span class="text-gray-placeholder">›</span>
        </template>
        <span class="text-gray-primary">{{ headerTitle }}</span>
        <el-tooltip v-if="selectionRegion && shareTagType(selectionRegion) != null" :content="$t('builder.share.siteTip')" placement="bottom">
          <el-tag size="small" :type="shareTagType(selectionRegion) ?? 'info'" effect="plain" class="ml-auto">{{ shareLabel(selectionRegion) }}</el-tag>
        </el-tooltip>
      </div>

      <!-- 设置态：主题设置（global）/ 页面设置（pages.<page> + variants.<surface>:<value>） -->
      <template v-if="settingsScope != null">
        <div v-loading="settingsLoading" class="min-h-0 flex-1 overflow-auto p-3">
          <template v-if="settings != null">
            <template v-if="settingsScope === 'theme'">
              <el-empty v-if="(settings.groups ?? []).length <= 0" :description="$t('theme.settings.empty')" :image-size="60" />
              <el-form v-else label-position="top" size="small">
                <div v-for="(group, gi) in settings.groups" :key="group.name" :class="Number(gi) > 0 ? 'mt-4' : ''" :data-setting-group="group.name">
                  <inspector-section :title="localized(group.label, group.name)" />
                  <el-form-item
                    v-for="(fieldDef, fieldName) in group.fields ?? {}"
                    :key="fieldName"
                    :label="localized((fieldDef as any).label, String(fieldName))"
                    :data-setting-field="`${group.name}.${String(fieldName)}`"
                  >
                    <shape-field-item
                      :def="fieldDef"
                      :model-value="settings.values[group.name]?.[fieldName] ?? (fieldDef as any).default ?? null"
                      @update:model-value="(val: any) => setGlobalValue(group.name, String(fieldName), val)"
                    />
                  </el-form-item>
                </div>
              </el-form>
            </template>
            <template v-else>
              <el-empty v-if="currentPageSettings.length <= 0 && currentVariantSettings.length <= 0" :description="$t('builder.settings.pageEmpty')" :image-size="60" />
              <el-form v-else label-position="top" size="small">
                <div v-for="pageDef in currentPageSettings" :key="pageDef.name">
                  <div v-for="(group, gi) in pageDef.groups ?? []" :key="group.name" :class="Number(gi) > 0 ? 'mt-4' : ''">
                    <inspector-section :title="localized(group.label, group.name)" />
                    <el-form-item v-for="(fieldDef, fieldName) in group.fields ?? {}" :key="fieldName" :label="localized((fieldDef as any).label, String(fieldName))">
                      <shape-field-item
                        :def="fieldDef"
                        :model-value="settings.pageValues[pageDef.name]?.[group.name]?.[fieldName] ?? (fieldDef as any).default ?? null"
                        @update:model-value="(val: any) => setPageValue(pageDef.name, group.name, String(fieldName), val)"
                      />
                    </el-form-item>
                  </div>
                </div>
                <!-- 变体设置节（§8.2）：编辑 variants.<surface>:<value> 存值；切换器未选变体或变体未声明设置组时整节不显示 -->
                <div v-for="variantDef in currentVariantSettings" :key="`${variantDef.surface}:${variantDef.value}`" class="mt-4 overflow-hidden rounded-md border border-gray-200">
                  <div class="flex items-center gap-1.5 border-b border-gray-200 bg-primary-lighter px-2.5 py-1.5">
                    <el-tag size="small">{{ $t('builder.variant.label') }}</el-tag>
                    <span class="min-w-0 flex-1 truncate text-xs font-medium text-gray-regular">{{ localized(variantDef.label, variantDef.value) }}</span>
                  </div>
                  <div class="px-2.5 pt-2.5">
                    <div v-for="(group, gi) in variantDef.groups ?? []" :key="group.name" :class="Number(gi) > 0 ? 'mt-4' : ''">
                      <inspector-section :title="localized(group.label, group.name)" />
                      <el-form-item v-for="(fieldDef, fieldName) in group.fields ?? {}" :key="fieldName" :label="localized((fieldDef as any).label, String(fieldName))">
                        <shape-field-item
                          :def="fieldDef"
                          :model-value="settings.variantValues[`${variantDef.surface}:${variantDef.value}`]?.[group.name]?.[fieldName] ?? (fieldDef as any).default ?? null"
                          @update:model-value="(val: any) => setVariantValue(`${variantDef.surface}:${variantDef.value}`, group.name, String(fieldName), val)"
                        />
                      </el-form-item>
                    </div>
                  </div>
                </div>
              </el-form>
            </template>
          </template>
        </div>
        <!-- 主题无任何设置结构时隐藏保存：后端对全空结构的保存返回 400 -->
        <div v-if="!settingsSchemaEmpty" class="inspector-footer flex shrink-0 items-center gap-2 border-t border-gray-200 bg-white px-3 py-2">
          <el-button type="primary" size="small" :loading="settingsSaving" :disabled="perm('theme:settings')" @click="() => emit('saveSettings')">
            {{ $t('save') }}
          </el-button>
        </div>
      </template>

      <!-- 选择态 -->
      <template v-else-if="selection != null">
        <div class="min-h-0 flex-1 overflow-auto p-3">
          <!-- 面板：内联设置表单 -->
          <template v-if="selection.kind === 'panel'">
            <template v-if="selectedPanel != null">
              <div class="mb-3 flex flex-wrap items-center gap-1.5 text-xs">
                <span class="font-mono text-gray-placeholder" :title="$t('builder.inspector.shapeName')">{{ selectedPanel.shape }}</span>
                <el-tag v-if="selectedPanel.enabled === false" size="small" type="info">{{ $t('placement.status.disabled') }}</el-tag>
                <el-tag v-if="selectedPanel.visibility === 'desktop' || selectedPanel.visibility === 'mobile'" size="small" type="warning">
                  {{ $t(`placement.visibility.${selectedPanel.visibility}`) }}
                </el-tag>
              </div>
              <inspector-section :title="$t('builder.inspector.panelSettings')" />
              <panel-settings-fields :form="panelForm" layout="inline" />
            </template>
            <el-alert v-else :title="$t('builder.inspector.notFound')" type="info" :closable="false" />
          </template>

          <!-- 行：行布局瓦片 -->
          <template v-else-if="selection.kind === 'row'">
            <template v-if="selectedRow != null">
              <inspector-section :title="$t('builder.inspector.rowLayout')" />
              <layout-picker :layouts="rowAllowedLayouts" :model-value="selectedRow.layout" :disabled="cannotEdit" @pick="handleRowLayoutPick" />
            </template>
            <el-alert v-else :title="$t('builder.inspector.notFound')" type="info" :closable="false" />
          </template>

          <!-- 区域：共享状态 + 约束 + 允许组件 + 添加行 -->
          <template v-else-if="selection.kind === 'region'">
            <template v-if="draft != null">
              <!-- 全站共享（page-regions §15）：跟随宿主行 → 仅此页面定制；已分叉 → 恢复为全站共享 -->
              <div v-if="selectionRegion && shareTagType(selectionRegion) != null" class="mb-3 flex items-center gap-2 rounded-md border border-gray-200 bg-gray-50 px-3 py-2">
                <template v-if="shareStates[selectionRegion] === 'shared'">
                  <el-tooltip :content="$t('builder.share.siteTip')" placement="bottom">
                    <el-tag size="small" type="info">{{ $t('builder.share.site') }}</el-tag>
                  </el-tooltip>
                  <el-tooltip :content="$t('builder.share.customizeTip')" placement="bottom">
                    <el-button link type="primary" size="small" :disabled="cannotEdit" @click="() => emit('forkRegion', selectionRegion)">
                      {{ $t('builder.share.customize') }}
                    </el-button>
                  </el-tooltip>
                </template>
                <template v-else>
                  <el-tag size="small" type="warning">{{ shareLabel(selectionRegion) }}</el-tag>
                  <el-button link type="primary" size="small" :disabled="cannotEdit" @click="() => emit('unforkRegion', selectionRegion)">
                    {{ $t('builder.share.revert') }}
                  </el-button>
                </template>
              </div>
              <div class="mb-3 flex items-center gap-2 text-xs">
                <span class="font-mono text-gray-placeholder" :title="$t('builder.inspector.regionName')">{{ selectionRegion }}</span>
                <el-tooltip v-if="regionDescriptionOf(draft) !== ''" :content="regionDescriptionOf(draft)" placement="bottom">
                  <el-icon class="text-gray-placeholder"><InfoFilled /></el-icon>
                </el-tooltip>
              </div>
              <inspector-section :title="$t('builder.inspector.constraints')" />
              <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-xs">
                <dt class="text-gray-secondary">{{ $t('builder.inspector.maxRows') }}</dt>
                <dd class="tabular-nums text-gray-primary">{{ draft.maxRows ?? $t('builder.inspector.unlimited') }}</dd>
                <dt class="text-gray-secondary">{{ $t('builder.inspector.maxPanelsPerColumn') }}</dt>
                <dd class="tabular-nums text-gray-primary">{{ draft.maxPanelsPerColumn ?? $t('builder.inspector.unlimited') }}</dd>
                <dt class="text-gray-secondary">{{ $t('builder.inspector.current') }}</dt>
                <dd class="tabular-nums text-gray-primary">{{ $t('region.history.counts', { rows: draft.rows.length, panels: regionPanelCount(draft) }) }}</dd>
                <dt class="text-gray-secondary">{{ $t('builder.inspector.allowedLayouts') }}</dt>
                <dd class="tabular-nums text-gray-primary">
                  {{ (draft.detail?.allowedRowLayouts ?? []).length > 0 ? rowAllowedLayouts.length : $t('builder.inspector.unlimited') }}
                </dd>
              </dl>
              <inspector-section class="mt-4" :title="$t('builder.inspector.allowedShapes')" />
              <div class="flex flex-wrap gap-1.5">
                <span
                  v-for="shape in draft.allowedShapes"
                  :key="shape"
                  class="inline-flex items-center gap-1.5 rounded-md border border-gray-200 bg-white py-0.5 pl-1 pr-2 text-xs text-gray-regular"
                  :title="shape"
                >
                  <span class="inline-block h-4 w-6 overflow-hidden rounded-sm border border-gray-100 bg-primary-lighter">
                    <img
                      v-if="shapeItemOf(shape)?.kind === 'shape' && (shapeItemOf(shape) as any).thumbnail"
                      :src="(shapeItemOf(shape) as any).thumbnail"
                      :alt="shapeLabel(shape)"
                      class="h-full w-full object-cover"
                    />
                    <shape-glyph v-else :icon="(shapeItemOf(shape) as any)?.icon ?? ''" />
                  </span>
                  {{ shapeLabel(shape) }}
                </span>
                <span v-if="draft.allowedShapes.length <= 0" class="text-xs text-gray-secondary">{{ $t('builder.inspector.allShapes') }}</span>
              </div>
              <inspector-section class="mt-4" :title="$t('region.row.add')" />
              <div v-if="rowsFull" class="text-xs text-warning">{{ $t('builder.drag.maxRows', { max: draft.maxRows }) }}</div>
              <template v-else>
                <div class="mb-2 text-xs text-gray-secondary">{{ $t('builder.inspector.addRowHint') }}</div>
                <layout-picker :layouts="rowAllowedLayouts" :disabled="cannotEdit" @pick="(key: string) => emit('addRow', { layout: key })" />
              </template>
            </template>
            <el-alert v-else :title="$t('builder.inspector.notFound')" type="info" :closable="false" />
          </template>

          <!-- 插槽（主题模板固定投放位）：说明 + 主题设置入口 -->
          <template v-else-if="selection.kind === 'slot'">
            <div class="mb-3 rounded-md bg-gray-50 px-3 py-2 text-xs leading-5 text-gray-secondary">{{ $t('builder.inspector.slotHint') }}</div>
            <div class="mb-3 text-xs text-gray-secondary">
              <span class="rounded bg-gray-100 px-1.5 font-mono">{{ selection.id }}</span>
            </div>
            <el-button size="small" :icon="Setting" @click="() => emit('openThemeSettings')">{{ $t('builder.themeSettings') }}</el-button>
          </template>

          <!-- 设置锚点（data-ujcms-setting，主题作者约定 §8.1）：跳主题设置 -->
          <template v-else-if="selection.kind === 'setting'">
            <div class="mb-3 rounded-md bg-gray-50 px-3 py-2 text-xs leading-5 text-gray-secondary">{{ $t('builder.inspector.settingHint') }}</div>
            <div class="mb-3 text-xs text-gray-secondary">
              <span class="rounded bg-gray-100 px-1.5 font-mono">{{ selection.id }}</span>
            </div>
            <el-button size="small" :icon="Setting" @click="() => emit('openThemeSettings')">{{ $t('builder.themeSettings') }}</el-button>
          </template>
        </div>

        <!-- 粘性脚栏：面板保存/重置/复制/删除；行删除 -->
        <div v-if="selection.kind === 'panel' && selectedPanel != null" class="inspector-footer flex shrink-0 items-center gap-2 border-t border-gray-200 bg-white px-3 py-2">
          <el-button type="primary" size="small" :loading="panelSaving" :disabled="!panelForm.dirty || cannotEdit" @click="handleSavePanel">{{ $t('save') }}</el-button>
          <el-button v-if="panelForm.dirty && !panelSaving" link size="small" @click="() => panelForm.reset()">{{ $t('reset') }}</el-button>
          <span class="flex-1"></span>
          <el-button :icon="CopyDocument" size="small" :disabled="cannotEdit || panelSaving" @click="handleDuplicatePanel">{{ $t('region.panel.duplicate') }}</el-button>
          <el-popconfirm :title="$t('builder.inspector.deletePanel.confirm')" @confirm="handleDeletePanel">
            <template #reference>
              <el-button :icon="Delete" size="small" type="danger" plain :disabled="cannotEdit || panelSaving">{{ $t('delete') }}</el-button>
            </template>
          </el-popconfirm>
        </div>
        <div v-else-if="selection.kind === 'row' && selectedRow != null" class="inspector-footer flex shrink-0 items-center gap-2 border-t border-gray-200 bg-white px-3 py-2">
          <el-popconfirm :title="$t('region.row.delete.confirm')" @confirm="handleDeleteRow">
            <template #reference>
              <el-button :icon="Delete" size="small" type="danger" plain :disabled="cannotEdit">{{ $t('region.row.delete') }}</el-button>
            </template>
          </el-popconfirm>
        </div>
      </template>

      <!-- 空选择：区域卡片 -->
      <template v-else>
        <div class="min-h-0 flex-1 overflow-auto p-3">
          <div class="mb-3 rounded-md bg-gray-50 px-3 py-2 text-xs leading-5 text-gray-secondary">{{ $t('builder.inspector.empty') }}</div>
          <button
            v-for="regionName in regionOrder"
            :key="regionName"
            type="button"
            class="region-card mb-2 w-full rounded-lg border border-gray-200 bg-white px-3 py-2.5 text-left transition hover:border-primary-light hover:shadow-sm"
            :title="regionDescriptionOf(drafts[regionName])"
            @click="() => emit('selectRegion', regionName)"
            @mouseenter="() => hoverRegion(regionName, true)"
            @mouseleave="() => hoverRegion(regionName, false)"
            @focus="() => hoverRegion(regionName, true)"
            @blur="() => hoverRegion(regionName, false)"
          >
            <div class="flex items-center gap-2">
              <span class="min-w-0 flex-1 truncate text-sm font-medium text-gray-primary">{{ regionLabelOf(drafts[regionName], regionName) }}</span>
              <span class="inline-flex shrink-0 items-center gap-1.5 text-xs text-gray-regular">
                <span class="h-2 w-2 rounded-full" :class="drafts[regionName]?.hasDraft ? 'bg-warning' : 'bg-success'"></span>
                {{ drafts[regionName]?.hasDraft ? $t('region.status.draft') : $t('region.status.published') }}
              </span>
            </div>
            <div class="mt-1 flex items-center gap-2 text-xs text-gray-secondary">
              <span class="font-mono text-gray-placeholder">{{ regionName }}</span>
              <span>{{ $t('region.history.counts', { rows: drafts[regionName]?.rows.length ?? 0, panels: regionPanelCount(drafts[regionName]) }) }}</span>
              <el-tag v-if="shareTagType(regionName) != null" size="small" :type="shareTagType(regionName) ?? 'info'" effect="plain" class="ml-auto">{{
                shareLabel(regionName)
              }}</el-tag>
            </div>
          </button>
          <div class="mt-3 text-xs leading-5 text-gray-placeholder">{{ $t('builder.inspector.structureHint') }}</div>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.inspector {
  flex-shrink: 0;
  width: 360px;
  transition: width 0.15s;
}
.inspector-collapsed {
  width: 40px;
}
@media (prefers-reduced-motion: reduce) {
  .inspector {
    transition: none;
  }
}
.inspector :deep(.el-button + .el-button) {
  margin-left: 0;
}
.region-card:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 1px;
}
/* 齿轮锚点定位后的短暂高亮（§8.1）；类由脚本加在 el-form-item / 组容器上 */
.inspector :deep(.setting-focus-flash) {
  border-radius: 4px;
  box-shadow: 0 0 0 2px var(--el-color-primary-light-5);
  transition: box-shadow 0.3s ease;
}
</style>

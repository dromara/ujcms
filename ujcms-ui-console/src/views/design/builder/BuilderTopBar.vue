<script setup lang="ts">
import { computed, ref, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  Aim,
  ArrowDown,
  Back,
  Refresh,
  TopRight,
  View,
  Setting,
  Operation,
  Grid,
  Upload,
  Delete,
  RefreshLeft,
  RefreshRight,
  Clock,
  Link,
  Monitor,
  Iphone,
  Share,
  Loading,
} from '@element-plus/icons-vue';
import { perm } from '@/stores/useCurrentUser';
import { pageIconOf } from './pageIcons';
import { redoHint, undoHint } from './shortcuts';
import type { Viewport } from './types';

/**
 * 构建器顶栏（visual-builder §10；shell-polish 三区重排）：
 * 上下文区（返回 · 页面切换器 · 布局变体 P5 §8.2 · 范围态 chip §9 情境化）｜
 * 预览地址区（路径选择器 preview-url-resolver P2 + 刷新/新标签）｜
 * 动作区（状态胶囊 · 撤销/重做 D8 · 视口分段 P5 §10 · 画布/结构分段 · 主题/页面设置/发布历史 · 发布/丢弃）。
 * 三个原子组只在组边界换行；图标按钮一律 tooltip + aria-label；分段控件带 aria-label 与可读文本。
 * 高 44px 与两侧栏头部齐平（默认 32px 控件 + 6px 上下留白）。
 */
defineOptions({
  name: 'BuilderTopBar',
});
const props = defineProps({
  pageLabel: { type: String, required: true },
  // 页面切换器候选（与 设计 → 页面 同一目录，仅含声明了区域的页面）：{ name, label }
  pageOptions: { type: Array as PropType<{ name: string; label: string }[]>, default: () => [] },
  pagePath: { type: String, required: true },
  // 预览路径候选（preview-url-resolver P2）：{ kind, label, url }，选择器按 kind 分组
  pathOptions: { type: Array as PropType<{ kind: string; label: string; url: string }[]>, default: () => [] },
  pathLoading: { type: Boolean, default: false },
  // 候选空态原因（'no-content' 时下拉内给出 导入演示数据 入口，§6）
  pathEmptyReason: { type: String as PropType<string | null>, default: null },
  // 当前活动布局变体值（'' = 未指定；仅 channel/article/topic 页面且目录非空时显示切换器）
  variant: { type: String, default: '' },
  variantOptions: { type: Array as PropType<{ value: string; label: string }[]>, default: () => [] },
  // 页面标识（范围态 chip 的影响范围提示按 surface 取文案，§9）
  page: { type: String, required: true },
  // 范围态（§9 情境化）：hidden = 单例页；shared = 共享布局；own/ownPending = 栏目独立布局（Pending = 行未创建，首存生效）
  scopeState: { type: String as PropType<'hidden' | 'shared' | 'own' | 'ownPending'>, default: 'hidden' },
  scopeChannelName: { type: String, default: '' },
  scopeCustomizable: { type: Boolean, default: false },
  viewport: { type: String as PropType<Viewport>, default: 'desktop' },
  structureMode: { type: Boolean, required: true },
  // 有草稿的区域名（发布/丢弃可用性 + 状态文案）
  draftRegions: { type: Array as PropType<string[]>, default: () => [] },
  // 区域名 → 显示名（状态 title 列出待发布区域）
  regionLabels: { type: Object as PropType<Record<string, string>>, default: () => ({}) },
  saving: { type: Boolean, default: false },
  canUndo: { type: Boolean, default: false },
  canRedo: { type: Boolean, default: false },
});
const emit = defineEmits<{
  back: [];
  render: [];
  fetchPages: [];
  switchPage: [name: string];
  fetchPaths: [q: string];
  seedData: [];
  openInNewTab: [];
  publishAll: [];
  discardAll: [];
  history: [];
  themeSettings: [];
  pageSettings: [];
  undo: [];
  redo: [];
  customizeScope: [];
  revertScope: [];
  'update:pagePath': [val: string];
  'update:variant': [val: string];
  'update:viewport': [val: Viewport];
  'update:structureMode': [val: boolean];
}>();

const { t } = useI18n();

// 候选按类别分组（fixed / channel / article / topic / form），保持后端排序
const pathGroups = computed<{ kind: string; label: string; options: { label: string; url: string }[] }[]>(() => {
  const groups: { kind: string; label: string; options: { label: string; url: string }[] }[] = [];
  for (const item of props.pathOptions) {
    let group = groups.find((g) => g.kind === item.kind);
    if (group == null) {
      group = { kind: item.kind, label: t(`pagePreview.path.kind.${item.kind}`), options: [] };
      groups.push(group);
    }
    group.options.push(item);
  }
  return groups;
});

// 状态胶囊（与范围 chip 同一形态，canvas-chrome）：保存中（主色 + 旋转图标）/ N 个区域待发布（警告）/ 已发布（灰底绿点）；
// title 列出待发布区域显示名
const pendingCount = computed<number>(() => props.draftRegions.length);
const statusText = computed<string>(() =>
  props.saving ? t('builder.saving') : pendingCount.value > 0 ? t('builder.status.pending', { count: pendingCount.value }) : t('region.status.published'),
);
type StatusState = 'saving' | 'pending' | 'published';
const statusState = computed<StatusState>(() => (props.saving ? 'saving' : pendingCount.value > 0 ? 'pending' : 'published'));
const STATUS_CHIP: Record<StatusState, string> = {
  saving: 'border-primary-light bg-primary-lighter text-primary',
  pending: 'border-warning-light bg-warning-lighter text-warning',
  published: 'border-gray-200 bg-gray-50 text-gray-regular',
};
const pendingTitle = computed<string>(() =>
  pendingCount.value > 0 ? t('builder.status.pendingRegions', { regions: props.draftRegions.map((r) => props.regionLabels[r] ?? r).join(t('punct.enum')) }) : '',
);

// 分段控件选项（图标 + 可读文本；视口项文本仅供读屏与 tooltip）
const deviceOptions = computed(() => [
  { value: 'desktop', label: t('builder.viewport.desktop'), icon: Monitor },
  { value: 'tablet', label: t('builder.viewport.tablet'), icon: Iphone },
  { value: 'mobile', label: t('builder.viewport.mobile'), icon: Iphone },
]);
const viewOptions = computed(() => [
  { value: 'canvas', label: t('builder.canvasView'), icon: View },
  { value: 'structure', label: t('builder.structureView'), icon: Grid },
]);

const cannotEdit = computed<boolean>(() => perm('pageRegion:update'));

// 页面切换器芯片图标按页面种类取形（pageIcons.ts）
const pageIcon = computed(() => pageIconOf(props.page));

// 页面切换菜单开合（触发钮的展开态样式 + 首次展开装载目录）；选中当前页不导航
const pageMenuOpen = ref<boolean>(false);
const onPageMenuVisible = (open: boolean): void => {
  pageMenuOpen.value = open;
  if (open) emit('fetchPages');
};
const onPageCommand = (name: string): void => {
  if (name !== props.page) emit('switchPage', name);
};
</script>

<template>
  <div class="topbar flex min-h-11 flex-wrap items-center gap-x-2 gap-y-1.5 border-b border-gray-200 bg-white px-2 py-1.5">
    <!-- 上下文组：返回 · 页面切换器 · 变体 · 范围态 -->
    <div class="flex min-w-0 items-center gap-x-1">
      <el-tooltip :content="$t('back')" placement="bottom">
        <el-button :icon="Back" text circle :aria-label="$t('back')" @click="() => emit('back')"></el-button>
      </el-tooltip>
      <!-- 页面切换器：页面种类图标芯片 + 页面名 + 展开箭头的触发钮（与侧栏头部同一芯片语言；图标随页面
           种类取形，pageIcons.ts），展开列出可编辑页面（首次展开装载目录，每行前置种类图标，当前页主色高亮）；
           选择即导航到该页构建器（Builder 路由 fullPath 键控 → 整体重挂载，预览路径/变体/范围随新页重置） -->
      <el-dropdown trigger="click" placement="bottom-start" @visible-change="onPageMenuVisible" @command="onPageCommand">
        <button type="button" class="topbar-page flex h-8 max-w-64 items-center gap-1.5 rounded-md pl-1 pr-2 outline-none" :class="{ 'topbar-page-open': pageMenuOpen }">
          <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-primary-lighter text-primary">
            <el-icon><component :is="pageIcon" /></el-icon>
          </span>
          <!-- 显示名尚未知晓（冷启动、无会话缓存）时显示骨架，不闪现原始页面名 -->
          <span v-if="pageLabel === ''" class="h-3.5 w-16 animate-pulse rounded bg-gray-200" aria-hidden="true"></span>
          <span v-else class="truncate text-sm font-semibold text-gray-primary">{{ pageLabel }}</span>
          <el-icon class="topbar-page-caret shrink-0 text-gray-secondary"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu class="topbar-page-menu">
            <template v-if="pageOptions.length > 0">
              <el-dropdown-item v-for="item in pageOptions" :key="item.name" :command="item.name" :aria-current="item.name === page ? 'page' : undefined">
                <span class="flex w-full items-center gap-2" :class="{ 'text-primary font-medium': item.name === page }">
                  <el-icon class="shrink-0" :class="item.name === page ? '' : 'text-gray-secondary'"><component :is="pageIconOf(item.name)" /></el-icon>
                  <span class="min-w-0 flex-1 truncate">{{ item.label }}</span>
                  <span class="shrink-0 font-mono text-xs text-gray-placeholder">{{ item.name }}</span>
                </span>
              </el-dropdown-item>
            </template>
            <el-dropdown-item v-else disabled>{{ $t('builder.pageSwitch.loading') }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <!-- 布局变体切换器（P5 §8.2；随预览页改版 2026-07-24）：默认反映当前预览页解析出的活动
           变体；手动选择 = 临时编辑上下文，下次导航恢复随页；不改写实体列 -->
      <template v-if="variantOptions.length > 0">
        <el-divider direction="vertical" />
        <span class="shrink-0 text-xs text-gray-secondary">{{ $t('builder.variant.label') }}</span>
        <el-select :model-value="variant" class="w-32 shrink-0" :placeholder="$t('builder.variant.unbound')" @update:model-value="(val: string) => emit('update:variant', val)">
          <el-option v-for="item in variantOptions" :key="item.value" :label="item.label" :value="item.value"></el-option>
        </el-select>
      </template>
      <!-- 范围态（§9 情境化改版）：范围随预览路径推导。共享布局 chip 只作影响范围提示；栏目页附
           仅此栏目定制 入口；独立布局态 chip 显示栏目名（行未创建 = 编辑后生效），出口 恢复为共享布局 -->
      <template v-if="scopeState !== 'hidden'">
        <el-divider direction="vertical" />
        <template v-if="scopeState === 'shared'">
          <el-tooltip :content="$t(`builder.scope.sharedTip.${page}`)" placement="bottom">
            <span class="scope-chip inline-flex h-7 shrink-0 items-center gap-1.5 rounded-full border border-gray-200 bg-gray-50 px-2.5 text-xs text-gray-regular">
              <el-icon><Share /></el-icon>
              {{ $t('builder.scope.shared') }}
            </span>
          </el-tooltip>
          <el-tooltip v-if="scopeCustomizable" :content="$t('builder.scope.customizeTip', { name: scopeChannelName })" placement="bottom">
            <el-button link type="primary" :disabled="cannotEdit" @click="() => emit('customizeScope')">{{ $t('builder.scope.customize') }}</el-button>
          </el-tooltip>
        </template>
        <template v-else>
          <span class="scope-chip inline-flex h-7 shrink-0 items-center gap-1.5 rounded-full border border-warning-light bg-warning-lighter px-2.5 text-xs text-warning">
            <el-icon><Aim /></el-icon>
            {{ scopeState === 'ownPending' ? $t('builder.scope.ownPending', { name: scopeChannelName }) : $t('builder.scope.own', { name: scopeChannelName }) }}
          </span>
          <el-button link type="primary" :disabled="cannotEdit" @click="() => emit('revertScope')">{{ $t('builder.scope.revert') }}</el-button>
        </template>
      </template>
    </div>

    <!-- 预览地址组（原子组）：宽屏弹性吸收剩余宽度，窄屏整组换行 -->
    <div class="topbar-address flex items-center gap-x-1.5">
      <!-- 预览路径选择器（preview-url-resolver P2）：候选分组展示，allow-create 保留自由输入（D3）；
           远程 q 过滤走解析端点；选定即触发既有渲染流 -->
      <el-select
        :model-value="pagePath"
        class="topbar-path"
        :title="$t('pagePreview.path')"
        filterable
        allow-create
        default-first-option
        remote
        reserve-keyword
        :remote-method="(q: string) => emit('fetchPaths', q)"
        :loading="pathLoading"
        :placeholder="$t('pagePreview.path.placeholder')"
        @visible-change="(open: boolean) => open && emit('fetchPaths', '')"
        @update:model-value="
          (val: string) => {
            emit('update:pagePath', val);
            emit('render');
          }
        "
      >
        <template #prefix>
          <el-icon><Link /></el-icon>
        </template>
        <!-- 候选两栏：名称为主、路径为右对齐的弱化等宽副信息；label 取路径——选中后输入框只显示路径 -->
        <el-option-group v-for="group in pathGroups" :key="group.kind" :label="group.label">
          <el-option v-for="item in group.options" :key="item.url" :label="item.url" :value="item.url">
            <div class="flex items-center justify-between gap-x-4">
              <span class="truncate">{{ item.label }}</span>
              <span class="shrink-0 font-mono text-xs text-gray-placeholder">{{ item.url }}</span>
            </div>
          </el-option>
        </el-option-group>
        <template #empty>
          <div class="px-3 py-2 text-xs text-gray-secondary">
            <template v-if="pathEmptyReason === 'no-content'">
              {{ $t('builder.path.noContent') }}
              <el-button link type="primary" size="small" @click="() => emit('seedData')">{{ $t('builder.path.goSeed') }}</el-button>
            </template>
            <template v-else>{{ $t('pagePreview.path.pickerEmpty') }}</template>
          </div>
        </template>
      </el-select>
      <el-button-group class="shrink-0 whitespace-nowrap">
        <el-tooltip :content="$t('pagePreview.refresh')" placement="bottom">
          <el-button :icon="Refresh" :aria-label="$t('pagePreview.refresh')" @click="() => emit('render')"></el-button>
        </el-tooltip>
        <el-tooltip :content="$t('pagePreview.openInNewTab')" placement="bottom">
          <el-button :icon="TopRight" :aria-label="$t('pagePreview.openInNewTab')" @click="() => emit('openInNewTab')"></el-button>
        </el-tooltip>
      </el-button-group>
    </div>

    <!-- 动作组：状态 · 撤销/重做 · 视口 · 视图 · 设置/历史 · 发布/丢弃 -->
    <div class="ml-auto flex shrink-0 flex-wrap items-center justify-end gap-x-2 gap-y-1">
      <span class="inline-flex h-7 shrink-0 items-center gap-1.5 rounded-full border px-2.5 text-xs" :class="STATUS_CHIP[statusState]" :title="pendingTitle">
        <el-icon v-if="statusState === 'saving'" class="is-loading"><Loading /></el-icon>
        <span v-else class="h-2 w-2 rounded-full" :class="statusState === 'pending' ? 'bg-warning' : 'bg-success'"></span>
        <span>{{ statusText }}</span>
      </span>
      <el-divider direction="vertical" />
      <el-button-group>
        <el-tooltip :content="`${$t('builder.undo')} ${undoHint}`" placement="bottom">
          <el-button :icon="RefreshLeft" :aria-label="$t('builder.undo')" :disabled="!canUndo || saving || cannotEdit" @click="() => emit('undo')"></el-button>
        </el-tooltip>
        <el-tooltip :content="`${$t('builder.redo')} ${redoHint}`" placement="bottom">
          <el-button :icon="RefreshRight" :aria-label="$t('builder.redo')" :disabled="!canRedo || saving || cannotEdit" @click="() => emit('redo')"></el-button>
        </el-tooltip>
      </el-button-group>
      <!-- 视口切换（P5 §10）：仅约束画布 iframe 宽度的预览开关（平板 = 横置手机图标）；结构视图下无画布，隐藏 -->
      <el-segmented
        v-if="!structureMode"
        :model-value="viewport"
        :options="deviceOptions"
        :aria-label="$t('builder.viewport.label')"
        class="shrink-0"
        @update:model-value="(val: string | number | boolean) => emit('update:viewport', String(val) as Viewport)"
      >
        <template #default="{ item }">
          <el-tooltip :content="item.label" placement="bottom">
            <span class="flex h-5 items-center px-0.5">
              <el-icon :class="{ 'rotate-90': item.value === 'tablet' }"><component :is="item.icon" /></el-icon>
              <span class="sr-only">{{ item.label }}</span>
            </span>
          </el-tooltip>
        </template>
      </el-segmented>
      <!-- 画布 / 结构视图（D7）：分段控件，图标 + 文字（替代无文字的开关） -->
      <el-segmented
        :model-value="structureMode ? 'structure' : 'canvas'"
        :options="viewOptions"
        :aria-label="$t('builder.viewMode')"
        class="shrink-0"
        @update:model-value="(val: string | number | boolean) => emit('update:structureMode', val === 'structure')"
      >
        <template #default="{ item }">
          <span class="flex items-center gap-1">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </span>
        </template>
      </el-segmented>
      <el-divider direction="vertical" />
      <el-button-group>
        <el-tooltip :content="$t('builder.themeSettings')" placement="bottom">
          <el-button :icon="Setting" :aria-label="$t('builder.themeSettings')" @click="() => emit('themeSettings')"></el-button>
        </el-tooltip>
        <el-tooltip :content="$t('builder.pageSettings')" placement="bottom">
          <el-button :icon="Operation" :aria-label="$t('builder.pageSettings')" @click="() => emit('pageSettings')"></el-button>
        </el-tooltip>
        <!-- 发布历史（region-history §9）：按区域的发布快照列表 + 恢复 -->
        <el-tooltip :content="$t('region.history.title')" placement="bottom">
          <el-button :icon="Clock" :aria-label="$t('region.history.title')" :disabled="perm('pageRegion:show')" @click="() => emit('history')"></el-button>
        </el-tooltip>
      </el-button-group>
      <el-divider direction="vertical" />
      <el-button type="primary" :icon="Upload" :disabled="pendingCount <= 0 || cannotEdit" @click="() => emit('publishAll')">
        {{ $t('region.op.publish') }}
        <span v-if="pendingCount > 0" class="ml-1.5 rounded-full bg-white/25 px-1.5 text-xs leading-4">{{ pendingCount }}</span>
      </el-button>
      <el-tooltip :content="$t('region.op.discard')" placement="bottom">
        <el-button :icon="Delete" plain :aria-label="$t('region.op.discard')" :disabled="pendingCount <= 0 || cannotEdit" @click="() => emit('discardAll')"></el-button>
      </el-tooltip>
    </div>
  </div>
</template>

<style scoped>
/* 间距统一由 flex gap 控制：EP 相邻按钮的默认左外边距归零（按钮组内部由 EP 自己接管） */
.topbar :deep(.el-button + .el-button) {
  margin-left: 0;
}
.topbar :deep(.el-divider--vertical) {
  height: 1.25rem;
  margin: 0 0.25rem;
}
.topbar-page {
  transition: background-color 0.15s;
}
.topbar-page:hover,
.topbar-page-open {
  background: var(--el-fill-color-light);
}
.topbar-page:focus-visible {
  box-shadow: 0 0 0 2px var(--el-color-primary-light-5);
}
.topbar-page-caret {
  transition: transform 0.15s;
}
.topbar-page-open .topbar-page-caret {
  transform: rotate(180deg);
}
/* 预览地址组整组弹性伸缩：宽屏吸收剩余空间，窄屏先于其他控件收缩，收缩到下限后整组换行 */
.topbar-address {
  flex: 1 1 16rem;
  min-width: 14rem;
}
.topbar-path {
  flex: 1 1 auto;
  min-width: 8rem;
  max-width: 36rem;
}
@media (prefers-reduced-motion: reduce) {
  .topbar-page,
  .topbar-page-caret {
    transition: none;
  }
}
</style>

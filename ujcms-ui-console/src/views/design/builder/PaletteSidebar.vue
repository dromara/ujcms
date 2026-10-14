<script setup lang="ts">
import { computed, nextTick, ref, type PropType } from 'vue';
import { useLocalStorage } from '@vueuse/core';
import type { InputInstance } from 'element-plus';
import { ArrowRight, Box, InfoFilled, Rank, Search } from '@element-plus/icons-vue';
import draggable from 'vuedraggable';
import { ShapeGlyph } from './ShapeGlyph';
import LayoutThumb from './LayoutThumb.vue';
import SidebarHeader from './SidebarHeader.vue';
import type { PaletteDragStart, PaletteItem } from './types';

/**
 * 组件调色板（visual-builder §7.1/§7.2/§7.4，P3；缩略图 P6/D11；双模式 P7；shell-polish 搜索/折叠组/键盘）：
 * 左侧可折叠栏，Shape 卡片按 presentation.group 分组（回退第一个 tag，再回退「其他」）、组内按
 * presentation.order 排序、缩略图为服务端解析的 URL（缺失或加载失败回退受控枚举 icon 的同语言线框占位图）。
 * 无任何区域可放置的条目置灰并给出原因（§7.4）。两套手势按 mode 严格互斥（P7/D12）：
 * - canvas：点击 = 点击放置（D4，无障碍/触屏路径；键盘 Enter/Space 同）；按下拖出 = 幽灵拖拽进
 *   iframe（§7.2）——指针按下后位移超过阈值判为拖拽，否则松开时判为点击（两条路径由 BuilderPage 编排）。
 * - structure：SortableJS 克隆源（pull:'clone'，不回收、不排序），拖入结构视图的列/空区域；
 *   校验经 structureMove 回调（Sortable 的 onMove 取自拖拽起点列表的实例，故挂在源侧；规则在 constraints.ts）。
 * 布局卡片（kind:'layout'，D15）：行布局目录的拖拽形态——画布态拖到行间隙 / 结构态拖入行列表
 * （克隆组 palette-rows 与 Shape 的 palette-shapes 隔离），放下即插入空行；无点击放置。
 * 搜索按显示名/组件名/描述过滤（保留分组）；组件目录通常不长，搜索框默认收起，头部放大镜按需展开
 * （关闭即清空）；组折叠与栏折叠状态持久化到 localStorage。
 */
defineOptions({
  name: 'PaletteSidebar',
});
const props = defineProps({
  items: { type: Array as PropType<PaletteItem[]>, default: () => [] },
  // 点击放置进行中的 Shape 名（对应卡片高亮）
  placingShape: { type: String as PropType<string | null>, default: null },
  disabled: { type: Boolean, default: false },
  // 画布态 = 指针手势（点击放置/幽灵拖拽）；结构态 = SortableJS 克隆拖拽（P7）
  mode: { type: String as PropType<'canvas' | 'structure'>, default: 'canvas' },
  // 结构态拖拽的 :move 校验（§7.4 对齐；BuilderPage 依据 evt.to 的 data-builder-* 定位目标）
  structureMove: { type: Function as PropType<(evt: any) => boolean>, default: null },
});
const emit = defineEmits<{
  // 点击放置（D4）：切换该 Shape 的放置模式
  pick: [name: string];
  // 幽灵拖拽起点：{ kind, name, label, event }
  dragStart: [payload: PaletteDragStart];
}>();

const collapsed = useLocalStorage<boolean>('ujcms.builder.palette.collapsed', false);
const collapsedGroups = useLocalStorage<string[]>('ujcms.builder.palette.collapsedGroups', []);
const query = ref<string>('');
const searchOpen = ref<boolean>(false);
const searchRef = ref<InputInstance>();

// 缩略图加载失败的条目（P6/D11 优雅降级：坏 URL / 主题图缺文件 → 回退 icon，不留破图）
const failedThumbs = ref<Record<string, boolean>>({});
const thumbOf = (item: PaletteItem): string | null => (item.kind === 'shape' && item.thumbnail && !failedThumbs.value[item.name] ? item.thumbnail : null);

interface PaletteGroup {
  name: string;
  items: PaletteItem[];
}

const matches = (item: PaletteItem, q: string): boolean => [item.label, item.name, item.description].some((s) => s.toLowerCase().includes(q));

const searching = computed<boolean>(() => query.value.trim() !== '');

// 搜索框按需展开：打开即聚焦；关闭（头部钮 / 输入框 Esc）即清空过滤
const toggleSearch = async (): Promise<void> => {
  searchOpen.value = !searchOpen.value;
  if (searchOpen.value) {
    await nextTick();
    searchRef.value?.focus();
  } else {
    query.value = '';
  }
};
const closeSearch = (): void => {
  if (searchOpen.value) void toggleSearch();
};

const groups = computed<PaletteGroup[]>(() => {
  const q = query.value.trim().toLowerCase();
  const byGroup = new Map<string, PaletteItem[]>();
  for (const item of props.items) {
    if (q !== '' && !matches(item, q)) continue;
    const list = byGroup.get(item.group) ?? [];
    list.push(item);
    byGroup.set(item.group, list);
  }
  return [...byGroup.entries()].map(([name, items]) => ({
    name,
    items: [...items].sort((a, b) => a.order - b.order || a.name.localeCompare(b.name)),
  }));
});

// 搜索中一律展开（过滤结果不能藏在折叠组里）
const isOpen = (name: string): boolean => searching.value || !collapsedGroups.value.includes(name);
const toggleGroup = (name: string): void => {
  if (searching.value) return;
  collapsedGroups.value = collapsedGroups.value.includes(name) ? collapsedGroups.value.filter((n) => n !== name) : [...collapsedGroups.value, name];
};

// ---- 结构态：SortableJS 克隆源（P7）--------------------------------------------------------------

// 克隆占位对象只带 __paletteShape / __paletteLayout 标记 + 展示标签；落点 change 处理器即刻剔除
// 并换成真实面板/行（标记键可 grep，意外存活会渲染成空芯片，肉眼可辨——绝不能混入保存载荷）
const cloneItem = (item: PaletteItem): any => (item.kind === 'layout' ? { __paletteLayout: item.name, label: item.label } : { __paletteShape: item.name, label: item.label });

// 布局卡片与 Shape 卡片走不同克隆组（组名匹配即隔离落点：布局只进行列表/空区域，Shape 只进列/空区域）；
// 分组按 group 恰含单一 kind（布局组由 BuilderPage 单独构造）
const groupConfig = (group: PaletteGroup): any =>
  group.items[0]?.kind === 'layout' ? { name: 'palette-rows', pull: 'clone', put: false } : { name: 'palette-shapes', pull: 'clone', put: false };

const onStructureMove = (evt: any): boolean => props.structureMove?.(evt) ?? true;

// ---- 画布态：点击 vs 拖拽判别：按下后位移 > 5px 判为拖拽，否则松开判为点击（触屏/键盘走点击放置） ----

let pressed: { kind: 'shape' | 'layout'; name: string; label: string; x: number; y: number; pointerId: number } | null = null;

const onPointerDown = (item: PaletteItem, e: PointerEvent): void => {
  if (props.mode !== 'canvas' || props.disabled || item.disabled || e.button !== 0) return;
  pressed = { kind: item.kind, name: item.name, label: item.label, x: e.clientX, y: e.clientY, pointerId: e.pointerId };
};

const onPointerMove = (e: PointerEvent): void => {
  if (pressed == null || e.pointerId !== pressed.pointerId) return;
  // 无按键的移动不可能是拖拽：按下后在条目外松开时 pressed 残留，这里兜底清掉（幽灵拖拽修复，P7）
  if (e.buttons === 0) {
    pressed = null;
    return;
  }
  if (Math.abs(e.clientX - pressed.x) + Math.abs(e.clientY - pressed.y) > 5) {
    const p = pressed;
    pressed = null;
    emit('dragStart', { kind: p.kind, name: p.name, label: p.label, event: e });
  }
};

const onPointerUp = (e: PointerEvent): void => {
  if (pressed == null || e.pointerId !== pressed.pointerId) return;
  const p = pressed;
  pressed = null;
  // 布局卡片无点击放置（行插入位置须由拖拽落点指定；无拖拽路径用检查器 添加行）
  if (p.kind !== 'layout') emit('pick', p.name);
};

const onPointerCancel = (): void => {
  pressed = null;
};

// 键盘路径（D4 无障碍）：Enter/Space = 点击放置；布局卡片与结构态无键盘动作（tabindex -1）
const keyboardable = (item: PaletteItem): boolean => props.mode === 'canvas' && !props.disabled && !item.disabled && item.kind !== 'layout';
const onKeyPick = (item: PaletteItem): void => {
  if (keyboardable(item)) emit('pick', item.name);
};
</script>

<template>
  <div class="palette flex h-full flex-col bg-white" :class="{ 'palette-collapsed': collapsed }">
    <sidebar-header v-model:collapsed="collapsed" :icon="Box" :title="$t('builder.palette.title')" :count="items.length" side="left" :toggle-label="$t('builder.palette.toggle')">
      <template #actions>
        <el-tooltip :content="$t('builder.palette.searchToggle')" placement="bottom">
          <el-button
            :icon="Search"
            link
            :type="searchOpen ? 'primary' : ''"
            :aria-label="$t('builder.palette.searchToggle')"
            :aria-pressed="searchOpen"
            :disabled="items.length <= 0"
            @click="toggleSearch"
          ></el-button>
        </el-tooltip>
      </template>
    </sidebar-header>
    <template v-if="!collapsed">
      <div v-if="searchOpen" class="shrink-0 border-b border-gray-200 p-2">
        <el-input ref="searchRef" v-model="query" size="small" clearable :prefix-icon="Search" :placeholder="$t('builder.palette.search')" @keydown.esc.stop="closeSearch" />
      </div>
      <div class="min-h-0 flex-1 overflow-auto p-2">
        <el-empty v-if="items.length <= 0" :description="$t('builder.palette.empty')" :image-size="48" />
        <div v-else-if="groups.length <= 0" class="py-4 text-center">
          <el-empty :description="$t('builder.palette.noMatch', { q: query.trim() })" :image-size="48" />
          <el-button link type="primary" size="small" @click="closeSearch">{{ $t('builder.palette.clearSearch') }}</el-button>
        </div>
        <div v-for="group in groups" :key="group.name" class="mb-2">
          <button
            type="button"
            class="palette-group flex w-full items-center gap-1 rounded px-1.5 py-1 text-xs font-semibold text-gray-regular hover:bg-gray-50"
            :aria-expanded="isOpen(group.name)"
            @click="() => toggleGroup(group.name)"
          >
            <el-icon class="palette-chevron text-gray-placeholder" :class="{ 'palette-chevron-open': isOpen(group.name) }"><ArrowRight /></el-icon>
            <span class="truncate">{{ group.name }}</span>
            <span class="ml-auto font-normal text-gray-placeholder">{{ group.items.length }}</span>
          </button>
          <!-- 组体：无 display 工具类的纯 v-show 容器（Tailwind important 会压过 v-show 的行内 display:none）。
               组内条目恒挂在克隆源 draggable 下（单一渲染路径）：画布态 disabled（指针手势接管）；
               结构态 pull:'clone' + put:false + sort:false 只出不进不排（:list 绑定，源数组零改写），
               置灰条目由 filter 排除（§7.4 原因见条目 tooltip）；条目根必须是普通 div 且 item 插槽内
               不能有注释节点（vuedraggable 按插槽唯一子节点的 vnode.el 映射 DOM），故 tooltip 挂在条目内部 -->
          <div v-show="isOpen(group.name)">
            <draggable
              :list="group.items"
              item-key="name"
              :group="groupConfig(group)"
              :sort="false"
              :clone="cloneItem"
              :move="onStructureMove"
              filter=".palette-item-disabled"
              :disabled="mode !== 'structure' || disabled"
              class="mt-1"
            >
              <template #item="{ element: item }">
                <div
                  class="palette-item mb-1.5 flex items-center gap-2 rounded-md border px-2 py-1.5"
                  :class="item.disabled ? 'palette-item-disabled' : placingShape === item.name ? 'palette-item-placing' : 'palette-item-enabled'"
                  role="button"
                  :tabindex="keyboardable(item) ? 0 : -1"
                  :aria-label="item.label"
                  :aria-disabled="disabled || item.disabled"
                  :aria-pressed="placingShape === item.name"
                  @pointerdown="(e: PointerEvent) => onPointerDown(item, e)"
                  @pointermove="onPointerMove"
                  @pointerup="onPointerUp"
                  @pointercancel="onPointerCancel"
                  @keydown.enter.prevent="() => onKeyPick(item)"
                  @keydown.space.prevent="() => onKeyPick(item)"
                >
                  <el-tooltip placement="right" :show-after="400">
                    <template #content>
                      <div class="max-w-60">
                        <div v-if="item.disabled">{{ item.disabledReason }}</div>
                        <template v-else>
                          <div>{{ item.description || item.label }}</div>
                          <div class="mt-0.5 font-mono text-xs opacity-70">{{ item.name }}</div>
                        </template>
                      </div>
                    </template>
                    <div class="flex min-w-0 flex-1 items-center gap-2">
                      <!-- 三种视觉形态共用同一 64×40 瓦片框（palette-tile）：布局迷你分栏图 / 缩略图 / icon 回退 -->
                      <layout-thumb v-if="item.kind === 'layout'" :widths="item.widths" class="palette-tile" :class="{ 'palette-tile-disabled': item.disabled }" />
                      <img
                        v-else-if="thumbOf(item)"
                        class="palette-tile palette-thumb"
                        :class="{ 'palette-tile-disabled': item.disabled }"
                        :src="item.thumbnail"
                        :alt="item.label"
                        draggable="false"
                        @error="failedThumbs[item.name] = true"
                      />
                      <div v-else class="palette-tile overflow-hidden" :class="{ 'palette-tile-disabled': item.disabled }">
                        <shape-glyph :icon="item.icon" />
                      </div>
                      <div class="min-w-0 flex-1">
                        <div class="truncate text-sm font-medium" :class="item.disabled ? 'text-gray-placeholder' : 'text-gray-primary'">{{ item.label }}</div>
                        <div v-if="item.description" class="truncate text-xs text-gray-secondary">{{ item.description }}</div>
                      </div>
                      <el-icon class="palette-grip shrink-0 text-gray-placeholder"><Rank /></el-icon>
                    </div>
                  </el-tooltip>
                </div>
              </template>
            </draggable>
          </div>
        </div>
      </div>
      <!-- 模式提示：画布态点击放置进行中 / 结构态拖入引导 -->
      <div v-if="placingShape != null" class="shrink-0 border-t border-primary-light bg-primary-lighter px-3 py-2 text-xs leading-5 text-primary">
        <el-icon class="mr-1 align-text-bottom"><InfoFilled /></el-icon>{{ $t('builder.palette.placing') }}
      </div>
      <div v-else-if="mode === 'structure' && items.length > 0" class="shrink-0 border-t border-gray-200 bg-gray-50 px-3 py-2 text-xs leading-5 text-gray-secondary">
        {{ $t('builder.palette.structureHint') }}
      </div>
    </template>
  </div>
</template>

<style scoped>
.palette {
  flex-shrink: 0;
  width: 256px;
  transition: width 0.15s;
}
.palette-collapsed {
  width: 40px;
}
.palette-chevron {
  transition: transform 0.15s;
}
.palette-chevron-open {
  transform: rotate(90deg);
}
/* 条目状态样式统一走此处的 EP 变量（不在元素上叠 Tailwind hover 类，避免 important 相争） */
.palette-item {
  touch-action: none;
  user-select: none;
  border-color: var(--el-border-color-lighter);
  transition:
    box-shadow 0.15s,
    border-color 0.15s,
    transform 0.15s;
}
.palette-item-enabled {
  cursor: grab;
}
.palette-item-enabled:hover,
.palette-item-enabled:focus-visible {
  border-color: var(--el-color-primary-light-5);
  outline: none;
  box-shadow:
    0 0 0 2px var(--el-color-primary-light-8),
    var(--el-box-shadow-light);
  transform: translateY(-1px);
}
.palette-item-enabled:hover .palette-grip,
.palette-item-enabled:focus-visible .palette-grip {
  opacity: 1;
}
.palette-item-placing {
  cursor: pointer;
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-7);
}
.palette-item-disabled {
  cursor: not-allowed;
  background: var(--el-fill-color-lighter);
}
.palette-grip {
  opacity: 0;
  transition: opacity 0.15s;
}
/* 统一瓦片框：布局图 / 缩略图 / icon 回退同尺寸同边框 */
.palette-tile {
  flex-shrink: 0;
  width: 64px;
  height: 40px;
  pointer-events: none;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}
.palette-tile-disabled {
  opacity: 0.5;
  filter: grayscale(1);
}
.palette-thumb {
  object-fit: cover;
}
@media (prefers-reduced-motion: reduce) {
  .palette,
  .palette-chevron,
  .palette-item,
  .palette-grip {
    transition: none;
  }
  .palette-item-enabled:hover,
  .palette-item-enabled:focus-visible {
    transform: none;
  }
}
</style>

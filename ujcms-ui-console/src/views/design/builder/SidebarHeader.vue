<script setup lang="ts">
import type { Component, PropType } from 'vue';
import { DArrowLeft, DArrowRight } from '@element-plus/icons-vue';

/**
 * 侧栏统一头部（shell-polish 后续：左右两栏同高同构）：种类图标芯片 + 标题 + 计数胶囊 + 动作插槽 + 折叠钮，
 * 高 44px 与顶栏齐平。折叠态退化为窄条，图标本身即展开按钮（tooltip 朝栏外侧弹出）。
 */
defineOptions({
  name: 'SidebarHeader',
});
defineProps({
  icon: { type: [Object, Function] as PropType<Component>, required: true },
  title: { type: String, required: true },
  // 标题旁的计数胶囊（null = 不显示）
  count: { type: Number as PropType<number | null>, default: null },
  collapsed: { type: Boolean, default: false },
  // 栏所在侧：决定折叠箭头方向与折叠态 tooltip 的弹出方向
  side: { type: String as PropType<'left' | 'right'>, default: 'left' },
  // 折叠/展开钮的无障碍名
  toggleLabel: { type: String, required: true },
});
defineEmits<{ 'update:collapsed': [val: boolean] }>();
</script>

<template>
  <div class="sidebar-header flex h-11 shrink-0 items-center border-b border-gray-200" :class="collapsed ? 'justify-center' : 'gap-2 px-3'">
    <el-tooltip v-if="collapsed" :content="$t('expand')" :placement="side === 'left' ? 'right' : 'left'">
      <button type="button" class="sidebar-rail flex h-7 w-7 items-center justify-center rounded-md text-gray-regular" :aria-label="toggleLabel" @click="$emit('update:collapsed', false)">
        <el-icon><component :is="icon" /></el-icon>
      </button>
    </el-tooltip>
    <template v-else>
      <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-primary-lighter text-primary">
        <el-icon><component :is="icon" /></el-icon>
      </span>
      <div class="flex min-w-0 flex-1 items-center gap-1.5">
        <span class="truncate text-sm font-semibold text-gray-primary">{{ title }}</span>
        <span v-if="count != null" class="shrink-0 rounded-full bg-gray-100 px-1.5 text-xs leading-4 tabular-nums text-gray-secondary">{{ count }}</span>
        <slot />
      </div>
      <slot name="actions" />
      <el-tooltip :content="$t('collapse')" placement="bottom">
        <el-button :icon="side === 'left' ? DArrowLeft : DArrowRight" link :aria-label="toggleLabel" @click="$emit('update:collapsed', true)"></el-button>
      </el-tooltip>
    </template>
  </div>
</template>

<style scoped>
/* 动作区按钮间距由 gap 控制：EP 相邻按钮的默认左外边距归零 */
.sidebar-header :deep(.el-button + .el-button) {
  margin-left: 0;
}
.sidebar-rail:hover,
.sidebar-rail:focus-visible {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  outline: none;
}
.sidebar-rail:focus-visible {
  box-shadow: 0 0 0 2px var(--el-color-primary-light-5);
}
</style>

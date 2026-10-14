<script setup lang="ts">
import type { PropType } from 'vue';
import { localizedLabel } from '@/i18n';
import type { RowLayout } from './types';
import LayoutThumb from './LayoutThumb.vue';

/**
 * 行布局可视选择器（检查器：行 → 改布局；区域 → 添加行）：网格瓦片 + 迷你分栏图，
 * 替代纯文字下拉。modelValue 为当前选中键（添加行场景无当前项，传 null）。
 */
defineOptions({
  name: 'LayoutPicker',
});
defineProps({
  layouts: { type: Array as PropType<RowLayout[]>, default: () => [] },
  modelValue: { type: String as PropType<string | null>, default: null },
  disabled: { type: Boolean, default: false },
});
const emit = defineEmits<{ pick: [key: string] }>();
</script>

<template>
  <div class="grid grid-cols-3 gap-2">
    <button
      v-for="l in layouts"
      :key="l.key"
      type="button"
      class="layout-tile flex flex-col gap-1 rounded-md border bg-white p-1.5 text-left transition"
      :class="l.key === modelValue ? 'border-primary bg-primary-lighter ring-2 ring-primary-light' : 'border-gray-200 hover:border-primary-light hover:shadow-sm'"
      :disabled="disabled"
      :aria-pressed="l.key === modelValue"
      :title="`${localizedLabel(l.label, l.key)} · ${l.columns} ${$t('region.columns')}`"
      @click="() => emit('pick', l.key)"
    >
      <layout-thumb :widths="l.widths" class="h-9 w-full rounded border border-gray-100 bg-gray-50" />
      <span class="block truncate text-center text-xs text-gray-regular">{{ localizedLabel(l.label, l.key) }}</span>
    </button>
  </div>
</template>

<style scoped>
.layout-tile:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.layout-tile:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 1px;
}
</style>

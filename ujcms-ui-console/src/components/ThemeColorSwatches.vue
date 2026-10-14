<script setup lang="ts">
import { Check } from '@element-plus/icons-vue';
import { THEME_COLORS, THEME_COLOR_NAMES, type ThemeColor } from '@/utils/theme';

/**
 * 主题色六色块（console-shell D2）：个人“外观设置”与“全局设置 › 基础设置 › 控制台默认外观”共用。
 * v-model 为预设中的 #rrggbb；点选即 emit，是否即时预览由调用方决定。
 */
defineOptions({
  name: 'ThemeColorSwatches',
});
defineProps({ modelValue: { type: String, required: true }, label: { type: String, default: '' } });
defineEmits<{ 'update:modelValue': [value: ThemeColor] }>();
</script>

<template>
  <div class="flex gap-3" role="radiogroup" :aria-label="label">
    <button
      v-for="(color, index) in THEME_COLORS"
      :key="color"
      type="button"
      role="radio"
      :aria-checked="modelValue === color"
      :aria-label="$t(`shell.appearance.color.${THEME_COLOR_NAMES[index]}`)"
      :title="$t(`shell.appearance.color.${THEME_COLOR_NAMES[index]}`)"
      class="flex items-center justify-center w-7 h-7 rounded-full text-white"
      :style="{ backgroundColor: color }"
      @click="() => $emit('update:modelValue', color)"
    >
      <el-icon v-if="modelValue === color" :size="14"><Check /></el-icon>
    </button>
  </div>
</template>

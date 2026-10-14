<script setup lang="ts">
/**
 * 行布局迷你分栏图：按 widths 比例绘制若干色块（调色板布局卡片 / 检查器布局选择 / 结构视图下拉共用）。
 * 尺寸由宿主的 class 决定（本组件只定分栏与配色）。
 */
defineOptions({
  name: 'LayoutThumb',
});
defineProps<{ widths: number[] }>();
</script>

<template>
  <div class="layout-thumb" aria-hidden="true">
    <span v-for="(w, i) in widths" :key="i" :style="{ flex: w }"></span>
  </div>
</template>

<style scoped>
.layout-thumb {
  display: flex;
  gap: 2px;
  box-sizing: border-box;
  padding: 4px;
}
.layout-thumb > span {
  display: block;
  min-width: 0;
  background: var(--el-color-primary-light-5);
  border-radius: 2px;
}
</style>

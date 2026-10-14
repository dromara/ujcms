<script setup lang="ts">
/** 首页区块的卡片壳：标题行（标题 · #action）+ 内容。五个区块共用，标题行因此一致 */
defineOptions({
  name: 'HomeCard',
});
defineProps<{ title: string }>();
</script>

<template>
  <div class="app-block p-4">
    <div class="flex items-center justify-between mb-3">
      <div class="text-base font-medium text-gray-primary">{{ title }}</div>
      <slot name="action"></slot>
    </div>
    <slot></slot>
  </div>
</template>

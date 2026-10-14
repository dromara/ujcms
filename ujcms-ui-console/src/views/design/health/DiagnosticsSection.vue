<script setup lang="ts">
// 诊断页小节：标题 + 计数 + 说明 + 内容（表格）。tone 区分「问题」（警示色计数）与「信息」（中性计数）
withDefaults(defineProps<{ title: string; count: number; tip?: string; tone?: 'warning' | 'info' }>(), { tip: undefined, tone: 'warning' });
</script>

<template>
  <section class="app-block px-6 py-5">
    <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
      <h2 class="text-base font-semibold text-gray-primary">{{ title }}</h2>
      <el-tag :type="tone" size="small" round>{{ count }}</el-tag>
      <div v-if="$slots.actions" class="ml-auto flex items-center">
        <slot name="actions"></slot>
      </div>
    </div>
    <p v-if="tip" class="mt-1.5 max-w-4xl text-sm leading-relaxed text-gray-secondary">{{ tip }}</p>
    <div class="mt-3">
      <slot></slot>
    </div>
  </section>
</template>

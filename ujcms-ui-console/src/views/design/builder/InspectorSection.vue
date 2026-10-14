<script setup lang="ts">
/**
 * 检查器节标题（主色竖条 + 标题；原三处复制的组头合一）。外边距由宿主控制（首节 mt-0，其余 mt-4）。
 */
defineOptions({
  name: 'InspectorSection',
});
defineProps<{ title: string }>();
</script>

<template>
  <div class="mb-2 flex items-center gap-1.5 border-b border-gray-100 pb-1.5">
    <span class="h-3.5 w-1 shrink-0 rounded-full bg-primary"></span>
    <span class="min-w-0 flex-1 truncate text-sm font-semibold text-gray-primary">{{ title }}</span>
    <slot name="extra" />
  </div>
</template>

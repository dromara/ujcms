<script setup lang="ts">
import { QuestionFilled } from '@element-plus/icons-vue';

// 根元素带 data-label（标签键）：命令面板据此定位设置项（views/config/settingsFocus.ts）

defineProps({
  label: { type: String, default: null },
  tooltip: { type: String, default: null },
  help: { type: Boolean, default: false },
  message: { type: String, default: '' },
  fixWidth: { type: Boolean, default: true },
});
</script>

<template>
  <div class="inline-flex items-center" :data-label="message || undefined">
    <div class="overflow-hidden text-clip whitespace-nowrap" :title="label ?? $t(message)">{{ label ?? $t(message) }}</div>
    <el-tooltip v-if="help" :content="tooltip ?? $t(message + '.tooltip')" placement="top">
      <el-icon class="text-base align-text-top"><QuestionFilled /></el-icon>
    </el-tooltip>
  </div>
</template>

<script setup lang="ts">
import { QuestionFilled } from '@element-plus/icons-vue';

defineProps({
  label: { type: String, default: null },
  tooltip: { type: String, default: null },
  help: { type: Boolean, default: false },
  message: { type: String, default: '' },
  fixWidth: { type: Boolean, default: true },
});
</script>

<template>
  <div class="inline-flex items-center">
    <div class="overflow-hidden text-clip whitespace-nowrap" :title="label ?? $t(message)">{{ label ?? $t(message) }}</div>
    <el-tooltip v-if="help" :content="tooltip ?? $t(message + '.tooltip')" placement="top">
      <el-icon class="text-base align-text-top"><QuestionFilled /></el-icon>
    </el-tooltip>
  </div>
</template>

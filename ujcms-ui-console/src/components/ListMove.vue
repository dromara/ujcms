<script setup lang="ts">
import { Top, Bottom, ArrowUp, ArrowDown } from '@element-plus/icons-vue';

defineProps({
  disabled: { type: Boolean, required: true },
});
defineEmits({
  move: null,
});
</script>

<template>
  <el-button-group>
    <el-button :disabled="disabled" :icon="Top" @click="() => $emit('move', 'top')">{{ $t('moveTop') }}</el-button>
    <el-button :disabled="disabled" :icon="ArrowUp" @click="() => $emit('move', 'up')">{{ $t('moveUp') }}</el-button>
    <el-button :disabled="disabled" :icon="ArrowDown" @click="() => $emit('move', 'down')">{{ $t('moveDown') }}</el-button>
    <el-button :disabled="disabled" :icon="Bottom" @click="() => $emit('move', 'bottom')">{{ $t('moveBottom') }}</el-button>
  </el-button-group>
</template>

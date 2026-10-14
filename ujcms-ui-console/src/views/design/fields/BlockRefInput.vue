<script setup lang="ts">
import { computed, toRefs } from 'vue';
import { queryBlockList } from '@/api/config';
import { useRefCatalog } from './refCatalog';
import { refId, refAlias } from './refValue';

defineOptions({
  name: 'BlockRefInput',
});
const props = defineProps({
  // 区块ID（blockRef 字段值：数字/数字串/{id}/{alias} 种子形态）
  modelValue: { type: [Number, String, Object], default: null },
});
defineEmits({ 'update:modelValue': null });

const { modelValue } = toRefs(props);
const { catalog, loaded } = useRefCatalog('block', (): Promise<any[]> => queryBlockList());
const blockList = computed<any[]>(() => catalog.value ?? []);

// 存储值归一为选项可匹配的字符串ID（别名形态按已加载目录解析）
const selectValue = computed<string | undefined>(() => {
  const id = refId(modelValue.value);
  if (id != null) return id;
  const alias = refAlias(modelValue.value);
  if (alias == null) return undefined;
  const block = blockList.value.find((item) => item.alias === alias);
  return block != null ? String(block.id) : undefined;
});
</script>

<template>
  <!-- 目录未到前不下发值：el-select 对无匹配选项的值直接显示裸ID -->
  <el-select
    :model-value="loaded ? selectValue : undefined"
    :loading="!loaded"
    clearable
    :placeholder="loaded ? $t('compositionField.blockRef.pick') : ''"
    class="w-full"
    @update:model-value="(val: any) => $emit('update:modelValue', val ?? null)"
  >
    <el-option v-for="block in blockList" :key="block.id" :label="block.name" :value="String(block.id)">
      <span>{{ block.name }}</span>
      <span class="ml-2 text-xs text-gray-400">{{ block.alias }}</span>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
import { computed, toRefs } from 'vue';
import { queryChannelList } from '@/api/content';
import { toTree } from '@/utils/tree';
import { useRefCatalog } from './refCatalog';
import { refId, refAlias } from './refValue';

defineOptions({
  name: 'ChannelRefInput',
});
const props = defineProps({
  // 栏目ID（channelRef 字段值：数字/数字串/{id}/{alias} 种子形态）
  modelValue: { type: [Number, String, Object], default: null },
});
defineEmits({ 'update:modelValue': null });

const { modelValue } = toRefs(props);
// 树随目录一起缓存：toTree 就地挂 children，同一列表不能重复建树
const { catalog, loaded } = useRefCatalog('channel', async () => {
  const list: any[] = await queryChannelList();
  return { list, tree: toTree(list) };
});
const channelList = computed<any[]>(() => catalog.value?.list ?? []);
const channelTree = computed<any[]>(() => catalog.value?.tree ?? []);

// 存储值归一为节点可匹配的字符串ID（别名形态按已加载目录解析）
const selectValue = computed<string | undefined>(() => {
  const id = refId(modelValue.value);
  if (id != null) return id;
  const alias = refAlias(modelValue.value);
  if (alias == null) return undefined;
  const channel = channelList.value.find((item) => item.slug === alias);
  return channel != null ? String(channel.id) : undefined;
});
</script>

<template>
  <!-- 目录未到前不下发值：树选择器对无匹配节点的值直接显示裸ID -->
  <el-tree-select
    :model-value="loaded ? selectValue : undefined"
    :data="channelTree"
    :loading="!loaded"
    node-key="id"
    :props="{ label: 'name' }"
    :render-after-expand="false"
    check-strictly
    clearable
    :placeholder="loaded ? $t('compositionField.channelRef.pick') : ''"
    class="w-full"
    @update:model-value="(val: any) => $emit('update:modelValue', val ?? null)"
  />
</template>

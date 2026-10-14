<script setup lang="ts">
import { computed } from 'vue';
import { queryVotePage } from '@/api/interaction';
import { useRefCatalog } from './refCatalog';
import { refId } from './refValue';

defineOptions({
  name: 'VoteRefInput',
});
defineProps({
  // 投票ID（voteRef 字段值：数字/数字串/{id}；无别名形态）
  modelValue: { type: [Number, String, Object], default: null },
});
defineEmits({ 'update:modelValue': null });

// 选择器口径：取前 100 条（站点内投票数量级远低于此；后端 page/pageSize 为必填参数）
const { catalog, loaded } = useRefCatalog('vote', async (): Promise<any[]> => (await queryVotePage({ page: 1, pageSize: 100 })).content);
const voteList = computed<any[]>(() => catalog.value ?? []);
</script>

<template>
  <!-- 目录未到前不下发值：el-select 对无匹配选项的值直接显示裸ID -->
  <el-select
    :model-value="loaded ? refId(modelValue) : undefined"
    :loading="!loaded"
    clearable
    :placeholder="loaded ? $t('compositionField.voteRef.pick') : ''"
    class="w-full"
    @update:model-value="(val: any) => $emit('update:modelValue', val ?? null)"
  >
    <el-option v-for="vote in voteList" :key="vote.id" :label="vote.title" :value="String(vote.id)"></el-option>
  </el-select>
</template>

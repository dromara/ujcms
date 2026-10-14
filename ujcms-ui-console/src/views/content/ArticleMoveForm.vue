<script setup lang="ts">
import { ref, watch, toRefs, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { toTree, disableParentTree, filterTreeNodeByName } from '@/utils/tree';
import { queryChannelList, moveArticle } from '@/api/content';

// 批量移动栏目（docs/spec/article-management/plan.md Phase 3）。栏目选择与文章表单同一规则：文章权限之内的
// 文章栏目，只能选末级。哪些文章会被略过由服务端判定，这里事先说明、事后报数
defineOptions({
  name: 'ArticleMoveForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  ids: { type: Array as PropType<string[]>, default: () => [] },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });

const { ids, modelValue: visible } = toRefs(props);
const { t } = useI18n();
const form = ref<any>();
const values = ref<{ channelId?: string }>({});
const channelData = ref<any[]>([]);
const channelLoading = ref<boolean>(false);
const buttonLoading = ref<boolean>(false);

watch(visible, async () => {
  if (!visible.value) {
    return;
  }
  values.value = {};
  form.value?.clearValidate();
  // 每次打开都重新读取：栏目可能刚被别人增删
  channelLoading.value = true;
  try {
    channelData.value = disableParentTree(toTree(await queryChannelList({ isArticlePermission: true, isReal: true })));
  } finally {
    channelLoading.value = false;
  }
});

const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid || values.value.channelId == null) return;
    buttonLoading.value = true;
    try {
      const { moved, skipped } = await moveArticle(ids.value, values.value.channelId);
      emit('finished');
      emit('update:modelValue', false);
      if (moved === 0) {
        ElMessage.warning(t('article.move.resultNone', { skipped }));
      } else if (skipped > 0) {
        ElMessage.success(t('article.move.resultSkipped', { moved, skipped }));
      } else {
        ElMessage.success(t('article.move.result', { moved }));
      }
    } finally {
      buttonLoading.value = false;
    }
  });
};
</script>

<template>
  <el-dialog :title="$t('article.op.move')" :model-value="modelValue" :width="560" @update:model-value="(event: boolean) => $emit('update:modelValue', event)">
    <el-form ref="form" :model="values" label-position="top" @submit.prevent>
      <div class="mb-3 text-sm text-gray-secondary">{{ $t('article.selectedCount', { count: ids.length }) }}</div>
      <el-form-item prop="channelId" :label="$t('article.move.target')" :rules="[{ required: true, message: () => $t('v.required') }]">
        <el-tree-select
          v-model="values.channelId"
          v-loading="channelLoading"
          :data="channelData"
          node-key="id"
          :props="{ label: 'name', disabled: 'disabled' }"
          :render-after-expand="false"
          :filter-node-method="filterTreeNodeByName"
          filterable
          default-expand-all
          class="w-full"
        />
      </el-form-item>
      <div class="text-xs leading-5 text-gray-secondary">{{ $t('article.move.tip') }}</div>
    </el-form>
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
      <el-button type="primary" :loading="buttonLoading" :disabled="ids.length <= 0" @click="() => handleSubmit()">{{ $t('ok') }}</el-button>
    </template>
  </el-dialog>
</template>

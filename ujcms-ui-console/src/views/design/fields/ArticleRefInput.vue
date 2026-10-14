<script setup lang="ts">
import { ref } from 'vue';
import { Search } from '@element-plus/icons-vue';
import ArticlePickerDialog from '@/components/ArticlePicker/ArticlePickerDialog.vue';
import { refId } from './refValue';

defineOptions({
  name: 'ArticleRefInput',
});
defineProps({
  // 文章ID（articleRef 字段值：数字/数字串/{id}；无别名形态）
  modelValue: { type: [Number, String, Object], default: null },
});
const emit = defineEmits({ 'update:modelValue': null });

const pickerVisible = ref<boolean>(false);
// 选中文章的标题（仅会话内回显；持久值只有ID）
const pickedTitle = ref<string>('');

// 共享文章选择对话框（docs/spec/article-related/plan.md §5.1），单选：选中即关闭
const handlePick = (rows: any[]) => {
  const row = rows[0];
  if (!row) return;
  pickedTitle.value = row.title;
  emit('update:modelValue', row.id);
};
</script>

<template>
  <div class="w-full flex items-center space-x-2">
    <el-input
      :model-value="refId(modelValue) ?? ''"
      :placeholder="$t('compositionField.articleRef.placeholder')"
      clearable
      class="flex-1"
      @update:model-value="(val: string) => $emit('update:modelValue', val !== '' ? val : null)"
    >
      <template v-if="pickedTitle !== ''" #prefix>
        <span class="text-gray-400 text-xs max-w-40 truncate">{{ pickedTitle }}</span>
      </template>
    </el-input>
    <el-button :icon="Search" @click="() => (pickerVisible = true)">{{ $t('compositionField.articleRef.pick') }}</el-button>

    <article-picker-dialog v-model="pickerVisible" :multiple="false" :statuses="[0, 1]" :title="$t('compositionField.articleRef.pick')" @pick="handlePick" />
  </div>
</template>

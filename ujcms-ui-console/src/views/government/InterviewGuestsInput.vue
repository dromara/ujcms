<script setup lang="ts">
import { ref, watch } from 'vue';
import { Delete, Plus } from '@element-plus/icons-vue';
import { useFormDisabled, useFormItem } from 'element-plus';
import { type InterviewGuest, formatGuests, parseGuests } from './interviewGuests';

defineOptions({
  name: 'InterviewGuestsInput',
});
const props = defineProps({ modelValue: { type: String, default: '' } });
const emit = defineEmits({ 'update:modelValue': null });

const disabled = useFormDisabled();
const { formItem } = useFormItem();
const rows = ref<InterviewGuest[]>([]);

// 外部值变化（打开编辑、切换上一条/下一条）时重新拆行；由本组件拼接产生的值与当前行一致时不重置，避免打断输入
watch(
  () => props.modelValue,
  (value) => {
    if (formatGuests(rows.value) !== (value ?? '')) {
      rows.value = parseGuests(value);
    }
  },
  { immediate: true },
);

const sync = () => {
  emit('update:modelValue', formatGuests(rows.value));
  formItem?.validate?.('change').catch(() => undefined);
};
const add = () => {
  rows.value.push({ name: '', title: '' });
};
const remove = (index: number) => {
  rows.value.splice(index, 1);
  sync();
};
</script>

<template>
  <div class="w-full space-y-2">
    <div v-for="(row, index) in rows" :key="index" class="flex items-center gap-2">
      <el-input v-model="row.name" maxlength="50" :placeholder="$t('interview.guests.name')" class="!w-48" @input="sync"></el-input>
      <el-input v-model="row.title" maxlength="100" :placeholder="$t('interview.guests.title')" class="flex-1" @input="sync"></el-input>
      <el-button :icon="Delete" :disabled="disabled" :title="$t('delete')" circle plain @click="() => remove(index)"></el-button>
    </div>
    <div class="flex items-center gap-3">
      <el-button :icon="Plus" :disabled="disabled" @click="() => add()">{{ $t('interview.guests.add') }}</el-button>
      <span v-if="rows.length > 0" class="text-xs text-gray-secondary">{{ $t('interview.guests.preview') }}{{ $t('punct.colon') }}{{ formatGuests(rows) || '—' }}</span>
    </div>
  </div>
</template>

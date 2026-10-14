<script setup lang="ts">
import { ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { createCensus } from '@/api/census';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'CensusForm',
});
const props = defineProps({ modelValue: { type: Boolean, required: true } });
const emit = defineEmits({ 'update:modelValue': null, finished: null });

const { t } = useI18n();
const form = ref<any>();
const values = ref<any>({});
const buttonLoading = ref<boolean>(false);

watch(
  () => props.modelValue,
  () => {
    if (props.modelValue) {
      const now = dayjs();
      values.value = { name: t('census.nameDefault', { year: now.year(), quarter: Math.floor(now.month() / 3) + 1 }) };
      form.value?.resetFields();
    }
  },
);

const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      await createCensus(values.value);
      ElMessage.success(t('success'));
      emit('update:modelValue', false);
      emit('finished');
    } finally {
      buttonLoading.value = false;
    }
  });
};
</script>

<template>
  <el-dialog
    :title="$t('add')"
    :model-value="modelValue"
    :close-on-click-modal="false"
    width="576px"
    top="10vh"
    @update:model-value="(event) => $emit('update:modelValue', event)"
  >
    <el-form ref="form" :model="values" label-position="top">
      <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="census.name" /></template>
        <el-input v-model="values.name" maxlength="150"></el-input>
      </el-form-item>
      <el-alert type="info" :closable="false" show-icon>{{ $t('census.scanTip') }}</el-alert>
    </el-form>
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
      <el-button type="primary" :loading="buttonLoading" @click="() => handleSubmit()">{{ $t('submit') }}</el-button>
    </template>
  </el-dialog>
</template>

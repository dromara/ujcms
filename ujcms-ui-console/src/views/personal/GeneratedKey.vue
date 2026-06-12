<script setup lang="ts">
import { toRefs, watch, ref } from 'vue';
import { queryGeneratedKey } from '@/api/personal';

defineOptions({
  name: 'GeneratedKey',
});
const props = defineProps({ modelValue: { type: Boolean, required: true } });
defineEmits({ 'update:modelValue': null });

const { modelValue: visible } = toRefs(props);
const loading = ref<boolean>(false);
const values = ref<any>({});

const fetchGeneratedKey = async () => {
  loading.value = true;
  try {
    values.value = await queryGeneratedKey();
  } finally {
    loading.value = false;
  }
};
watch(visible, async () => {
  if (visible.value) {
    fetchGeneratedKey();
  }
});
</script>

<template>
  <el-dialog :title="$t('menu.personal.homepage.generatedKey')" :model-value="modelValue" :width="820" @update:model-value="(event) => $emit('update:modelValue', event)">
    <el-input v-loading="loading" :model-value="values" autosize :readonly="true" type="textarea" />
    <el-button v-loading="loading" class="mt-2" type="primary" @click.prevent="fetchGeneratedKey">{{ $t('refresh') }}</el-button>
  </el-dialog>
</template>

<script setup lang="ts">
import { toRefs, watch, ref } from 'vue';
import { queryMachineCode } from '@/api/personal';

defineOptions({
  name: 'MachineCode',
});
const props = defineProps({ modelValue: { type: Boolean, required: true } });
defineEmits({ 'update:modelValue': null });
const { modelValue: visible } = toRefs(props);
const loading = ref<boolean>(false);
const machineCode = ref<string>();
const unwatch = watch(visible, async () => {
  if (visible.value) {
    loading.value = true;
    try {
      machineCode.value = await queryMachineCode();
    } finally {
      loading.value = false;
      unwatch();
    }
  }
});
</script>

<template>
  <el-dialog :title="$t('menu.personal.machine.code')" :model-value="modelValue" :width="820" top="5vh" @update:model-value="(event) => $emit('update:modelValue', event)">
    <!-- <pre><code class="whitespace-pre-wrap">{{ machineCode }}</code></pre> -->
    <el-input v-loading="loading" :model-value="machineCode" autosize :readonly="true" type="textarea" />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, type PropType } from 'vue';
import { queryPerformanceType, createPerformanceType, updatePerformanceType, deletePerformanceType } from '@/api/config';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'PerformanceTypeForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.config.performanceType')"
    :query-bean="queryPerformanceType"
    :create-bean="createPerformanceType"
    :update-bean="updatePerformanceType"
    :delete-bean="deletePerformanceType"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ score: 1 })"
    :to-values="(bean) => ({ ...bean })"
    :model-value="modelValue"
    perms="performanceType"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{}">
      <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="performanceType.name" /></template>
        <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="score" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="performanceType.score" help /></template>
        <el-input-number v-model="values.score" :min="0" :max="999999"></el-input-number>
      </el-form-item>
    </template>
  </dialog-form>
</template>
